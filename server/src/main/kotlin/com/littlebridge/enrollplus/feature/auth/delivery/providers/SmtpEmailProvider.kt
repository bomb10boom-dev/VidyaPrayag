/*
 * File: SmtpEmailProvider.kt
 * Module: feature.auth.delivery.providers
 *
 * Plain-old SMTP email OTP. Works with literally any RFC-2821 server:
 *
 *   • Gmail (smtp.gmail.com:465 SSL) — free, but requires an "App
 *       Password" if 2FA is on
 *   • Resend (smtp.resend.com:465)   — 3000 emails/month free, best DX
 *   • SES (email-smtp.{region}.amazonaws.com:465) — 62k/mo free if
 *       sending from EC2
 *   • Mailgun / Postmark / Brevo (Sendinblue) — all have free tiers
 *   • A local Postfix / your own MTA
 *
 * Implementation uses jakarta.mail (Eclipse Angus) so we don't pull
 * in spring-boot-starter-mail. ~600 KB on the classpath.
 *
 * Env vars
 * --------
 *   SMTP_HOST           (required)   e.g. smtp.gmail.com
 *   SMTP_PORT           optional     default 465 (SSL)
 *   SMTP_USERNAME       (required)
 *   SMTP_PASSWORD       (required)   App Password / API key
 *   SMTP_FROM           (required)   "VidyaPrayag <noreply@yourdomain.com>"
 *   SMTP_USE_SSL        optional     "true" (default) = SMTPS:465
 *   SMTP_USE_STARTTLS   optional     "true" = STARTTLS:587
 *                                     (mutually exclusive with USE_SSL;
 *                                      use STARTTLS for port 587)
 *   SMTP_TIMEOUT_MS     optional     default 8000
 *
 * Choose ONE of SMTP_USE_SSL=true OR SMTP_USE_STARTTLS=true (not both).
 */
package com.littlebridge.enrollplus.feature.auth.delivery.providers

import com.littlebridge.enrollplus.feature.auth.delivery.OtpChannel
import com.littlebridge.enrollplus.feature.auth.delivery.OtpDeliveryRequest
import com.littlebridge.enrollplus.feature.auth.delivery.OtpDeliveryResult
import com.littlebridge.enrollplus.feature.auth.delivery.OtpEnv
import com.littlebridge.enrollplus.feature.auth.delivery.OtpMessageTemplates
import com.littlebridge.enrollplus.feature.auth.delivery.OtpProvider
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.Properties
import java.util.UUID

object SmtpEmailProvider : OtpProvider {

    private val log = LoggerFactory.getLogger("OtpProvider/SMTP")

    override val name: String = "smtp"
    override val channel: OtpChannel = OtpChannel.EMAIL

    override fun isConfigured(): Boolean =
        OtpEnv.allSet("SMTP_HOST", "SMTP_USERNAME", "SMTP_PASSWORD", "SMTP_FROM")

    override suspend fun send(request: OtpDeliveryRequest): OtpDeliveryResult {
        val started = System.currentTimeMillis()
        if (request.identifierType != "email") {
            return OtpDeliveryResult.Skipped(name, channel, "non-email identifier")
        }

        val host = OtpEnv.get("SMTP_HOST")!!
        val username = OtpEnv.get("SMTP_USERNAME")!!
        val password = OtpEnv.get("SMTP_PASSWORD")!!
        val from = OtpEnv.get("SMTP_FROM")!!
        val useSsl = OtpEnv.getBool("SMTP_USE_SSL", default = true)
        val useStarttls = OtpEnv.getBool("SMTP_USE_STARTTLS", default = false)
        val port = OtpEnv.getInt("SMTP_PORT", if (useSsl) 465 else 587)
        val timeoutMs = OtpEnv.getInt("SMTP_TIMEOUT_MS", 8_000)

        // jakarta.mail is blocking — push to the IO dispatcher.
        return withContext(Dispatchers.IO) {
            try {
                val props = Properties().apply {
                    put("mail.smtp.host", host)
                    put("mail.smtp.port", port.toString())
                    put("mail.smtp.auth", "true")
                    put("mail.smtp.connectiontimeout", timeoutMs.toString())
                    put("mail.smtp.timeout", timeoutMs.toString())
                    put("mail.smtp.writetimeout", timeoutMs.toString())
                    if (useSsl) {
                        put("mail.smtp.ssl.enable", "true")
                    }
                    if (useStarttls) {
                        put("mail.smtp.starttls.enable", "true")
                        put("mail.smtp.starttls.required", "true")
                    }
                }
                val session = Session.getInstance(
                    props,
                    object : Authenticator() {
                        override fun getPasswordAuthentication() =
                            PasswordAuthentication(username, password)
                    },
                )
                val msg = MimeMessage(session).apply {
                    setFrom(InternetAddress.parse(from).first())
                    setRecipients(Message.RecipientType.TO, request.identifier)
                    subject = OtpMessageTemplates.emailSubject(request.code)
                    val plain = MimeBodyPart().apply {
                        setText(
                            OtpMessageTemplates.emailBody(
                                request.code, request.ttlMinutes, request.purpose
                            ),
                            "utf-8"
                        )
                    }
                    val html = MimeBodyPart().apply {
                        setContent(
                            OtpMessageTemplates.emailHtmlBody(
                                request.code, request.ttlMinutes, request.purpose
                            ),
                            "text/html; charset=utf-8"
                        )
                    }
                    setContent(MimeMultipart("alternative").apply {
                        addBodyPart(plain)
                        addBodyPart(html)
                    })
                    val msgId = "<otp-${UUID.randomUUID()}@vidyaprayag>"
                    setHeader("Message-ID", msgId)
                    setHeader("X-VP-Purpose", request.purpose)
                }
                Transport.send(msg)

                val latency = System.currentTimeMillis() - started
                val mid = msg.getHeader("Message-ID")?.firstOrNull()
                log.info(
                    "[OTP/SMTP] OK to=***@{} latency={}ms",
                    request.identifier.substringAfter('@', "?"), latency,
                )
                OtpDeliveryResult.Sent(
                    providerName = name,
                    channel = channel,
                    providerMessageId = mid,
                    latencyMillis = latency,
                    rawResponseSummary = "delivered via $host:$port",
                )
            } catch (t: Throwable) {
                val latency = System.currentTimeMillis() - started
                log.warn(
                    "[OTP/SMTP] EXC host={} latency={}ms err={} msg={}",
                    host, latency, t.javaClass.simpleName, t.message?.take(120),
                )
                OtpDeliveryResult.Failed(
                    providerName = name,
                    channel = channel,
                    reason = "exception: ${t.javaClass.simpleName}: ${t.message?.take(120) ?: ""}",
                    latencyMillis = latency,
                )
            }
        }
    }
}
