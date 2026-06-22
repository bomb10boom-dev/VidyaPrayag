/*
 * File: OtpGatewayDeviceRepository.kt
 * Module: feature.gateway.repository
 *
 * Persistence layer for the otp_gateway_devices table — the ONLY place that
 * reads or writes OTPSender gateway-device rows. The gateway routing and the
 * OtpService (device selection) talk to gateway devices through this repository
 * so the registration/liveness invariants stay in one spot.
 *
 * REGISTRATION INVARIANT
 *   The natural key is `device_id` (one OTPSender install). Registration is
 *   idempotent on it:
 *     - NEW device_id      → INSERT a fresh row.
 *     - EXISTING device_id → UPDATE its fcm_token / metadata, re-assert
 *                            is_active = true, refresh last_seen_at.
 *   A device's FCM token rotates over time; re-registration captures the new
 *   token without creating a duplicate row.
 *
 * DEVICE SELECTION (OTP dispatch)
 *   activeGatewayForDispatch(window) returns the most-recently-active device
 *   whose is_active = true AND last_seen_at is within the liveness window
 *   (default 5 minutes). When none qualifies it returns null and the caller
 *   leaves the SMS request pending — it NEVER fails OTP generation.
 */
package com.littlebridge.enrollplus.feature.gateway.repository

import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.OtpGatewayDevicesTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Projection of a gateway-device row for the service / API layers.
 */
data class GatewayDeviceRow(
    val id: UUID,
    val deviceId: String,
    val deviceName: String?,
    val fcmToken: String,
    val appVersion: String?,
    val isActive: Boolean,
    val batteryLevel: Int?,
    val networkType: String?,
    val lastSeenAt: Instant?,
)

class OtpGatewayDeviceRepository {

    /**
     * Register (or re-register) an OTPSender gateway device.
     *
     * Idempotent on [deviceId]: a re-registration refreshes fcm_token,
     * device_name, app_version, re-asserts is_active = true, and bumps
     * last_seen_at. Returns true when a new row was inserted, false on update.
     */
    suspend fun register(
        deviceId: String,
        fcmToken: String,
        deviceName: String? = null,
        appVersion: String? = null,
    ): Boolean = dbQuery {
        val now = Instant.now()
        val existing = OtpGatewayDevicesTable.selectAll()
            .where { OtpGatewayDevicesTable.deviceId eq deviceId }
            .singleOrNull()

        if (existing == null) {
            OtpGatewayDevicesTable.insert {
                it[OtpGatewayDevicesTable.deviceId] = deviceId
                it[OtpGatewayDevicesTable.deviceName] = deviceName
                it[OtpGatewayDevicesTable.fcmToken] = fcmToken
                it[OtpGatewayDevicesTable.appVersion] = appVersion
                it[isActive] = true
                it[lastSeenAt] = now
                it[createdAt] = now
                it[updatedAt] = now
            }
            true
        } else {
            OtpGatewayDevicesTable.update({ OtpGatewayDevicesTable.deviceId eq deviceId }) {
                it[OtpGatewayDevicesTable.fcmToken] = fcmToken
                if (deviceName != null) it[OtpGatewayDevicesTable.deviceName] = deviceName
                if (appVersion != null) it[OtpGatewayDevicesTable.appVersion] = appVersion
                it[isActive] = true
                it[lastSeenAt] = now
                it[updatedAt] = now
            }
            false
        }
    }

    /**
     * Heartbeat: refresh last_seen_at and (optionally) battery_level +
     * network_type for [deviceId]. Returns the number of rows updated (0 when
     * the device is unknown — the caller surfaces a 404).
     */
    suspend fun heartbeat(
        deviceId: String,
        batteryLevel: Int? = null,
        networkType: String? = null,
    ): Int = dbQuery {
        val now = Instant.now()
        OtpGatewayDevicesTable.update({ OtpGatewayDevicesTable.deviceId eq deviceId }) {
            it[lastSeenAt] = now
            it[updatedAt] = now
            if (batteryLevel != null) it[OtpGatewayDevicesTable.batteryLevel] = batteryLevel
            if (networkType != null) it[OtpGatewayDevicesTable.networkType] = networkType
            // A device sending heartbeats is alive — re-assert active.
            it[isActive] = true
        }
    }

    /**
     * The freshest ACTIVE gateway eligible for dispatch: is_active = true AND
     * last_seen_at within [windowMinutes] (default 5). Returns the most
     * recently active device, or null when none qualifies (→ leave pending).
     */
    suspend fun activeGatewayForDispatch(windowMinutes: Long = 5): GatewayDeviceRow? = dbQuery {
        val cutoff = Instant.now().minus(windowMinutes, ChronoUnit.MINUTES)
        OtpGatewayDevicesTable.selectAll()
            .where {
                (OtpGatewayDevicesTable.isActive eq true) and
                    (OtpGatewayDevicesTable.lastSeenAt greaterEq cutoff)
            }
            .orderBy(OtpGatewayDevicesTable.lastSeenAt, SortOrder.DESC)
            .limit(1)
            .map { it.toRow() }
            .singleOrNull()
    }

    /** Look up a single device by its natural key. */
    suspend fun findByDeviceId(deviceId: String): GatewayDeviceRow? = dbQuery {
        OtpGatewayDevicesTable.selectAll()
            .where { OtpGatewayDevicesTable.deviceId eq deviceId }
            .map { it.toRow() }
            .singleOrNull()
    }

    private fun org.jetbrains.exposed.sql.ResultRow.toRow() = GatewayDeviceRow(
        id = this[OtpGatewayDevicesTable.id].value,
        deviceId = this[OtpGatewayDevicesTable.deviceId],
        deviceName = this[OtpGatewayDevicesTable.deviceName],
        fcmToken = this[OtpGatewayDevicesTable.fcmToken],
        appVersion = this[OtpGatewayDevicesTable.appVersion],
        isActive = this[OtpGatewayDevicesTable.isActive],
        batteryLevel = this[OtpGatewayDevicesTable.batteryLevel],
        networkType = this[OtpGatewayDevicesTable.networkType],
        lastSeenAt = this[OtpGatewayDevicesTable.lastSeenAt],
    )
}
