/*
 * File: OtpSmsRequestRepository.kt
 * Module: feature.auth.gateway
 *
 * Data access for `otp_sms_requests` — one row per outbound SMS the backend
 * hands off to an OTPSender gateway device.
 *
 * Lifecycle of a row
 * ------------------
 *   PENDING     created by /otp/send. No device assigned yet, or a device
 *               was assigned but never confirmed receipt.
 *   PROCESSING  a gateway device fetched the request details and is about
 *               to send from its SIM.
 *   SENT        the gateway reported the SMS sent successfully.
 *   FAILED      the gateway reported a failure, or the backend marked it
 *               failed on dispatch error.
 *
 * All access via `DatabaseFactory.dbQuery { ... }` (shared HikariCP pool +
 * suspended transaction). `object` singleton — no DI.
 */
package com.littlebridge.vidyaprayag.feature.auth.gateway

import com.littlebridge.vidyaprayag.db.DatabaseFactory.dbQuery
import com.littlebridge.vidyaprayag.db.OtpSmsRequestsTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

/** Read-side projection of one SMS request row. */
data class SmsRequest(
    val id: UUID,
    val phoneNumber: String,
    val message: String,
    val status: String,            // PENDING | PROCESSING | SENT | FAILED
    val provider: String?,
    val assignedDeviceId: UUID?,
    val errorMessage: String?,
    val createdAt: Instant,
    val sentAt: Instant?,
    val deliveredAt: Instant?,
)

/** Status constants — kept here so routes/services share one source of truth. */
object SmsRequestStatus {
    const val PENDING = "PENDING"
    const val PROCESSING = "PROCESSING"
    const val SENT = "SENT"
    const val FAILED = "FAILED"
}

object OtpSmsRequestRepository {

    /**
     * Create a PENDING request and return its UUID. The caller (OtpService)
     * then tries to dispatch it; if no gateway is online it stays PENDING and
     * is recoverable via GET /api/v1/gateway/pending.
     */
    suspend fun createPending(
        phoneNumber: String,
        message: String,
    ): UUID = dbQuery {
        val now = Instant.now()
        OtpSmsRequestsTable.insert {
            it[OtpSmsRequestsTable.phoneNumber] = phoneNumber
            it[OtpSmsRequestsTable.message] = message
            it[OtpSmsRequestsTable.status] = SmsRequestStatus.PENDING
            it[OtpSmsRequestsTable.createdAt] = now
        }[OtpSmsRequestsTable.id].value
    }

    /** Full row for a given request id — used by the gateway fetch endpoint. */
    suspend fun findById(id: UUID): SmsRequest? = dbQuery {
        OtpSmsRequestsTable.selectAll()
            .where { OtpSmsRequestsTable.id eq id }
            .singleOrNull()
            ?.toSmsRequest()
    }

    /**
     * Atomically transition a PENDING request to PROCESSING and stamp the
     * assigned device + provider name. Returns the row if the transition was
     * applied (i.e. it was actually PENDING), null otherwise — guards against
     * two gateways grabbing the same request.
     *
     * [providerName] is recorded in the `provider` column so admin monitoring
     * can distinguish "gateway" (OTPSender device SIM) from "msg91" / "twilio"
     * (cloud fallback). Defaults to "gateway" for the primary device flow.
     */
    suspend fun markProcessing(
        id: UUID,
        assignedDeviceId: UUID? = null,
        providerName: String = "gateway",
    ): SmsRequest? = dbQuery {
        val existing = OtpSmsRequestsTable.selectAll()
            .where { OtpSmsRequestsTable.id eq id }
            .singleOrNull()
            ?: return@dbQuery null

        if (existing[OtpSmsRequestsTable.status] != SmsRequestStatus.PENDING) {
            return@dbQuery null
        }

        OtpSmsRequestsTable.update({ OtpSmsRequestsTable.id eq id }) {
            it[OtpSmsRequestsTable.status] = SmsRequestStatus.PROCESSING
            it[OtpSmsRequestsTable.assignedDevice] = assignedDeviceId
            it[OtpSmsRequestsTable.provider] = providerName
        }
        OtpSmsRequestsTable.selectAll()
            .where { OtpSmsRequestsTable.id eq id }
            .single()
            .toSmsRequest()
    }

    /**
     * Apply a terminal status report from a gateway (SENT or FAILED). Only
     * valid from PROCESSING; a late/duplicate report is ignored. Returns the
     * updated row, or null if no row / wrong state.
     */
    suspend fun reportStatus(
        id: UUID,
        status: String,
        errorMessage: String?,
    ): SmsRequest? = dbQuery {
        val existing = OtpSmsRequestsTable.selectAll()
            .where { OtpSmsRequestsTable.id eq id }
            .singleOrNull()
            ?: return@dbQuery null

        // Only accept terminal reports from PROCESSING. (A PENDING request
        // was never claimed by a gateway, so a status report for it is a bug
        // or a replay; SENT/FAILED are already terminal.)
        if (existing[OtpSmsRequestsTable.status] != SmsRequestStatus.PROCESSING) {
            return@dbQuery null
        }
        val now = Instant.now()
        OtpSmsRequestsTable.update({ OtpSmsRequestsTable.id eq id }) {
            it[OtpSmsRequestsTable.status] = status
            if (status == SmsRequestStatus.SENT) {
                it[OtpSmsRequestsTable.sentAt] = now
                it[OtpSmsRequestsTable.deliveredAt] = now
                it[OtpSmsRequestsTable.errorMessage] = null
            } else {
                it[OtpSmsRequestsTable.errorMessage] = errorMessage
            }
        }
        OtpSmsRequestsTable.selectAll()
            .where { OtpSmsRequestsTable.id eq id }
            .single()
            .toSmsRequest()
    }

    /**
     * Mark a PENDING request FAILED directly (used when the backend cannot
     * dispatch and the operator chooses not to leave it queued). Distinct
     * from [reportStatus] which is the gateway's terminal report.
     */
    suspend fun markFailed(id: UUID, reason: String): Boolean = dbQuery {
        val rows = OtpSmsRequestsTable.update({
            (OtpSmsRequestsTable.id eq id) and
                (OtpSmsRequestsTable.status eq SmsRequestStatus.PENDING)
        }) {
            it[OtpSmsRequestsTable.status] = SmsRequestStatus.FAILED
            it[OtpSmsRequestsTable.errorMessage] = reason
        }
        rows > 0
    }

    /** All PENDING requests, oldest first — the gateway recovery endpoint. */
    suspend fun listPending(limit: Int = 100): List<SmsRequest> = dbQuery {
        OtpSmsRequestsTable.selectAll()
            .where { OtpSmsRequestsTable.status eq SmsRequestStatus.PENDING }
            .orderBy(OtpSmsRequestsTable.createdAt, SortOrder.ASC)
            .limit(limit.coerceIn(1, 500))
            .map { it.toSmsRequest() }
    }

    /** Count requests by terminal status — admin monitoring. */
    suspend fun countByStatus(status: String): Long = dbQuery {
        OtpSmsRequestsTable.selectAll()
            .where { OtpSmsRequestsTable.status eq status }
            .count()
    }

    /** Recent requests of a given status, newest first — admin monitoring. */
    suspend fun listByStatus(status: String, limit: Int = 50): List<SmsRequest> = dbQuery {
        OtpSmsRequestsTable.selectAll()
            .where { OtpSmsRequestsTable.status eq status }
            .orderBy(OtpSmsRequestsTable.createdAt, SortOrder.DESC)
            .limit(limit.coerceIn(1, 500))
            .map { it.toSmsRequest() }
    }
}

private fun org.jetbrains.exposed.sql.ResultRow.toSmsRequest(): SmsRequest = SmsRequest(
    id = this[OtpSmsRequestsTable.id].value,
    phoneNumber = this[OtpSmsRequestsTable.phoneNumber],
    message = this[OtpSmsRequestsTable.message],
    status = this[OtpSmsRequestsTable.status],
    provider = this[OtpSmsRequestsTable.provider],
    assignedDeviceId = this[OtpSmsRequestsTable.assignedDevice],
    errorMessage = this[OtpSmsRequestsTable.errorMessage],
    createdAt = this[OtpSmsRequestsTable.createdAt],
    sentAt = this[OtpSmsRequestsTable.sentAt],
    deliveredAt = this[OtpSmsRequestsTable.deliveredAt],
)
