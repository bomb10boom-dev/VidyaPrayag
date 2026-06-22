/*
 * File: MessagesRouting.kt
 * Module: feature.school
 *
 * Endpoints (all JWT):
 *   GET  /api/v1/school/messages/threads
 *   GET  /api/v1/school/messages/threads/{id}/messages
 *   POST /api/v1/school/messages/threads/{id}/read
 *   POST /api/v1/school/messages
 *
 * Spec ref: school_api_spec.artifact.md §Module: Messages
 *
 * Threads are scoped to the authenticated user via `owner_user_id`, so the
 * admin only ever sees their own inbox.  `time` is formatted server-side
 * ("10:45 AM" for today, "Yesterday" / "MMM dd" otherwise) so the UI does
 * no date math.
 *
 * `POST /api/v1/school/messages` is the "send" path.  If `thread_id` is
 * null we create a fresh thread on the fly (recipient defaults to the
 * authenticated user — useful for system-generated alerts) and return both
 * the new thread id and message id.  When `thread_id` is supplied we append
 * a message and refresh the thread's preview / timestamp.
 */
package com.littlebridge.enrollplus.feature.school

import com.littlebridge.enrollplus.core.created
import com.littlebridge.enrollplus.core.fail
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.okMessage
import com.littlebridge.enrollplus.core.requireSchoolContext
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.MessageThreadsTable
import com.littlebridge.enrollplus.db.MessagesTable
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

// ---------------- DTOs ----------------

@Serializable
data class MessageThreadDto(
    val id: String,
    @SerialName("sender_name") val senderName: String,
    @SerialName("sender_role") val senderRole: String,
    @SerialName("last_message") val lastMessage: String,
    val time: String,
    @SerialName("unread_count") val unreadCount: Int,
    @SerialName("sender_image_url") val senderImageUrl: String? = null,
    @SerialName("icon_name") val iconName: String? = null,
    @SerialName("is_read") val isRead: Boolean
)

@Serializable
data class MessageThreadsResponse(val threads: List<MessageThreadDto>)

@Serializable
data class SendMessageDto(
    @SerialName("thread_id") val threadId: String? = null,
    @SerialName("sender_name") val senderName: String? = null,
    @SerialName("sender_role") val senderRole: String? = null,
    @SerialName("sender_image_url") val senderImageUrl: String? = null,
    @SerialName("icon_name") val iconName: String? = null,
    @SerialName("recipient_user_id") val recipientUserId: String? = null,
    val body: String
)

@Serializable
data class SendMessageResponse(
    @SerialName("thread_id") val threadId: String,
    @SerialName("message_id") val messageId: String
)

@Serializable
data class MessageDto(
    val id: String,
    val body: String,
    @SerialName("is_mine") val isMine: Boolean,
    @SerialName("sender_id") val senderId: String? = null,
    @SerialName("created_at") val createdAt: String,
    val time: String
)

@Serializable
data class ThreadMessagesResponse(
    @SerialName("thread_id") val threadId: String,
    @SerialName("sender_name") val senderName: String,
    val messages: List<MessageDto>
)

// ---------------- helpers ----------------

private fun formatThreadTime(ts: Instant): String {
    val zid = ZoneId.systemDefault()
    val zdt = ts.atZone(zid)
    val today = LocalDate.now(zid)
    return when (zdt.toLocalDate()) {
        today -> zdt.format(DateTimeFormatter.ofPattern("h:mm a"))
        today.minusDays(1) -> "Yesterday"
        else -> zdt.format(DateTimeFormatter.ofPattern("MMM dd"))
    }
}

private fun org.jetbrains.exposed.sql.ResultRow.toThreadDto() = MessageThreadDto(
    id = this[MessageThreadsTable.id].value.toString(),
    senderName = this[MessageThreadsTable.senderName],
    senderRole = this[MessageThreadsTable.senderRole],
    lastMessage = this[MessageThreadsTable.lastMessage],
    time = formatThreadTime(this[MessageThreadsTable.lastMessageAt]),
    unreadCount = this[MessageThreadsTable.unreadCount],
    senderImageUrl = this[MessageThreadsTable.senderImageUrl],
    iconName = this[MessageThreadsTable.iconName],
    isRead = this[MessageThreadsTable.isRead]
)

fun Route.messagesRouting() {
    authenticate("jwt") {
        route("/api/v1/school/messages") {

            // -------- LIST THREADS --------
            get("/threads") {
                val ctx = call.requireSchoolContext() ?: return@get
                val uid = ctx.userId

                val payload = dbQuery {
                    val rows = MessageThreadsTable.selectAll()
                        .where {
                            (MessageThreadsTable.ownerUserId eq uid) and
                                (MessageThreadsTable.schoolId eq ctx.schoolId)
                        }
                        .orderBy(MessageThreadsTable.lastMessageAt, SortOrder.DESC)
                        .map { it.toThreadDto() }
                    MessageThreadsResponse(rows)
                }
                call.ok(payload, message = "Threads fetched successfully")
            }

            // -------- CONVERSATION (messages inside a thread) --------
            get("/threads/{id}/messages") {
                val ctx = call.requireSchoolContext() ?: return@get
                val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: run { call.fail("Invalid id"); return@get }

                val payload = dbQuery {
                    // The thread must belong to the caller (owner) AND school.
                    val thread = MessageThreadsTable.selectAll()
                        .where {
                            (MessageThreadsTable.id eq id) and
                                (MessageThreadsTable.ownerUserId eq ctx.userId) and
                                (MessageThreadsTable.schoolId eq ctx.schoolId)
                        }
                        .singleOrNull()
                        ?: return@dbQuery null

                    // RA-51: read by conversation_id so the admin sees both sides.
                    val (_, rows) = conversationMessagesFor(id, ctx.userId) ?: return@dbQuery null
                    val msgs = rows.map { row ->
                        val sid = row[MessagesTable.senderId]
                        val createdInstant = row[MessagesTable.createdAt]
                        MessageDto(
                            id = row[MessagesTable.id].value.toString(),
                            body = row[MessagesTable.body],
                            isMine = sid == ctx.userId,
                            senderId = sid?.toString(),
                            createdAt = createdInstant.toString(),
                            time = formatThreadTime(createdInstant)
                        )
                    }
                    ThreadMessagesResponse(
                        threadId = id.toString(),
                        senderName = thread[MessageThreadsTable.senderName],
                        messages = msgs
                    )
                }
                if (payload == null) call.fail("Thread not found", HttpStatusCode.NotFound)
                else {
                    // Opening a conversation clears its unread badge.
                    dbQuery {
                        MessageThreadsTable.update({
                            (MessageThreadsTable.id eq id) and (MessageThreadsTable.ownerUserId eq ctx.userId)
                        }) {
                            it[unreadCount] = 0
                            it[isRead] = true
                            it[updatedAt] = Instant.now()
                        }
                    }
                    call.ok(payload, message = "Conversation fetched")
                }
            }

            // -------- MARK READ --------
            post("/threads/{id}/read") {
                val ctx = call.requireSchoolContext() ?: return@post
                val uid = ctx.userId
                val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: run { call.fail("Invalid id"); return@post }

                val n = dbQuery {
                    MessageThreadsTable.update({
                        (MessageThreadsTable.id eq id) and (MessageThreadsTable.ownerUserId eq uid)
                    }) {
                        it[unreadCount] = 0
                        it[isRead] = true
                        it[updatedAt] = Instant.now()
                    }
                }
                if (n == 0) call.fail("Thread not found", HttpStatusCode.NotFound)
                else call.okMessage("Thread marked as read")
            }

            // -------- SEND (creates thread on the fly when thread_id is null) --------
            post {
                val ctx = call.requireSchoolContext() ?: return@post
                val uid = ctx.userId
                val req = call.receive<SendMessageDto>()
                if (req.body.isBlank()) {
                    call.fail("body is required"); return@post
                }

                val schoolId = ctx.schoolId
                val now = Instant.now()

                // For an existing thread, verify ownership+school BEFORE writing.
                if (req.threadId != null) {
                    val parsed = runCatching { UUID.fromString(req.threadId) }.getOrNull()
                        ?: run { call.fail("Invalid thread_id"); return@post }
                    val owns = dbQuery {
                        MessageThreadsTable.selectAll().where {
                            (MessageThreadsTable.id eq parsed) and
                                (MessageThreadsTable.ownerUserId eq uid) and
                                (MessageThreadsTable.schoolId eq schoolId)
                        }.count() > 0L
                    }
                    if (!owns) {
                        call.fail("Thread not found", HttpStatusCode.NotFound); return@post
                    }
                }

                // RA-51: one shared engine handles append / two-party / self.
                val recipientId = req.recipientUserId
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                // Resolve the admin's display name once so we can reuse it for the notification.
                val actorName = req.senderName ?: dbQuery { resolveMessagingUser(uid)?.fullName } ?: "Admin Desk"
                val result = dbQuery {
                    sendInConversation(
                        senderId = uid,
                        senderSchoolId = schoolId,
                        body = req.body,
                        threadId = req.threadId?.let { UUID.fromString(it) },
                        recipientId = recipientId,
                        senderName = actorName,
                        senderRole = req.senderRole ?: "Admin",
                        senderImageUrl = req.senderImageUrl,
                        iconName = req.iconName,
                        now = now,
                    )
                }
                if (result == null) {
                    call.fail("Thread not found", HttpStatusCode.NotFound)
                } else {
                    // RA-S08: notify the recipient (parity with the teacher send path). Uses the
                    // peer resolved by the engine, so it works for both new-thread and append sends.
                    notifyMessageRecipient(
                        recipientId = result.recipientId,
                        schoolId = schoolId,
                        actorId = uid,
                        actorName = actorName,
                        threadId = result.senderThreadId,
                        body = req.body,
                    )
                    call.created(
                        SendMessageResponse(result.senderThreadId.toString(), result.messageId.toString()),
                        message = "Message sent"
                    )
                }
            }
        }
    }
}
