/*
 * File: NotificationRouting.kt
 * Module: feature.notification.api
 *
 * HTTP surface for the notification FOUNDATION (push delivery + device token
 * registration). Distinct from the role-aware INBOX in feature/notifications/
 * (plural) — that spine handles read/unread state + the bell summary; THIS
 * module handles FCM device-token registration + admin-initiated push dispatch.
 *
 *   POST /api/device-tokens               — register/refresh the caller's own
 *                                            FCM/APNs token (multi-device safe).
 *   POST /api/admin/notifications/send    — school-admin broadcasts a push to
 *                                            a set of userIds via Firebase Admin SDK.
 *
 * COLLABORATORS (NO DI on the server — see Notify.kt pattern)
 *   Module-level singletons hold the repository + service so every request
 *   shares the same instance without introducing a DI container. Both are
 *   trivially stateless beyond their DB / Firebase handles.
 */
package com.littlebridge.enrollplus.feature.notification.api

import com.littlebridge.enrollplus.core.fail
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.principalUserUuid
import com.littlebridge.enrollplus.core.requireSchoolAdmin
import com.littlebridge.enrollplus.core.resolveSchoolIdForUser
import com.littlebridge.enrollplus.feature.notification.dto.RegisterDeviceTokenRequest
import com.littlebridge.enrollplus.feature.notification.dto.RegisterDeviceTokenResponse
import com.littlebridge.enrollplus.feature.notification.dto.SendNotificationRequest
import com.littlebridge.enrollplus.feature.notification.dto.SendNotificationResponse
import com.littlebridge.enrollplus.feature.notification.repository.DeviceTokenRepository
import com.littlebridge.enrollplus.feature.notification.service.NotificationService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

// ------------------------------------------------------------------
// No-DI collaborators — module-level singletons (see Notify.kt pattern).
// DeviceTokenRepository owns all device_tokens mutations; NotificationService
// is the reusable push-dispatch entry point used by the admin send endpoint
// (and, in future, by any server feature that needs to push).
// ------------------------------------------------------------------
private val deviceTokenRepository = DeviceTokenRepository()
private val notificationService = NotificationService(deviceTokenRepository)

/**
 * Mounts the notification FOUNDATION endpoints:
 *
 *   POST /api/device-tokens
 *     Auth: any authenticated user (jwt). Registers the caller's own device
 *     token. school_id is resolved server-side from app_users (not trusted
 *     from the client) when the caller belongs to a school. Multi-device safe
 *     — see [DeviceTokenRepository.upsertToken].
 *
 *   POST /api/admin/notifications/send
 *     Auth: school admin (SCHOOL_ADMIN_ROLES via [requireSchoolAdmin]). Accepts
 *     a [SendNotificationRequest] (title, body, userIds, optional deepLink +
 *     arbitrary data) and dispatches a hybrid FCM payload to every active
 *     device token owned by the recipients. Returns a [SendNotificationResponse]
 *     tally (sent / failed). When Firebase credentials are not configured the
 *     service degrades to a no-op (success=false, sent=0) instead of 5xx-ing.
 */
fun Route.notificationRouting() {
    authenticate("jwt") {

        // -------- register / refresh a device token --------
        post("/api/device-tokens") {

            val uid = call.principalUserUuid() ?: run {
                call.fail("Invalid token", HttpStatusCode.Unauthorized, "UNAUTHORIZED")
                return@post
            }
            val req = runCatching { call.receive<RegisterDeviceTokenRequest>() }.getOrNull()
                ?: run {
                    call.fail("Invalid request body", HttpStatusCode.BadRequest, "BAD_REQUEST")
                    return@post
                }
            if (req.token.isBlank()) {
                call.fail("token must not be blank", HttpStatusCode.BadRequest, "BAD_REQUEST")
                return@post
            }

            // Resolve the caller's school (optional — parents/teachers may not
            // have one yet; the token is still registered unscoped so a later
            // school-bound broadcast can find them).
            val schoolId = runCatching { resolveSchoolIdForUser(uid) }.getOrNull()

            deviceTokenRepository.upsertToken(
                userId = uid,
                token = req.token,
                platform = req.platform.ifBlank { "android" },
                schoolId = schoolId,
                appVersion = req.appVersion,
                deviceModel = req.deviceModel,
            )

            call.ok(RegisterDeviceTokenResponse(success = true), message = "Device token registered")
        }

        // -------- admin: broadcast a push notification --------
        post("/api/admin/notifications/send") {
            // School-admin guard — responds 401/403/404 itself on failure.
            // We do not need ctx for the dispatch (recipients are userIds in
            // the request body), but the guard is what enforces that only
            // school admins can trigger a server-side push.
            @Suppress("UNUSED_VARIABLE")
            val ctx = call.requireSchoolAdmin() ?: return@post

            val req = runCatching { call.receive<SendNotificationRequest>() }.getOrNull()
                ?: run {
                    call.fail("Invalid request body", HttpStatusCode.BadRequest, "BAD_REQUEST")
                    return@post
                }
            if (req.title.isBlank() || req.body.isBlank()) {
                call.fail("title and body must not be blank", HttpStatusCode.BadRequest, "BAD_REQUEST")
                return@post
            }

            val response: SendNotificationResponse = notificationService.send(req)
            call.ok(response, message = "Notification dispatched")
        }
    }
}
