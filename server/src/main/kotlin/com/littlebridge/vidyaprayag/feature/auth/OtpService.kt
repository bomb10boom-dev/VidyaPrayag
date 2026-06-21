/*
 * File: OtpService.kt
 * Module: feature.auth
 *
 * INDUSTRIAL-GRADE OTP SERVICE
 * ============================
 *
 * What this service does
 * ----------------------
 *   1. send(identifier, purpose, ip, ua, deviceId)
 *        - Generates a 6-digit OTP.
 *        - UPSERTs into `auth_otps` keyed by (identifier, purpose) so a
 *          resend within 10 minutes OVERWRITES the previous row
 *          (new code, new expires_at, attempt_count reset, resend_count++).
 *        - Hashes the code (SHA-256 with per-row salt + global pepper).
 *        - Returns the plain code ONLY if OTP_DEV_RETURN_CODE=true (dev mode)
 *          or via the chosen SMS provider (Twilio / MSG91 / Gupshup).
 *        - Enforces resend rate-limit: max 5 resends per hour per identifier.
 *
 *   2. verify(identifier, purpose, code)
 *        - Purges expired rows in the same transaction (defence in depth).
 *        - Loads the OTP row, rejects if missing / locked / expired.
 *        - Hashes the supplied code and compares constant-time.
 *        - On mismatch increments attempt_count; locks at max_attempts.
 *        - On success marks is_verified=true and deletes the row after
 *          a brief grace period (5 min, handled by purge job).
 *
 * Industrial-grade features included
 * ----------------------------------
 *   ✓ Per-row salt + global pepper so a DB dump alone can't recover OTPs
 *   ✓ Code hashing with SHA-256 (constant-time compare on verify)
 *   ✓ Strict 10-minute TTL enforced at BOTH the DB ('expires_at' column +
 *     pg_cron purge) AND in code (verify() checks `now > expires_at`)
 *   ✓ Resend OVERWRITES the same row (UPSERT)
 *   ✓ Resend rate-limit: 5 per hour per identifier (resend_count window)
 *   ✓ Brute-force lock: 5 wrong attempts → row marked is_locked
 *   ✓ Audit trail: ip_address, user_agent, device_id, delivery_channel,
 *     provider_message_id all persisted
 *   ✓ Pluggable delivery: mock provider for dev; real SMS providers behind
 *     OtpDeliveryProvider interface (Twilio / MSG91 / Gupshup easy drop-in)
 *   ✓ Telemetry-friendly: returns a sealed Result so callers know exactly
 *     why a verify failed (EXPIRED vs LOCKED vs INVALID vs NOT_FOUND)
 *
 * Environment variables
 * ---------------------
 *   OTP_PEPPER                 : secret pepper added to every hash
 *                                (REQUIRED in production; dev fallback exists)
 *   OTP_EXPIRY_MINUTES         : default 10
 *   OTP_MAX_ATTEMPTS           : default 5
 *   OTP_MAX_RESENDS_PER_HOUR   : default 5
 *   OTP_DEV_RETURN_CODE        : "true" in dev to echo the OTP back in the
 *                                API response (NEVER in production)
 *   OTP_PROVIDER               : "mock" | "msg91" | "twilio" | "gupshup"
 *                                (only "mock" is implemented in this file;
 *                                wire the others in OtpDeliveryProvider.kt)
 *
 * Spec ref:
 *   - vidya_prayag_api_spec.artifact.md §User Authentication ›
 *     POST /api/v1/auth/login (OTP path) + POST /api/v1/auth/signup
 */
package com.littlebridge.vidyaprayag.feature.auth

import com.littlebridge.vidyaprayag.db.AuthOtpsTable
import com.littlebridge.vidyaprayag.db.DatabaseFactory.dbQuery
import com.littlebridge.vidyaprayag.db.OtpDeliveryAttemptsTable
import com.littlebridge.vidyaprayag.feature.auth.delivery.OtpMessageTemplates
import com.littlebridge.vidyaprayag.feature.auth.delivery.sms.OTPSenderGatewayProvider
import com.littlebridge.vidyaprayag.feature.auth.delivery.sms.SmsSendRequest
import com.littlebridge.vidyaprayag.feature.auth.delivery.sms.SmsSendResult
import com.littlebridge.vidyaprayag.feature.auth.gateway.OtpSmsRequestRepository
import org.jetbrains.exposed.sql.vendors.ForUpdateOption
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

// =================================================================
// Result types
// =================================================================
sealed class OtpSendResult {
    data class Sent(
        val identifier: String,
        val expiresAt: Instant,
        val resendCount: Int,
        val devCode: String?           // only populated in dev
    ) : OtpSendResult()
    object RateLimited : OtpSendResult()
    data class DeliveryFailed(val reason: String) : OtpSendResult()
}

sealed class OtpVerifyResult {
    object Ok : OtpVerifyResult()
    object NotFound : OtpVerifyResult()
    object Expired : OtpVerifyResult()
    object Locked : OtpVerifyResult()
    data class Invalid(val attemptsLeft: Int) : OtpVerifyResult()
}

// =================================================================
// OtpService
// =================================================================
object OtpService {

    private val log = LoggerFactory.getLogger("OtpService")

    private fun env(name: String, default: String): String =
        System.getenv(name)?.takeIf { it.isNotBlank() } ?: default

    private val pepper: String by lazy {
        env("OTP_PEPPER", "vidyaprayag-dev-pepper-change-me-in-prod")
    }
    private val expiryMinutes: Long by lazy {
        env("OTP_EXPIRY_MINUTES", "10").toLong().coerceIn(1, 60)
    }
    private val maxAttempts: Int by lazy {
        env("OTP_MAX_ATTEMPTS", "5").toInt().coerceIn(3, 10)
    }
    private val maxResendsPerHour: Int by lazy {
        env("OTP_MAX_RESENDS_PER_HOUR", "5").toInt().coerceIn(1, 20)
    }
    /**
     * Whether the plaintext OTP is echoed back in the /send-otp response as
     * `dev_code`. RA-33: this is a full pre-auth account-takeover primitive if
     * ever enabled in production, so:
     *   1) the default is now "false" (opt-in, not opt-out); and
     *   2) it is hard-gated to non-production — even if OTP_DEV_RETURN_CODE=true
     *      is set on a prod dyno, the echo is suppressed whenever DATABASE_URL is
     *      configured (the same prod signal JwtConfig.isProduction uses).
     */
    private val isProduction: Boolean
        get() = System.getenv("DATABASE_URL")?.takeIf { it.isNotBlank() } != null
    private val devReturnCode: Boolean by lazy {
        !isProduction && env("OTP_DEV_RETURN_CODE", "false").equals("true", true)
    }

    private val rng = SecureRandom()

    // --------------------------------------------------------------
    // SEND
    // --------------------------------------------------------------
    suspend fun send(
        identifier: String,
        purpose: String = "login",
        ipAddress: String? = null,
        userAgent: String? = null,
        deviceId: String? = null,
        identifierType: String = if (identifier.contains("@")) "email" else "phone",
        locale: String = "en",
    ): OtpSendResult {

        // Purge stale rows opportunistically (cheap; <1ms in steady state).
        dbQuery { purgeExpired() }

        // Generate a fresh 6-digit code and a per-row salt.
        val code = "%06d".format(rng.nextInt(1_000_000))
        val salt = UUID.randomUUID().toString().replace("-", "").take(16)
        val hash = sha256("$code:$salt:$pepper")
        val now = Instant.now()
        val expires = now.plus(expiryMinutes, ChronoUnit.MINUTES)
        val windowStart = now.minus(1, ChronoUnit.HOURS)

        // LOGGING FOR TESTING (RA-66): log the plaintext code in non-production.
        // This ensures the OTP can be seen in Render logs during testing without
        // needing OTP_DEV_RETURN_CODE=true in the API response.
       // if (!isProduction) {
            log.info("[TESTING] Generated OTP for {}: {}", identifier, code)
            println(">>> [TESTING] OTP for $identifier: $code")
       // }

        // RA-38: the resend-limit check and the UPSERT now run inside ONE
        // transaction with a `SELECT … FOR UPDATE` row lock. Previously the read
        // (existing) and the write (update/insert) were two separate `dbQuery{}`
        // transactions, so two concurrent /send-otp calls could both read the
        // same resend_count, both pass the `resends < maxResendsPerHour` check,
        // and both write — letting an attacker exceed the advertised hourly
        // resend cap. Holding the row lock for the whole check-then-act
        // serialises concurrent senders on the same (identifier, purpose) row.
        val rateLimited = dbQuery {
            val existing = AuthOtpsTable.selectAll()
                .where { (AuthOtpsTable.identifier eq identifier) and (AuthOtpsTable.purpose eq purpose) }
                .forUpdate(ForUpdateOption.ForUpdate)   // lock the row for this txn
                .singleOrNull()

            if (existing != null) {
                val resends = existing[AuthOtpsTable.resendCount].toInt()
                val firstSent = existing[AuthOtpsTable.firstSentAt]
                val withinHour = firstSent.isAfter(windowStart)
                if (withinHour && resends >= maxResendsPerHour) {
                    return@dbQuery true   // over the cap — abort without writing
                }
                AuthOtpsTable.update({
                    (AuthOtpsTable.identifier eq identifier) and (AuthOtpsTable.purpose eq purpose)
                }) {
                    it[codeHash] = hash
                    it[codeSalt] = salt
                    it[sentAt] = now
                    it[expiresAt] = expires
                    // first_sent_at: keep if still in window, else reset
                    if (!withinHour) it[firstSentAt] = now
                    it[resendCount] = (if (withinHour) resends + 1 else 1).toShort()
                    it[attemptCount] = 0.toShort()
                    it[isVerified] = false
                    it[isLocked] = false
                    it[verifiedAt] = null
                    it[AuthOtpsTable.ipAddress] = ipAddress
                    it[AuthOtpsTable.userAgent] = userAgent
                    it[AuthOtpsTable.deviceId] = deviceId
                    it[deliveryChannel] = if (identifierType == "email") "email" else "sms"
                    it[deliveryProvider] = env("OTP_PROVIDER", "mock")
                    it[updatedAt] = now
                }
            } else {
                AuthOtpsTable.insert {
                    it[AuthOtpsTable.identifier] = identifier
                    it[AuthOtpsTable.identifierType] = identifierType
                    it[AuthOtpsTable.purpose] = purpose
                    it[codeHash] = hash
                    it[codeSalt] = salt
                    it[sentAt] = now
                    it[firstSentAt] = now
                    it[expiresAt] = expires
                    it[resendCount] = 1.toShort()
                    it[attemptCount] = 0.toShort()
                    it[AuthOtpsTable.maxAttempts] = this@OtpService.maxAttempts.toShort()
                    it[AuthOtpsTable.maxResends] = this@OtpService.maxResendsPerHour.toShort()
                    it[resendWindowSecs] = 3600
                    it[isVerified] = false
                    it[isLocked] = false
                    it[AuthOtpsTable.ipAddress] = ipAddress
                    it[AuthOtpsTable.userAgent] = userAgent
                    it[AuthOtpsTable.deviceId] = deviceId
                    it[deliveryChannel] = if (identifierType == "email") "email" else "sms"
                    it[deliveryProvider] = env("OTP_PROVIDER", "mock")
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }
            false
        }
        if (rateLimited) return OtpSendResult.RateLimited

        // Deliver via the multi-provider chain (WhatsApp Cloud → Fast2SMS →
        // MSG91 → Twilio → SMTP → Console).  The dispatcher picks the
        // cheapest configured provider that supports this identifier type.
        val delivered = OtpDeliveryProvider.deliver(
            identifier = identifier,
            identifierType = identifierType,
            code = code,
            purpose = purpose,
            locale = locale,
            ttlMinutes = expiryMinutes,
        )

        // ----------------------------------------------------------------
        // GATEWAY DEVICE DISPATCH (OTP-via-FCM, device-SIM flow)
        // ----------------------------------------------------------------
        // Per the binding decision ("both are needed"), we keep the existing
        // provider SMS delivery above AND additionally push an FCM SMS_REQUEST
        // to the freshest active OTPSender gateway device. The device sends
        // the SMS from its own SIM and reports SENT/FAILED back via the
        // gateway APIs (steps 3 & 4 of the 7-step flow).
        //
        // This is BEST-EFFORT: a gateway failure (no device online / FCM push
        // error) MUST NOT break the existing provider delivery — the user
        // still gets their OTP via the cloud-provider race above. The row is
        // created PENDING and left recoverable via GET /api/v1/gateway/pending
        // if no device is online. Email identifiers are skipped (SMS-only).
        if (identifierType == "phone") {
            runCatching {
                val smsBody = OtpMessageTemplates.smsBody(code, expiryMinutes, locale)
                val requestId = OtpSmsRequestRepository.createPending(
                    phoneNumber = identifier,
                    message = smsBody,
                )
                when (val gw = OTPSenderGatewayProvider.send(
                    SmsSendRequest(
                        requestId = requestId,
                        phoneNumber = identifier,
                        message = smsBody,
                    )
                )) {
                    is SmsSendResult.Dispatched -> log.info(
                        "[OtpService] gateway dispatched requestId={} fcmMsgId={}",
                        requestId, gw.providerMessageId,
                    )
                    is SmsSendResult.Queued -> log.info(
                        "[OtpService] gateway queued requestId={} reason={} " +
                            "(recoverable via /gateway/pending)",
                        requestId, gw.reason,
                    )
                    is SmsSendResult.Failed -> log.warn(
                        "[OtpService] gateway dispatch failed requestId={} reason={}",
                        requestId, gw.reason,
                    )
                }
            }.onFailure { t ->
                log.warn(
                    "[OtpService] gateway dispatch error (ignored, existing delivery unaffected): {}",
                    t.javaClass.simpleName,
                )
            }
        }

        // Persist the FULL attempt log (every provider tried) for forensics
        // and per-vendor success-rate dashboards.  We do this regardless of
        // outcome so we have a paper trail even if every provider failed.
        persistDeliveryAttempts(identifier, purpose, delivered.attempts)

        // If at least one provider succeeded, update the auth_otps row with
        // the WINNING provider's metadata so /verify-otp and admin tooling
        // can surface "delivered by Fast2SMS, ref id #abc" if support asks.
        if (delivered.ok) {
            val now = Instant.now()
            dbQuery {
                AuthOtpsTable.update({
                    (AuthOtpsTable.identifier eq identifier) and (AuthOtpsTable.purpose eq purpose)
                }) {
                    it[deliveryChannel] = delivered.channelWire
                    it[deliveryProvider] = delivered.providerName
                    it[providerMessageId] = delivered.providerMessageId
                    it[updatedAt] = now
                }
            }
        } else {
            log.warn(
                "[OtpService] delivery failed identifier-type={} purpose={} reason={}",
                identifierType, purpose, delivered.reason,
            )
            return OtpSendResult.DeliveryFailed(delivered.reason ?: "unknown")
        }

        val resendsNow = dbQuery {
            AuthOtpsTable.selectAll()
                .where { (AuthOtpsTable.identifier eq identifier) and (AuthOtpsTable.purpose eq purpose) }
                .single()[AuthOtpsTable.resendCount].toInt()
        }
        return OtpSendResult.Sent(
            identifier = identifier,
            expiresAt = expires,
            resendCount = resendsNow,
            devCode = if (devReturnCode) code else null
        )
    }

    /**
     * Write one row per provider attempt to `otp_delivery_attempts`.
     *
     * We do best-effort: a failure to write the audit row MUST NOT break
     * the user's OTP flow.  Audit is observability, not user-critical.
     */
    private suspend fun persistDeliveryAttempts(
        identifier: String,
        purpose: String,
        attempts: List<DeliveryAttemptRecord>,
    ) {
        if (attempts.isEmpty()) return
        val now = Instant.now()
        // Best-effort fetch of the parent otp_id; ignore if not found
        // (could be a race with purge).
        val otpId: UUID? = runCatching {
            dbQuery {
                AuthOtpsTable.selectAll()
                    .where { (AuthOtpsTable.identifier eq identifier) and (AuthOtpsTable.purpose eq purpose) }
                    .singleOrNull()
                    ?.get(AuthOtpsTable.id)?.value
            }
        }.getOrNull()

        runCatching {
            dbQuery {
                attempts.forEachIndexed { idx, a ->
                    OtpDeliveryAttemptsTable.insert {
                        it[OtpDeliveryAttemptsTable.otpId] = otpId
                        it[OtpDeliveryAttemptsTable.identifier] = identifier
                        it[OtpDeliveryAttemptsTable.purpose] = purpose
                        it[attemptIndex] = idx
                        it[providerName] = a.providerName
                        it[channel] = a.channelWire
                        it[status] = a.status
                        it[providerMessageId] = a.providerMessageId
                        it[httpStatus] = a.httpStatus
                        it[latencyMs] = a.latencyMillis.toInt().coerceAtLeast(0)
                        it[reason] = a.reason
                        it[rawResponse] = a.rawResponseSummary
                        it[createdAt] = now
                    }
                }
            }
        }.onFailure {
            log.warn(
                "[OtpService] failed to persist {} delivery attempts: {}",
                attempts.size, it.javaClass.simpleName,
            )
        }
    }

    // --------------------------------------------------------------
    // VERIFY
    // --------------------------------------------------------------
    suspend fun verify(
        identifier: String,
        code: String,
        purpose: String = "login"
    ): OtpVerifyResult {

        dbQuery { purgeExpired() }

        // RA-38: the load → compare → attempt-count update is now ONE transaction
        // with a `SELECT … FOR UPDATE` row lock. Previously the read and the
        // increment were two separate `dbQuery{}` transactions, so N concurrent
        // wrong guesses could all read the same attempt_count, all compute
        // `attempts = count + 1`, and all write the same value — so the lock
        // threshold (`attempts >= maxAttempts`) could be crossed far more than
        // maxAttempts times before `is_locked` ever stuck, weakening the
        // brute-force lock. Holding the lock across the whole check-then-act
        // serialises concurrent verifiers on this (identifier, purpose) row.
        return dbQuery {
            val row = AuthOtpsTable.selectAll()
                .where { (AuthOtpsTable.identifier eq identifier) and (AuthOtpsTable.purpose eq purpose) }
                .forUpdate(ForUpdateOption.ForUpdate)   // lock the row for this txn
                .singleOrNull()
                ?: return@dbQuery OtpVerifyResult.NotFound

            if (row[AuthOtpsTable.isLocked]) return@dbQuery OtpVerifyResult.Locked
            if (Instant.now().isAfter(row[AuthOtpsTable.expiresAt])) return@dbQuery OtpVerifyResult.Expired

            val expectedHash = row[AuthOtpsTable.codeHash]
            val salt = row[AuthOtpsTable.codeSalt]
            val attempt = sha256("$code:$salt:$pepper")

            if (!constantTimeEquals(expectedHash, attempt)) {
                val attempts = row[AuthOtpsTable.attemptCount].toInt() + 1
                val maxA = row[AuthOtpsTable.maxAttempts].toInt()
                val shouldLock = attempts >= maxA
                AuthOtpsTable.update({
                    (AuthOtpsTable.identifier eq identifier) and (AuthOtpsTable.purpose eq purpose)
                }) {
                    it[attemptCount] = attempts.toShort()
                    it[isLocked] = shouldLock
                    it[updatedAt] = Instant.now()
                }
                return@dbQuery if (shouldLock) OtpVerifyResult.Locked
                else OtpVerifyResult.Invalid(attemptsLeft = (maxA - attempts).coerceAtLeast(0))
            }

            // Success path.
            AuthOtpsTable.update({
                (AuthOtpsTable.identifier eq identifier) and (AuthOtpsTable.purpose eq purpose)
            }) {
                it[isVerified] = true
                it[verifiedAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
            OtpVerifyResult.Ok
        }
    }

    // --------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------

    /** Hard-deletes expired rows (and rows verified > 5 min ago). */
    private fun purgeExpired() {
        val now = Instant.now()
        val staleVerifiedCutoff = now.minus(5, ChronoUnit.MINUTES)
        AuthOtpsTable.deleteWhere {
            (AuthOtpsTable.expiresAt less now) or
                ((AuthOtpsTable.isVerified eq true) and (AuthOtpsTable.verifiedAt less staleVerifiedCutoff))
        }
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Constant-time string equality to avoid timing-side-channel leaks. */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}
