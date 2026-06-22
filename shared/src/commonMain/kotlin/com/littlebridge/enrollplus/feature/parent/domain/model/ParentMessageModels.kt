/*
 * File: ParentMessageModels.kt
 * Module: feature.parent.domain.model
 *
 * RA-51 — domain models for the PARENT messaging surface, mirroring the server
 * DTOs in server/.../feature/user/ParentMessagesRouting.kt. The parent talks to
 * the SAME message_threads / messages tables as the school side (two-party
 * conversation engine), so the field shapes match the admin MessageModels.
 *
 * Endpoints:
 *   GET  /api/v1/parent/messages/threads
 *   GET  /api/v1/parent/messages/threads/{id}/messages
 *   POST /api/v1/parent/messages/threads/{id}/read
 *   POST /api/v1/parent/messages
 *
 * Envelope: server wraps payloads in ApiResponse{ success, message, data }, but
 * the parent module flattens that into each response type (mirrors
 * ParentDashboardResponse), so these carry `success` + the typed `data`.
 */
package com.littlebridge.enrollplus.feature.parent.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ParentMessageThreadDto(
    val id: String,
    @SerialName("sender_name") val senderName: String,
    @SerialName("sender_role") val senderRole: String,
    @SerialName("last_message") val lastMessage: String,
    val time: String,
    @SerialName("unread_count") val unreadCount: Int = 0,
    @SerialName("sender_image_url") val senderImageUrl: String? = null,
    @SerialName("icon_name") val iconName: String? = null,
    @SerialName("is_read") val isRead: Boolean = true,
)

@Serializable
data class ParentMessageThreadsData(val threads: List<ParentMessageThreadDto> = emptyList())

@Serializable
data class ParentMessageThreadsResponse(
    val success: Boolean = false,
    val data: ParentMessageThreadsData = ParentMessageThreadsData(),
)

@Serializable
data class ParentMessageDto(
    val id: String,
    val body: String,
    @SerialName("is_mine") val isMine: Boolean = false,
    @SerialName("sender_id") val senderId: String? = null,
    @SerialName("created_at") val createdAt: String,
    val time: String,
)

@Serializable
data class ParentThreadMessagesData(
    @SerialName("thread_id") val threadId: String,
    @SerialName("sender_name") val senderName: String,
    val messages: List<ParentMessageDto> = emptyList(),
)

@Serializable
data class ParentThreadMessagesResponse(
    val success: Boolean = false,
    val data: ParentThreadMessagesData? = null,
)

@Serializable
data class ParentSendMessageRequest(
    @SerialName("thread_id") val threadId: String? = null,
    @SerialName("sender_name") val senderName: String? = null,
    @SerialName("sender_role") val senderRole: String? = null,
    @SerialName("sender_image_url") val senderImageUrl: String? = null,
    @SerialName("icon_name") val iconName: String? = null,
    @SerialName("recipient_user_id") val recipientUserId: String? = null,
    val body: String,
)

@Serializable
data class ParentSendMessageData(
    @SerialName("thread_id") val threadId: String,
    @SerialName("message_id") val messageId: String,
)

@Serializable
data class ParentSendMessageResponse(
    val success: Boolean = false,
    val message: String? = null,
    val data: ParentSendMessageData? = null,
)

/**
 * RA-S07 — a person the parent can START a conversation with (a teacher or admin
 * in the child's school). `id` is the recipient's app_users id, usable directly as
 * `recipient_user_id` on POST /api/v1/parent/messages.
 * Server: GET /api/v1/parent/messages/recipients.
 */
@Serializable
data class ParentRecipientDto(
    val id: String,
    val name: String,
    val role: String = "",
    val subtitle: String = "",
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("is_class_teacher") val isClassTeacher: Boolean = false,
)

@Serializable
data class ParentRecipientsData(val recipients: List<ParentRecipientDto> = emptyList())

@Serializable
data class ParentRecipientsResponse(
    val success: Boolean = false,
    val data: ParentRecipientsData? = null,
)
