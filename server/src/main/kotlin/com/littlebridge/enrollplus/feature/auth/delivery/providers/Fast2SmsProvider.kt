/*
 * File: Fast2SmsProvider.kt
 * Module: feature.auth.delivery.providers
 *
 * Fast2SMS — India SMS gateway. Very popular for OTP traffic because:
 *   • Sub-rupee pricing (₹0.15–0.25 per OTP segment with DLT route)
 *   • Free wallet credit on signup (good for early dev / smoke tests)
 *   • DLT-compliant "OTP route" returns OTP through the user's mobile
 *     even if their phone is blocking promotional SMS
 *
 * Two routes are supported here, both surfaced by env:
 *
 *   FAST2SMS_ROUTE = "otp"  (default — cheapest, only digits, India-only)
 *      POST https://www.fast2sms.com/dev/bulkV2
 *      body: route=otp&variables_values=<code>&numbers=<10digits>
 *      No DLT template needed for the OTP route; Fast2SMS owns the body.
 *
 *   FAST2SMS_ROUTE = "dlt"  (DLT-approved transactional/promotional)
 *      POST https://www.fast2sms.com/dev/bulkV2
 *      body: route=dlt&sender_id=<id>&message=<dlt_msg_id>&variables_values=<code>&numbers=<10digits>
 *
 * Env vars
 * --------
 *   FAST2SMS_API_KEY            (required)  authorisation header
 *   FAST2SMS_ROUTE              "otp" | "dlt"   default "otp"
 *   FAST2SMS_SENDER_ID          (required for dlt) 6-char sender id
 *   FAST2SMS_DLT_TEMPLATE_ID    (required for dlt) numeric template id
 *
 * Phone normalisation
 * -------------------
 * Fast2SMS expects 10-digit Indian numbers (no country code, no +).
 * We strip the leading "+91" defensively. Non-+91 numbers are skipped
 * with a clear reason so the dispatcher falls through to the next
 * provider (e.g. Twilio for international).
 */
package com.littlebridge.enrollplus.feature.auth.delivery.providers

import com.littlebridge.enrollplus.feature.auth.delivery.OtpChannel
import com.littlebridge.enrollplus.feature.auth.delivery.OtpDeliveryRequest
import com.littlebridge.enrollplus.feature.auth.delivery.OtpDeliveryResult
import com.littlebridge.enrollplus.feature.auth.delivery.OtpEnv
import com.littlebridge.enrollplus.feature.auth.delivery.OtpHttpClient
import com.littlebridge.enrollplus.feature.auth.delivery.OtpProvider
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import org.slf4j.LoggerFactory

object Fast2SmsProvider : OtpProvider {

    private val log = LoggerFactory.getLogger("OtpProvider/Fast2SMS")
    private const val ENDPOINT = "https://www.fast2sms.com/dev/bulkV2"

    override val name: String = "fast2sms"
    override val channel: OtpChannel = OtpChannel.SMS

    override fun isConfigured(): Boolean = OtpEnv.allSet("FAST2SMS_API_KEY")

    override suspend fun send(request: OtpDeliveryRequest): OtpDeliveryResult {
        val started = System.currentTimeMillis()

        // Channel guard — never try to send SMS to an email address.
        if (request.identifierType != "phone") {
            return OtpDeliveryResult.Skipped(name, channel, "non-phone identifier")
        }

        val tenDigit = toIndianTenDigits(request.identifier)
            ?: return OtpDeliveryResult.Skipped(
                name, channel,
                "Fast2SMS only supports 10-digit Indian numbers — got ${request.identifier}"
            )

        val apiKey = OtpEnv.get("FAST2SMS_API_KEY")!!
        val route = OtpEnv.get("FAST2SMS_ROUTE", "otp").lowercase()

        val form: Parameters = when (route) {
            "dlt" -> {
                val senderId = OtpEnv.get("FAST2SMS_SENDER_ID")
                val tmplId = OtpEnv.get("FAST2SMS_DLT_TEMPLATE_ID")
                if (senderId == null || tmplId == null) {
                    return OtpDeliveryResult.Skipped(
                        name, channel,
                        "FAST2SMS_ROUTE=dlt but FAST2SMS_SENDER_ID / FAST2SMS_DLT_TEMPLATE_ID not set"
                    )
                }
                Parameters.build {
                    append("route", "dlt")
                    append("sender_id", senderId)
                    append("message", tmplId)
                    append("variables_values", request.code)
                    append("numbers", tenDigit)
                    append("flash", "0")
                }
            }
            else -> Parameters.build {
                // The "otp" route: variables_values is just the 4-6 digit code,
                // Fast2SMS substitutes it into "Your OTP is {#var#}, valid 10 min."
                append("route", "otp")
                append("variables_values", request.code)
                append("numbers", tenDigit)
            }
        }

        return try {
            val response = OtpHttpClient.client.post(ENDPOINT) {
                headers {
                    append("authorization", apiKey)
                    append("accept", "application/json")
                }
                setBody(FormDataContent(form))
            }
            val status = response.status.value
            val text = runCatching { response.bodyAsText() }.getOrElse { "<no body>" }
            val latency = System.currentTimeMillis() - started

            // Fast2SMS always returns 200 with `return: true/false` in the JSON body.
            val looksOk = status in 200..299 &&
                text.contains("\"return\":true", ignoreCase = true)

            if (looksOk) {
                val requestId = extractField(text, "request_id")
                log.info(
                    "[OTP/Fast2SMS] OK identifier=*****{} route={} request_id={} latency={}ms",
                    tenDigit.takeLast(2), route, requestId, latency,
                )
                OtpDeliveryResult.Sent(
                    providerName = name,
                    channel = channel,
                    providerMessageId = requestId,
                    latencyMillis = latency,
                    rawResponseSummary = trimSummary(text),
                )
            } else {
                log.warn(
                    "[OTP/Fast2SMS] FAIL status={} body={} latency={}ms",
                    status, trimSummary(text), latency,
                )
                OtpDeliveryResult.Failed(
                    providerName = name,
                    channel = channel,
                    reason = "Fast2SMS returned non-success",
                    latencyMillis = latency,
                    httpStatus = status,
                    rawResponseSummary = trimSummary(text),
                )
            }
        } catch (t: Throwable) {
            val latency = System.currentTimeMillis() - started
            log.warn("[OTP/Fast2SMS] EXC latency={}ms err={}", latency, t.javaClass.simpleName)
            OtpDeliveryResult.Failed(
                providerName = name,
                channel = channel,
                reason = "exception: ${t.javaClass.simpleName}: ${t.message?.take(120) ?: ""}",
                latencyMillis = latency,
            )
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** Convert any of [+91XXXXXXXXXX, 91XXXXXXXXXX, XXXXXXXXXX] → 10-digit. */
    private fun toIndianTenDigits(e164: String): String? {
        val digits = e164.filter { it.isDigit() }
        return when {
            digits.length == 10 -> digits
            digits.length == 12 && digits.startsWith("91") -> digits.drop(2)
            digits.length == 11 && digits.startsWith("0") -> digits.drop(1)
            else -> null
        }
    }

    /** Naïve JSON field extractor — Fast2SMS payloads are tiny and stable. */
    private fun extractField(json: String, field: String): String? {
        val re = Regex("\"$field\"\\s*:\\s*\"?([^\",}\\]]+)\"?")
        return re.find(json)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun trimSummary(s: String): String =
        s.replace(Regex("\\s+"), " ").take(240)
}
