/*
 * File: AuthRouting.kt
 * Module: feature.auth
 *
 * Auth endpoints (REAL — no hardcoded OTP, no demo users):
 *
 *   POST /api/v1/auth/check-user   (public)
 *   POST /api/v1/auth/send-otp     (public)            -- generates real OTP
 *   POST /api/v1/auth/verify-otp   (public)            -- verifies + (optional) logs in
 *   POST /api/v1/auth/signup       (public)            -- needs verified OTP for phone OR password+email
 *   POST /api/v1/auth/login        (public)            -- password (email) OR OTP (phone)
 *   POST /api/v1/auth/refresh      (public)            -- exchange refresh token for new access token
 *
 * Identifier rules
 * ----------------
 *  - Phone:  E.164 form expected (e.g. +919876543210).  We normalise:
 *      "9876543210"      → "+919876543210"  (assume IN)
 *      "+919876543210"   → unchanged
 *      "+1 415 555 2671" → "+14155552671"   (strip spaces)
 *  - Email:  trim + lower-case.
 *
 * OTP flow
 * --------
 *   1) Client calls /send-otp { "identifier": "+91…", "purpose": "login" }
 *      → server generates code, persists in auth_otps, dispatches via the
 *        configured provider (mock prints to stdout).  If OTP_DEV_RETURN_CODE
 *        is true the response includes `"dev_code": "123456"` for testing.
 *   2) Client calls /verify-otp { "identifier", "code", "purpose" }
 *      → on success the OTP row is marked verified.
 *   3) Client calls /login (phone path) or /signup (creates app_users row).
 *
 * Spec ref:
 *   - vidya_prayag_api_spec.artifact.md §Module: User Authentication
 */
package com.littlebridge.enrollplus.feature.auth

import com.littlebridge.enrollplus.core.JwtConfig
import com.littlebridge.enrollplus.core.created
import com.littlebridge.enrollplus.core.fail
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.okMessage
import com.littlebridge.enrollplus.core.principalUserId
import com.littlebridge.enrollplus.db.AppUsersTable
import com.littlebridge.enrollplus.db.AuthOtpsTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.SchoolsTable
import com.littlebridge.enrollplus.db.UserSessionsTable
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

// ============================================================
// DTOs
// ============================================================
@Serializable
data class CheckUserDto(val identifier: String, val role: String? = null)

@Serializable
data class CheckUserResponse(
    @SerialName("is_new_user") val isNewUser: Boolean,
    @SerialName("auth_method_required") val authMethodRequired: String,
    val message: String
)

@Serializable
data class SendOtpDto(
    val identifier: String,
    val purpose: String? = null,
    @SerialName("device_id") val deviceId: String? = null
)

@Serializable
data class SendOtpResponse(
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("resend_count") val resendCount: Int,
    @SerialName("dev_code") val devCode: String? = null,
    val message: String,
    // OTPSender gateway SMS request id (feature/setup_notification). Non-null only
    // when the OTP was routed through the gateway; null on the direct-SMS path.
    @SerialName("request_id") val requestId: String? = null,
)

@Serializable
data class VerifyOtpDto(
    val identifier: String,
    val code: String,
    val purpose: String? = null
)

@Serializable
data class VerifyOtpResponse(
    val verified: Boolean,
    val message: String
)

@Serializable
data class SignupDto(
    val name: String,
    val identifier: String,
    val role: String,
    val password: String? = null,
    val otp: String? = null,
    @SerialName("device_info") val deviceInfo: DeviceInfo? = null
)

@Serializable
data class DeviceInfo(
    @SerialName("device_id") val deviceId: String? = null,
    val platform: String? = null
)

@Serializable
data class LoginDto(
    val identifier: String,
    val role: String? = null,
    val password: String? = null,
    val otp: String? = null,
    @SerialName("device_info") val deviceInfo: DeviceInfo? = null
)

@Serializable
data class AuthTokenResponse(
    val token: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val role: String,
    @SerialName("profile_completed") val profileCompleted: Boolean,
    // RA-54: tells the client whether a forced password change is pending
    // (provisioned teachers on first login). Defaults false for everyone else.
    @SerialName("must_change_password") val mustChangePassword: Boolean = false
)

@Serializable
data class ChangePasswordDto(
    @SerialName("old_password") val oldPassword: String? = null,
    @SerialName("new_password") val newPassword: String
)

@Serializable
data class RefreshDto(@SerialName("refresh_token") val refreshToken: String)

// ============================================================
// School self-registration (Onboard your school)
// ------------------------------------------------------------
// The ONLY sanctioned way to self-mint a school_admin. Unlike the
// anonymous /signup route (which is hard-locked to `parent` to close the
// RA-53 privilege-escalation chain), this endpoint atomically:
//   1. creates the school_admin app_users row (profile_completed=false),
//   2. creates the owning `schools` row with onboarded_at=NULL
//      (i.e. onboarding_status = 'pending' — NOT active),
//   3. links app_users.school_id → the new school,
//   4. returns a JWT carrying role=school_admin.
// The existing onboarding wizard then fills in the school's details; the
// final REVIEW submit stamps schools.onboarded_at (status → active) and
// flips app_users.profile_completed = true. So a freshly registered school
// ALWAYS lands on the onboarding wizard, never the dashboard.
// ============================================================
@Serializable
data class SchoolRegisterDto(
    // Admin account
    val name: String,                 // admin / principal contact name
    val identifier: String,           // email (password path) — staff use email
    val password: String,
    // School seed (kept minimal — the wizard collects the rest)
    @SerialName("school_name") val schoolName: String,
    val board: String? = null,        // CBSE | ICSE | UP State | Other
    @SerialName("school_type") val schoolType: String? = null,
    val city: String? = null,
    val state: String? = null,
    @SerialName("contact_phone") val contactPhone: String? = null,
    @SerialName("device_info") val deviceInfo: DeviceInfo? = null,
)

@Serializable
data class LogoutDto(@SerialName("refresh_token") val refreshToken: String? = null)

// ============================================================
// Helpers
// ============================================================
private fun isEmail(id: String) = id.contains("@")

/** Normalise raw identifier text per the rules in the file header. */
internal fun normaliseIdentifier(raw: String): String {
    val trimmed = raw.trim()
    if (isEmail(trimmed)) return trimmed.lowercase()
    val digits = trimmed.replace("\\s|-".toRegex(), "")
    return when {
        digits.startsWith("+") -> digits
        digits.length == 10 && digits.all { it.isDigit() } -> "+91$digits"
        digits.length == 12 && digits.startsWith("91") -> "+$digits"
        else -> digits
    }
}

internal fun sha256Hex(s: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    return md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

/**
 * Salted, work-factored password hash (PBKDF2-HMAC-SHA256, per-user random
 * salt). Replaces the previous unsalted `sha256Hex("pwd:$p")` (audit §3.3).
 * Delegates to [PasswordHasher] so signup writes a secure hash. Login MUST
 * verify via [PasswordHasher.verify], never by re-hashing and comparing
 * strings (a salted hash is non-deterministic).
 */
internal fun hashPassword(p: String): String = PasswordHasher.hash(p)

private fun lookupUserByIdentifier(identifier: String): org.jetbrains.exposed.sql.ResultRow? {
    return AppUsersTable.selectAll()
        .where { (AppUsersTable.phone eq identifier) or (AppUsersTable.email eq identifier) }
        .firstOrNull()
}

private fun roleNormalised(input: String?): String {
    val r = (input ?: "parent").lowercase()
    return when (r) {
        "admin", "school_admin" -> "school_admin"
        "teacher" -> "teacher"
        "super_admin" -> "super_admin"
        else -> "parent"
    }
}

// ============================================================
// Routing
// ============================================================
fun Route.authRouting() {
    route("/api/v1/auth") {

        // -------- check-user --------
        post("/check-user") {
            val req = call.receive<CheckUserDto>()
            val id = normaliseIdentifier(req.identifier)
            if (id.isBlank()) { call.fail("identifier is required"); return@post }

            val user = dbQuery { lookupUserByIdentifier(id) }
            val method = if (isEmail(id)) "PASSWORD" else "OTP"
            call.ok(
                CheckUserResponse(
                    isNewUser = user == null,
                    authMethodRequired = method,
                    message = if (user == null) "User does not exist. Proceed to signup."
                              else "User found. Please continue with $method."
                ),
                message = "Check completed"
            )
        }

        // -------- send-otp --------
        post("/send-otp") {
            val req = runCatching { call.receive<SendOtpDto>() }.getOrNull()
                ?: run { call.fail("Invalid body: expected { identifier, purpose? }"); return@post }
            val id = normaliseIdentifier(req.identifier)
            if (id.isBlank()) { call.fail("identifier is required"); return@post }

            val ip = call.request.origin.remoteHost
            val ua = call.request.headers["User-Agent"]
            val purpose = req.purpose ?: "login"
            // Honour Accept-Language header so the OTP body is in the user's
            // language when the provider supports it (Fast2SMS DLT / WA / SMTP).
            // We only need the primary subtag; "hi-IN,en;q=0.5" → "hi".
            val locale = call.request.headers["Accept-Language"]
                ?.substringBefore(',')?.substringBefore('-')?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "en"

            val result = OtpService.send(
                identifier = id,
                purpose = purpose,
                ipAddress = ip,
                userAgent = ua,
                deviceId = req.deviceId,
                locale = locale,
            )
            when (result) {
                is OtpSendResult.Sent -> call.ok(
                    SendOtpResponse(
                        expiresAt = result.expiresAt.toString(),
                        resendCount = result.resendCount,
                        devCode = result.devCode,
                        message = "OTP sent. Valid for 10 minutes.",
                        requestId = result.requestId,
                    ),
                    message = "OTP sent"
                )
                is OtpSendResult.RateLimited -> call.fail(
                    "Too many OTP requests. Please wait an hour and try again.",
                    HttpStatusCode.TooManyRequests,
                    errorCode = "OTP_RATE_LIMITED"
                )
                is OtpSendResult.DeliveryFailed -> call.fail(
                    "Failed to deliver OTP: ${result.reason}",
                    HttpStatusCode.BadGateway,
                    errorCode = "OTP_DELIVERY_FAILED"
                )
            }
        }

        // -------- verify-otp --------
        post("/verify-otp") {
            val req = runCatching { call.receive<VerifyOtpDto>() }.getOrNull()
                ?: run { call.fail("Invalid body: expected { identifier, code, purpose? }"); return@post }
            val id = normaliseIdentifier(req.identifier)
            if (id.isBlank() || req.code.isBlank()) { call.fail("identifier and code are required"); return@post }
            val purpose = req.purpose ?: "login"

            when (val r = OtpService.verify(id, req.code, purpose)) {
                OtpVerifyResult.Ok -> call.ok(VerifyOtpResponse(true, "OTP verified"), "OK")
                OtpVerifyResult.NotFound -> call.fail(
                    "No active OTP for this identifier. Please request a new one.",
                    HttpStatusCode.NotFound, "OTP_NOT_FOUND"
                )
                OtpVerifyResult.Expired -> call.fail(
                    "OTP expired (10-minute window elapsed). Please request a new one.",
                    HttpStatusCode.Gone, "OTP_EXPIRED"
                )
                OtpVerifyResult.Locked -> call.fail(
                    "Too many wrong attempts. OTP locked — request a new one.",
                    HttpStatusCode.Locked, "OTP_LOCKED"
                )
                is OtpVerifyResult.Invalid -> call.fail(
                    "Incorrect OTP. Attempts left: ${r.attemptsLeft}",
                    HttpStatusCode.Unauthorized, "OTP_INVALID"
                )
            }
        }

        // -------- signup --------
        post("/signup") {
            val req = runCatching { call.receive<SignupDto>() }.getOrNull()
                ?: run { call.fail("Invalid body"); return@post }
            val id = normaliseIdentifier(req.identifier)
            if (id.isBlank() || req.name.isBlank() || req.role.isBlank()) {
                call.fail("name, identifier and role are required"); return@post
            }

            val existing = dbQuery { lookupUserByIdentifier(id) }
            if (existing != null) {
                call.fail("Account already exists. Please login.", HttpStatusCode.Conflict, "USER_EXISTS")
                return@post
            }

            // Phone signup → require verified OTP first.
            // Email signup → require password.
            if (isEmail(id)) {
                if (req.password.isNullOrBlank()) {
                    call.fail("password is required for email signup", HttpStatusCode.BadRequest)
                    return@post
                }
            } else {
                val verified = dbQuery {
                    AuthOtpsTable.selectAll()
                        .where {
                            (AuthOtpsTable.identifier eq id) and
                                (AuthOtpsTable.purpose eq "signup") and
                                (AuthOtpsTable.isVerified eq true)
                        }.singleOrNull()
                }
                if (verified == null) {
                    // Allow `purpose=login` verified OTP too, for clients that
                    // don't distinguish login/signup until the user is found.
                    val verifiedAny = dbQuery {
                        AuthOtpsTable.selectAll()
                            .where {
                                (AuthOtpsTable.identifier eq id) and
                                    (AuthOtpsTable.isVerified eq true)
                            }.singleOrNull()
                    }
                    if (verifiedAny == null) {
                        call.fail(
                            "Phone signup requires a verified OTP. Call /send-otp then /verify-otp first.",
                            HttpStatusCode.BadRequest, "OTP_REQUIRED"
                        )
                        return@post
                    }
                }
            }

            // RA-53 🔴 — Public self-service privilege escalation hard-stop.
            // The public /signup route is the ONLY anonymous account-mint path,
            // so it must mint EXACTLY a parent. Admin/teacher/super_admin
            // accounts are *provisioned* server-side (admins via an operator /
            // invite, teachers via TeacherProvisioningRouting) and never via
            // anonymous self-signup. We therefore IGNORE req.role entirely here
            // and force "parent", regardless of what the client sends. This
            // closes the end-to-end escalation chain where AdminAuthScreenV2
            // signup → /signup(role=ADMIN) → school_admin tenant creation.
            val requestedRole = roleNormalised(req.role)
            if (requestedRole != "parent") {
                call.fail(
                    "Self-service signup can only create a parent account. " +
                        "Staff accounts are provisioned by your administrator.",
                    HttpStatusCode.Forbidden,
                    "SIGNUP_ROLE_FORBIDDEN"
                )
                return@post
            }
            val role = "parent"
            val now = Instant.now()
            val newId = UUID.randomUUID()
            dbQuery {
                val normId = id
                AppUsersTable.insert {
                    it[AppUsersTable.id] = newId
                    it[fullName] = req.name.trim()
                    it[AppUsersTable.role] = role
                    if (isEmail(normId)) {
                        it[email] = normId
                        it[passwordHash] = req.password?.let { p -> hashPassword(p) }
                        it[isEmailVerified] = true
                    } else {
                        it[phone] = normId
                        it[isPhoneVerified] = true
                    }
                    it[profileCompleted] = false
                    it[isActive] = true
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }

            val token = JwtConfig.issueToken(newId.toString(), role, req.name)
            val refresh = JwtConfig.issueRefreshToken(newId.toString())
            persistSession(
                userId = newId,
                refreshToken = refresh,
                deviceId = req.deviceInfo?.deviceId,
                platform = req.deviceInfo?.platform,
                ip = call.request.origin.remoteHost,
                ua = call.request.headers["User-Agent"]
            )

            call.created(
                AuthTokenResponse(
                    token = token, refreshToken = refresh,
                    userId = newId.toString(), name = req.name,
                    role = role, profileCompleted = false
                ),
                message = "Account created successfully"
            )
        }

        // -------- register-school (Onboard your school) --------
        // Public, email+password only. Creates a school_admin + an empty
        // `schools` row (onboarded_at=NULL → pending) and returns a JWT.
        post("/register-school") {
            val req = runCatching { call.receive<SchoolRegisterDto>() }.getOrNull()
                ?: run { call.fail("Invalid body"); return@post }

            val id = normaliseIdentifier(req.identifier)
            if (id.isBlank() || req.name.isBlank() || req.schoolName.isBlank()) {
                call.fail("name, identifier and school_name are required"); return@post
            }
            // Staff register with email+password (no OTP path here).
            if (!isEmail(id)) {
                call.fail("School registration requires a valid email address", HttpStatusCode.BadRequest, "EMAIL_REQUIRED")
                return@post
            }
            if (req.password.isBlank() || req.password.length < 8) {
                call.fail("Password must be at least 8 characters", HttpStatusCode.BadRequest, "PASSWORD_TOO_SHORT")
                return@post
            }

            val existing = dbQuery { lookupUserByIdentifier(id) }
            if (existing != null) {
                call.fail("An account already exists for this email. Please sign in.", HttpStatusCode.Conflict, "USER_EXISTS")
                return@post
            }

            val now = Instant.now()
            val newUserId = UUID.randomUUID()
            val newSchoolId = UUID.randomUUID()
            val cleanName = req.schoolName.trim()
            val slugBase = cleanName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
            val slug = (slugBase.ifBlank { "school" }) + "-" + newSchoolId.toString().take(6)

            dbQuery {
                // 1) Create the owning school row in a 'pending' onboarding state
                //    (onboarded_at left NULL). Only the seed fields are written;
                //    the wizard fills the rest. NOT-NULL columns get sensible
                //    placeholders the wizard overwrites (city/district).
                SchoolsTable.insert {
                    it[SchoolsTable.id] = newSchoolId
                    it[name] = cleanName
                    it[SchoolsTable.slug] = slug
                    it[board] = req.board?.takeIf { b -> b.isNotBlank() } ?: "CBSE"
                    it[medium] = "English"
                    it[schoolGender] = "co_ed"
                    it[contactEmail] = id
                    it[contactPhone] = req.contactPhone?.takeIf { p -> p.isNotBlank() }
                    it[principalName] = req.name.trim()
                    it[city] = req.city?.takeIf { c -> c.isNotBlank() } ?: "Unknown"
                    it[district] = req.city?.takeIf { c -> c.isNotBlank() } ?: "Unknown"
                    it[state] = req.state?.takeIf { s -> s.isNotBlank() } ?: "Uttar Pradesh"
                    it[isActive] = true
                    it[onboardedAt] = null   // explicit: onboarding NOT complete
                    it[createdAt] = now
                    it[updatedAt] = now
                }
                // 2) Create the school_admin account linked to the new school.
                AppUsersTable.insert {
                    it[AppUsersTable.id] = newUserId
                    it[fullName] = req.name.trim()
                    it[role] = "school_admin"
                    it[email] = id
                    it[passwordHash] = hashPassword(req.password)
                    it[isEmailVerified] = true
                    it[schoolId] = newSchoolId
                    it[profileCompleted] = false   // gate → onboarding wizard
                    it[mustChangePassword] = false
                    it[isActive] = true
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }

            val token = JwtConfig.issueToken(newUserId.toString(), "school_admin", req.name.trim())
            val refresh = JwtConfig.issueRefreshToken(newUserId.toString())
            persistSession(
                userId = newUserId,
                refreshToken = refresh,
                deviceId = req.deviceInfo?.deviceId,
                platform = req.deviceInfo?.platform,
                ip = call.request.origin.remoteHost,
                ua = call.request.headers["User-Agent"]
            )

            call.created(
                AuthTokenResponse(
                    token = token, refreshToken = refresh,
                    userId = newUserId.toString(), name = req.name.trim(),
                    role = "school_admin", profileCompleted = false
                ),
                message = "School registered. Continue with onboarding."
            )
        }

        // -------- login --------
        post("/login") {
            val bodyReq: LoginDto? = runCatching { call.receive<LoginDto>() }.getOrNull()
            val q = call.request.queryParameters
            val req = LoginDto(
                identifier = bodyReq?.identifier ?: q["identifier"] ?: "",
                role = bodyReq?.role ?: q["role"],
                password = bodyReq?.password ?: q["password"],
                otp = bodyReq?.otp ?: q["otp"],
                deviceInfo = bodyReq?.deviceInfo
            )
            val id = normaliseIdentifier(req.identifier)
            if (id.isBlank()) { call.fail("identifier is required"); return@post }

            // RA-41: throttle the email/password path before any DB work so
            // credential stuffing can't run unbounded. Checked per-IP AND
            // per-identifier; the OTP path keeps its own OtpService throttle.
            val clientIp = call.request.origin.remoteHost
            if (isEmail(id) && LoginThrottle.isThrottled(clientIp, id)) {
                call.fail(
                    "Too many login attempts. Please wait a few minutes and try again.",
                    HttpStatusCode.TooManyRequests,
                    "LOGIN_THROTTLED"
                )
                return@post
            }

            // RA-41: ONE generic outcome for "unknown account" and "wrong
            // password" on the email path, so the response can't be used to
            // enumerate which accounts exist. (Previously USER_NOT_FOUND vs
            // INVALID_CREDENTIALS distinguished the two.)
            val row = dbQuery { lookupUserByIdentifier(id) }
            if (row == null) {
                if (isEmail(id)) {
                    LoginThrottle.recordFailure(clientIp, id)
                    call.fail("Invalid email or password", HttpStatusCode.Unauthorized, "INVALID_CREDENTIALS")
                } else {
                    // Phone path can't proceed without a user + OTP; keep the
                    // OTP-flow wording (an unknown phone reveals nothing extra
                    // because /send-otp must succeed first anyway).
                    call.fail("No active OTP. Call /send-otp first.", HttpStatusCode.NotFound, "OTP_NOT_FOUND")
                }
                return@post
            }

            // RA-34: a deactivated / off-boarded account must never be able to
            // authenticate. Reject before any credential check so an inactive
            // user cannot even probe their password/OTP.
            if (!row[AppUsersTable.isActive]) {
                call.fail("This account has been deactivated. Contact your administrator.", HttpStatusCode.Forbidden, "ACCOUNT_DEACTIVATED")
                return@post
            }

            // Email → password.  Phone → OTP.
            if (isEmail(id)) {
                val stored = row[AppUsersTable.passwordHash]
                if (!PasswordHasher.verify(req.password.orEmpty(), stored)) {
                    // RA-41: record the failure and return the SAME generic
                    // message/code as the unknown-account branch above.
                    LoginThrottle.recordFailure(clientIp, id)
                    call.fail("Invalid email or password", HttpStatusCode.Unauthorized, "INVALID_CREDENTIALS")
                    return@post
                }
                // RA-41: a correct password clears the identifier throttle window
                // so earlier typos don't keep a legitimate user locked out.
                LoginThrottle.recordSuccess(id)
                // Transparently upgrade legacy unsalted SHA-256 hashes on a
                // successful login so the password store self-heals over time.
                if (PasswordHasher.needsRehash(stored)) {
                    val upgraded = PasswordHasher.hash(req.password.orEmpty())
                    dbQuery {
                        AppUsersTable.update({ AppUsersTable.id eq row[AppUsersTable.id] }) {
                            it[passwordHash] = upgraded
                        }
                    }
                }
            } else {
                if (req.otp.isNullOrBlank()) {
                    call.fail("otp is required for phone login", HttpStatusCode.BadRequest, "OTP_REQUIRED")
                    return@post
                }
                when (val r = OtpService.verify(id, req.otp, "login")) {
                    OtpVerifyResult.Ok -> Unit
                    OtpVerifyResult.NotFound -> { call.fail("No active OTP. Call /send-otp first.", HttpStatusCode.NotFound, "OTP_NOT_FOUND"); return@post }
                    OtpVerifyResult.Expired -> { call.fail("OTP expired. Please request a new one.", HttpStatusCode.Gone, "OTP_EXPIRED"); return@post }
                    OtpVerifyResult.Locked -> { call.fail("OTP locked. Request a new one.", HttpStatusCode.Locked, "OTP_LOCKED"); return@post }
                    is OtpVerifyResult.Invalid -> { call.fail("Invalid OTP. Attempts left: ${r.attemptsLeft}", HttpStatusCode.Unauthorized, "OTP_INVALID"); return@post }
                }
            }

            val userId = row[AppUsersTable.id].value
            val name = row[AppUsersTable.fullName]
            val role = row[AppUsersTable.role]
            val token = JwtConfig.issueToken(userId.toString(), role, name)
            val refresh = JwtConfig.issueRefreshToken(userId.toString())

            dbQuery {
                AppUsersTable.update({ AppUsersTable.id eq userId }) {
                    it[lastLoginAt] = Instant.now()
                    it[updatedAt] = Instant.now()
                }
            }
            persistSession(
                userId = userId,
                refreshToken = refresh,
                deviceId = req.deviceInfo?.deviceId,
                platform = req.deviceInfo?.platform,
                ip = call.request.origin.remoteHost,
                ua = call.request.headers["User-Agent"]
            )

            call.ok(
                AuthTokenResponse(
                    token = token, refreshToken = refresh,
                    userId = userId.toString(), name = name, role = role,
                    profileCompleted = row[AppUsersTable.profileCompleted],
                    mustChangePassword = row[AppUsersTable.mustChangePassword]
                ),
                message = "Login successful"
            )
        }

        // -------- refresh --------
        post("/refresh") {
            val req = runCatching { call.receive<RefreshDto>() }.getOrNull()
                ?: run { call.fail("Invalid body: expected { refresh_token }"); return@post }
            val hash = sha256Hex(req.refreshToken)
            val now = Instant.now()
            val row = dbQuery {
                UserSessionsTable.selectAll()
                    .where { UserSessionsTable.refreshTokenHash eq hash }
                    .singleOrNull()
            } ?: run {
                call.fail("Invalid refresh token", HttpStatusCode.Unauthorized, "REFRESH_INVALID")
                return@post
            }
            // RA-35: refresh-token reuse detection. A token whose row is ALREADY
            // revoked (but not expired) means someone replayed a rotated token —
            // either the legitimate client after rotation, or a thief. Treat it
            // as a theft signal and revoke the ENTIRE session family for the user
            // so neither party can continue.
            if (row[UserSessionsTable.revokedAt] != null) {
                val reuseUid = row[UserSessionsTable.userId]
                dbQuery {
                    UserSessionsTable.update({ UserSessionsTable.userId eq reuseUid }) { it[revokedAt] = now }
                }
                call.fail("Refresh token has been revoked. Please login again.", HttpStatusCode.Unauthorized, "REFRESH_REUSE_DETECTED")
                return@post
            }
            if (row[UserSessionsTable.expiresAt].isBefore(now)) {
                call.fail("Refresh token expired", HttpStatusCode.Unauthorized, "REFRESH_EXPIRED")
                return@post
            }
            val uid = row[UserSessionsTable.userId]
            val user = dbQuery { AppUsersTable.selectAll().where { AppUsersTable.id eq uid }.singleOrNull() }
                ?: run { call.fail("User not found", HttpStatusCode.Unauthorized, "USER_NOT_FOUND"); return@post }
            // RA-34: a deactivated user must not be able to mint fresh tokens via
            // /refresh. Reject and revoke all of their sessions so the refresh
            // token is dead immediately (kill-switch on deactivation).
            if (!user[AppUsersTable.isActive]) {
                dbQuery {
                    UserSessionsTable.update({ UserSessionsTable.userId eq uid }) { it[revokedAt] = now }
                }
                call.fail("This account has been deactivated. Contact your administrator.", HttpStatusCode.Forbidden, "ACCOUNT_DEACTIVATED")
                return@post
            }
            val token = JwtConfig.issueToken(uid.toString(), user[AppUsersTable.role], user[AppUsersTable.fullName])

            // RA-35: ROTATE the refresh token on every use. Mint a new refresh
            // token, revoke the presented row, and persist a new row (same device
            // metadata, fresh 30-day window). A leaked old token is now single-use:
            // replaying it after the legitimate client rotates trips reuse-detection.
            val newRefresh = JwtConfig.issueRefreshToken(uid.toString())
            dbQuery {
                UserSessionsTable.update({ UserSessionsTable.id eq row[UserSessionsTable.id] }) {
                    it[revokedAt] = now
                    it[lastUsedAt] = now
                }
                UserSessionsTable.insert {
                    it[userId] = uid
                    it[refreshTokenHash] = sha256Hex(newRefresh)
                    it[deviceId] = row[UserSessionsTable.deviceId]
                    it[platform] = row[UserSessionsTable.platform]
                    it[ipAddress] = call.request.origin.remoteHost
                    it[userAgent] = call.request.headers["User-Agent"]
                    it[issuedAt] = now
                    it[expiresAt] = now.plus(30, ChronoUnit.DAYS)
                    it[lastUsedAt] = now
                    it[createdAt] = now
                }
            }
            call.ok(
                AuthTokenResponse(
                    token = token, refreshToken = newRefresh,
                    userId = uid.toString(),
                    name = user[AppUsersTable.fullName],
                    role = user[AppUsersTable.role],
                    profileCompleted = user[AppUsersTable.profileCompleted],
                    mustChangePassword = user[AppUsersTable.mustChangePassword]
                ),
                message = "Token refreshed"
            )
        }
    }

    // -------- logout (server-side revocation, audit §3.6) --------
    // Requires a valid access token; revokes the matching refresh-token session
    // row so the refresh token cannot be reused for its remaining 30-day life.
    authenticate("jwt") {
        route("/api/v1/auth") {
            // -------- change-password (RA-54) --------
            // Authenticated. Verifies the old password (when one exists),
            // stores a fresh PBKDF2 hash, flips profile_completed=true and
            // must_change_password=false (resolving the teacher first-login
            // gate permanently), and revokes all OTHER sessions for the user
            // so a stolen old-credential session can't continue.
            post("/change-password") {
                val uid = call.principalUserId()
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: run { call.fail("Invalid token", HttpStatusCode.Unauthorized); return@post }
                val req = runCatching { call.receive<ChangePasswordDto>() }.getOrNull()
                    ?: run { call.fail("Invalid body: expected { new_password, old_password? }"); return@post }
                if (req.newPassword.length < 8) {
                    call.fail("New password must be at least 8 characters", HttpStatusCode.BadRequest, "PASSWORD_TOO_SHORT")
                    return@post
                }

                val user = dbQuery {
                    AppUsersTable.selectAll().where { AppUsersTable.id eq uid }.singleOrNull()
                } ?: run { call.fail("User not found", HttpStatusCode.NotFound, "USER_NOT_FOUND"); return@post }

                val stored = user[AppUsersTable.passwordHash]
                val mustChange = user[AppUsersTable.mustChangePassword]
                // When the user already has a password AND is not in a forced
                // first-change flow, the old password is required and must match.
                // (Forced first-change teachers may set a new password without
                // re-entering the generated one, since the gate exists precisely
                // because that generated password is not theirs.)
                if (!stored.isNullOrBlank() && !mustChange) {
                    if (req.oldPassword.isNullOrBlank() ||
                        !PasswordHasher.verify(req.oldPassword, stored)
                    ) {
                        call.fail("Current password is incorrect", HttpStatusCode.Unauthorized, "OLD_PASSWORD_INVALID")
                        return@post
                    }
                }

                val now = Instant.now()
                val newHash = PasswordHasher.hash(req.newPassword)
                dbQuery {
                    AppUsersTable.update({ AppUsersTable.id eq uid }) {
                        it[passwordHash] = newHash
                        it[profileCompleted] = true
                        it[mustChangePassword] = false
                        it[updatedAt] = now
                    }
                    // Revoke all sessions; the current client keeps its access
                    // token until expiry but must re-login for a refresh.
                    UserSessionsTable.update({ UserSessionsTable.userId eq uid }) {
                        it[revokedAt] = now
                    }
                }
                call.okMessage("Password changed")
            }

            post("/logout") {
                val uid = call.principalUserId()
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: run { call.fail("Invalid token", HttpStatusCode.Unauthorized); return@post }
                val body = runCatching { call.receive<LogoutDto>() }.getOrNull()
                val now = Instant.now()
                dbQuery {
                    val refreshHash = body?.refreshToken?.let { sha256Hex(it) }
                    if (refreshHash != null) {
                        // Revoke just the session tied to this refresh token.
                        UserSessionsTable.update({
                            (UserSessionsTable.userId eq uid) and
                                (UserSessionsTable.refreshTokenHash eq refreshHash)
                        }) { it[revokedAt] = now }
                    } else {
                        // No refresh token supplied → revoke all of the user's
                        // sessions as a safe fallback (idempotent for already-
                        // revoked rows).
                        UserSessionsTable.update({
                            UserSessionsTable.userId eq uid
                        }) { it[revokedAt] = now }
                    }
                }
                call.okMessage("Logged out")
            }
        }
    }
}

private suspend fun persistSession(
    userId: UUID,
    refreshToken: String,
    deviceId: String?,
    platform: String?,
    ip: String?,
    ua: String?
) {
    val now = Instant.now()
    dbQuery {
        UserSessionsTable.insert {
            it[UserSessionsTable.userId] = userId
            it[refreshTokenHash] = sha256Hex(refreshToken)
            it[UserSessionsTable.deviceId] = deviceId
            it[UserSessionsTable.platform] = platform
            it[ipAddress] = ip
            it[userAgent] = ua
            it[issuedAt] = now
            it[expiresAt] = now.plus(30, ChronoUnit.DAYS)
            it[createdAt] = now
        }
    }
}

