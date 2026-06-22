/*
 * File: GatewayFcmDispatcher.kt
 * Module: feature.gateway.service
 *
 * The single FCM dispatch entry point for the OTPSender SMS gateway. Unlike
 * NotificationService (which fans a USER-FACING notification out to many app
 * device tokens via the PRIMARY Firebase project), this dispatcher sends a
 * SILENT, DATA-ONLY message to ONE OTPSender gateway token via the SEPARATE
 * OTPSender Firebase project ([FirebaseAdminInitializer.otpSenderApp]).
 *
 * WHY DATA-ONLY (no `notification` block)
 *   The OTPSender app must wake up and act on the payload (send an SMS) even in
 *   the background — it must NOT render a tray notification. A notification
 *   block would (a) show a useless tray entry and (b) on Android, background
 *   messages with a notification block are NOT delivered to the app's
 *   onMessageReceived handler. So we send data-only and set high priority.
 *
 * GRACEFUL DEGRADATION
 *   When the OTPSender Firebase app is not initialised (credentials absent),
 *   [dispatch] returns false WITHOUT throwing. The caller (OtpService) then
 *   leaves the SMS request pending — OTP generation never fails on push being
 *   unavailable. The OTPSender recovery flow (GET /pending) will pick the
 *   request up later.
 */
package com.littlebridge.enrollplus.feature.gateway.service

import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.littlebridge.enrollplus.feature.notification.firebase.FirebaseAdminInitializer
import org.slf4j.LoggerFactory

class GatewayFcmDispatcher {

    private val log = LoggerFactory.getLogger("GatewayFcmDispatcher")

    /**
     * Push a data-only "send this SMS" command to the OTPSender gateway
     * identified by [fcmToken].
     *
     * @return true when FCM accepted the message; false when the OTPSender
     *   Firebase app is unavailable or the send failed. NEVER throws.
     */
    suspend fun dispatch(
        fcmToken: String,
        requestId: String,
        phoneNumber: String,
        otp: String?,
        message: String,
    ): Boolean {
        val app = FirebaseAdminInitializer.otpSenderApp()
            ?: run {
                log.warn(
                    "[GatewayFcm] OTPSender Firebase app unavailable — leaving request {} pending",
                    requestId,
                )
                return false
            }

        val data = buildMap {
            put("type", "SEND_SMS")
            put("requestId", requestId)
            put("phoneNumber", phoneNumber)
            put("message", message)
            if (!otp.isNullOrBlank()) put("otp", otp)
        }

        val fcmMessage = Message.builder()
            .setToken(fcmToken)
            .putAllData(data)
            // High priority so the gateway phone wakes promptly even when dozing.
            .setAndroidConfig(
                AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .build()
            )
            .build()

        return runCatching {
            FirebaseMessaging.getInstance(app).send(fcmMessage)
        }.map {
            log.info("[GatewayFcm] dispatched SMS request {} to gateway", requestId)
            true
        }.getOrElse {
            log.warn(
                "[GatewayFcm] dispatch failed for request {}: {}",
                requestId, it.message?.take(160),
            )
            false
        }
    }
}
