/*
 * File: WhatsAppCloudProvider.kt
 * Module: feature.auth.delivery.providers
 *
 * Meta (Facebook) WhatsApp Cloud API — direct, no Twilio middleman.
 * Free tier: 1,000 user-initiated conversations / month + unlimited
 * service conversations to existing customers. After that ~₹0.30/msg.
 *
 * This is the CHEAPEST channel at low scale (free), so we generally
 * place it FIRST in OTP_CHANNEL_ORDER.
 *
 * OTP messages count as "authentication" template messages — Meta
 * requires you to register an authentication template in the WhatsApp
 * Business Manager before going live. We send by template name and
 * pass the OTP as the {{1}} body variable.
 *
 *   POST https://graph.facebook.com/v17.0/{PHONE_NUMBER_ID}/messages
 *   Auth: Bearer <ACCESS_TOKEN>
 *   Body: {
 *     "messaging_product":"whatsapp",
 *     "to":"<E164 without '+'>",
 *     "type":"template",
 *     "template":{
 *       "name":"<WHATSAPP_TEMPLATE_NAME>",
 *       "language":{"code":"en"},
 *       "components":[
 *         {"type":"body","parameters":[{"type":"text","text":"<CODE>"}]},
 *         {"type":"button","sub_type":"url","index":"0",
 *          "parameters":[{"type":"text","text":"<CODE>"}]}
 *       ]
 *     }
 *   }
 *
 * Env vars
 * --------
 *   WHATSAPP_ACCESS_TOKEN     (required)   long-lived system user token
 *   WHATSAPP_PHONE_NUMBER_ID  (required)   from WA Business Manager
 *   WHATSAPP_TEMPLATE_NAME    optional     default "vidyaprayag_otp"
 *   WHATSAPP_TEMPLATE_LANG    optional     default "en"
 *   WHATSAPP_API_VERSION      optional     default "v19.0"
 *   WHATSAPP_INCLUDE_BUTTON   optional     "true" (default) | "false"
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
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory

object WhatsAppCloudProvider : OtpProvider {

    private val log = LoggerFactory.getLogger("OtpProvider/WhatsAppCloud")

    override val name: String = "whatsapp_cloud"
    override val channel: OtpChannel = OtpChannel.WHATSAPP

    override fun isConfigured(): Boolean =
        OtpEnv.allSet("WHATSAPP_ACCESS_TOKEN", "WHATSAPP_PHONE_NUMBER_ID")

    override suspend fun send(request: OtpDeliveryRequest): OtpDeliveryResult {
        val started = System.currentTimeMillis()
        if (request.identifierType != "phone") {
            return OtpDeliveryResult.Skipped(name, channel, "non-phone identifier")
        }
        val to = request.identifier.removePrefix("+")  // Cloud API expects digits-only
        val token = OtpEnv.get("WHATSAPP_ACCESS_TOKEN")!!
        val phoneId = OtpEnv.get("WHATSAPP_PHONE_NUMBER_ID")!!
        val template = OtpEnv.get("WHATSAPP_TEMPLATE_NAME", "vidyaprayag_otp")
        val lang = OtpEnv.get("WHATSAPP_TEMPLATE_LANG", "en")
        val apiVer = OtpEnv.get("WHATSAPP_API_VERSION", "v19.0")
        val withButton = OtpEnv.getBool("WHATSAPP_INCLUDE_BUTTON", default = true)
        val endpoint = "https://graph.facebook.com/$apiVer/$phoneId/messages"

        val payload = buildJsonObject {
            put("messaging_product", "whatsapp")
            put("to", to)
            put("type", "template")
            putJsonObject("template") {
                put("name", template)
                putJsonObject("language") { put("code", lang) }
                putJsonArray("components") {
                    add(buildJsonObject {
                        put("type", "body")
                        putJsonArray("parameters") {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", request.code)
                            })
                        }
                    })
                    if (withButton) {
                        add(buildJsonObject {
                            put("type", "button")
                            put("sub_type", "url")
                            put("index", "0")
                            putJsonArray("parameters") {
                                add(buildJsonObject {
                                    put("type", "text")
                                    put("text", request.code)
                                })
                            }
                        })
                    }
                }
            }
        }

        return try {
            val response = OtpHttpClient.client.post(endpoint) {
                contentType(ContentType.Application.Json)
                headers {
                    append("authorization", "Bearer $token")
                    append("accept", "application/json")
                }
                setBody(payload)
            }
            val status = response.status.value
            val text = runCatching { response.bodyAsText() }.getOrElse { "<no body>" }
            val latency = System.currentTimeMillis() - started
            if (status in 200..299) {
                val msgId = extractMessageId(text)
                log.info(
                    "[OTP/WACloud] OK to=*****{} template={} msg_id={} latency={}ms",
                    to.takeLast(2), template, msgId, latency,
                )
                OtpDeliveryResult.Sent(
                    providerName = name,
                    channel = channel,
                    providerMessageId = msgId,
                    latencyMillis = latency,
                    rawResponseSummary = trim(text),
                )
            } else {
                log.warn("[OTP/WACloud] FAIL status={} body={} latency={}ms", status, trim(text), latency)
                OtpDeliveryResult.Failed(
                    providerName = name,
                    channel = channel,
                    reason = "WhatsApp Cloud returned $status",
                    latencyMillis = latency,
                    httpStatus = status,
                    rawResponseSummary = trim(text),
                )
            }
        } catch (t: Throwable) {
            val latency = System.currentTimeMillis() - started
            log.warn("[OTP/WACloud] EXC latency={}ms err={}", latency, t.javaClass.simpleName)
            OtpDeliveryResult.Failed(
                providerName = name,
                channel = channel,
                reason = "exception: ${t.javaClass.simpleName}: ${t.message?.take(120) ?: ""}",
                latencyMillis = latency,
            )
        }
    }

    /** Pulls `messages[0].id` out of the WA Cloud response. */
    private fun extractMessageId(json: String): String? {
        val re = Regex("\"messages\"\\s*:\\s*\\[\\s*\\{[^}]*?\"id\"\\s*:\\s*\"([^\"]+)\"")
        return re.find(json)?.groupValues?.getOrNull(1)
    }

    private fun trim(s: String) = s.replace(Regex("\\s+"), " ").take(240)
}
