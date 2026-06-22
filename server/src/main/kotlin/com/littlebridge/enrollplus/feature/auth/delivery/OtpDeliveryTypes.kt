/*
 * File: OtpDeliveryTypes.kt
 * Module: feature.auth.delivery
 *
 * Common type definitions for the pluggable OTP-delivery layer.
 *
 *  - OtpChannel       : "sms" | "whatsapp" | "email" | "voice" | "console"
 *  - OtpDeliveryRequest : everything a provider needs to send one OTP
 *  - OtpDeliveryResult  : Sent | Skipped | Failed (with reason + provider id)
 *  - OtpProvider        : the contract every concrete provider implements
 *
 * The actual concrete providers (Fast2SMS, MSG91, Twilio, WhatsApp Cloud,
 * SMTP, Console) live in sibling files inside this package and are wired
 * up in `OtpDeliveryDispatcher`. They are completely free to come and go
 * — adding a new SMS gateway is just:
 *
 *   1) Drop in a new `SomeProvider.kt` that implements `OtpProvider`.
 *   2) Add it to `OtpDeliveryDispatcher.buildProviderChain()`.
 *   3) Document its env vars in `.env.example`.
 *
 * No other file needs to change. No interfaces are leaked outside
 * `feature.auth.delivery` so we keep the public OTP API surface tiny.
 */
package com.littlebridge.enrollplus.feature.auth.delivery

/**
 * Logical channel an OTP travels over.
 *
 * NOTE: this is _independent_ of the provider name. e.g. MSG91 can send
 * over SMS OR WhatsApp; Twilio same. We keep the two concepts separate so
 * the call site can say "I'd like this OTP over WhatsApp if you can,
 * otherwise SMS" without caring which vendor does it.
 */
enum class OtpChannel(val wireName: String) {
    SMS("sms"),
    WHATSAPP("whatsapp"),
    EMAIL("email"),
    VOICE("voice"),
    CONSOLE("console");

    companion object {
        fun parse(raw: String?): OtpChannel? =
            entries.firstOrNull { it.wireName.equals(raw?.trim(), ignoreCase = true) }
    }
}

/**
 * Everything a provider needs to send one OTP. Immutable on purpose so the
 * dispatcher can hand the same request to multiple providers in a fallback
 * chain without one provider mutating fields the next one will read.
 */
data class OtpDeliveryRequest(
    /** Plain destination — phone in E.164 (`+919876543210`) or email. */
    val identifier: String,

    /** "phone" or "email". Validated upstream by [normaliseIdentifier]. */
    val identifierType: String,

    /** The 6-digit numeric code, plain. Only lives in memory, never logged. */
    val code: String,

    /** What this OTP is for — purely informational, used in templates. */
    val purpose: String,

    /**
     * Locale hint, defaults to "en". Frontend can pass "hi", "mr", etc.
     * Providers that support localized templates (MSG91 OTP route) use this.
     */
    val locale: String = "en",

    /** OTP lifetime in minutes — surfaced in the user-facing message body. */
    val ttlMinutes: Long = 10,
)

/**
 * Result of ONE provider's attempt. The dispatcher records every attempt
 * to `otp_delivery_attempts` regardless of outcome (full forensic trail).
 */
sealed class OtpDeliveryResult {

    /** Provider accepted the message. */
    data class Sent(
        val providerName: String,
        val channel: OtpChannel,
        val providerMessageId: String?,
        val latencyMillis: Long,
        val rawResponseSummary: String? = null,
    ) : OtpDeliveryResult()

    /** Provider isn't configured (missing creds) — try the next one. */
    data class Skipped(
        val providerName: String,
        val channel: OtpChannel,
        val reason: String,
    ) : OtpDeliveryResult()

    /** Provider was tried but failed (4xx/5xx, timeout, etc.). */
    data class Failed(
        val providerName: String,
        val channel: OtpChannel,
        val reason: String,
        val latencyMillis: Long,
        val httpStatus: Int? = null,
        val rawResponseSummary: String? = null,
    ) : OtpDeliveryResult()
}

/**
 * One concrete OTP delivery vendor. Stateless — implementations should be
 * `object`s or singletons, holding only env-derived config.
 */
interface OtpProvider {

    /** Stable name for logs + audit table: "fast2sms", "msg91_sms", etc. */
    val name: String

    /** Channel this provider delivers over. */
    val channel: OtpChannel

    /**
     * True iff this provider has all the env vars it needs to attempt a
     * send. The dispatcher uses this to short-circuit unconfigured
     * providers without an HTTP round-trip.
     */
    fun isConfigured(): Boolean

    /** Actually try to send. Must NOT throw — wrap errors in [OtpDeliveryResult.Failed]. */
    suspend fun send(request: OtpDeliveryRequest): OtpDeliveryResult
}

/**
 * Public output of the whole dispatcher — what the OtpService stores
 * back into the `auth_otps` row.
 */
data class DispatchOutcome(
    /** True iff at least one provider returned [OtpDeliveryResult.Sent]. */
    val ok: Boolean,
    val winningProvider: String?,
    val winningChannel: OtpChannel?,
    val providerMessageId: String?,
    /** Human-readable reason if `ok == false` (joined Failed reasons). */
    val failureReason: String?,
    /** All individual attempts, oldest first. Persisted for audit. */
    val attempts: List<OtpDeliveryResult>,
)
