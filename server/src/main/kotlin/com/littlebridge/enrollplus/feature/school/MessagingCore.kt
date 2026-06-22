/*
 * File: MessagingCore.kt
 * Module: feature.school
 *
 * RA-51 — the SHARED two-party conversation engine used by both the admin
 * (MessagesRouting) and parent (ParentMessagesRouting) surfaces, and by the
 * teacher "message class parents" path.
 *
 * The model (see MessageThreadsTable doc):
 *   - A conversation between user A and user B is TWO thread rows (one owned by
 *     A, one owned by B) that share a `conversation_id`.
 *   - Each owner's row tracks THAT owner's view: unread_count / is_read, plus the
 *     OTHER party's display name+role+avatar (peer_user_id).
 *   - Messages are keyed by `conversation_id`, so both participants read the same
 *     history regardless of which row they own.
 *   - System/self threads (alerts, receipts) keep peer_user_id = null and
 *     conversation_id = their own id (single-owner inbox — unchanged behaviour).
 *
 * MULTI-TENANCY: every row carries the real school_id of the participants — never
 * a user id (fixes the RA-51 school_id-corruption bug). A cross-role conversation
 * is only created when both users belong to the same school.
 */
package com.littlebridge.enrollplus.feature.school

import com.littlebridge.enrollplus.db.AppUsersTable
import com.littlebridge.enrollplus.db.MessageThreadsTable
import com.littlebridge.enrollplus.db.MessagesTable
import com.littlebridge.enrollplus.feature.notifications.Notify
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

/** A user's directory card, resolved for addressing them in a conversation. */
internal data class MessagingUser(
    val id: UUID,
    val schoolId: UUID?,
    val fullName: String,
    val role: String,
    val profilePicUrl: String?,
)

/** Resolve a user's display identity (school-scoped lookup is the caller's job). */
internal fun resolveMessagingUser(userId: UUID): MessagingUser? =
    AppUsersTable.selectAll().where { AppUsersTable.id eq userId }.singleOrNull()?.let { row ->
        MessagingUser(
            id = userId,
            schoolId = row[AppUsersTable.schoolId],
            fullName = row[AppUsersTable.fullName],
            role = row[AppUsersTable.role],
            profilePicUrl = row[AppUsersTable.profilePicUrl],
        )
    }

/**
 * The conversation_id for an existing thread row (falls back to the row id for
 * legacy rows written before RA-51 added the column).
 */
internal fun ResultRow.conversationKey(): UUID =
    this[MessageThreadsTable.conversationId] ?: this[MessageThreadsTable.id].value

/**
 * Find the conversation_id of an existing 1:1 conversation between [a] and [b]
 * within [schoolId], if one already exists (so we append instead of forking a
 * new pair every time).
 */
internal fun existingConversationId(schoolId: UUID, a: UUID, b: UUID): UUID? =
    MessageThreadsTable.selectAll().where {
        (MessageThreadsTable.schoolId eq schoolId) and
            (MessageThreadsTable.ownerUserId eq a) and
            (MessageThreadsTable.peerUserId eq b)
    }.firstOrNull()?.conversationKey()

/**
 * Result of [sendInConversation]: the sender's own thread row id + the new
 * message id + the conversation id.
 */
internal data class SendResult(
    val senderThreadId: UUID,
    val messageId: UUID,
    val conversationId: UUID,
    // RA-S08: the resolved peer (recipient) of this send, regardless of whether the caller
    // passed `recipientId` explicitly or appended to an existing thread (peer read off the row).
    // null only for self/system threads. Lets every send-path notify the recipient uniformly.
    val recipientId: UUID? = null,
)

/**
 * The single source of truth for "send a message". Handles three cases:
 *
 *   1. Appending to an existing thread the sender owns (threadId given).
 *   2. Starting / continuing a real two-party conversation with [recipientId]
 *      (creates BOTH participants' rows on first contact, then appends).
 *   3. A self / system thread (recipientId == sender or null) — single-owner row,
 *      unchanged legacy behaviour.
 *
 * Ownership of an existing [threadId] MUST be verified by the caller first.
 * Returns null only when an existing thread row cannot be found.
 */
internal fun sendInConversation(
    senderId: UUID,
    senderSchoolId: UUID,
    body: String,
    threadId: UUID?,
    recipientId: UUID?,
    // Display fields for a freshly-created thread, as the PEER will see the sender.
    senderName: String,
    senderRole: String,
    senderImageUrl: String?,
    iconName: String?,
    now: Instant = Instant.now(),
): SendResult? {
    val preview = body.take(200)

    // ---- Case 1: append to an existing thread the sender owns ----
    if (threadId != null) {
        val row = MessageThreadsTable.selectAll()
            .where { (MessageThreadsTable.id eq threadId) and (MessageThreadsTable.ownerUserId eq senderId) }
            .singleOrNull() ?: return null
        val convId = row.conversationKey()
        val peer = row[MessageThreadsTable.peerUserId]

        // Refresh the sender's own row (read for them).
        MessageThreadsTable.update({ MessageThreadsTable.id eq threadId }) {
            it[lastMessage] = preview
            it[lastMessageAt] = now
            it[updatedAt] = now
            it[isRead] = true
            it[conversationId] = convId
        }
        // Bump the peer's mirror row (unread for them).
        if (peer != null) {
            bumpPeerRow(convId, peer, preview, now)
        }
        val msgId = insertMessage(threadId, convId, senderId, body, now)
        // RA-S08: peer read off the existing thread row (null for a self/system thread).
        return SendResult(threadId, msgId, convId, recipientId = peer)
    }

    // ---- Case 3: self / system thread ----
    if (recipientId == null || recipientId == senderId) {
        val convId = UUID.randomUUID()
        val newThread = UUID.randomUUID()
        MessageThreadsTable.insert {
            it[id] = newThread
            it[MessageThreadsTable.schoolId] = senderSchoolId
            it[conversationId] = convId
            it[ownerUserId] = senderId
            it[peerUserId] = null
            it[MessageThreadsTable.senderName] = senderName
            it[MessageThreadsTable.senderRole] = senderRole
            it[MessageThreadsTable.senderImageUrl] = senderImageUrl
            it[MessageThreadsTable.iconName] = iconName
            it[lastMessage] = preview
            it[lastMessageAt] = now
            it[unreadCount] = 0
            it[isRead] = true
            it[createdAt] = now
            it[updatedAt] = now
        }
        val msgId = insertMessage(newThread, convId, senderId, body, now)
        // RA-S08: self/system thread has no peer to notify.
        return SendResult(newThread, msgId, convId, recipientId = null)
    }

    // ---- Case 2: real two-party conversation ----
    val sender = resolveMessagingUser(senderId)
    val recipient = resolveMessagingUser(recipientId)
        ?: return null
    // Reuse an existing conversation between these two within the school.
    val convId = existingConversationId(senderSchoolId, senderId, recipientId) ?: UUID.randomUUID()

    // Sender's row (their view of the recipient).
    val senderThread = upsertParticipantRow(
        conversationId = convId,
        ownerId = senderId,
        peerId = recipientId,
        schoolId = senderSchoolId,
        peerName = recipient.fullName,
        peerRole = recipient.role.replaceFirstChar { it.uppercase() },
        peerImage = recipient.profilePicUrl,
        iconName = null,
        preview = preview,
        now = now,
        ownerIsSender = true,
    )
    // Recipient's row (their view of the sender) — unread for them.
    upsertParticipantRow(
        conversationId = convId,
        ownerId = recipientId,
        peerId = senderId,
        schoolId = senderSchoolId,
        peerName = senderName,
        peerRole = senderRole,
        peerImage = senderImageUrl ?: sender?.profilePicUrl,
        iconName = iconName,
        preview = preview,
        now = now,
        ownerIsSender = false,
    )

    val msgId = insertMessage(senderThread, convId, senderId, body, now)
    return SendResult(senderThread, msgId, convId, recipientId = recipientId)
}

/** Insert one message row, keyed by both the owning thread and the conversation. */
private fun insertMessage(threadId: UUID, conversationId: UUID, senderId: UUID, body: String, now: Instant): UUID {
    val msgId = UUID.randomUUID()
    MessagesTable.insert {
        it[id] = msgId
        it[MessagesTable.threadId] = threadId
        it[MessagesTable.conversationId] = conversationId
        it[MessagesTable.senderId] = senderId
        it[MessagesTable.body] = body
        it[MessagesTable.createdAt] = now
    }
    return msgId
}

/** Bump a peer's existing row: new preview + 1 unread. */
private fun bumpPeerRow(conversationId: UUID, peerOwner: UUID, preview: String, now: Instant) {
    MessageThreadsTable.update({
        (MessageThreadsTable.conversationId eq conversationId) and
            (MessageThreadsTable.ownerUserId eq peerOwner)
    }) {
        it[lastMessage] = preview
        it[lastMessageAt] = now
        it[updatedAt] = now
        it[isRead] = false
        it[unreadCount] = MessageThreadsTable.unreadCount + 1
    }
}

/**
 * Create or refresh ONE participant's thread row for a conversation. When the
 * row already exists we update its preview/timestamp and (for the recipient)
 * increment unread; otherwise we insert a fresh row.
 */
private fun upsertParticipantRow(
    conversationId: UUID,
    ownerId: UUID,
    peerId: UUID,
    schoolId: UUID,
    peerName: String,
    peerRole: String,
    peerImage: String?,
    iconName: String?,
    preview: String,
    now: Instant,
    ownerIsSender: Boolean,
): UUID {
    val existing = MessageThreadsTable.selectAll().where {
        (MessageThreadsTable.conversationId eq conversationId) and
            (MessageThreadsTable.ownerUserId eq ownerId)
    }.firstOrNull()

    if (existing != null) {
        val rowId = existing[MessageThreadsTable.id].value
        MessageThreadsTable.update({ MessageThreadsTable.id eq rowId }) {
            it[lastMessage] = preview
            it[lastMessageAt] = now
            it[updatedAt] = now
            if (ownerIsSender) {
                it[isRead] = true
            } else {
                it[isRead] = false
                it[unreadCount] = MessageThreadsTable.unreadCount + 1
            }
        }
        return rowId
    }

    val newId = UUID.randomUUID()
    MessageThreadsTable.insert {
        it[id] = newId
        it[MessageThreadsTable.schoolId] = schoolId
        it[MessageThreadsTable.conversationId] = conversationId
        it[ownerUserId] = ownerId
        it[peerUserId] = peerId
        it[senderName] = peerName
        it[senderRole] = peerRole
        it[senderImageUrl] = peerImage
        it[MessageThreadsTable.iconName] = iconName
        it[lastMessage] = preview
        it[lastMessageAt] = now
        it[unreadCount] = if (ownerIsSender) 0 else 1
        it[isRead] = ownerIsSender
        it[createdAt] = now
        it[updatedAt] = now
    }
    return newId
}

/**
 * Load all messages in the conversation that owns [threadId], scoped so only a
 * legitimate owner of one of the conversation's rows can read them. Returns null
 * if [ownerId] does not own a row in that conversation.
 */
internal fun conversationMessagesFor(threadId: UUID, ownerId: UUID): Pair<UUID, List<ResultRow>>? {
    val ownRow = MessageThreadsTable.selectAll().where {
        (MessageThreadsTable.id eq threadId) and (MessageThreadsTable.ownerUserId eq ownerId)
    }.singleOrNull() ?: return null
    val convId = ownRow.conversationKey()
    // Read by conversation_id when present; legacy rows fall back to thread_id.
    val rows = MessagesTable.selectAll().where {
        (MessagesTable.conversationId eq convId) or (MessagesTable.threadId eq threadId)
    }.orderBy(MessagesTable.createdAt, SortOrder.ASC).toList()
    return convId to rows
}

/**
 * RA-S08 — the SHARED "notify the recipient of a 1:1 message" helper, used by both the admin
 * (MessagesRouting) and teacher (TeacherMessagesRouting) send paths so they can't drift apart.
 *
 * Best-effort: a notification failure must never fail the send. No-ops for a self/system thread
 * (recipientId == null) or a message to oneself. The deep link targets the recipient's Messages
 * surface; the body is the (truncated) message text so the push/notification is actually useful.
 */
internal suspend fun notifyMessageRecipient(
    recipientId: UUID?,
    schoolId: UUID,
    actorId: UUID,
    actorName: String,
    threadId: UUID,
    body: String,
) {
    if (recipientId == null || recipientId == actorId) return
    runCatching {
        Notify.toUser(
            userId = recipientId,
            category = "message",
            title = "Message from ${actorName.ifBlank { "your child's school" }}",
            body = body.take(120),
            schoolId = schoolId,
            actorId = actorId,
            deepLink = "parent/messages",
            refType = "message",
            refId = threadId.toString(),
        )
    }
}
