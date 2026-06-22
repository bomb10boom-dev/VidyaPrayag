/*
 * File: TwilioProvider.kt
 * Module: feature.auth.delivery.providers
 *
 * Twilio Programmable Messaging — used as the *international* fallback
 * after Fast2SMS / MSG91 (which are India-only). Twilio also unlocks
 * WhatsApp messaging through the same API surface, so this provider
 * supports BOTH channels driven by env flag `TWILIO_CHANNEL`.
 *
 * Endpoint
 *   POST https://api.twilio.com/2010-04-01/Accounts/{SID}/Messages.json
 *   HTTP Basic auth: SID : AUTH_TOKEN
 *   form: From=<from>, To=<to>, Body=<msg>
 *
 * Env vars
 * --------
 *   TWILIO_ACCOUNT_SID    (required)
 *   TWILIO_AUTH_TOKEN     (required)
 *   TWILIO_FROM           (required)   "+15551234567" for SMS,
 *                                       or "whatsapp:+14155238886" for WA
 *   TWILIO_CHANNEL        "sms" | "whatsapp"  default "sms"
 *
 * Pricing reality check
 * ---------------------
 * Twilio SMS to India is ~₹0.40-0.55/SMS plus carrier fees; WhatsApp
 * sandbox is free for dev but the production Business API has a
 * Conversation Fee (~₹0.30-0.50). Keep Twilio LAST in the chain so we
 * spend the cheap providers' budgets first.
 */
package com.littlebridge.enrollplus.feature.auth.delivery.providers

import com.littlebridge.enrollplus.feature.auth.delivery.OtpChannel
import com.littlebridge.enrollplus.feature.auth.delivery.OtpDeliveryRequest
import com.littlebridge.enrollplus.feature.auth.delivery.OtpDeliveryResult
import com.littlebridge.enrollplus.feature.auth.delivery.OtpEnv
import com.littlebridge.enrollplus.feature.auth.delivery.OtpHttpClient
import com.littlebridge.enrollplus.feature.auth.delivery.OtpMessageTemplates
import com.littlebridge.enrollplus.feature.auth.delivery.OtpProvider
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import org.slf4j.LoggerFactory
import java.util.Base64

object TwilioProvider : OtpProvider {

    private val log = LoggerFactory.getLogger("OtpProvider/Twilio")

    override val name: String = "twilio"

    override val channel: OtpChannel
        get() = when (OtpEnv.get("TWILIO_CHANNEL", "sms").lowercase()) {
            "whatsapp", "wa" -> OtpChannel.WHATSAPP
            else -> OtpChannel.SMS
        }

    override fun isConfigured(): Boolean =
        OtpEnv.allSet("TWILIO_ACCOUNT_SID", "TWILIO_AUTH_TOKEN", "TWILIO_FROM")

    override suspend fun send(request: OtpDeliveryRequest): OtpDeliveryResult {
        val started = System.currentTimeMillis()

        if (request.identifierType != "phone") {
            return OtpDeliveryResult.Skipped(name, channel, "non-phone identifier")
        }

        val sid = OtpEnv.get("TWILIO_ACCOUNT_SID")!!
        val token = OtpEnv.get("TWILIO_AUTH_TOKEN")!!
        val from = OtpEnv.get("TWILIO_FROM")!!
        val endpoint = "https://api.twilio.com/2010-04-01/Accounts/$sid/Messages.json"

        val to = when (channel) {
            OtpChannel.WHATSAPP -> "whatsapp:${ensurePlus(request.identifier)}"
            else -> ensurePlus(request.identifier)
        }

        val body = when (channel) {
            OtpChannel.WHATSAPP -> OtpMessageTemplates.whatsappBody(
                request.code, request.ttlMinutes, request.purpose, request.locale,
            )
            else -> OtpMessageTemplates.smsBody(request.code, request.ttlMinutes, request.locale)
        }

        val form = Parameters.build {
            append("From", from)
            append("To", to)
            append("Body", body)
        }

        val basic = Base64.getEncoder()
            .encodeToString("$sid:$token".toByteArray(Charsets.UTF_8))

        return try {
            val response = OtpHttpClient.client.post(endpoint) {
                headers {
                    append("authorization", "Basic $basic")
                    append("accept", "application/json")
                }
                setBody(FormDataContent(form))
            }
            val status = response.status.value
            val text = runCatching { response.bodyAsText() }.getOrElse { "<no body>" }
            val latency = System.currentTimeMillis() - started
            if (status in 200..299) {
                val sid2 = extractField(text, "sid")
                log.info(
                    "[OTP/Twilio] OK channel={} to=*****{} sid={} latency={}ms",
                    channel.wireName, request.identifier.takeLast(2), sid2, latency,
                )
                OtpDeliveryResult.Sent(
                    providerName = name,
                    channel = channel,
                    providerMessageId = sid2,
                    latencyMillis = latency,
                    rawResponseSummary = trim(text),
                )
            } else {
                log.warn("[OTP/Twilio] FAIL status={} body={} latency={}ms", status, trim(text), latency)
                OtpDeliveryResult.Failed(
                    providerName = name,
                    channel = channel,
                    reason = "Twilio returned $status",
                    latencyMillis = latency,
                    httpStatus = status,
                    rawResponseSummary = trim(text),
                )
            }
        } catch (t: Throwable) {
            val latency = System.currentTimeMillis() - started
            log.warn("[OTP/Twilio] EXC latency={}ms err={}", latency, t.javaClass.simpleName)
            OtpDeliveryResult.Failed(
                providerName = name,
                channel = channel,
                reason = "exception: ${t.javaClass.simpleName}: ${t.message?.take(120) ?: ""}",
                latencyMillis = latency,
            )
        }
    }

    private fun ensurePlus(num: String): String =
        if (num.startsWith("+")) num else "+$num"

    private fun extractField(json: String, field: String): String? {
        val re = Regex("\"$field\"\\s*:\\s*\"?([^\",}\\]]+)\"?")
        return re.find(json)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun trim(s: String) = s.replace(Regex("\\s+"), " ").take(240)
}
