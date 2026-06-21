/*
 * File: OtpGatewayDeviceRepository.kt
 * Module: feature.auth.gateway
 *
 * Data access for `otp_gateway_devices` — the registered OTPSender Android
 * gateway devices. Each device reports a Firebase Cloud Messaging (FCM)
 * registration token; the backend pushes an `SMS_REQUEST` data message to
 * that token and the app sends the SMS from its own SIM.
 *
 * All access goes through `DatabaseFactory.dbQuery { ... }` so it runs on the
 * shared HikariCP pool + suspended transaction, matching every other feature
 * in the codebase. No Koin/DI — this is an `object` singleton.
 *
 * Gateway-selection policy (spec):
 *   "Latest active gateway where last_seen_at <= 5 minutes. If none: leave
 *    PENDING."  => [findActiveForDispatch] returns the most-recently-seen
 *   active device whose heartbeat is within the 5-minute freshness window.
 */
package com.littlebridge.vidyaprayag.feature.auth.gateway

import com.littlebridge.vidyaprayag.db.DatabaseFactory.dbQuery
import com.littlebridge.vidyaprayag.db.OtpGatewayDevicesTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Read-side projection of one gateway device row. */
data class GatewayDevice(
    val id: UUID,
    val deviceId: String,
    val deviceName: String?,
    val fcmToken: String,
    val appVersion: String?,
    val lastSeenAt: Instant,
    val isActive: Boolean,
    val batteryLevel: Int?,
    val networkType: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Heartbeat freshness window. A device older than this is not dispatched to. */
internal const val GATEWAY_FRESHNESS_MINUTES = 5L

object OtpGatewayDeviceRepository {

    /**
     * Insert-or-update a device on register/heartbeat. Keyed by the unique
     * `device_id` (Android-wide). On update we refresh the FCM token (it can
     * rotate), app version, device name, and mark the device active + seen-now.
     * Returns the stable row UUID.
     */
    suspend fun register(
        deviceId: String,
        deviceName: String?,
        fcmToken: String,
        appVersion: String?,
    ): UUID = dbQuery {
        val now = Instant.now()
        val existing = OtpGatewayDevicesTable.selectAll()
            .where { OtpGatewayDevicesTable.deviceId eq deviceId }
            .singleOrNull()

        if (existing != null) {
            val id = existing[OtpGatewayDevicesTable.id].value
            OtpGatewayDevicesTable.update({ OtpGatewayDevicesTable.deviceId eq deviceId }) {
                it[OtpGatewayDevicesTable.fcmToken] = fcmToken
                if (deviceName != null) it[OtpGatewayDevicesTable.deviceName] = deviceName
                if (appVersion != null) it[OtpGatewayDevicesTable.appVersion] = appVersion
                it[OtpGatewayDevicesTable.isActive] = true
                it[OtpGatewayDevicesTable.lastSeenAt] = now
                it[OtpGatewayDevicesTable.updatedAt] = now
            }
            id
        } else {
            OtpGatewayDevicesTable.insert {
                it[OtpGatewayDevicesTable.deviceId] = deviceId
                it[OtpGatewayDevicesTable.deviceName] = deviceName
                it[OtpGatewayDevicesTable.fcmToken] = fcmToken
                it[OtpGatewayDevicesTable.appVersion] = appVersion
                it[OtpGatewayDevicesTable.isActive] = true
                it[OtpGatewayDevicesTable.lastSeenAt] = now
                it[OtpGatewayDevicesTable.createdAt] = now
                it[OtpGatewayDevicesTable.updatedAt] = now
            }[OtpGatewayDevicesTable.id].value
        }
    }

    /**
     * Refresh `last_seen_at` + battery + network. Returns false if the device
     * is unknown (the app should re-register before heartbeating).
     */
    suspend fun heartbeat(
        deviceId: String,
        batteryLevel: Int?,
        networkType: String?,
    ): Boolean = dbQuery {
        val existing = OtpGatewayDevicesTable.selectAll()
            .where { OtpGatewayDevicesTable.deviceId eq deviceId }
            .singleOrNull() ?: return@dbQuery false

        val now = Instant.now()
        OtpGatewayDevicesTable.update({ OtpGatewayDevicesTable.deviceId eq deviceId }) {
            it[OtpGatewayDevicesTable.lastSeenAt] = now
            it[OtpGatewayDevicesTable.updatedAt] = now
            if (batteryLevel != null) it[OtpGatewayDevicesTable.batteryLevel] =
                batteryLevel.coerceIn(0, 100)
            if (networkType != null) it[OtpGatewayDevicesTable.networkType] =
                networkType.take(16)
        }
        true
    }

    /**
     * The gateway-selection query. Picks the active device with the most
     * recent heartbeat within the freshness window. Returns null if none —
     * the caller leaves the SMS request PENDING for later recovery.
     */
    suspend fun findActiveForDispatch(): GatewayDevice? = dbQuery {
        val cutoff = Instant.now().minus(GATEWAY_FRESHNESS_MINUTES, ChronoUnit.MINUTES)
        OtpGatewayDevicesTable.selectAll()
            .where {
                (OtpGatewayDevicesTable.isActive eq true) and
                    (OtpGatewayDevicesTable.lastSeenAt greaterEq cutoff)
            }
            .orderBy(OtpGatewayDevicesTable.lastSeenAt, SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.toGatewayDevice()
    }

    /**
     * Look up an active device by its `device_id` — used to authenticate
     * gateway API calls (fetch-request, report-status, pending). Only active
     * + freshly-heartbeated devices may pull requests, so this reuses the
     * same freshness window as dispatch.
     */
    suspend fun findActiveByDeviceId(deviceId: String): GatewayDevice? = dbQuery {
        val cutoff = Instant.now().minus(GATEWAY_FRESHNESS_MINUTES, ChronoUnit.MINUTES)
        OtpGatewayDevicesTable.selectAll()
            .where {
                (OtpGatewayDevicesTable.deviceId eq deviceId) and
                    (OtpGatewayDevicesTable.isActive eq true) and
                    (OtpGatewayDevicesTable.lastSeenAt greaterEq cutoff)
            }
            .singleOrNull()
            ?.toGatewayDevice()
    }

    /** Mark a device inactive (the app called a deregister endpoint or 404'd). */
    suspend fun deactivate(deviceId: String): Boolean = dbQuery {
        val rows = OtpGatewayDevicesTable.update({ OtpGatewayDevicesTable.deviceId eq deviceId }) {
            it[OtpGatewayDevicesTable.isActive] = false
            it[OtpGatewayDevicesTable.updatedAt] = Instant.now()
        }
        rows > 0
    }

    /** All active devices — for the admin monitoring snapshot. */
    suspend fun listActive(): List<GatewayDevice> = dbQuery {
        OtpGatewayDevicesTable.selectAll()
            .where { OtpGatewayDevicesTable.isActive eq true }
            .orderBy(OtpGatewayDevicesTable.lastSeenAt, SortOrder.DESC)
            .map { it.toGatewayDevice() }
    }

    /** All devices (active + inactive), most recently seen first — admin. */
    suspend fun listAll(limit: Int = 100): List<GatewayDevice> = dbQuery {
        OtpGatewayDevicesTable.selectAll()
            .orderBy(OtpGatewayDevicesTable.lastSeenAt, SortOrder.DESC)
            .limit(limit.coerceIn(1, 500))
            .map { it.toGatewayDevice() }
    }

    /** Hard-delete a device row — admin cleanup only. */
    suspend fun delete(deviceId: String): Int = dbQuery {
        OtpGatewayDevicesTable.deleteWhere { OtpGatewayDevicesTable.deviceId eq deviceId }
    }
}

private fun org.jetbrains.exposed.sql.ResultRow.toGatewayDevice(): GatewayDevice =
    GatewayDevice(
        id = this[OtpGatewayDevicesTable.id].value,
        deviceId = this[OtpGatewayDevicesTable.deviceId],
        deviceName = this[OtpGatewayDevicesTable.deviceName],
        fcmToken = this[OtpGatewayDevicesTable.fcmToken],
        appVersion = this[OtpGatewayDevicesTable.appVersion],
        lastSeenAt = this[OtpGatewayDevicesTable.lastSeenAt],
        isActive = this[OtpGatewayDevicesTable.isActive],
        batteryLevel = this[OtpGatewayDevicesTable.batteryLevel],
        networkType = this[OtpGatewayDevicesTable.networkType],
        createdAt = this[OtpGatewayDevicesTable.createdAt],
        updatedAt = this[OtpGatewayDevicesTable.updatedAt],
    )
