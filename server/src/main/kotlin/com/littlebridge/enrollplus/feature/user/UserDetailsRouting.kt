/*
 * File: UserDetailsRouting.kt
 * Module: feature.user
 *
 * GET /api/v1/user/details   (JWT required)
 *
 * Spec ref: vidya_prayag_api_spec.artifact.md §Get User Details & Onboarding State
 *
 * Source of truth (post-login):
 *   - app_users           : personal_details (id, role, name, email, mobile, pic)
 *   - schools             : the school this user belongs to (via app_users.school_id)
 *   - school_classes      : Step 3 (ACADEMIC) completion check
 *   - app_config "flags"  : drives menu_features (is_enabled/is_live) so ops can
 *                           toggle modules without redeploying
 *
 * Onboarding step status logic (only meaningful for school_admin / super_admin):
 *   Step 1 (BASIC)     → school name + contact_email/phone set
 *   Step 2 (BRANDING)  → logo_url present
 *   Step 3 (ACADEMIC)  → at least one row in school_classes
 *   Step 4 (REVIEW)    → school.onboarded_at IS NOT NULL
 */
package com.littlebridge.enrollplus.feature.user

import com.littlebridge.enrollplus.core.fail
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.principalUserId
import com.littlebridge.enrollplus.db.AppConfigTable
import com.littlebridge.enrollplus.db.AppUsersTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.feature.onboarding.computeOnboardingStatus
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

@Serializable
data class PersonalDetails(
    val role: String,
    val id: String,
    val name: String,
    @SerialName("profile_pic") val profilePic: String? = null,
    val email: String? = null,
    val mobile: String? = null
)

@Serializable
data class OnboardingStepDto(
    val name: String,
    val description: String,
    val status: String,
    val icon: String,
    @SerialName("is_enabled") val isEnabled: Boolean,
    @SerialName("is_required") val isRequired: Boolean
)

@Serializable
data class SupportInfo(
    val name: String,
    val description: String,
    @SerialName("contact_number") val contactNumber: String,
    @SerialName("contact_email") val contactEmail: String,
    val icon: String
)

@Serializable
data class MenuFeature(
    val name: String,
    @SerialName("is_enabled") val isEnabled: Boolean,
    @SerialName("is_live") val isLive: Boolean
)

@Serializable
data class AppTheme(
    val name: String,
    @SerialName("is_enabled") val isEnabled: Boolean,
    @SerialName("is_live") val isLive: Boolean
)

@Serializable
data class OnboardingDetails(
    @SerialName("onboarding_status") val onboardingStatus: String,
    @SerialName("total_steps") val totalSteps: Int,
    @SerialName("list_of_steps") val listOfSteps: List<OnboardingStepDto>,
    @SerialName("support_info") val supportInfo: SupportInfo,
    @SerialName("tutorial_video_link") val tutorialVideoLink: String,
    @SerialName("menu_features") val menuFeatures: List<MenuFeature>,
    @SerialName("app_themes") val appThemes: List<AppTheme>,
    @SerialName("tos_link") val tosLink: String,
    @SerialName("privacy_policy_link") val privacyPolicyLink: String
)

@Serializable
data class UserDetailsResponse(
    @SerialName("personal_details") val personalDetails: PersonalDetails,
    @SerialName("onboarding_details") val onboardingDetails: OnboardingDetails
)

private val DEFAULT_SUPPORT = SupportInfo(
    name = "VidyaPrayag Success Team",
    description = "Available 9am - 6pm for setup help",
    contactNumber = "+91-9988776655",
    contactEmail = "support@vidyaprayag.com",
    icon = "support_agent"
)

private val DEFAULT_THEMES = listOf(
    AppTheme("LIGHT", isEnabled = true, isLive = true),
    AppTheme("DARK", isEnabled = true, isLive = true),
    AppTheme("MIDNIGHT", isEnabled = true, isLive = true)
)

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun Route.userDetailsRouting() {
    authenticate("jwt") {
        route("/api/v1/user") {
            get("/details") {
                val uid = call.principalUserId() ?: run {
                    call.fail("Invalid token", HttpStatusCode.Unauthorized); return@get
                }
                val userUuid = runCatching { UUID.fromString(uid) }.getOrNull() ?: run {
                    call.fail("Malformed token subject", HttpStatusCode.Unauthorized); return@get
                }

                // Single source of truth for onboarding completion (shared with
                // SchoolDashboard). Derived strictly from persisted school data.
                val status = computeOnboardingStatus(userUuid)

                val payload = dbQuery {
                    val u = AppUsersTable.selectAll()
                        .where { AppUsersTable.id eq userUuid }
                        .singleOrNull()
                        ?: return@dbQuery null

                    val personal = PersonalDetails(
                        role = u[AppUsersTable.role].uppercase(),
                        id = u[AppUsersTable.id].value.toString(),
                        name = u[AppUsersTable.fullName],
                        profilePic = u[AppUsersTable.profilePicUrl],
                        email = u[AppUsersTable.email],
                        mobile = u[AppUsersTable.phone]
                    )

                    val basicsDone = status.basicsDone
                    val brandingDone = status.brandingDone
                    val academicDone = status.academicDone
                    val finalDone = status.finalDone

                    fun statusFor(done: Boolean, prevDone: Boolean) = when {
                        done -> "COMPLETED"
                        prevDone -> "PENDING"
                        else -> "LOCKED"
                    }

                    val steps = listOf(
                        OnboardingStepDto("Institutional Basics", "Core school info and identity",
                            statusFor(basicsDone, true), "school", true, true),
                        OnboardingStepDto("Branding & Visuals", "Logo and portal themes",
                            statusFor(brandingDone, basicsDone), "palette", basicsDone, true),
                        OnboardingStepDto("Academic Structure", "Grade levels and curricula",
                            statusFor(academicDone, brandingDone), "history_edu", brandingDone, true),
                        OnboardingStepDto("Launch & Review", "Final check & go live",
                            statusFor(finalDone, academicDone), "rocket_launch", academicDone, true)
                    )

                    val overall = status.overallStatus

                    // Menu features come from app_config.flags.
                    val flagsRaw = AppConfigTable.selectAll()
                        .where { AppConfigTable.key eq "flags" }
                        .singleOrNull()
                        ?.get(AppConfigTable.value)
                    val flags: JsonObject = flagsRaw?.let {
                        runCatching { lenientJson.parseToJsonElement(it).let { e -> e as JsonObject } }
                            .getOrNull() ?: JsonObject(emptyMap())
                    } ?: JsonObject(emptyMap())
                    fun flag(name: String, default: Boolean = true): Boolean =
                        flags[name]?.jsonPrimitive?.content?.equals("true", true) ?: default

                    val menu = listOf(
                        MenuFeature("Analytics", isEnabled = true, isLive = flag("is_ai_narrative_live", false)),
                        MenuFeature("PTM Management", isEnabled = true, isLive = true),
                        MenuFeature("Scholarships",
                            isEnabled = flag("show_scholarships", false),
                            isLive = flag("show_scholarships", false)),
                        MenuFeature("Attendance", isEnabled = true, isLive = true),
                        MenuFeature("Calendar", isEnabled = true, isLive = true)
                    )

                    val ob = OnboardingDetails(
                        onboardingStatus = overall,
                        totalSteps = steps.size,
                        listOfSteps = steps,
                        supportInfo = DEFAULT_SUPPORT,
                        tutorialVideoLink = "https://vidyaprayag.com/tutorials/onboarding",
                        menuFeatures = menu,
                        appThemes = DEFAULT_THEMES,
                        tosLink = "https://vidyaprayag.com/terms",
                        privacyPolicyLink = "https://vidyaprayag.com/privacy"
                    )

                    UserDetailsResponse(personalDetails = personal, onboardingDetails = ob)
                }

                if (payload == null) {
                    call.fail("User not found", HttpStatusCode.NotFound)
                } else {
                    call.ok(payload, message = "User details fetched")
                }
            }
        }
    }
}
