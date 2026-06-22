/*
 * File: GatewaySmsService.kt
 * Module: feature.gateway.service
 *
 * The reusable "send this OTP SMS via the OTPSender gateway" entry point used
 * by OtpService. It encapsulates the target OTP flow:
 *
 *   Store OTP (already done by OtpService)
 *        ↓
 *   Create SMS request  (status = pending)
 *        ↓
 *   Locate active OTPSender device (is_active + last_seen_at within window)
 *        ↓
 *   Send FCM data-message to that device  → mark request dispatched
 *        ↓
 *   Return the requestId
 *
 * NO SMS PROVIDER CALL happens here — the OTPSender phone sends the SMS from
 * its own SIM. The backend never touches Fast2SMS / MSG91 / Twilio on this path.
 *
 * DEGRADATION (never fails OTP generation)
 *   - No active gateway device → request stays `pending`; the OTPSender recovery
 *     flow (GET /pending) picks it up later. Result: ok=true, dispatched=false.
 *   - FCM unavailable / send fails → request stays `pending` likewise.
 *   The caller treats a created requestId as success regardless of dispatch.
 *
 * ENABLEMENT
 *   [isEnabled] is true only when OTP_GATEWAY_ENABLED=true. When false, OtpService
 *   uses its existing multi-provider direct-SMS chain unchanged.
 */
package com.littlebridge.enrollplus.feature.gateway.service

import com.littlebridge.enrollplus.feature.gateway.repository.OtpGatewayDeviceRepository
import com.littlebridge.enrollplus.feature.gateway.repository.SmsRequestRepository
import org.slf4j.LoggerFactory

/** Outcome of a gateway dispatch attempt. */
data class GatewayDispatchResult(
    /** The created SMS request's public id (always present when ok=true). */
    val requestId: String?,
    /** True when the SMS request row was created (regardless of FCM delivery). */
    val ok: Boolean,
    /** True when an FCM data-message was actually pushed to a live gateway. */
    val dispatched: Boolean,
    /** Human-readable note for logs / telemetry. */
    val note: String? = null,
)

class GatewaySmsService(
    private val deviceRepository: OtpGatewayDeviceRepository = OtpGatewayDeviceRepository(),
    private val smsRequestRepository: SmsRequestRepository = SmsRequestRepository(),
    private val fcmDispatcher: GatewayFcmDispatcher = GatewayFcmDispatcher(),
) {

    private val log = LoggerFactory.getLogger("GatewaySmsService")

    /**
     * Send an OTP SMS through the OTPSender gateway.
     *
     * @param phoneNumber destination in E.164.
     * @param otp the plaintext code (the gateway phone types it into the SMS).
     * @param message the full SMS body.
     * @param purpose login | signup | …
     */
    suspend fun sendOtpSms(
        phoneNumber: String,
        otp: String,
        message: String,
        purpose: String,
    ): GatewayDispatchResult {
        // 1) Create the SMS request (pending).
        val requestId = smsRequestRepository.create(
            phoneNumber = phoneNumber,
            otp = otp,
            message = message,
            purpose = purpose,
        )

        // 2) Locate the freshest active gateway device.
        val device = deviceRepository.activeGatewayForDispatch(windowMinutes = livenessWindowMinutes())
        if (device == null) {
            log.warn(
                "[GatewaySms] no active OTPSender device — request {} left pending",
                requestId,
            )
            return GatewayDispatchResult(
                requestId = requestId,
                ok = true,
                dispatched = false,
                note = "no active gateway device; left pending",
            )
        }

        // 3) Push the FCM data-message to that device.
        val pushed = fcmDispatcher.dispatch(
            fcmToken = device.fcmToken,
            requestId = requestId,
            phoneNumber = phoneNumber,
            otp = otp,
            message = message,
        )

        // 4) Mark dispatched on success; otherwise leave pending for recovery.
        if (pushed) {
            smsRequestRepository.markDispatched(requestId, device.deviceId)
            return GatewayDispatchResult(
                requestId = requestId,
                ok = true,
                dispatched = true,
                note = "dispatched to ${device.deviceId}",
            )
        }

        return GatewayDispatchResult(
            requestId = requestId,
            ok = true,
            dispatched = false,
            note = "FCM dispatch failed; left pending",
        )
    }

    companion object {
        /** Gateway path is active only when OTP_GATEWAY_ENABLED=true. */
        fun isEnabled(): Boolean =
            System.getenv("OTP_GATEWAY_ENABLED")?.equals("true", ignoreCase = true) == true

        /** Device-liveness window in minutes (default 5, per spec). */
        fun livenessWindowMinutes(): Long =
            System.getenv("OTP_GATEWAY_LIVENESS_MINUTES")?.toLongOrNull()?.coerceIn(1, 60) ?: 5L
    }
}
