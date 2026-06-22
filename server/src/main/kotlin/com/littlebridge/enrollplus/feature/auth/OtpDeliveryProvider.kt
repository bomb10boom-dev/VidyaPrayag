/*
 * File: OtpDeliveryProvider.kt
 * Module: feature.auth
 *
 * THIN FACADE in front of the new multi-provider delivery layer.
 *
 *   feature.auth.OtpDeliveryProvider          ← this file (legacy entry point)
 *           │
 *           ▼  delegates to
 *   feature.auth.delivery.OtpDeliveryDispatcher
 *           │
 *           ▼  iterates
 *   ConsoleProvider / Fast2SmsProvider / Msg91Provider /
 *   TwilioProvider / WhatsAppCloudProvider / SmtpEmailProvider
 *
 * Why a facade?  The existing OtpService.kt calls
 * `OtpDeliveryProvider.deliver(...)` and expects a simple `DeliveryOutcome`
 * back. We keep that surface stable so a) nothing else has to change in
 * the auth feature and b) we can still inject test doubles by overriding
 * just this object.
 *
 * The `attempts` list on the new `DeliveryOutcome` is the full audit
 * trail that OtpService persists to `otp_delivery_attempts` for forensics.
 *
 * Configuration is fully env-driven — see .env.example for the full
 * list of provider knobs (FAST2SMS_*, MSG91_*, TWILIO_*, WHATSAPP_*,
 * SMTP_*, OTP_PROVIDER, OTP_CHANNEL_ORDER, OTP_PROVIDER_ORDER).
 */
package com.littlebridge.enrollplus.feature.auth

import com.littlebridge.enrollplus.feature.auth.delivery.OtpDeliveryDispatcher
import com.littlebridge.enrollplus.feature.auth.delivery.OtpDeliveryRequest
import com.littlebridge.enrollplus.feature.auth.delivery.OtpDeliveryResult

/**
 * Aggregated outcome the OtpService consumes. Backwards-compatible with the
 * pre-multi-provider version: `ok` / `providerMessageId` / `reason` are
 * still here. New audit fields are additive so existing call sites compile
 * unchanged.
 */
data class DeliveryOutcome(
    val ok: Boolean,
    val providerMessageId: String? = null,
    val reason: String? = null,
    /** Vendor that succeeded (e.g. "fast2sms"). Null on failure. */
    val providerName: String? = null,
    /** "sms" | "whatsapp" | "email" | "voice" | "console". Null on failure. */
    val channelWire: String? = null,
    /** Full attempt log — every Sent/Failed/Skipped, oldest first. */
    val attempts: List<DeliveryAttemptRecord> = emptyList(),
)

/** One row's worth of audit data — persisted by OtpService. */
data class DeliveryAttemptRecord(
    val providerName: String,
    val channelWire: String,
    val status: String,            // "sent" | "failed" | "skipped"
    val providerMessageId: String?,
    val httpStatus: Int?,
    val latencyMillis: Long,
    val reason: String?,
    val rawResponseSummary: String?,
)

object OtpDeliveryProvider {

    suspend fun deliver(
        identifier: String,
        identifierType: String,
        code: String,
        purpose: String,
        locale: String = "en",
        ttlMinutes: Long = 10,
    ): DeliveryOutcome {
        val outcome = OtpDeliveryDispatcher.dispatch(
            OtpDeliveryRequest(
                identifier = identifier,
                identifierType = identifierType,
                code = code,
                purpose = purpose,
                locale = locale,
                ttlMinutes = ttlMinutes,
            )
        )
        return DeliveryOutcome(
            ok = outcome.ok,
            providerMessageId = outcome.providerMessageId,
            reason = outcome.failureReason,
            providerName = outcome.winningProvider,
            channelWire = outcome.winningChannel?.wireName,
            attempts = outcome.attempts.map { it.toRecord() },
        )
    }
}

private fun OtpDeliveryResult.toRecord(): DeliveryAttemptRecord = when (this) {
    is OtpDeliveryResult.Sent -> DeliveryAttemptRecord(
        providerName = providerName,
        channelWire = channel.wireName,
        status = "sent",
        providerMessageId = providerMessageId,
        httpStatus = null,
        latencyMillis = latencyMillis,
        reason = null,
        rawResponseSummary = rawResponseSummary,
    )
    is OtpDeliveryResult.Failed -> DeliveryAttemptRecord(
        providerName = providerName,
        channelWire = channel.wireName,
        status = "failed",
        providerMessageId = null,
        httpStatus = httpStatus,
        latencyMillis = latencyMillis,
        reason = reason,
        rawResponseSummary = rawResponseSummary,
    )
    is OtpDeliveryResult.Skipped -> DeliveryAttemptRecord(
        providerName = providerName,
        channelWire = channel.wireName,
        status = "skipped",
        providerMessageId = null,
        httpStatus = null,
        latencyMillis = 0,
        reason = reason,
        rawResponseSummary = null,
    )
}


