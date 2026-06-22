/*
 * File: ConsoleProvider.kt
 * Module: feature.auth.delivery.providers
 *
 * "Last resort" / dev provider. Prints the OTP to the server's stdout
 * and ALWAYS succeeds.
 *
 * When does it run in production?
 *   - It SHOULD NOT. It's only added to the provider chain when
 *     OTP_ENABLE_CONSOLE_FALLBACK=true (default: true in dev, MUST be
 *     false in prod).
 *
 * Why keep it at all?
 *   - Local dev with no SMS provider configured.
 *   - CI / Postman regression tests — pair with OTP_DEV_RETURN_CODE=true.
 *   - Lets the OTP service stay green during a multi-provider outage so
 *     internal staff testing can continue (gated by an env flag).
 *
 * Privacy note
 * ------------
 * The plain code is printed ONLY by this provider. Every other provider
 * (real ones) treats the code as a secret and never logs it.
 */
package com.littlebridge.enrollplus.feature.auth.delivery.providers

import com.littlebridge.enrollplus.feature.auth.delivery.OtpChannel
import com.littlebridge.enrollplus.feature.auth.delivery.OtpDeliveryRequest
import com.littlebridge.enrollplus.feature.auth.delivery.OtpDeliveryResult
import com.littlebridge.enrollplus.feature.auth.delivery.OtpEnv
import com.littlebridge.enrollplus.feature.auth.delivery.OtpProvider
import org.slf4j.LoggerFactory

object ConsoleProvider : OtpProvider {

    private val log = LoggerFactory.getLogger("OtpProvider/Console")

    override val name: String = "console"
    override val channel: OtpChannel = OtpChannel.CONSOLE

    override fun isConfigured(): Boolean =
        OtpEnv.getBool("OTP_ENABLE_CONSOLE_FALLBACK", default = true)

    override suspend fun send(request: OtpDeliveryRequest): OtpDeliveryResult {
        val start = System.currentTimeMillis()
        // INTENTIONAL: we log the code here because this is the dev fallback.
        // Do NOT copy this pattern into any other provider.
        log.info(
            "[OTP/console] identifier={} purpose={} code={} ttl={}min",
            request.identifier, request.purpose, request.code, request.ttlMinutes,
        )
        // ALSO echo to stdout for `docker logs` simplicity.
        println(
            ">>> [OTP/console] ${request.identifier} (${request.purpose}) " +
                "→ CODE: ${request.code}  (valid ${request.ttlMinutes} min)"
        )
        return OtpDeliveryResult.Sent(
            providerName = name,
            channel = channel,
            providerMessageId = "console-${System.currentTimeMillis()}",
            latencyMillis = System.currentTimeMillis() - start,
            rawResponseSummary = "stdout",
        )
    }
}
