/*
 * File: TeacherMessagesRouting.kt
 * Module: feature.teacher
 *
 * RA-51 — the TEACHER leg of parent ↔ teacher messaging. Teachers are not in
 * SCHOOL_ROLES, so they cannot use /api/v1/school/messages; this mirror exposes
 * the SAME two-party conversation engine (MessagingCore) on a teacher-gated path.
 *
 *   GET   /api/v1/teacher/messages/threads
 *   GET   /api/v1/teacher/messages/threads/{id}/messages
 *   POST  /api/v1/teacher/messages/threads/{id}/read
 *   POST  /api/v1/teacher/messages                 { thread_id?|recipient_user_id?, body }
 *   POST  /api/v1/teacher/messages/class           { class_name, section?, body }
 *
 * The /class endpoint wires the formerly-dead "Message class parents" button
 * (TeacherClassesScreenV2): it fans out a 1:1 conversation to EACH parent of the
 * named class the teacher owns, so every parent gets it in their own inbox.
 *
 * MULTI-TENANCY: every row carries ctx.schoolId; a teacher can only broadcast to
 * a (class, section) they are assigned to (teacher_subject_assignments).
 */
package com.littlebridge.enrollplus.feature.teacher

import com.littlebridge.enrollplus.core.created
import com.littlebridge.enrollplus.core.fail
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.okMessage
import com.littlebridge.enrollplus.core.requireTeacherContext
import com.littlebridge.enrollplus.core.teacherAssignmentsFor
import com.littlebridge.enrollplus.db.ChildrenTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.MessageThreadsTable
import com.littlebridge.enrollplus.db.MessagesTable
import com.littlebridge.enrollplus.db.StudentsTable
import com.littlebridge.enrollplus.feature.notifications.Notify
import com.littlebridge.enrollplus.feature.school.conversationMessagesFor
import com.littlebridge.enrollplus.feature.school.notifyMessageRecipient
import com.littlebridge.enrollplus.feature.school.resolveMessagingUser
import com.littlebridge.enrollplus.feature.school.sendInConversation
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

// ---------------- DTOs (mirror the parent/admin message shapes) ----------------

@Serializable
data class TeacherMessageThreadDto(
    val id: String,
    @SerialName("sender_name") val senderName: String,
    @SerialName("sender_role") val senderRole: String,
    @SerialName("last_message") val lastMessage: String,
    val time: String,
    @SerialName("unread_count") val unreadCount: Int,
    @SerialName("sender_image_url") val senderImageUrl: String? = null,
    @SerialName("icon_name") val iconName: String? = null,
    @SerialName("is_read") val isRead: Boolean,
)

@Serializable
data class TeacherMessageThreadsResponse(val threads: List<TeacherMessageThreadDto>)

@Serializable
data class TeacherMessageDto(
    val id: String,
    val body: String,
    @SerialName("is_mine") val isMine: Boolean,
    @SerialName("sender_id") val senderId: String? = null,
    @SerialName("created_at") val createdAt: String,
    val time: String,
)

@Serializable
data class TeacherThreadMessagesResponse(
    @SerialName("thread_id") val threadId: String,
    @SerialName("sender_name") val senderName: String,
    val messages: List<TeacherMessageDto>,
)

@Serializable
data class TeacherSendMessageDto(
    @SerialName("thread_id") val threadId: String? = null,
    @SerialName("recipient_user_id") val recipientUserId: String? = null,
    val body: String,
)

@Serializable
data class TeacherSendMessageResponse(
    @SerialName("thread_id") val threadId: String,
    @SerialName("message_id") val messageId: String,
)

@Serializable
data class TeacherClassBroadcastDto(
    @SerialName("class_name") val className: String,
    val section: String? = null,
    val body: String,
)

@Serializable
data class TeacherClassBroadcastResponse(
    @SerialName("recipients") val recipients: Int,
)

// ---------------- helpers ----------------

private fun fmtTeacherTime(ts: Instant): String {
    val zid = ZoneId.systemDefault()
    val zdt = ts.atZone(zid)
    val today = LocalDate.now(zid)
    return when (zdt.toLocalDate()) {
        today -> zdt.format(DateTimeFormatter.ofPattern("h:mm a"))
        today.minusDays(1) -> "Yesterday"
        else -> zdt.format(DateTimeFormatter.ofPattern("MMM dd"))
    }
}

private fun org.jetbrains.exposed.sql.ResultRow.toTeacherThreadDto() = TeacherMessageThreadDto(
    id = this[MessageThreadsTable.id].value.toString(),
    senderName = this[MessageThreadsTable.senderName],
    senderRole = this[MessageThreadsTable.senderRole],
    lastMessage = this[MessageThreadsTable.lastMessage],
    time = fmtTeacherTime(this[MessageThreadsTable.lastMessageAt]),
    unreadCount = this[MessageThreadsTable.unreadCount],
    senderImageUrl = this[MessageThreadsTable.senderImageUrl],
    iconName = this[MessageThreadsTable.iconName],
    isRead = this[MessageThreadsTable.isRead],
)

/** Parent user-ids of the students in a (class, section) within a school. */
private fun parentsOfClass(schoolId: UUID, className: String, section: String): List<UUID> {
    val codes = StudentsTable.selectAll().where {
        (StudentsTable.schoolId eq schoolId) and
            (StudentsTable.className eq className) and
            (StudentsTable.section eq section) and
            (StudentsTable.isActive eq true)
    }.map { it[StudentsTable.studentCode] }.toSet()
    if (codes.isEmpty()) return emptyList()
    // Portable OR-reduce instead of `inList` (kept consistent with the
    // project's Exposed-version-safe approach — see AnnouncementRouting).
    return ChildrenTable.selectAll().where {
        (ChildrenTable.schoolId eq schoolId) and (ChildrenTable.isActive eq true)
    }.filter { it[ChildrenTable.studentCode] in codes }
        .map { it[ChildrenTable.parentId] }
        .distinct()
}

fun Route.teacherMessagesRouting() {
    authenticate("jwt") {
        route("/api/v1/teacher/messages") {

            // -------- LIST THREADS --------
            get("/threads") {
                val ctx = call.requireTeacherContext() ?: return@get
                val payload = dbQuery {
                    val rows = MessageThreadsTable.selectAll()
                        .where {
                            (MessageThreadsTable.ownerUserId eq ctx.userId) and
                                (MessageThreadsTable.schoolId eq ctx.schoolId)
                        }
                        .orderBy(MessageThreadsTable.lastMessageAt, SortOrder.DESC)
                        .map { it.toTeacherThreadDto() }
                    TeacherMessageThreadsResponse(rows)
                }
                call.ok(payload, message = "Threads fetched successfully")
            }

            // -------- CONVERSATION --------
            get("/threads/{id}/messages") {
                val ctx = call.requireTeacherContext() ?: return@get
                val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: run { call.fail("Invalid id"); return@get }

                val payload = dbQuery {
                    val thread = MessageThreadsTable.selectAll()
                        .where {
                            (MessageThreadsTable.id eq id) and
                                (MessageThreadsTable.ownerUserId eq ctx.userId) and
                                (MessageThreadsTable.schoolId eq ctx.schoolId)
                        }
                        .singleOrNull() ?: return@dbQuery null

                    val (_, rows) = conversationMessagesFor(id, ctx.userId) ?: return@dbQuery null
                    val msgs = rows.map { row ->
                        val sid = row[MessagesTable.senderId]
                        val created = row[MessagesTable.createdAt]
                        TeacherMessageDto(
                            id = row[MessagesTable.id].value.toString(),
                            body = row[MessagesTable.body],
                            isMine = sid == ctx.userId,
                            senderId = sid?.toString(),
                            createdAt = created.toString(),
                            time = fmtTeacherTime(created),
                        )
                    }
                    TeacherThreadMessagesResponse(
                        threadId = id.toString(),
                        senderName = thread[MessageThreadsTable.senderName],
                        messages = msgs,
                    )
                }
                if (payload == null) call.fail("Thread not found", HttpStatusCode.NotFound)
                else {
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
                val ctx = call.requireTeacherContext() ?: return@post
                val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: run { call.fail("Invalid id"); return@post }
                val n = dbQuery {
                    MessageThreadsTable.update({
                        (MessageThreadsTable.id eq id) and (MessageThreadsTable.ownerUserId eq ctx.userId)
                    }) {
                        it[unreadCount] = 0
                        it[isRead] = true
                        it[updatedAt] = Instant.now()
                    }
                }
                if (n == 0) call.fail("Thread not found", HttpStatusCode.NotFound)
                else call.okMessage("Thread marked as read")
            }

            // -------- SEND (reply / start 1:1) --------
            post {
                val ctx = call.requireTeacherContext() ?: return@post
                val req = call.receive<TeacherSendMessageDto>()
                if (req.body.isBlank()) { call.fail("body is required"); return@post }

                val recipientId = req.recipientUserId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                val now = Instant.now()
                val result = dbQuery {
                    sendInConversation(
                        senderId = ctx.userId,
                        senderSchoolId = ctx.schoolId,
                        body = req.body,
                        threadId = req.threadId?.let { UUID.fromString(it) },
                        recipientId = recipientId,
                        senderName = ctx.fullName.ifBlank { "Teacher" },
                        senderRole = "Teacher",
                        senderImageUrl = null,
                        iconName = null,
                        now = now,
                    )
                }
                if (result == null) {
                    call.fail("Thread not found", HttpStatusCode.NotFound)
                } else {
                    // RA-S08: shared helper; use the peer the engine resolved (works for
                    // new-thread and append sends alike) and include the message body.
                    notifyMessageRecipient(result.recipientId, ctx.schoolId, ctx.userId, ctx.fullName, result.senderThreadId, req.body)
                    call.created(
                        TeacherSendMessageResponse(result.senderThreadId.toString(), result.messageId.toString()),
                        message = "Message sent",
                    )
                }
            }

            // -------- BROADCAST TO CLASS PARENTS --------
            post("/class") {
                val ctx = call.requireTeacherContext() ?: return@post
                val req = call.receive<TeacherClassBroadcastDto>()
                if (req.body.isBlank()) { call.fail("body is required"); return@post }
                if (req.className.isBlank()) { call.fail("class_name is required"); return@post }

                // The teacher must own the (class, section) — admins may broadcast to any.
                val owned = teacherAssignmentsFor(ctx)
                val privileged = ctx.role == "school_admin" || ctx.role == "admin"
                val section = req.section
                    ?: owned.firstOrNull { it.className == req.className }?.section
                    ?: "A"
                val ownsClass = privileged || owned.any { it.className == req.className && it.section == section }
                if (!ownsClass) {
                    call.fail("You are not assigned to this class", HttpStatusCode.Forbidden)
                    return@post
                }

                val now = Instant.now()
                val parents = dbQuery { parentsOfClass(ctx.schoolId, req.className, section) }
                if (parents.isEmpty()) {
                    call.fail("No parents found for ${req.className}-$section", HttpStatusCode.NotFound)
                    return@post
                }

                dbQuery {
                    parents.forEach { parentId ->
                        sendInConversation(
                            senderId = ctx.userId,
                            senderSchoolId = ctx.schoolId,
                            body = req.body,
                            threadId = null,
                            recipientId = parentId,
                            senderName = ctx.fullName.ifBlank { "Teacher" },
                            senderRole = "Teacher (${req.className}-$section)",
                            senderImageUrl = null,
                            iconName = null,
                            now = now,
                        )
                    }
                }
                // One notification fan-out to every parent.
                Notify.toUsers(
                    userIds = parents,
                    category = "message",
                    title = "Message from ${ctx.fullName.ifBlank { "your child's teacher" }}",
                    body = req.body.take(120),
                    schoolId = ctx.schoolId,
                    actorId = ctx.userId,
                    deepLink = "parent/messages",
                    refType = "message",
                )
                call.created(TeacherClassBroadcastResponse(parents.size), message = "Message sent to ${parents.size} parents")
            }
        }
    }
}

// RA-S08: the per-recipient message notification now lives in the shared MessagingCore
// (notifyMessageRecipient) so the admin and teacher send paths can't drift. The teacher
// broadcast-to-class path still uses Notify.toUsers directly above.
