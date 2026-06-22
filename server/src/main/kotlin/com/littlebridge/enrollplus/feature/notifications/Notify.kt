/*
 * File: Notify.kt
 * Module: feature.notifications
 *
 * The single write-path for the cross-user notification spine (RA-41/42/46/50).
 * Every trigger that should reach a user — student absent → parent, marks
 * published → parent, homework assigned → class parents, announcement posted →
 * parents+teachers, leave applied/decided, link-child decided, fee status
 * change — calls Notify.* so notifications are created consistently, with the
 * originating school_id carried for tenant isolation.
 *
 * MULTI-TENANCY: every row stores `schoolId`. Recipients are resolved by the
 * caller (which already holds a school-scoped context) and we never fan out
 * across schools — the caller passes only in-tenant recipient ids.
 */
package com.littlebridge.enrollplus.feature.notifications

import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.NotificationsTable
import org.jetbrains.exposed.sql.batchInsert
import java.time.Instant
import java.util.UUID

object Notify {

    /**
     * Create one notification per recipient. No-op when [userIds] is empty.
     * Safe to call inside or outside an existing transaction (opens its own).
     */
    suspend fun toUsers(
        userIds: Collection<UUID>,
        category: String,
        title: String,
        body: String = "",
        schoolId: UUID? = null,
        actorId: UUID? = null,
        deepLink: String? = null,
        refType: String? = null,
        refId: String? = null,
    ) {
        val recipients = userIds.distinct().filter { true }
        if (recipients.isEmpty()) return
        val now = Instant.now()
        dbQuery {
            NotificationsTable.batchInsert(recipients) { uid ->
                this[NotificationsTable.userId] = uid
                this[NotificationsTable.schoolId] = schoolId
                this[NotificationsTable.category] = category
                this[NotificationsTable.title] = title
                this[NotificationsTable.body] = body
                this[NotificationsTable.deepLink] = deepLink
                this[NotificationsTable.actorId] = actorId
                this[NotificationsTable.refType] = refType
                this[NotificationsTable.refId] = refId
                this[NotificationsTable.isRead] = false
                this[NotificationsTable.createdAt] = now
            }
        }
    }

    /** Convenience for a single recipient. */
    suspend fun toUser(
        userId: UUID,
        category: String,
        title: String,
        body: String = "",
        schoolId: UUID? = null,
        actorId: UUID? = null,
        deepLink: String? = null,
        refType: String? = null,
        refId: String? = null,
    ) = toUsers(listOf(userId), category, title, body, schoolId, actorId, deepLink, refType, refId)
}
