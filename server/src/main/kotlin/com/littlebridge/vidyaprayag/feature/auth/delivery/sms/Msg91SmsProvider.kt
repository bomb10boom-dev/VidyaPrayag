/*
 * File: Msg91SmsProvider.kt
 * Module: feature.auth.delivery.sms
 *
 * Thin SmsProvider adapter over the existing MSG91 cloud provider. This exists
 * for FUTURE extensibility — e.g. a "cloud fallback when no gateway device is
 * online" policy. The current OtpService gateway dispatch uses
 * OTPSenderGatewayProvider; MSG91 keeps running through the existing
 * OtpDeliveryProvider race untouched. This adapter just surfaces MSG91 as an
 * SmsProvider so a future single-channel dispatch path can use it while still
 * going through the `otp_sms_requests` row lifecycle.
 *
 * Why code extraction?
 * --------------------
 * The SmsProvider contract carries an opaque, fully-rendered `message` string
 * (no raw OTP code). But MSG91's Flow API requires the raw 6-digit code as a
 * DLT-template variable — it CANNOT send an arbitrary body (TRAI 2018 DLT
 * regulations mandate a pre-approved template with variable substitution
 * done server-side). So we extract the leading 6-digit token from the rendered
 * message (every locale template in OtpMessageTemplates.smsBody starts with
 * "$code ") and hand it to Msg91Provider via an OtpDeliveryRequest.
 *
 * Row lifecycle: PENDING → PROCESSING (claim, no device) → SENT/FAILED
 * (terminal report). Cloud failures are terminal (markFailed) rather than
 * reverted-to-PENDING, since retrying a down/misconfigured cloud provider
 * would just fail again — unlike the gateway path where a device may come
 * back online.
 *
 * No Koin/DI — `object` singleton.
 */
package com.littlebridge.vidyaprayag.feature.auth.delivery.sms

import com.littlebridge.vidyaprayag.feature.auth.delivery.OtpDeliveryRequest
import com.littlebridge.vidyaprayag.feature.auth.delivery.OtpDeliveryResult
import com.littlebridge.vidyaprayag.feature.auth.delivery.providers.Msg91Provider
import com.littlebridge.vidyaprayag.feature.auth.gateway.OtpSmsRequestRepository
import com.littlebridge.vidyaprayag.feature.auth.gateway.SmsRequestStatus
import org.slf4j.LoggerFactory

object Msg91SmsProvider : SmsProvider {

    private val log = LoggerFactory.getLogger("SmsProvider/Msg91")

    override val name: String = "msg91"

    /** Delegates to Msg91Provider's env check (MSG91_AUTH_KEY + MSG91_FLOW_ID). */
    override fun isConfigured(): Boolean = Msg91Provider.isConfigured()

    override suspend fun send(request: SmsSendRequest): SmsSendResult {
        // MSG91 needs the raw code as a DLT template variable — extract it
        // from the rendered message (leading 6-digit token).
        val code = extractCode(request.message)
            ?: return SmsSendResult.Failed(
                providerName = name,
                reason = "could not extract 6-digit OTP code from message",
            )

        // Claim the row (PENDING → PROCESSING). No device assignment — this is
        // a cloud provider, not a gateway device. Guards against a concurrent
        // dispatch (gateway or another cloud adapter) grabbing the same row.
        val claimed = OtpSmsRequestRepository.markProcessing(
            id = request.requestId,
            assignedDeviceId = null,
            providerName = name,
        )
        if (claimed == null) {
            log.info(
                "[Sms/Msg91] requestId={} was no longer PENDING — skipping",
                request.requestId,
            )
            return SmsSendResult.Failed(
                providerName = name,
                reason = "request was no longer PENDING (claimed by another dispatch)",
            )
        }

        // Delegate the actual HTTP send to the existing Msg91Provider.
        val outcome = Msg91Provider.send(
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
                        "[Sms/Msg91] sent but failed to mark row SENT: {}",
                        t.javaClass.simpleName,
                    )
                }
                log.info(
                    "[Sms/Msg91] sent requestId={} providerMsgId={}",
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
                        "[Sms/Msg91] failed and failed to mark row FAILED: {}",
                        t.javaClass.simpleName,
                    )
                }
                log.warn(
                    "[Sms/Msg91] failed requestId={} reason={}",
                    request.requestId, outcome.reason,
                )
                SmsSendResult.Failed(
                    providerName = name,
                    reason = outcome.reason,
                )
            }
            is OtpDeliveryResult.Skipped -> {
                // Shouldn't happen (isConfigured gate + phone-only SmsProvider),
                // but handle defensively — treat as a terminal failure.
                runCatching {
                    OtpSmsRequestRepository.reportStatus(
                        request.requestId, SmsRequestStatus.FAILED, outcome.reason,
                    )
                }
                log.warn(
                    "[Sms/Msg91] skipped requestId={} reason={}",
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
     * locale template in `OtpMessageTemplates.smsBody` starts with "$code "
     * (code followed by a space), so the first whitespace-delimited token is
     * the code. Returns null if the token isn't exactly 6 digits — fail safe.
     */
    private fun extractCode(message: String): String? {
        val firstToken = message.trim().substringBefore(' ')
        return firstToken.takeIf { it.length == 6 && it.all(Char::isDigit) }
    }
}
