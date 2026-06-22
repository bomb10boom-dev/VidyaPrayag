/*
 * File: Msg91Provider.kt
 * Module: feature.auth.delivery.providers
 *
 * MSG91 — India SMS gateway with the cleanest DLT compliance story.
 * Best-known pricing (May 2026): ₹0.18–0.22/SMS on the OTP route after
 * the free 100 SMS / 30 days that comes with a new account.
 *
 * Uses MSG91's "Flow" API — the gateway-managed template engine. You
 * register the template in MSG91 dashboard, get a Flow ID, then we POST
 * {flow_id, mobiles, OTP} and MSG91 plugs the OTP into the approved
 * template body server-side. This is the only legal way to send OTP SMS
 * in India under TRAI 2018 DLT regulations.
 *
 *   POST https://api.msg91.com/api/v5/flow/
 *   Headers: authkey=<MSG91_AUTH_KEY>, content-type=application/json
 *   Body:   {"template_id":"<FLOW_ID>", "short_url":"0",
 *            "recipients":[{"mobiles":"91XXXXXXXXXX","OTP":"123456"}]}
 *
 * Env vars
 * --------
 *   MSG91_AUTH_KEY        (required)   account auth key
 *   MSG91_FLOW_ID         (required)   the approved DLT template flow id
 *   MSG91_OTP_VAR_NAME    optional     default "OTP" — must match the
 *                                       variable name in your template
 *   MSG91_SENDER_ID       optional     6-char DLT sender id (only used
 *                                       if your flow doesn't embed it)
 */
package com.littlebridge.enrollplus.feature.auth.delivery.providers

import com.littlebridge.enrollplus.feature.auth.delivery.OtpChannel
import com.littlebridge.enrollplus.feature.auth.delivery.OtpDeliveryRequest
import com.littlebridge.enrollplus.feature.auth.delivery.OtpDeliveryResult
import com.littlebridge.enrollplus.feature.auth.delivery.OtpEnv
import com.littlebridge.enrollplus.feature.auth.delivery.OtpHttpClient
import com.littlebridge.enrollplus.feature.auth.delivery.OtpProvider
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.slf4j.LoggerFactory

object Msg91Provider : OtpProvider {

    private val log = LoggerFactory.getLogger("OtpProvider/MSG91")
    private const val ENDPOINT = "https://api.msg91.com/api/v5/flow/"

    override val name: String = "msg91"
    override val channel: OtpChannel = OtpChannel.SMS

    override fun isConfigured(): Boolean =
        OtpEnv.allSet("MSG91_AUTH_KEY", "MSG91_FLOW_ID")

    override suspend fun send(request: OtpDeliveryRequest): OtpDeliveryResult {
        val started = System.currentTimeMillis()

        if (request.identifierType != "phone") {
            return OtpDeliveryResult.Skipped(name, channel, "non-phone identifier")
        }

        val msg91Mobile = toMsg91Format(request.identifier)
            ?: return OtpDeliveryResult.Skipped(
                name, channel,
                "MSG91 requires country-code-prefixed digits (got ${request.identifier})"
            )

        val authKey = OtpEnv.get("MSG91_AUTH_KEY")!!
        val flowId = OtpEnv.get("MSG91_FLOW_ID")!!
        val otpVar = OtpEnv.get("MSG91_OTP_VAR_NAME", "OTP")
        val senderId = OtpEnv.get("MSG91_SENDER_ID")

        val payload: JsonObject = buildJsonObject {
            put("template_id", flowId)
            put("short_url", "0")
            if (senderId != null) put("sender", senderId)
            putJsonArray("recipients") {
                add(
                    buildJsonObject {
                        put("mobiles", msg91Mobile)
                        put(otpVar, request.code)
                    }
                )
            }
        }

        return try {
            val response = OtpHttpClient.client.post(ENDPOINT) {
                contentType(ContentType.Application.Json)
                headers {
                    append("authkey", authKey)
                    append("accept", "application/json")
                }
                setBody(payload)
            }
            val status = response.status.value
            val text = runCatching { response.bodyAsText() }.getOrElse { "<no body>" }
            val latency = System.currentTimeMillis() - started
            val okType = text.contains("\"type\":\"success\"", ignoreCase = true)
            if (status in 200..299 && okType) {
                val reqId = extractField(text, "request_id") ?: extractField(text, "message")
                log.info(
                    "[OTP/MSG91] OK mobile=*****{} flow={} request_id={} latency={}ms",
                    msg91Mobile.takeLast(2), flowId, reqId, latency,
                )
                OtpDeliveryResult.Sent(
                    providerName = name,
                    channel = channel,
                    providerMessageId = reqId,
                    latencyMillis = latency,
                    rawResponseSummary = trim(text),
                )
            } else {
                log.warn("[OTP/MSG91] FAIL status={} body={} latency={}ms", status, trim(text), latency)
                OtpDeliveryResult.Failed(
                    providerName = name,
                    channel = channel,
                    reason = "MSG91 returned non-success",
                    latencyMillis = latency,
                    httpStatus = status,
                    rawResponseSummary = trim(text),
                )
            }
        } catch (t: Throwable) {
            val latency = System.currentTimeMillis() - started
            log.warn("[OTP/MSG91] EXC latency={}ms err={}", latency, t.javaClass.simpleName)
            OtpDeliveryResult.Failed(
                providerName = name,
                channel = channel,
                reason = "exception: ${t.javaClass.simpleName}: ${t.message?.take(120) ?: ""}",
                latencyMillis = latency,
            )
        }
    }

    /** MSG91 wants country-code-prefixed digits (no '+'). +919876543210 → 919876543210. */
    private fun toMsg91Format(e164: String): String? {
        val digits = e164.filter { it.isDigit() }
        return when {
            digits.length in 11..15 -> digits         // already includes country code
            digits.length == 10     -> "91$digits"    // assume India
            else                    -> null
        }
    }

    private fun extractField(json: String, field: String): String? {
        val re = Regex("\"$field\"\\s*:\\s*\"?([^\",}\\]]+)\"?")
        return re.find(json)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun trim(s: String): String = s.replace(Regex("\\s+"), " ").take(240)
}
