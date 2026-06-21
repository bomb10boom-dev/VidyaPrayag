/*
 * File: TwilioSmsProvider.kt
 * Module: feature.auth.delivery.sms
 *
 * Thin SmsProvider adapter over the existing Twilio cloud provider. Same
 * rationale as Msg91SmsProvider: FUTURE extensibility for a single-channel
 * cloud-fallback dispatch policy. The current OtpService gateway dispatch uses
 * OTPSenderGatewayProvider; Twilio keeps running through the existing
 * OtpDeliveryProvider race untouched.
 *
 * Why code extraction?
 * --------------------
 * The SmsProvider contract carries an opaque, fully-rendered `message` string.
 * TwilioProvider.send() rebuilds the SMS body internally from the raw code via
 * `OtpMessageTemplates.smsBody(code, ttlMinutes, locale)` — it does NOT use a
 * pre-rendered body. So we extract the leading 6-digit code from the rendered
 * message (every locale template starts with "$code ") and hand it to
 * TwilioProvider via an OtpDeliveryRequest. The rebuilt body will match the
 * original for the default English/10-min case; locale/ttl are cosmetic for
 * this future-fallback path.
 *
 * Row lifecycle: PENDING → PROCESSING (claim, no device) → SENT/FAILED
 * (terminal report). Cloud failures are terminal — see Msg91SmsProvider doc.
 *
 * No Koin/DI — `object` singleton.
 */
package com.littlebridge.vidyaprayag.feature.auth.delivery.sms

import com.littlebridge.vidyaprayag.feature.auth.delivery.OtpDeliveryRequest
import com.littlebridge.vidyaprayag.feature.auth.delivery.OtpDeliveryResult
import com.littlebridge.vidyaprayag.feature.auth.delivery.providers.TwilioProvider
import com.littlebridge.vidyaprayag.feature.auth.gateway.OtpSmsRequestRepository
import com.littlebridge.vidyaprayag.feature.auth.gateway.SmsRequestStatus
import org.slf4j.LoggerFactory

object TwilioSmsProvider : SmsProvider {

    private val log = LoggerFactory.getLogger("SmsProvider/Twilio")

    override val name: String = "twilio"

    /** Delegates to TwilioProvider's env check (SID + AUTH_TOKEN + FROM). */
    override fun isConfigured(): Boolean = TwilioProvider.isConfigured()

    override suspend fun send(request: SmsSendRequest): SmsSendResult {
        // TwilioProvider rebuilds the body from the raw code — extract it from
        // the rendered message (leading 6-digit token).
        val code = extractCode(request.message)
            ?: return SmsSendResult.Failed(
                providerName = name,
                reason = "could not extract 6-digit OTP code from message",
            )

        // Claim the row (PENDING → PROCESSING). No device — cloud provider.
        val claimed = OtpSmsRequestRepository.markProcessing(
            id = request.requestId,
            assignedDeviceId = null,
            providerName = name,
        )
        if (claimed == null) {
            log.info(
                "[Sms/Twilio] requestId={} was no longer PENDING — skipping",
                request.requestId,
            )
            return SmsSendResult.Failed(
                providerName = name,
                reason = "request was no longer PENDING (claimed by another dispatch)",
            )
        }

        // Delegate the actual HTTP send to the existing TwilioProvider.
        val outcome = TwilioProvider.send(
            OtpDeliveryRequest(
                identifier = request.phoneNumber,
                identifierType = "phone",
                code = code,
                purpose = "otp",
                locale = "en",
                ttlMinutes = 10,
            )
        )

        return when (outcome) {
            is OtpDeliveryResult.Sent -> {
                runCatching {
                    OtpSmsRequestRepository.reportStatus(
                        request.requestId, SmsRequestStatus.SENT, null,
                    )
                }.onFailure { t ->
                    log.warn(
                        "[Sms/Twilio] sent but failed to mark row SENT: {}",
                        t.javaClass.simpleName,
                    )
                }
                log.info(
                    "[Sms/Twilio] sent requestId={} providerMsgId={}",
                    request.requestId, outcome.providerMessageId,
                )
                SmsSendResult.Dispatched(
                    providerName = name,
                    providerMessageId = outcome.providerMessageId,
                )
            }
            is OtpDeliveryResult.Failed -> {
                runCatching {
                    OtpSmsRequestRepository.reportStatus(
                        request.requestId, SmsRequestStatus.FAILED, outcome.reason,
                    )
                }.onFailure { t ->
                    log.warn(
                        "[Sms/Twilio] failed and failed to mark row FAILED: {}",
                        t.javaClass.simpleName,
                    )
                }
                log.warn(
                    "[Sms/Twilio] failed requestId={} reason={}",
                    request.requestId, outcome.reason,
                )
                SmsSendResult.Failed(
                    providerName = name,
                    reason = outcome.reason,
                )
            }
            is OtpDeliveryResult.Skipped -> {
                runCatching {
                    OtpSmsRequestRepository.reportStatus(
                        request.requestId, SmsRequestStatus.FAILED, outcome.reason,
                    )
                }
                log.warn(
                    "[Sms/Twilio] skipped requestId={} reason={}",
                    request.requestId, outcome.reason,
                )
                SmsSendResult.Failed(
                    providerName = name,
                    reason = outcome.reason,
                )
            }
        }
    }

    /**
     * Extract the leading 6-digit OTP code from the rendered SMS body. Every
     * locale template in `OtpMessageTemplates.smsBody` starts with "$code ".
     * Returns null if the token isn't exactly 6 digits — fail safe.
     */
    private fun extractCode(message: String): String? {
        val firstToken = message.trim().substringBefore(' ')
        return firstToken.takeIf { it.length == 6 && it.all(Char::isDigit) }
    }
}
