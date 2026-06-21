/*
 * File: GatewayFcmService.kt
 * Module: feature.auth.gateway
 *
 * Pushes an FCM data message to a registered OTPSender Android gateway device
 * so the app fetches the SMS request and sends it from its own SIM.
 *
 * Wire format (data-only message — never a notification, we don't want this
 * to render in the system tray; the app handles it via a foreground service /
 * high-priority FCM callback):
 *
 *     data {
 *       "type"      : "SMS_REQUEST"
 *       "requestId" : "<uuid of the otp_sms_requests row>"
 *     }
 *
 * The app then GETs /api/v1/gateway/requests/{requestId} for the phone number
 * + message, sends the SMS, and POSTs .../status with SENT/FAILED. Keeping the
 * payload tiny means we never exceed the 4 KB FCM data cap and the push is as
 * fast as possible.
 *
 * Android delivery priority is set to HIGH so the device wakes the app promptly
 * even when backgrounded (FCM high-priority data messages get a ~10s execution
 * window on Android 12+, which is enough to fire the HTTP fetch + SmsManager).
 *
 * No Koin/DI — `object` singleton, matching the rest of the auth feature.
 */
package com.littlebridge.vidyaprayag.feature.auth.gateway

import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidMessagePriority
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Result of one FCM push attempt. Mirrors the shape the caller
 * (OTPSenderGatewayProvider) needs to decide the row transition.
 */
sealed class FcmPushResult {
    /** FCM accepted the message and returned a message id. */
    data class Sent(val messageId: String) : FcmPushResult()

    /** FCM / FirebaseApp is not configured — no point retrying on this JVM. */
    data class NotConfigured(val reason: String) : FcmPushResult()

    /** FCM rejected the push (invalid token, 4xx/5xx, exception). */
    data class Failed(val reason: String) : FcmPushResult()
}

object GatewayFcmService {

    private val log = LoggerFactory.getLogger("GatewayFcmService")

    /** Data-only message type tag — the app filters on this exact string. */
    const val MSG_TYPE = "SMS_REQUEST"

    /**
     * Push an SMS_REQUEST to [fcmToken]. Does NOT throw — every failure is
     * wrapped in [FcmPushResult.Failed] / [FcmPushResult.NotConfigured].
     *
     * NOTE: FCM.sendMessage is a synchronous, blocking HTTP call to Google's
     * FCM v1 endpoint. The caller runs this from a coroutine, so blocking is
     * acceptable, but we keep the call off the request-dispatch thread by
     * virtue of the coroutine context (the OTP send path already runs on
     * Dispatchers.IO through dbQuery; the FCM call sits between two dbQuery
     * blocks and inherits the same dispatcher).
     */
    fun sendSmsRequest(fcmToken: String, requestId: UUID): FcmPushResult {
        if (fcmToken.isBlank()) {
            return FcmPushResult.NotConfigured("blank FCM token")
        }

        val app: FirebaseApp = FirebaseAdminInitializer.ensureInitialized()
            ?: return FcmPushResult.NotConfigured(
                "FirebaseApp not initialised (no service-account creds or prior boot failed)"
            )

        val message: Message = Message.builder()
            .setToken(fcmToken)
            .putData("type", MSG_TYPE)
            .putData("requestId", requestId.toString())
            .setAndroidConfig(
                AndroidConfig.builder()
                    // HIGH wakes a backgrounded app promptly (FCM grants a
                    // ~10s execution window on Android 12+ for high-priority
                    // data messages — enough to fetch + SmsManager.send).
                    .setPriority(AndroidMessagePriority.HIGH)
                    // Allow up to 4 weeks of FCM retries if the device is
                    // offline when we push (seconds). Keeps the request alive
                    // across a short device outage so the gateway eventually
                    // sends even if it was on cellular + battery-saver.
                    .setTtl(2_419_200L)
                    .build()
            )
            .build()

        return try {
            val msgId = FirebaseMessaging.getInstance(app).send(message)
            log.info(
                "[GatewayFcm] pushed SMS_REQUEST requestId={} fcm=*****{} messageId={}",
                requestId, fcmToken.takeLast(4), msgId,
            )
            FcmPushResult.Sent(messageId = msgId)
        } catch (t: Throwable) {
            val reason = "${t.javaClass.simpleName}: ${t.message?.take(200)}"
            log.warn(
                "[GatewayFcm] push FAILED requestId={} fcm=*****{} reason={}",
                requestId, fcmToken.takeLast(4), reason,
            )
            FcmPushResult.Failed(reason)
        }
    }
}
