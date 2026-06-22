/*
 * File: SendNotificationDtos.kt
 * Module: feature.notification.dto
 *
 * Request/response DTOs for the admin send-notification endpoint
 * (POST /api/admin/notifications/send). This endpoint is the manual-testing
 * and future-integration entry point into the NotificationService — it lets a
 * school admin (or an operator in Postman) push a notification to a set of
 * users without writing code.
 *
 * The same payload shape is what future modules (announcement broadcast,
 * attendance alert, fee reminder, …) will hand to NotificationService
 * internally, so this DTO doubles as the canonical "send push" contract.
 */
package com.littlebridge.enrollplus.feature.notification.dto

import kotlinx.serialization.Serializable

/**
 * Body of `POST /api/admin/notifications/send`.
 *
 *  - `title` / `body`      → rendered in the system notification tray.
 *  - `userIds`             → recipient app_users.id list. Empty = no recipients
 *                            (the handler still returns 200 with sent=0; it is
 *                            NOT an error so callers can fan out conditionally).
 *  - `deepLink`            → optional in-app route, e.g. "/calendar/event/123".
 *                            Delivered as a data payload key so the client
 *                            FirebaseMessagingService can route the tap.
 *  - `data`                → arbitrary extra key/value pairs merged into the FCM
 *                            data payload. Use for `type`, `entityId`,
 *                            `schoolId`, etc. — see the payload format doc.
 */
@Serializable
data class SendNotificationRequest(
    val title: String,
    val body: String,
    val userIds: List<String> = emptyList(),
    val deepLink: String? = null,
    val data: Map<String, String> = emptyMap()
)

/**
 * Body of `POST /api/admin/notifications/send` response. Reports per-batch
 * delivery tallies so the caller can surface partial-failure to the operator.
 * `success` is true when the dispatch completed (even with some failures); it
 * is false only when the NotificationService could not run at all (e.g.
 * Firebase Admin SDK is not initialised because credentials are missing).
 */
@Serializable
data class SendNotificationResponse(
    val success: Boolean,
    val sentCount: Int,
    val failedCount: Int
)
