/*
 * File: MessageModels.kt
 * Module: feature.admin.domain.model
 *
 * Domain models that mirror the server DTOs from
 *   server/.../feature/school/MessagesRouting.kt
 *
 * Endpoints:
 *   GET  /api/v1/school/messages/threads
 *   POST /api/v1/school/messages/threads/{id}/read
 *   POST /api/v1/school/messages
 *
 * Notes:
 * - `time` is server-formatted ("10:45 AM" / "Yesterday" / "MMM dd"). The UI
 *   does no date math.
 * - When `threadId` is null on [SendMessageRequest], the server creates a
 *   fresh thread on the fly with the calling user as the recipient.
 */
package com.littlebridge.enrollplus.feature.admin.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One thread in the admin's inbox. Field names intentionally mirror the
 * existing `MessageThread` data class the screen was built against, so the
 * UI layer changes minimally when we swap from mock to network-backed state.
 */
@Serializable
data class MessageThread(
    val id: String,
    @SerialName("sender_name") val senderName: String,
    @SerialName("sender_role") val senderRole: String,
    @SerialName("last_message") val lastMessage: String,
    val time: String,
    @SerialName("unread_count") val unreadCount: Int = 0,
    @SerialName("sender_image_url") val senderImageUrl: String? = null,
    @SerialName("icon_name") val iconName: String? = null,
    @SerialName("is_read") val isRead: Boolean = true
)

/** GET /api/v1/school/messages/threads response payload. */
@Serializable
data class MessageThreadsResponse(
    val threads: List<MessageThread>
)

/**
 * POST /api/v1/school/messages request body.
 *
 * `threadId` null = create a new thread; non-null = append to existing thread.
 * `body` is required (server rejects blank).
 */
@Serializable
data class SendMessageRequest(
    @SerialName("thread_id") val threadId: String? = null,
    @SerialName("sender_name") val senderName: String? = null,
    @SerialName("sender_role") val senderRole: String? = null,
    @SerialName("sender_image_url") val senderImageUrl: String? = null,
    @SerialName("icon_name") val iconName: String? = null,
    @SerialName("recipient_user_id") val recipientUserId: String? = null,
    val body: String
)

/** POST /api/v1/school/messages response payload. */
@Serializable
data class SendMessageResponse(
    @SerialName("thread_id") val threadId: String,
    @SerialName("message_id") val messageId: String
)

/**
 * A single message inside a thread.
 * Mirrors the server `MessageDto` in MessagesRouting.kt.
 * `isMine` lets the UI right-align the admin's own messages.
 * `time` is server-formatted ("10:45 AM" / "Yesterday" / "MMM dd").
 */
@Serializable
data class Message(
    val id: String,
    val body: String,
    @SerialName("is_mine") val isMine: Boolean = false,
    @SerialName("sender_id") val senderId: String? = null,
    @SerialName("created_at") val createdAt: String,
    val time: String
)

/**
 * GET /api/v1/school/messages/threads/{id}/messages response payload.
 * Mirrors the server `ThreadMessagesResponse`.
 */
@Serializable
data class ThreadMessagesResponse(
    @SerialName("thread_id") val threadId: String,
    @SerialName("sender_name") val senderName: String,
    val messages: List<Message>
)
