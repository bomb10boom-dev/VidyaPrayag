/*
 * File: Application.kt
 * Module: server entry point
 *
 * Wires up all Ktor plugins and feature routes for the VidyaPrayag backend.
 *
 * Plugins installed (in order):
 *   1. IgnoreTrailingSlash       — /foo and /foo/ map to the same handler
 *   2. CORS                      — open during dev so the Compose web target
 *                                   and your phone can hit the local server
 *   3. CallLogging               — basic stdout logging of every request
 *   4. ContentNegotiation        — kotlinx.serialization JSON
 *   5. Authentication ("jwt")    — JWT bearer via core/SecurityModule
 *   6. StatusPages               — uniform error envelopes
 *
 * Routes mounted:
 *   - GET  /                              — liveness greeting
 *   - landingRouting()                    — /api/v1/content/landing
 *   - appStatusRouting()                  — /api/v1/config/app-status
 *   - authRouting()                       — /api/v1/auth/... (+ legacy /auth/...)
 *   - supportRouting()                    — /api/v1/content/support
 *   - userDetailsRouting()                — /api/v1/user/details
 *   - userProfileRouting()                — /api/v1/user/profile[…]
 *   - onboardingRouting()                 — /api/v1/onboarding/...
 *   - announcementRouting()               — /api/v1/school/announcements[…]
 *   - admissionRouting()                  — /api/v1/admissions/enquiries[…]
 *   - schoolRouting()                     — /api/v1/school/{analytics,calendar,holidays,attendance/daily}
 *   - parentDashboardRouting()            — /api/v1/parent/dashboard
 *   - trackProgressRouting()              — /api/v1/parent/track-progress
 *   - parentFeesRouting()                 — /api/v1/parent/fees
 *   - parentLinkRouting()                 — /api/v1/parent/{schools/search, link-child}
 *   - schoolDashboardRouting()            — /api/v1/school/dashboard
 *   - adminDashboardRouting()             — /api/admin/dashboard/{summary,analytics,activity}
 *   - adminDashboardOverviewRouting()     — /api/admin/dashboard/overview
 *   - schoolAnalyticsRouting()            — /api/v1/school/analytics/{overview,class-performance,teacher-performance,student/{id},syllabus-coverage}
 *   - leaveRequestsRouting()              — /api/v1/school/leave-requests[…]
 *   - ptmRouting()                        — /api/v1/school/ptm
 *   - messagesRouting()                   — /api/v1/school/messages[…]
 *   - resultsRouting()                    — /api/v1/school/results
 *   - teacherAssignmentRouting()          — /api/v1/school/teacher-assignments[…]
 *   - mediaRouting()                      — /api/v1/school/media/upload[…] (binary → Supabase Storage)
 *   - teacherRouting()                    — /api/v1/teacher/{home,classes,profile,attendance,marks,syllabus,homework}
 *
 * On boot:
 *   DatabaseFactory.init() creates/migrates all tables and seeds CMS + demo data.
 *
 * Manual DevOps steps (one-time):
 *   - Set DATABASE_URL in .env or env (postgres://… or jdbc:postgresql://…)
 *   - Set JWT_SECRET to a strong random value in production
 */
package com.littlebridge.enrollplus

import com.littlebridge.enrollplus.core.configureErrorHandling
import com.littlebridge.enrollplus.core.configureJwt
import com.littlebridge.enrollplus.db.DatabaseFactory
import com.littlebridge.enrollplus.feature.admissions.admissionRouting
import com.littlebridge.enrollplus.feature.announcements.announcementRouting
import com.littlebridge.enrollplus.feature.auth.authRouting
import com.littlebridge.enrollplus.feature.calendar.academicCalendarRouting
import com.littlebridge.enrollplus.feature.calendar.academicYearRouting
import com.littlebridge.enrollplus.feature.auth.otpAdminRouting
import com.littlebridge.enrollplus.feature.config.appStatusRouting
import com.littlebridge.enrollplus.feature.config.versionRouting
import com.littlebridge.enrollplus.feature.content.landingRouting
import com.littlebridge.enrollplus.feature.content.supportRouting
import com.littlebridge.enrollplus.feature.gateway.api.gatewayRouting
import com.littlebridge.enrollplus.feature.media.mediaRouting
import com.littlebridge.enrollplus.feature.notification.api.notificationRouting
import com.littlebridge.enrollplus.feature.notifications.notificationsRouting
import com.littlebridge.enrollplus.feature.onboarding.onboardingRouting
import com.littlebridge.enrollplus.feature.parent.parentDashboardRouting
import com.littlebridge.enrollplus.feature.parent.parentFeesRouting
import com.littlebridge.enrollplus.feature.parent.parentLeaveRouting
import com.littlebridge.enrollplus.feature.parent.parentLinkRouting
import com.littlebridge.enrollplus.feature.parent.parentAcademicsRouting
import com.littlebridge.enrollplus.feature.parent.trackProgressRouting
import com.littlebridge.enrollplus.feature.school.adminDashboardRouting
import com.littlebridge.enrollplus.feature.school.adminDashboardOverviewRouting
import com.littlebridge.enrollplus.feature.school.leaveRequestsRouting
import com.littlebridge.enrollplus.feature.school.messagesRouting
import com.littlebridge.enrollplus.feature.school.ptmRouting
import com.littlebridge.enrollplus.feature.school.resultsRouting
import com.littlebridge.enrollplus.feature.school.schoolAnalyticsRouting
import com.littlebridge.enrollplus.feature.school.schoolDashboardRouting
import com.littlebridge.enrollplus.feature.school.schoolIntelligenceRouting
import com.littlebridge.enrollplus.feature.school.schoolProfileRouting
import com.littlebridge.enrollplus.feature.school.schoolRecordsRouting
import com.littlebridge.enrollplus.feature.school.schoolStudentsRouting
import com.littlebridge.enrollplus.feature.school.schoolTimetableRouting
import com.littlebridge.enrollplus.feature.school.nonTeachingStaffRouting
import com.littlebridge.enrollplus.feature.school.schoolRouting
import com.littlebridge.enrollplus.feature.school.teacherAssignmentRouting
import com.littlebridge.enrollplus.feature.school.teacherProvisioningRouting
import com.littlebridge.enrollplus.feature.teacher.teacherLeaveRouting
import com.littlebridge.enrollplus.feature.teacher.teacherMessagesRouting
import com.littlebridge.enrollplus.feature.teacher.teacherRouting
import com.littlebridge.enrollplus.feature.user.parentRouting
import com.littlebridge.enrollplus.feature.user.parentMessagesRouting
import com.littlebridge.enrollplus.feature.user.userDetailsRouting
import com.littlebridge.enrollplus.feature.user.userProfileRouting
import com.littlebridge.enrollplus.core.ApiError
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Properties

/**
 * RA-36: max accepted Content-Length for non-multipart (JSON) requests, in bytes.
 * Without this, any `call.receive<…>()` buffers an unbounded body into memory →
 * a single fat POST to e.g. /api/v1/auth/login can OOM the 512 MB dyno. The media
 * upload route enforces its own 25 MB multipart cap, so we exempt multipart here.
 */
private const val MAX_JSON_BODY_BYTES = 1L * 1024 * 1024 // 1 MB

private fun loadRootLocalProperties(): Properties {
    val file = File("local.properties")

    return Properties().apply {
        if (file.exists()) {
            file.inputStream().use { load(it) }
        }
    }
}

fun main() {
    DatabaseFactory.init()
    val props = loadRootLocalProperties()

    val host = props.getProperty("SERVER_HOST") ?: "0.0.0.0"
    val port = props.getProperty("SERVER_PORT")
        ?.toIntOrNull()
        ?: SERVER_PORT

    embeddedServer(
        Netty,
        port = port,
        host = host,
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    install(IgnoreTrailingSlash)

    // RA-36: reject oversized JSON bodies before they are buffered into memory.
    // Multipart uploads (media route) are exempt — they enforce their own 25 MB
    // streaming cap. Anything else declaring a Content-Length over the JSON limit
    // is refused with 413 so a single fat POST cannot OOM the dyno.
    intercept(ApplicationCallPipeline.Plugins) {
        val contentType = call.request.contentType()
        val isMultipart = contentType.match(ContentType.MultiPart.FormData)
        val declaredLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (!isMultipart && declaredLength != null && declaredLength > MAX_JSON_BODY_BYTES) {
            call.respond(
                HttpStatusCode.PayloadTooLarge,
                ApiError(
                    message = "Request body too large (max ${MAX_JSON_BODY_BYTES / 1024} KB).",
                    errorCode = "PAYLOAD_TOO_LARGE",
                ),
            )
            return@intercept finish()
        }
    }

    install(CORS) {
        // RA-37: open CORS (anyHost) is fine in dev but in production lets any
        // origin script authenticated cross-origin calls with a captured bearer
        // token. In prod (DATABASE_URL present) we lock to an explicit allow-list
        // from CORS_ALLOWED_ORIGINS (comma-separated host[:port], optionally with
        // scheme). Only dev/local falls back to anyHost().
        val isProduction = System.getenv("DATABASE_URL")?.isNotBlank() == true
        val configuredOrigins = System.getenv("CORS_ALLOWED_ORIGINS")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        if (isProduction && configuredOrigins.isNotEmpty()) {
            configuredOrigins.forEach { origin ->
                // Accept "https://app.example.com", "app.example.com" or with a port.
                val withoutScheme = origin.substringAfter("://", origin)
                val scheme = if (origin.contains("://")) origin.substringBefore("://") else null
                val host = withoutScheme.substringBefore(":")
                val schemes = scheme?.let { listOf(it) } ?: listOf("https", "http")
                allowHost(host, schemes = schemes)
            }
        } else {
            // Dev/local (no DATABASE_URL) or prod without an explicit allow-list:
            // keep the permissive default so local web + device testing still work.
            anyHost()
        }
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("App-Version")
        allowHeader("Platform")
        allowHeader("Device-Id")
        allowHeader("Accept-Language")
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
    }

    install(CallLogging)

    install(AutoHeadResponse)

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            explicitNulls = false
        })
    }

    install(Authentication) { configureJwt() }

    install(StatusPages) { configureErrorHandling() }

    routing {
        get("/") {
            call.respondText("Ktor: ${Greeting().greet()} — VidyaPrayag API v1 is live")
        }

        // Public
        landingRouting()
        appStatusRouting()
        versionRouting()             // /api/v1/config/version — backend-target visibility
        authRouting()
        supportRouting()

        // Ops-only — entire route group is unmounted (404) unless
        // OTP_ADMIN_TOKEN env var is set. See feature/auth/OtpAdminRouting.kt.
        // Safe to leave wired up on Render free tier — no overhead when
        // the token env is empty.
        otpAdminRouting()

        // OTPSender SMS-gateway integration (feature/setup_notification).
        // Machine-to-machine peer authenticated via the X-Gateway-Token header.
        // Entire route group is unmounted (404) unless OTP_GATEWAY_TOKEN is set
        // — same ops-only convention as otpAdminRouting above.
        //   POST /api/v1/gateway/register
        //   POST /api/v1/gateway/heartbeat
        //   GET  /api/v1/gateway/requests/{requestId}
        //   POST /api/v1/gateway/requests/{requestId}/status
        //   GET  /api/v1/gateway/pending
        gatewayRouting()

        // Authenticated
        userDetailsRouting()
        userProfileRouting()
        parentRouting()
        parentMessagesRouting()      // /api/v1/parent/messages[…] — parent-school harmony (report §9.2)
        onboardingRouting()
        announcementRouting()
        admissionRouting()
        schoolRouting()

        // Parent ecosystem (parent_api_spec.artifact.md)
        parentDashboardRouting()     // /api/v1/parent/dashboard
        trackProgressRouting()       // /api/v1/parent/track-progress
        parentAcademicsRouting()     // /api/v1/parent/child/{id}/{attendance,marks,syllabus} — RA-43/RA-56 child-scoped reads
        parentFeesRouting()          // /api/v1/parent/fees
        parentLeaveRouting()         // /api/v1/parent/leave — RA-44 parent applies/lists child leave (routes to class teacher)
        parentLinkRouting()          // /api/v1/parent/{schools/search, link-child} + /api/v1/school/link-requests{,/{id}/approve|reject} — RA-48 link approval workflow

        // School ecosystem (school_api_spec.artifact.md)
        schoolDashboardRouting()     // /api/v1/school/dashboard
        adminDashboardRouting()      // /api/admin/dashboard/{summary,analytics,activity} — redesigned SchoolHomeScreenV2 data
        adminDashboardOverviewRouting() // /api/admin/dashboard/overview — consolidated command-center payload for SchoolHomeScreenV2
        schoolIntelligenceRouting()  // /api/v1/school/dashboard/intelligence — Command Center: attendance timeline+anomalies+exam overlay, early-warning students, academic health grid, activity feed (all real-data)
        schoolAnalyticsRouting()     // /api/v1/school/analytics/{overview,class-performance,teacher-performance,student/{id},syllabus-coverage}
        leaveRequestsRouting()       // /api/v1/school/leave-requests[…]
        ptmRouting()                 // /api/v1/school/ptm
        messagesRouting()            // /api/v1/school/messages[…]
        resultsRouting()             // /api/v1/school/results
        teacherAssignmentRouting()   // /api/v1/school/teacher-assignments[…] — structured teacher⇄class⇄subject model (report §5.5)
        teacherProvisioningRouting() // /api/v1/school/teachers[…] — school-admin creates teacher app_users rows (audit finding C)
        schoolProfileRouting()       // /api/v1/school/profile — RA-47 read/edit institutional schools row (school-admin write)
        schoolStudentsRouting()      // /api/v1/school/students[…] + teachers/{id} — RA-45 student roster + student/teacher profile (school-scoped)
        nonTeachingStaffRouting()    // /api/v1/school/staff[…] — RA-S17 non-teaching-staff vertical (school-scoped CRUD)
        schoolRecordsRouting()       // /api/v1/school/{attendance/summary,marks/summary,fees/ledger} — RA-52 admin Records rollups (school-scoped reads)
        schoolTimetableRouting()     // /api/v1/school/timetable — school-wide weekly schedule (all classes) from teacher_periods, for the Command Center calendar (read-only, additive)
        mediaRouting()               // /api/v1/school/media/upload[…] — REAL binary uploads → Supabase Storage (kills URL placeholders)

        // Academic Calendar platform (VP-CAL) — centralized planning & scheduling
        academicCalendarRouting()    // /api/admin/calendar/{dashboard,events[…],events/{id}/duplicate}
        academicYearRouting()        // /api/admin/academic-years[…] — real Academic Year management (replaces "Coming Soon")

        // Teacher vertical (master rebuild doc Step 7 / gap G1)
        teacherRouting()             // /api/v1/teacher/{home,classes,profile,attendance,marks,syllabus,homework}
        teacherLeaveRouting()        // /api/v1/teacher/leave-requests[…] — RA-44 teacher lists/decides leave for their classes
        teacherMessagesRouting()     // /api/v1/teacher/messages[…] — RA-51 teacher↔parent messaging + class broadcast

        // Cross-user notification spine (audit part-2 RA-41/42/46/50) — role-aware
        // inbox replacing the parent-only synth; persisted read state; bell summary.
        notificationsRouting()       // /api/v1/notifications[/summary,/{id}/read,/read-all,/device-token]

        // Notification FOUNDATION (push infra — distinct from the inbox spine):
        //   /api/device-tokens            — register/refresh FCM token (any role)
        //   /api/admin/notifications/send — school-admin broadcast via Firebase Admin SDK
        notificationRouting()
    }
}
