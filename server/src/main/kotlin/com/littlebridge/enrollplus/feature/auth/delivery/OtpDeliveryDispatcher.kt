/*
 * File: OtpDeliveryDispatcher.kt
 * Module: feature.auth.delivery
 *
 * The brain of the OTP delivery layer.
 *
 * Responsibilities
 * ----------------
 *  1. Resolve the ordered list of providers to try based on:
 *       a) the identifier type (phone vs email)
 *       b) the desired channel order (OTP_CHANNEL_ORDER env)
 *       c) the OTP_PROVIDER env "pin" — if set, skip the chain and use
 *          ONLY that one provider (useful for forcing a specific gateway
 *          during incident response, or during canary testing)
 *  2. Iterate the chain, stopping at the first Sent.
 *  3. Return a DispatchOutcome that aggregates every attempt so the
 *     OtpService can persist the audit trail.
 *
 * The dispatcher has NO knowledge of the auth_otps DB schema — it is
 * a pure side-effecting function from request → outcome. This keeps it
 * testable in isolation (drop in fake providers, assert the chain).
 *
 * Env vars
 * --------
 *   OTP_PROVIDER          When non-blank and != "auto"/"chain", PINS a
 *                         specific provider (e.g. "fast2sms"). Useful for
 *                         forcing a vendor during a live incident.
 *   OTP_CHANNEL_ORDER     CSV of channels in order of preference, e.g.
 *                           "whatsapp,sms,email"
 *                         Default: "sms,whatsapp,email"
 *                         Phone identifiers iterate only SMS+WhatsApp
 *                         providers; email identifiers only EMAIL.
 *   OTP_PROVIDER_ORDER    (advanced) Hard-pinned provider order, CSV.
 *                         When set, overrides the channel-based ordering
 *                         and uses these provider names verbatim.
 *                         Example: "whatsapp_cloud,fast2sms,msg91,smtp,console"
 */
package com.littlebridge.enrollplus.feature.auth.delivery

import com.littlebridge.enrollplus.feature.auth.delivery.providers.ConsoleProvider
import com.littlebridge.enrollplus.feature.auth.delivery.providers.Fast2SmsProvider
import com.littlebridge.enrollplus.feature.auth.delivery.providers.Msg91Provider
import com.littlebridge.enrollplus.feature.auth.delivery.providers.SmtpEmailProvider
import com.littlebridge.enrollplus.feature.auth.delivery.providers.TwilioProvider
import com.littlebridge.enrollplus.feature.auth.delivery.providers.WhatsAppCloudProvider
import org.slf4j.LoggerFactory

object OtpDeliveryDispatcher {

    private val log = LoggerFactory.getLogger("OtpDeliveryDispatcher")

    /** ALL known providers, in default preference order (cheapest first). */
    val knownProviders: List<OtpProvider> = listOf(
        WhatsAppCloudProvider,  // free up to 1k/mo
        Fast2SmsProvider,       // ~₹0.15-0.25 (India SMS)
        Msg91Provider,          // ~₹0.18-0.22 (India SMS, best DLT)
        TwilioProvider,         // ₹0.40+ (intl SMS / WhatsApp)
        SmtpEmailProvider,      // free (email only)
        ConsoleProvider,        // dev fallback
    )

    /**
     * Run the chain. Returns a [DispatchOutcome] describing every attempt.
     */
    suspend fun dispatch(request: OtpDeliveryRequest): DispatchOutcome {
        val chain = buildProviderChain(request)
        val attempts = mutableListOf<OtpDeliveryResult>()

        if (chain.isEmpty()) {
            log.error(
                "[OtpDispatcher] no providers available for identifierType={} — " +
                    "check OTP_PROVIDER / OTP_CHANNEL_ORDER / provider env vars",
                request.identifierType,
            )
            return DispatchOutcome(
                ok = false,
                winningProvider = null,
                winningChannel = null,
                providerMessageId = null,
                failureReason = "no providers configured for ${request.identifierType}",
                attempts = emptyList(),
            )
        }

        for (provider in chain) {
            if (!provider.isConfigured()) {
                attempts += OtpDeliveryResult.Skipped(
                    provider.name, provider.channel, "not configured"
                )
                continue
            }
            val result = runCatching { provider.send(request) }
                .getOrElse {
                    OtpDeliveryResult.Failed(
                        providerName = provider.name,
                        channel = provider.channel,
                        reason = "uncaught: ${it.javaClass.simpleName}: ${it.message?.take(120) ?: ""}",
                        latencyMillis = 0,
                    )
                }
            attempts += result
            if (result is OtpDeliveryResult.Sent) {
                return DispatchOutcome(
                    ok = true,
                    winningProvider = result.providerName,
                    winningChannel = result.channel,
                    providerMessageId = result.providerMessageId,
                    failureReason = null,
                    attempts = attempts,
                )
            }
        }

        val reason = attempts
            .filterIsInstance<OtpDeliveryResult.Failed>()
            .joinToString("; ") { "${it.providerName}=${it.reason}" }
            .ifBlank { "all providers skipped (none configured)" }
        log.warn("[OtpDispatcher] all {} providers failed: {}", chain.size, reason)
        return DispatchOutcome(
            ok = false,
            winningProvider = null,
            winningChannel = null,
            providerMessageId = null,
            failureReason = reason,
            attempts = attempts,
        )
    }

    /** Build the per-request chain honouring all env-driven overrides. */
    internal fun buildProviderChain(request: OtpDeliveryRequest): List<OtpProvider> {
        // ---- Highest priority: hard pin via OTP_PROVIDER ----
        val pinned = OtpEnv.get("OTP_PROVIDER")?.lowercase()
        if (!pinned.isNullOrBlank() && pinned !in setOf("auto", "chain", "default")) {
            knownProviders.firstOrNull { it.name.equals(pinned, ignoreCase = true) }
                ?.let { return listOf(it) }
            log.warn("[OtpDispatcher] OTP_PROVIDER='{}' not recognised; using auto chain", pinned)
        }

        // ---- Second priority: explicit provider order ----
        val providerOrder = OtpEnv.getList("OTP_PROVIDER_ORDER", emptyList())
        if (providerOrder.isNotEmpty()) {
            val byName = knownProviders.associateBy { it.name.lowercase() }
            return providerOrder.mapNotNull { byName[it.lowercase()] }
                .filter { providerCanServe(it, request) }
        }

        // ---- Default: channel-based ordering ----
        val channelOrder = OtpEnv.getList(
            "OTP_CHANNEL_ORDER",
            // Default: SMS first because it's the most universally reachable
            // channel for India parents.  WhatsApp is great but requires the
            // user to have it installed; not 100% reliable in tier-3 towns.
            listOf("sms", "whatsapp", "email"),
        ).mapNotNull { OtpChannel.parse(it) }

        val ordered = mutableListOf<OtpProvider>()
        for (ch in channelOrder) {
            knownProviders
                .filter { it.channel == ch && providerCanServe(it, request) }
                .forEach { if (it !in ordered) ordered += it }
        }
        // Always tack the Console fallback at the very end (it gates itself).
        if (ConsoleProvider !in ordered && providerCanServe(ConsoleProvider, request)) {
            ordered += ConsoleProvider
        }
        return ordered
    }

    /**
     * Filter that drops providers physically incapable of serving this
     * request type (e.g. SMTP for a phone identifier). Cheaper than letting
     * the provider Skip itself because it shortens the audit trail.
     */
    private fun providerCanServe(p: OtpProvider, req: OtpDeliveryRequest): Boolean {
        val isEmail = req.identifierType == "email"
        return when (p.channel) {
            OtpChannel.EMAIL -> isEmail
            OtpChannel.SMS, OtpChannel.WHATSAPP, OtpChannel.VOICE -> !isEmail
            OtpChannel.CONSOLE -> true  // Console serves anything
        }
    }

    /**
     * Diagnostic snapshot — used by the /api/v1/admin/otp-diagnostic endpoint
     * so ops can confirm which providers are wired up without reading env.
     */
    fun snapshot(): Map<String, Any> {
        return mapOf(
            "known_providers" to knownProviders.map {
                mapOf(
                    "name" to it.name,
                    "channel" to it.channel.wireName,
                    "configured" to it.isConfigured(),
                )
            },
            "channel_order_default" to OtpEnv.getList(
                "OTP_CHANNEL_ORDER", listOf("sms", "whatsapp", "email")
            ),
            "provider_order_override" to OtpEnv.getList("OTP_PROVIDER_ORDER", emptyList()),
            "pinned_provider" to (OtpEnv.get("OTP_PROVIDER") ?: ""),
            "console_fallback_enabled" to OtpEnv.getBool("OTP_ENABLE_CONSOLE_FALLBACK", true),
            "dev_return_code" to OtpEnv.getBool("OTP_DEV_RETURN_CODE", false),
        )
    }
}
