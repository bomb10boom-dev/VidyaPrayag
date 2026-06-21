/*
 * File: SmsProvider.kt
 * Module: feature.auth.delivery.sms
 *
 * NARROW SMS-ONLY DELIVERY ABSTRACTION.
 *
 * Rationale
 * ---------
 * The existing `OtpProvider` (feature.auth.delivery) is deliberately BROAD:
 * it spans sms / whatsapp / email / voice / console, carries the OTP code +
 * purpose + locale + ttl, and lets each provider render its own body from
 * `OtpMessageTemplates`. That breadth is great for the cloud-delivery race
 * but is the wrong shape for the new OTPSender gateway device flow, which is
 * strictly:
 *
 *     "take this fully-rendered SMS body and this phone number, and get the
 *      bytes onto a phone by any means — a registered Android gateway's SIM
 *      via FCM, or a cloud SMS provider, or whatever we add next."
 *
 * `SmsProvider` is that narrower contract. It knows nothing about OTP codes,
 * channels, locales or templates — the caller (`OtpService`) already built
 * the message via `OtpMessageTemplates.smsBody(...)` and hands it in as an
 * opaque string. This keeps the OTP module coupled to ONE tiny interface
 * instead of to every SMS transport we ever ship.
 *
 * Implementations (this module)
 * -----------------------------
 *   - OTPSenderGatewayProvider — pushes an FCM SMS_REQUEST to the registered
 *     OTPSender Android app, which sends the SMS from its own SIM and reports
 *     SENT/FAILED back through the gateway APIs. THE primary new path.
 *   - Msg91SmsProvider          — thin adapter over the existing MSG91 cloud
 *     provider, surfaced as an SmsProvider for future single-channel dispatch.
 *   - TwilioSmsProvider         — same, over the existing Twilio provider.
 *
 * The Msg91/Twilio adapters exist for FUTURE extensibility (e.g. a "cloud
 * fallback when no gateway device is online" policy). The current OtpService
 * gateway dispatch uses OTPSenderGatewayProvider; the cloud providers keep
 * running through the existing OtpDeliveryProvider race untouched.
 */
package com.littlebridge.vidyaprayag.feature.auth.delivery.sms

import java.util.UUID

/**
 * One SMS transport. Implementations should be stateless `object`s holding
 * only env-derived config + references to shared helpers (repository, FCM
 * client) — never per-request state.
 */
interface SmsProvider {

    /** Stable name for logs + the `otp_sms_requests.provider` column. */
    val name: String

    /**
     * True iff this transport has the static config it needs to even attempt
     * a send (env creds, SDK initialisation). This MUST NOT perform DB I/O —
     * it's called from non-suspend contexts. Per-request availability (e.g.
     * "is a gateway device online right now?") is resolved inside [send].
     */
    fun isConfigured(): Boolean

    /**
     * Attempt to deliver [request]. Must NOT throw — wrap every failure in
     * [SmsSendResult.Failed]. Implementations own the `otp_sms_requests` row
     * transition (PENDING -> PROCESSING on dispatch, SENT/FAILED on outcome).
     */
    suspend fun send(request: SmsSendRequest): SmsSendResult
}

/**
 * Everything a transport needs to send one SMS. The `requestId` is the
 * `otp_sms_requests.id` so the provider can update that row in place.
 */
data class SmsSendRequest(
    val requestId: UUID,
    /** Destination in E.164 (`+919876543210`). */
    val phoneNumber: String,
    /** Fully-rendered SMS body — already locale-aware, already DLT-safe. */
    val message: String,
)

/**
 * Outcome of one transport's attempt.
 *
 *  - Dispatched: pushed to a gateway / accepted by a cloud provider. The
 *    row is now PROCESSING; final SENT/FAILED arrives asynchronously via
 *    the gateway status API (gateway path) or a provider callback.
 *  - Queued:     no transport was available right now (e.g. zero online
 *    gateway devices). The row stays PENDING and is recoverable via
 *    GET /api/v1/gateway/pending. NOT a failure — the gateway may come
 *    back online and drain it.
 *  - Failed:     transport rejected the request or errored. The row is
 *    marked FAILED with the reason.
 */
sealed class SmsSendResult {
    data class Dispatched(
        val providerName: String,
        val providerMessageId: String? = null,
    ) : SmsSendResult()

    data class Queued(
        val providerName: String,
        val reason: String,
    ) : SmsSendResult()

    data class Failed(
        val providerName: String,
        val reason: String,
    ) : SmsSendResult()
}
