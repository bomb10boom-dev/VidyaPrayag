/*
 * File: OtpAdminRouting.kt
 * Module: feature.auth
 *
 * Operations-only endpoints to debug the OTP delivery layer without
 * needing SSH onto the server or grepping logs.  All endpoints are
 * gated by a static admin token (header `X-Admin-Token`) which MUST be
 * set in production via the `OTP_ADMIN_TOKEN` env var.  When the env
 * var is unset, the endpoints are 404'd (not 401'd) so we don't even
 * advertise their existence to the world.
 *
 * Endpoints
 * ---------
 *   GET  /api/v1/admin/otp/diagnostic
 *     → snapshot of provider config + chain order. Safe for ops dashboards.
 *
 *   GET  /api/v1/admin/otp/attempts?identifier=+91...&limit=20
 *     → last N delivery attempts for an identifier — surfaces "MSG91
 *        returned 401" so support can re-issue creds without grepping
 *        logs.  Strips the raw_response field to a 240-char summary.
 *
 *   POST /api/v1/admin/otp/test-send
 *     body: { identifier, locale? }
 *     → forces a real OTP send (NOT verified, NOT persisted to auth_otps
 *        — purely for smoke-testing a new provider's creds in prod).
 *        Code is returned in the response so the operator can verify
 *        receipt on their own device.
 *
 * Why keep these separate from /auth/?
 *   Because the auth router is public and these are not.  A mistake in
 *   the auth router (e.g. removing a `authenticate("jwt")` block) would
 *   otherwise expose internal tooling.
 */
package com.littlebridge.enrollplus.feature.auth

import com.littlebridge.enrollplus.core.fail
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.OtpDeliveryAttemptsTable
import com.littlebridge.enrollplus.feature.auth.delivery.OtpDeliveryDispatcher
import com.littlebridge.enrollplus.feature.auth.delivery.OtpDeliveryRequest
import com.littlebridge.enrollplus.feature.auth.delivery.OtpDeliveryResult
import com.littlebridge.enrollplus.feature.auth.delivery.OtpEnv
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import java.security.SecureRandom

private fun isAdminEnabled(): Boolean =
    !System.getenv("OTP_ADMIN_TOKEN").isNullOrBlank()

private fun adminToken(): String? =
    System.getenv("OTP_ADMIN_TOKEN")?.takeIf { it.isNotBlank() }

/** Constant-time string compare — same pattern as OtpService. */
private fun ctEq(a: String, b: String): Boolean {
    if (a.length != b.length) return false
    var d = 0
    for (i in a.indices) d = d or (a[i].code xor b[i].code)
    return d == 0
}

@Serializable
data class OtpAttemptDto(
    @SerialName("attempt_index") val attemptIndex: Int,
    @SerialName("provider_name") val providerName: String,
    val channel: String,
    val status: String,
    @SerialName("provider_message_id") val providerMessageId: String?,
    @SerialName("http_status") val httpStatus: Int?,
    @SerialName("latency_ms") val latencyMs: Int,
    val reason: String?,
    @SerialName("raw_response") val rawResponse: String?,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class OtpAttemptsResponse(
    val identifier: String,
    val count: Int,
    val attempts: List<OtpAttemptDto>,
)

@Serializable
data class OtpDiagnosticResponse(
    val providers: List<ProviderInfo>,
    @SerialName("channel_order_default") val channelOrderDefault: List<String>,
    @SerialName("provider_order_override") val providerOrderOverride: List<String>,
    @SerialName("pinned_provider") val pinnedProvider: String,
    @SerialName("console_fallback_enabled") val consoleFallbackEnabled: Boolean,
    @SerialName("dev_return_code") val devReturnCode: Boolean,
)

@Serializable
data class ProviderInfo(
    val name: String,
    val channel: String,
    val configured: Boolean,
)

@Serializable
data class OtpTestSendRequest(
    val identifier: String,
    val locale: String? = null,
)

@Serializable
data class OtpTestSendResponse(
    val ok: Boolean,
    @SerialName("winning_provider") val winningProvider: String?,
    @SerialName("winning_channel") val winningChannel: String?,
    @SerialName("provider_message_id") val providerMessageId: String?,
    @SerialName("failure_reason") val failureReason: String?,
    val code: String,                            // visible by design (admin only)
    val attempts: List<OtpAttemptDto>,
)

fun Route.otpAdminRouting() {
    if (!isAdminEnabled()) return  // entire surface is invisible without OTP_ADMIN_TOKEN

    route("/api/v1/admin/otp") {

        // ----- diagnostic -----
        get("/diagnostic") {
            val tok = call.request.headers["X-Admin-Token"]
            if (tok.isNullOrBlank() || !ctEq(tok, adminToken()!!)) {
                call.fail("forbidden", HttpStatusCode.Forbidden, "ADMIN_FORBIDDEN")
                return@get
            }
            val providers = OtpDeliveryDispatcher.knownProviders.map {
                ProviderInfo(
                    name = it.name,
                    channel = it.channel.wireName,
                    configured = runCatching { it.isConfigured() }.getOrDefault(false),
                )
            }
            call.ok(
                OtpDiagnosticResponse(
                    providers = providers,
                    channelOrderDefault = OtpEnv.getList(
                        "OTP_CHANNEL_ORDER", listOf("sms", "whatsapp", "email")
                    ),
                    providerOrderOverride = OtpEnv.getList("OTP_PROVIDER_ORDER", emptyList()),
                    pinnedProvider = OtpEnv.get("OTP_PROVIDER") ?: "",
                    consoleFallbackEnabled = OtpEnv.getBool("OTP_ENABLE_CONSOLE_FALLBACK", true),
                    devReturnCode = OtpEnv.getBool("OTP_DEV_RETURN_CODE", false),
                ),
                message = "OTP provider diagnostic",
            )
        }

        // ----- attempts (audit trail viewer) -----
        get("/attempts") {
            val tok = call.request.headers["X-Admin-Token"]
            if (tok.isNullOrBlank() || !ctEq(tok, adminToken()!!)) {
                call.fail("forbidden", HttpStatusCode.Forbidden, "ADMIN_FORBIDDEN")
                return@get
            }
            val raw = call.request.queryParameters["identifier"]
                ?: run { call.fail("identifier query param is required"); return@get }
            val identifier = normaliseIdentifier(raw)
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 50).coerceIn(1, 500)

            val rows = dbQuery {
                OtpDeliveryAttemptsTable.selectAll()
                    .where { OtpDeliveryAttemptsTable.identifier eq identifier }
                    .orderBy(OtpDeliveryAttemptsTable.createdAt, SortOrder.DESC)
                    .limit(limit)
                    .map { row ->
                        OtpAttemptDto(
                            attemptIndex = row[OtpDeliveryAttemptsTable.attemptIndex],
                            providerName = row[OtpDeliveryAttemptsTable.providerName],
                            channel = row[OtpDeliveryAttemptsTable.channel],
                            status = row[OtpDeliveryAttemptsTable.status],
                            providerMessageId = row[OtpDeliveryAttemptsTable.providerMessageId],
                            httpStatus = row[OtpDeliveryAttemptsTable.httpStatus],
                            latencyMs = row[OtpDeliveryAttemptsTable.latencyMs],
                            reason = row[OtpDeliveryAttemptsTable.reason],
                            rawResponse = row[OtpDeliveryAttemptsTable.rawResponse],
                            createdAt = row[OtpDeliveryAttemptsTable.createdAt].toString(),
                        )
                    }
            }
            call.ok(
                OtpAttemptsResponse(identifier = identifier, count = rows.size, attempts = rows),
                message = "OTP delivery attempts",
            )
        }

        // ----- test-send (live smoke test, no DB write to auth_otps) -----
        post("/test-send") {
            val tok = call.request.headers["X-Admin-Token"]
            if (tok.isNullOrBlank() || !ctEq(tok, adminToken()!!)) {
                call.fail("forbidden", HttpStatusCode.Forbidden, "ADMIN_FORBIDDEN")
                return@post
            }
            val body = runCatching { call.receive<OtpTestSendRequest>() }.getOrNull()
                ?: run { call.fail("Invalid body: { identifier, locale? }"); return@post }
            val identifier = normaliseIdentifier(body.identifier)
            if (identifier.isBlank()) {
                call.fail("identifier is required"); return@post
            }
            val identifierType = if (identifier.contains("@")) "email" else "phone"
            val code = "%06d".format(SecureRandom().nextInt(1_000_000))

            val outcome = OtpDeliveryDispatcher.dispatch(
                OtpDeliveryRequest(
                    identifier = identifier,
                    identifierType = identifierType,
                    code = code,
                    purpose = "test",
                    locale = body.locale ?: "en",
                    ttlMinutes = 10,
                )
            )

            val nowStr = java.time.Instant.now().toString()
            val attemptDtos = outcome.attempts.mapIndexed { idx, a ->
                when (a) {
                    is OtpDeliveryResult.Sent -> OtpAttemptDto(
                        attemptIndex = idx,
                        providerName = a.providerName,
                        channel = a.channel.wireName,
                        status = "sent",
                        providerMessageId = a.providerMessageId,
                        httpStatus = null,
                        latencyMs = a.latencyMillis.toInt(),
                        reason = null,
                        rawResponse = a.rawResponseSummary,
                        createdAt = nowStr,
                    )
                    is OtpDeliveryResult.Failed -> OtpAttemptDto(
                        attemptIndex = idx,
                        providerName = a.providerName,
                        channel = a.channel.wireName,
                        status = "failed",
                        providerMessageId = null,
                        httpStatus = a.httpStatus,
                        latencyMs = a.latencyMillis.toInt(),
                        reason = a.reason,
                        rawResponse = a.rawResponseSummary,
                        createdAt = nowStr,
                    )
                    is OtpDeliveryResult.Skipped -> OtpAttemptDto(
                        attemptIndex = idx,
                        providerName = a.providerName,
                        channel = a.channel.wireName,
                        status = "skipped",
                        providerMessageId = null,
                        httpStatus = null,
                        latencyMs = 0,
                        reason = a.reason,
                        rawResponse = null,
                        createdAt = nowStr,
                    )
                }
            }
            call.ok(
                OtpTestSendResponse(
                    ok = outcome.ok,
                    winningProvider = outcome.winningProvider,
                    winningChannel = outcome.winningChannel?.wireName,
                    providerMessageId = outcome.providerMessageId,
                    failureReason = outcome.failureReason,
                    code = code,
                    attempts = attemptDtos,
                ),
                message = if (outcome.ok) "Test OTP dispatched" else "All providers failed",
            )
        }
    }
}
