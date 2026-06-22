/*
 * File: SchoolDashboardRouting.kt
 * Module: feature.school
 *
 * Endpoint: GET /api/v1/school/dashboard   (JWT)
 *
 * Spec ref: school_api_spec.artifact.md §Module: School Dashboard
 *
 * Drives the home tab of the school admin app (SchoolDashboardScreen).
 * Returns three blocks:
 *
 *   1. welcome             — CMS key `school_dashboard_welcome`
 *   2. onboarding_progress — server-computed completed step count vs CMS
 *                            template `school_dashboard_steps`
 *   3. support             — CMS key `school_dashboard_support`
 *
 * Completion math: a step is "completed" when the authenticated user has at
 * least one row in `school_onboarding_drafts` with the matching step_type:
 *     id 1 → BASIC
 *     id 2 → BRANDING
 *     id 3 → ACADEMIC
 *     id 4 → REVIEW
 * (mapping kept here, not in CMS, because it's the contract with the existing
 *  /api/v1/onboarding/[step] implementation).
 *
 * NOTE: Do NOT write a literal "/" + "*" sequence in this block comment.
 * Kotlin block comments are NESTABLE — an inner "/" + "*" opens a new
 * nested comment that must be closed by its own "*" + "/", otherwise the
 * outer comment runs all the way to EOF and the compiler reports
 * "Syntax error: Unclosed comment" on the line after this file.
 *
 * If the user has not started any onboarding, all four steps return
 * is_completed=false and progress=0.0 (the UI shows "Pending").
 */
package com.littlebridge.enrollplus.feature.school

import com.littlebridge.enrollplus.core.fail
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.principalUserId
import com.littlebridge.enrollplus.db.AppConfigTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.feature.onboarding.computeOnboardingStatus
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

// ---------------- DTOs ----------------

@Serializable
data class SchoolDashboardWelcome(
    val title: String,
    val subtitle: String,
    @SerialName("cta_label") val ctaLabel: String
)

@Serializable
data class OnboardingStepDto(
    val id: Int,
    val title: String,
    val description: String,
    @SerialName("is_completed") val isCompleted: Boolean,
    @SerialName("icon_url") val iconUrl: String? = null
)

@Serializable
data class OnboardingProgressDto(
    @SerialName("completed_steps") val completedSteps: Int,
    @SerialName("total_steps") val totalSteps: Int,
    val progress: Double,
    @SerialName("status_label") val statusLabel: String,
    val steps: List<OnboardingStepDto>
)

@Serializable
data class SchoolSupportDto(
    val title: String,
    val subtitle: String,
    @SerialName("action_label") val actionLabel: String,
    @SerialName("video_label") val videoLabel: String,
    @SerialName("video_url") val videoUrl: String? = null
)

@Serializable
data class SchoolDashboardResponse(
    val welcome: SchoolDashboardWelcome,
    @SerialName("onboarding_progress") val onboardingProgress: OnboardingProgressDto,
    val support: SchoolSupportDto
)

// internal — used only for parsing the CMS template
@Serializable
private data class StepTemplate(
    val id: Int,
    val title: String,
    val description: String,
    @SerialName("icon_url") val iconUrl: String? = null
)

// Built-in fallback when the CMS template `school_dashboard_steps` is empty.
private val DEFAULT_STEP_TEMPLATES = listOf(
    StepTemplate(1, "Institutional Basics", "Core school info and identity", null),
    StepTemplate(2, "Branding & Visuals", "Logo and portal themes", null),
    StepTemplate(3, "Academic Structure", "Grade levels and curricula", null),
    StepTemplate(4, "Launch & Review", "Final check & go live", null)
)

private val LENIENT_JSON = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

private fun cmsString(key: String, default: String): String =
    AppConfigTable.selectAll()
        .where { AppConfigTable.key eq key }
        .singleOrNull()
        ?.get(AppConfigTable.value)
        ?: default

fun Route.schoolDashboardRouting() {
    authenticate("jwt") {
        route("/api/v1/school") {
            get("/dashboard") {
                val uid = call.principalUserId()?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: run {
                    call.fail("Invalid token", HttpStatusCode.Unauthorized); return@get
                }

                // Single source of truth: derive completion from persisted school
                // data (schools row + school_classes + onboarded_at), NOT from the
                // presence of half-typed draft rows.
                val status = computeOnboardingStatus(uid)

                val payload = dbQuery {
                    // ---- 1. welcome ----
                    val welcomeRaw = cmsString(
                        "school_dashboard_welcome",
                        """{"title":"Welcome, Admin","subtitle":"Let's build your digital campus.","cta_label":"Start Onboarding"}"""
                    )
                    val welcome = runCatching {
                        LENIENT_JSON.decodeFromString(SchoolDashboardWelcome.serializer(), welcomeRaw)
                    }.getOrElse {
                        SchoolDashboardWelcome("Welcome, Admin", "Let's build your digital campus.", "Start Onboarding")
                    }

                    // ---- 2. onboarding_progress ----
                    val stepsRaw = cmsString("school_dashboard_steps", "[]")
                    val templatesRaw: List<StepTemplate> = runCatching {
                        LENIENT_JSON.decodeFromString(
                            ListSerializer(StepTemplate.serializer()),
                            stepsRaw
                        )
                    }.getOrElse { emptyList() }

                    // Fall back to a built-in 4-step template if CMS has none, so
                    // the progress card always reflects the real status.
                    val templates = templatesRaw.ifEmpty { DEFAULT_STEP_TEMPLATES }

                    val steps = templates.map { tpl ->
                        OnboardingStepDto(
                            id = tpl.id,
                            title = tpl.title,
                            description = tpl.description,
                            isCompleted = status.isStepDone(tpl.id),
                            iconUrl = tpl.iconUrl
                        )
                    }
                    val total = steps.size
                    val done = steps.count { it.isCompleted }
                    val progress = if (total == 0) 0.0 else done.toDouble() / total.toDouble()
                    val statusLabel = when {
                        total == 0 -> "Not Configured"
                        done == 0 -> "Pending"
                        done < total -> "In Progress"
                        else -> "Complete"
                    }

                    // ---- 3. support ----
                    val supportRaw = cmsString(
                        "school_dashboard_support",
                        """{"title":"Need help?","subtitle":"Available 24/7","action_label":"CHAT","video_label":"Watch Onboarding Video","video_url":null}"""
                    )
                    val support = runCatching {
                        LENIENT_JSON.decodeFromString(SchoolSupportDto.serializer(), supportRaw)
                    }.getOrElse {
                        SchoolSupportDto("Need help?", "Available 24/7", "CHAT", "Watch Onboarding Video", null)
                    }

                    SchoolDashboardResponse(
                        welcome = welcome,
                        onboardingProgress = OnboardingProgressDto(
                            completedSteps = done,
                            totalSteps = total,
                            progress = progress,
                            statusLabel = statusLabel,
                            steps = steps
                        ),
                        support = support
                    )
                }

                call.ok(payload, message = "School dashboard fetched successfully")
            }
        }
    }
}
