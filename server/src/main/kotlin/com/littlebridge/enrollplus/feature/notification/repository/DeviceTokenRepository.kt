/*
 * File: DeviceTokenRepository.kt
 * Module: feature.notification.repository
 *
 * Persistence layer for the device_tokens table — the ONLY place that reads
 * or writes device token rows for the notification foundation. Every other
 * module (NotificationService, the registration route, future features) talks
 * to device tokens through this repository so the multi-device invariants
 * stay in one spot.
 *
 * MULTI-DEVICE INVARIANT (mandatory per the notification foundation spec):
 *   A user may own several devices (phone + tablet + another phone). We
 *   intentionally do NOT overwrite previous tokens when registering a new
 *   one. Registration is keyed by the (token) string itself:
 *     - NEW token        → INSERT a fresh row (the user gains a device).
 *     - EXISTING token   → UPDATE metadata + re-assert is_active=true and
 *                          refresh last_seen_at. The owner is re-pointed to
 *                          the calling user (covers a handed-down device).
 *   Every other active token for the user is left untouched.
 */
package com.littlebridge.enrollplus.feature.notification.repository

import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.DeviceTokensTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

/**
 * Minimal projection of a device token row, returned to the service layer.
 * The service only needs the token string + platform; everything else is
 * metadata for the registration / admin paths.
 */
data class DeviceTokenRow(
    val id: UUID,
    val userId: UUID,
    val token: String,
    val platform: String
)

class DeviceTokenRepository {

    /**
     * Register (or re-register) a single device token for [userId].
     *
     * Idempotent on the token value: a re-registration refreshes `platform`,
     * `app_version`, `device_model`, `school_id`, `last_seen_at`, and
     * re-asserts `is_active = true`. It NEVER touches the user's other token
     * rows, so a user's phone + tablet + second phone all stay valid.
     *
     * Returns `true` when a new row was inserted, `false` when an existing row
     * was updated (useful for the registration route's logging).
     */
    suspend fun upsertToken(
        userId: UUID,
        token: String,
        platform: String,
        schoolId: UUID? = null,
        appVersion: String? = null,
        deviceModel: String? = null,
    ): Boolean = dbQuery {
        val now = Instant.now()
        val existing = DeviceTokensTable.selectAll()
            .where { DeviceTokensTable.token eq token }
            .singleOrNull()

        if (existing == null) {
            DeviceTokensTable.insert {
                it[DeviceTokensTable.userId] = userId
                it[DeviceTokensTable.schoolId] = schoolId
                it[DeviceTokensTable.token] = token
                it[DeviceTokensTable.platform] = platform
                it[DeviceTokensTable.appVersion] = appVersion
                it[DeviceTokensTable.deviceModel] = deviceModel
                it[DeviceTokensTable.isActive] = true
                it[DeviceTokensTable.createdAt] = now
                it[DeviceTokensTable.updatedAt] = now
                it[DeviceTokensTable.lastSeenAt] = now
            }
            true
        } else {
            DeviceTokensTable.update({ DeviceTokensTable.token eq token }) {
                it[DeviceTokensTable.userId] = userId
                it[DeviceTokensTable.schoolId] = schoolId
                it[DeviceTokensTable.platform] = platform
                it[DeviceTokensTable.appVersion] = appVersion
                it[DeviceTokensTable.deviceModel] = deviceModel
                it[DeviceTokensTable.isActive] = true
                it[DeviceTokensTable.updatedAt] = now
                it[DeviceTokensTable.lastSeenAt] = now
            }
            false
        }
    }

    /**
     * Every ACTIVE token owned by [userId]. Used by NotificationService to
     * fan a single notification out to all of a user's devices.
     */
    suspend fun activeTokensForUser(userId: UUID): List<DeviceTokenRow> = dbQuery {
        DeviceTokensTable.selectAll()
            .where { (DeviceTokensTable.userId eq userId) and (DeviceTokensTable.isActive eq true) }
            .map {
                DeviceTokenRow(
                    id = it[DeviceTokensTable.id].value,
                    userId = it[DeviceTokensTable.userId],
                    token = it[DeviceTokensTable.token],
                    platform = it[DeviceTokensTable.platform],
                )
            }
    }

    /**
     * Active tokens for every user in [userIds] (one flat list, deduped by
     * token). This is the fan-out shape NotificationService consumes when
     * dispatching a single notification to many recipients at once.
     */
    suspend fun activeTokensForUsers(userIds: Collection<UUID>): List<DeviceTokenRow> = dbQuery {
        if (userIds.isEmpty()) return@dbQuery emptyList()
        val distinct = userIds.distinct()
        DeviceTokensTable.selectAll()
            .where {
                (DeviceTokensTable.userId inList distinct) and (DeviceTokensTable.isActive eq true)
            }
            .map {
                DeviceTokenRow(
                    id = it[DeviceTokensTable.id].value,
                    userId = it[DeviceTokensTable.userId],
                    token = it[DeviceTokensTable.token],
                    platform = it[DeviceTokensTable.platform],
                )
            }
            .distinctBy { it.token }
    }

    /**
     * Mark a single token inactive. Called by NotificationService when Firebase
     * reports the token as invalid/unregistered (UNREGISTERED / UNREGISTERED-ish
     * error codes). We keep the row for audit; we just stop sending to it so we
     * do not burn FCM quota retrying a dead install.
     */
    suspend fun deactivateToken(token: String): Int = dbQuery {
        DeviceTokensTable.update({ DeviceTokensTable.token eq token }) {
            it[isActive] = false
            it[updatedAt] = Instant.now()
        }
    }

    /**
     * Bulk-deactivate every token owned by [userId]. Used by the auth/logout
     * and user-provisioning paths (e.g. teacher de-provisioning) so a removed
     * user stops receiving pushes. Returns the number of rows deactivated.
     */
    suspend fun deactivateAllForUser(userId: UUID): Int = dbQuery {
        DeviceTokensTable.update({ DeviceTokensTable.userId eq userId }) {
            it[isActive] = false
            it[updatedAt] = Instant.now()
        }
    }

    /**
     * Hard-delete every token owned by [userId]. Used by the teacher
     * de-provisioning path (TeacherProvisioningRouting) which previously
     * inlined a `deleteWhere` — now routed through here so the data layer
     * owns the mutation.
     */
    suspend fun deleteAllForUser(userId: UUID): Int = dbQuery {
        DeviceTokensTable.deleteWhere { DeviceTokensTable.userId eq userId }
    }

    /**
     * Refresh `last_seen_at` for a token without otherwise mutating the row.
     * Called by the registration path on a no-op re-registration so we keep a
     * cheap liveness signal even when nothing else changed.
     */
    suspend fun touchLastSeen(token: String) = dbQuery {
        DeviceTokensTable.update({ DeviceTokensTable.token eq token }) {
            it[lastSeenAt] = Instant.now()
        }
        Unit
    }
}
