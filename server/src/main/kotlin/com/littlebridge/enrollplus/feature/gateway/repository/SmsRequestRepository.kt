/*
 * File: SmsRequestRepository.kt
 * Module: feature.gateway.repository
 *
 * Persistence layer for the sms_requests table — the ONLY place that reads or
 * writes SMS-delivery request rows for the OTPSender gateway integration.
 *
 * LIFECYCLE
 *   pending     → created by OtpService when an OTP must go out via the gateway
 *                 (no active gateway found yet, OR awaiting FCM delivery).
 *   dispatched  → an FCM data-message has been pushed to a chosen gateway
 *                 device; device_id + dispatched_at are set.
 *   sent        → the gateway reported successful SMS delivery (sent_at set).
 *   failed      → the gateway reported a failure (error_message set).
 *
 * The `request_id` is the public, client-facing id (a UUID string). The
 * internal PK `id` is never exposed.
 */
package com.littlebridge.enrollplus.feature.gateway.repository

import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.SmsRequestsTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

/** Projection of an sms_requests row for the service / API layers. */
data class SmsRequestRow(
    val id: UUID,
    val requestId: String,
    val phoneNumber: String,
    val otp: String?,
    val message: String,
    val status: String,
    val deviceId: String?,
    val errorMessage: String?,
    val purpose: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val dispatchedAt: Instant?,
    val sentAt: Instant?,
)

object SmsRequestStatus {
    const val PENDING = "pending"
    const val DISPATCHED = "dispatched"
    const val SENT = "sent"
    const val FAILED = "failed"
}

class SmsRequestRepository {

    /**
     * Create a brand-new SMS request in the `pending` state. Returns the
     * generated public [request_id] (a UUID string) so the caller can echo it
     * back to the auth client and embed it in the FCM payload.
     */
    suspend fun create(
        phoneNumber: String,
        otp: String?,
        message: String,
        purpose: String,
    ): String = dbQuery {
        val now = Instant.now()
        val requestId = UUID.randomUUID().toString()
        SmsRequestsTable.insert {
            it[SmsRequestsTable.requestId] = requestId
            it[SmsRequestsTable.phoneNumber] = phoneNumber
            it[SmsRequestsTable.otp] = otp
            it[SmsRequestsTable.message] = message
            it[status] = SmsRequestStatus.PENDING
            it[SmsRequestsTable.purpose] = purpose
            it[createdAt] = now
            it[updatedAt] = now
        }
        requestId
    }

    /**
     * Mark a request as dispatched to [deviceId] (an FCM data-message was
     * pushed to that gateway). Sets dispatched_at. Returns rows updated.
     */
    suspend fun markDispatched(requestId: String, deviceId: String): Int = dbQuery {
        val now = Instant.now()
        SmsRequestsTable.update({ SmsRequestsTable.requestId eq requestId }) {
            it[status] = SmsRequestStatus.DISPATCHED
            it[SmsRequestsTable.deviceId] = deviceId
            it[dispatchedAt] = now
            it[updatedAt] = now
        }
    }

    /**
     * Apply a gateway status callback. [status] must be SENT or FAILED.
     *   - SENT  → sets sent_at, clears error_message.
     *   - FAILED → sets error_message.
     * Returns rows updated (0 when request_id is unknown → caller 404s).
     */
    suspend fun applyStatus(
        requestId: String,
        status: String,
        errorMessage: String? = null,
    ): Int = dbQuery {
        val now = Instant.now()
        SmsRequestsTable.update({ SmsRequestsTable.requestId eq requestId }) {
            it[SmsRequestsTable.status] = status
            it[updatedAt] = now
            when (status) {
                SmsRequestStatus.SENT -> {
                    it[sentAt] = now
                    it[SmsRequestsTable.errorMessage] = null
                }
                SmsRequestStatus.FAILED -> {
                    it[SmsRequestsTable.errorMessage] = errorMessage
                }
            }
        }
    }

    /** Fetch a single request by its public id. */
    suspend fun findByRequestId(requestId: String): SmsRequestRow? = dbQuery {
        SmsRequestsTable.selectAll()
            .where { SmsRequestsTable.requestId eq requestId }
            .map { it.toRow() }
            .singleOrNull()
    }

    /**
     * Pending requests still awaiting processing — used by the OTPSender
     * recovery flow (GET /api/v1/gateway/pending). Returns both `pending`
     * (never dispatched) and `dispatched` (FCM sent but not yet confirmed)
     * rows so a gateway that restarted can pick up in-flight work. Oldest
     * first, capped at [limit].
     */
    suspend fun listPending(limit: Int = 100): List<SmsRequestRow> = dbQuery {
        SmsRequestsTable.selectAll()
            .where {
                (SmsRequestsTable.status eq SmsRequestStatus.PENDING) or
                    (SmsRequestsTable.status eq SmsRequestStatus.DISPATCHED)
            }
            .orderBy(SmsRequestsTable.createdAt, SortOrder.ASC)
            .limit(limit)
            .map { it.toRow() }
    }

    private fun org.jetbrains.exposed.sql.ResultRow.toRow() = SmsRequestRow(
        id = this[SmsRequestsTable.id].value,
        requestId = this[SmsRequestsTable.requestId],
        phoneNumber = this[SmsRequestsTable.phoneNumber],
        otp = this[SmsRequestsTable.otp],
        message = this[SmsRequestsTable.message],
        status = this[SmsRequestsTable.status],
        deviceId = this[SmsRequestsTable.deviceId],
        errorMessage = this[SmsRequestsTable.errorMessage],
        purpose = this[SmsRequestsTable.purpose],
        createdAt = this[SmsRequestsTable.createdAt],
        updatedAt = this[SmsRequestsTable.updatedAt],
        dispatchedAt = this[SmsRequestsTable.dispatchedAt],
        sentAt = this[SmsRequestsTable.sentAt],
    )
}
