/*
 * File: NotificationService.kt
 * Module: feature.notification.service
 *
 * The single, reusable push-dispatch entry point for the whole backend. Every
 * future feature that needs to push (announcement broadcast, attendance alert,
 * fee reminder, calendar event reminder, …) hands a [SendNotificationRequest]
 * shape to [send] — the same shape the admin testing endpoint
 * (POST /api/admin/notifications/send) uses — and gets back a
 * [SendNotificationResponse] tally.
 *
 * RESPONSIBILITIES
 *   1. Resolve recipient device tokens via [DeviceTokenRepository] (multi-
 *      device fan-out: every ACTIVE token for every requested user).
 *   2. Build hybrid FCM payloads: a `notification` block (title + body) so the
 *      system tray renders the push when the app is in the background, PLUS a
 *      `data` block carrying `title`, `body`, `deepLink`, and the caller's
 *      arbitrary `data` map (type / entityId / schoolId / …) so the client
 *      FirebaseMessagingService can route the tap and foreground-handle it.
 *   3. Dispatch via the Firebase Admin SDK (NOT raw FCM REST) using
 *      sendEachForMulticast, chunked at the 500-token FCM multicast ceiling.
 *   4. Reconcile per-token results: UNREGISTERED / invalid tokens are marked
 *      inactive in device_tokens (so we stop burning FCM quota on dead
 *      installs); other failures are counted but left active for retry.
 *   5. Return sent/failed tallies. When Firebase is not initialised
 *      (credentials absent → FirebaseAdminInitializer.app() == null) the
 *      service degrades to a no-op returning success=false, sent=0 — it NEVER
 *      throws, so the admin endpoint and internal callers stay boot-safe.
 *
 * WHY A SERVICE (NOT INLINE ROUTE LOGIC)
 *   The notification foundation spec mandates an abstraction reusable by
 *   future features. Keeping dispatch + token reconciliation in one place
 *   means a new "broadcast announcement" call site is a one-liner, and the
 *   invalid-token cleanup policy stays consistent everywhere.
 */
package com.littlebridge.enrollplus.feature.notification.service

import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import com.littlebridge.enrollplus.feature.notification.dto.SendNotificationRequest
import com.littlebridge.enrollplus.feature.notification.dto.SendNotificationResponse
import com.littlebridge.enrollplus.feature.notification.firebase.FirebaseAdminInitializer
import com.littlebridge.enrollplus.feature.notification.repository.DeviceTokenRepository
import java.util.UUID

class NotificationService(
    private val deviceTokenRepository: DeviceTokenRepository
) {

    /**
     * Dispatch [request] to every active device token owned by
     * [request.userIds]. Multi-device safe: each user's every active token
     * receives its own copy.
     *
     * Contract:
     *   - empty [SendNotificationRequest.userIds] → 200 with sent=0, failed=0
     *     (NOT an error — lets callers fan out conditionally).
     *   - Firebase unavailable (no credentials) → success=false, sent=0,
     *     failed=0. Callers can surface "push not configured" without crashing.
     *   - Partial failure → success=true, with the failedCount reflected.
     */
    suspend fun send(request: SendNotificationRequest): SendNotificationResponse {
        // 1) Nothing to do — keep this a 200, not an error.
        if (request.userIds.isEmpty()) {
            return SendNotificationResponse(success = true, sentCount = 0, failedCount = 0)
        }

        // 2) Firebase Admin SDK must be initialised. When credentials are
        //    absent (local dev / not-yet-configured prod) we degrade to a
        //    no-op instead of crashing the request path.
        val app = FirebaseAdminInitializer.app()
            ?: return SendNotificationResponse(success = false, sentCount = 0, failedCount = 0)

        // 3) Resolve every active token for the requested users (multi-device
        //    fan-out, deduped by token).
        val userIds = request.userIds.mapNotNull { it.toUuidOrNull() }
        if (userIds.isEmpty()) {
            return SendNotificationResponse(success = true, sentCount = 0, failedCount = 0)
        }
        val tokens = deviceTokenRepository.activeTokensForUsers(userIds)
        if (tokens.isEmpty()) {
            return SendNotificationResponse(success = true, sentCount = 0, failedCount = 0)
        }

        val messaging = FirebaseMessaging.getInstance(app)
        val dataPayload = buildDataPayload(request)
        val notification = Notification.builder()
            .setTitle(request.title)
            .setBody(request.body)
            .build()

        var sent = 0
        var failed = 0

        // 4) Chunk at the FCM multicast ceiling (500 tokens / call) and
        //    dispatch each chunk via sendEachForMulticast. Per-token results
        //    drive both the tally and the invalid-token cleanup.
        tokens.map { it.token }.chunked(MULTICAST_CHUNK_SIZE).forEach { chunk ->
            val multicast = MulticastMessage.builder()
                .addAllTokens(chunk)
                .setNotification(notification)
                .putAllData(dataPayload)
                .build()

            val batchResponse = runCatching { messaging.sendEachForMulticast(multicast) }
                .getOrElse {
                    // Whole chunk failed (network / auth / quota). Count every
                    // token as failed but leave them active — a transient
                    // outage should not retire otherwise-valid tokens.
                    failed += chunk.size
                    println("NOTIFY_DISPATCH: multicast batch failed for ${chunk.size} tokens: ${it.message}")
                    return@forEach
                }

            batchResponse.responses.forEachIndexed { index, sendResponse ->
                if (sendResponse.isSuccessful) {
                    sent++
                } else {
                    failed++
                    val token = chunk[index]
                    handleFailure(token, sendResponse.exception)
                }
            }
        }

        return SendNotificationResponse(
            success = true,
            sentCount = sent,
            failedCount = failed
        )
    }

    // ------------------------------------------------------------------
    // Payload + failure reconciliation
    // ------------------------------------------------------------------

    /**
     * Build the FCM `data` payload (all string values). We always include
     * `title` and `body` so a foreground client can render the notification
     * itself (the `notification` block is only auto-rendered in the background).
     * `deepLink` (when present) is merged in so the client can route the tap.
     * The caller's arbitrary [SendNotificationRequest.data] map is then merged
     * on top — it typically carries `type`, `entityId`, `schoolId`, etc.
     */
    private fun buildDataPayload(request: SendNotificationRequest): Map<String, String> {
        val payload = linkedMapOf<String, String>(
            "title" to request.title,
            "body" to request.body,
        )
        request.deepLink?.takeIf { it.isNotBlank() }?.let { payload["deepLink"] = it }
        // Caller-provided extras win (merge last).
        request.data.forEach { (k, v) -> payload[k] = v }
        return payload
    }

    /**
     * Reconcile a per-token failure. FCM reports a token as gone via the
     * UNREGISTERED error code (and a couple of historical aliases). When we see
     * one of those, we mark the token inactive so future fan-outs skip it —
     * this keeps device_tokens from accumulating dead installs and stops us
     * burning FCM quota retrying them. Other errors (quota, internal, etc.) are
     * logged but leave the token active for the next attempt.
     */
    private suspend fun handleFailure(token: String, exception: Exception?) {
        val fcmException = exception as? FirebaseMessagingException
        val code = fcmException?.errorCode
        if (code?.name in INVALID_TOKEN_ERROR_CODES) {
            runCatching { deviceTokenRepository.deactivateToken(token) }
                .onFailure { println("NOTIFY_DISPATCH: failed to deactivate token: ${it.message}") }
        } else {
            println("NOTIFY_DISPATCH: send failed for token …${token.takeLast(6)}: code=$code msg=${fcmException?.message?.take(120)}")
        }
    }

    private fun String.toUuidOrNull(): UUID? =
        runCatching { UUID.fromString(this) }.getOrNull()

    companion object {
        /** FCM caps a multicast at 500 tokens; chunk to stay under. */
        private const val MULTICAST_CHUNK_SIZE = 500

        /**
         * Firebase Admin SDK error codes that signal the token is no longer
         * valid and must be retired. See
         * https://firebase.google.com/docs/cloud-messaging/send-message#admin-sdk-error-codes
         */
        private val INVALID_TOKEN_ERROR_CODES = setOf(
            "UNREGISTERED",
            "invalid-registration-token",
            "registration-token-not-registered",
        )
    }
}
