/*
 * File: OTPSenderGatewayProvider.kt
 * Module: feature.auth.delivery.sms
 *
 * THE PRIMARY NEW SMS TRANSPORT — pushes an FCM `SMS_REQUEST` to the latest
 * active OTPSender Android gateway device, which fetches the request details
 * and sends the SMS from its own SIM, then reports SENT/FAILED back through
 * the gateway status API.
 *
 * Flow (matches the 7-step spec):
 *   1. OtpService already generated + persisted the OTP and created the
 *      `otp_sms_requests` row (PENDING) — it hands us that row's id.
 *   2. We select the freshest active gateway device (last_seen_at <= 5 min).
 *   3. If none online → Queued (row stays PENDING; recoverable via
 *      GET /api/v1/gateway/pending when a device comes back).
 *   4. We atomically transition the row PENDING → PROCESSING, stamping the
 *      assigned device (so a second gateway can't grab the same request).
 *   5. We push the FCM data message {type=SMS_REQUEST, requestId} to the
 *      device's token via GatewayFcmService.
 *   6. On FCM accept → Dispatched (the gateway will report SENT/FAILED async).
 *      On FCM failure → we revert the row to PENDING (so it's still
 *      recoverable) and return Failed (OtpService logs it but the cloud
 *      provider race is still running independently, so the user still gets
 *      their OTP).
 *
 * This provider does NOT block on the gateway sending the SMS — that's an
 * async device-side action reported later. The SmsSendResult here is the
 * outcome of the *dispatch* (FCM push), not the final delivery.
 *
 * No Koin/DI — `object` singleton.
 */
package com.littlebridge.vidyaprayag.feature.auth.delivery.sms

import com.littlebridge.vidyaprayag.feature.auth.gateway.FcmPushResult
import com.littlebridge.vidyaprayag.feature.auth.gateway.GatewayFcmService
import com.littlebridge.vidyaprayag.feature.auth.gateway.OtpGatewayDeviceRepository
import com.littlebridge.vidyaprayag.feature.auth.gateway.OtpSmsRequestRepository
import com.littlebridge.vidyaprayag.feature.auth.gateway.SmsRequestStatus
import org.slf4j.LoggerFactory

object OTPSenderGatewayProvider : SmsProvider {

    private val log = LoggerFactory.getLogger("SmsProvider/OTPSenderGateway")

    override val name: String = "gateway"

    /**
     * True iff Firebase credentials are plausibly available. Per-request
     * availability ("is a device online right now?") is resolved inside
     * [send] via [OtpGatewayDeviceRepository.findActiveForDispatch] — that's
     * a DB read and must NOT happen here (isConfigured is called from
     * non-suspend contexts).
     */
    override fun isConfigured(): Boolean =
        com.littlebridge.vidyaprayag.feature.auth.gateway.FirebaseAdminInitializer.isConfigured()

    override suspend fun send(request: SmsSendRequest): SmsSendResult {
        // 1. Select the freshest active gateway device.
        val device = OtpGatewayDeviceRepository.findActiveForDispatch()
        if (device == null) {
            log.info(
                "[Sms/Gateway] no active gateway device within freshness window — " +
                    "requestId={} left PENDING for recovery",
                request.requestId,
            )
            // Row stays PENDING; the gateway /pending endpoint will drain it
            // once a device heartbeats back online.
            return SmsSendResult.Queued(
                providerName = name,
                reason = "no active gateway device within 5-min freshness window",
            )
        }

        // 2. Atomically claim the row (PENDING → PROCESSING) for this device.
        //    Guards against two gateways both grabbing the same request.
        val claimed = OtpSmsRequestRepository.markProcessing(
            id = request.requestId,
            assignedDeviceId = device.id,
            providerName = name,
        )
        if (claimed == null) {
            // Someone else (a concurrent /otp/send or a recovery poll) already
            // moved it off PENDING. Not ours to dispatch.
            log.info(
                "[Sms/Gateway] requestId={} was no longer PENDING — skipping",
                request.requestId,
            )
            return SmsSendResult.Failed(
                providerName = name,
                reason = "request was no longer PENDING (claimed by another dispatch)",
            )
        }

        // 3. Push the FCM SMS_REQUEST to the device.
        return when (val push = GatewayFcmService.sendSmsRequest(device.fcmToken, request.requestId)) {
            is FcmPushResult.Sent -> {
                log.info(
                    "[Sms/Gateway] dispatched requestId={} to device={} fcmMsgId={}",
                    request.requestId, device.deviceId, push.messageId,
                )
                // Row is PROCESSING; the gateway app will report SENT/FAILED
                // via POST /api/v1/gateway/requests/{id}/status.
                SmsSendResult.Dispatched(
                    providerName = name,
                    providerMessageId = push.messageId,
                )
            }
            is FcmPushResult.NotConfigured -> {
                // Firebase creds missing/prior boot failed. Revert to PENDING
                // so the request is still recoverable once creds are fixed.
                revertToPending(request.requestId)
                log.warn(
                    "[Sms/Gateway] FCM not configured for requestId={} reason={} — reverted to PENDING",
                    request.requestId, push.reason,
                )
                SmsSendResult.Failed(
                    providerName = name,
                    reason = "FCM not configured: ${push.reason}",
                )
            }
            is FcmPushResult.Failed -> {
                // FCM rejected (invalid/expired token, 4xx/5xx). Revert to
                // PENDING so a subsequent dispatch can pick a different device.
                revertToPending(request.requestId)
                log.warn(
                    "[Sms/Gateway] FCM push failed for requestId={} reason={} — reverted to PENDING",
                    request.requestId, push.reason,
                )
                SmsSendResult.Failed(
                    providerName = name,
                    reason = "FCM push failed: ${push.reason}",
                )
            }
        }
    }

    /**
     * Best-effort revert PROCESSING → PENDING so the request stays in the
     * recovery queue. We don't use [OtpSmsRequestRepository.markFailed]
     * because this isn't a terminal failure — a retry may succeed. If the
     * revert loses a race (the gateway already reported SENT), we leave the
     * row as-is; the logged warning is enough.
     */
    private suspend fun revertToPending(requestId: java.util.UUID) {
        runCatching {
            com.littlebridge.vidyaprayag.db.DatabaseFactory.dbQuery {
                com.littlebridge.vidyaprayag.db.OtpSmsRequestsTable.update({
                    (com.littlebridge.vidyaprayag.db.OtpSmsRequestsTable.id eq requestId) and
                        (com.littlebridge.vidyaprayag.db.OtpSmsRequestsTable.status eq SmsRequestStatus.PROCESSING)
                }) {
                    it[com.littlebridge.vidyaprayag.db.OtpSmsRequestsTable.status] = SmsRequestStatus.PENDING
                    it[com.littlebridge.vidyaprayag.db.OtpSmsRequestsTable.assignedDevice] = null
                    it[com.littlebridge.vidyaprayag.db.OtpSmsRequestsTable.provider] = null
                }
            }
        }.onFailure { t ->
            log.warn(
                "[Sms/Gateway] failed to revert requestId={} to PENDING: {}",
                requestId, t.javaClass.simpleName,
            )
        }
    }
}
