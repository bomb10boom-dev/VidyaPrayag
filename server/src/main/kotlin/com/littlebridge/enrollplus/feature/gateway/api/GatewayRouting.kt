/*
 * File: GatewayRouting.kt
 * Module: feature.gateway.api
 *
 * HTTP surface for the OTPSender SMS gateway integration. The OTPSender Android
 * app talks to these endpoints to register itself, stay alive, fetch SMS work,
 * and report delivery outcomes:
 *
 *   POST /api/v1/gateway/register                       — register / refresh a gateway device
 *   POST /api/v1/gateway/heartbeat                       — liveness + battery/network telemetry
 *   GET  /api/v1/gateway/requests/{requestId}            — fetch one SMS request
 *   POST /api/v1/gateway/requests/{requestId}/status     — report SENT | FAILED
 *   GET  /api/v1/gateway/pending                          — recovery: list pending requests
 *
 * AUTHENTICATION
 *   The gateway is a machine-to-machine peer, NOT a logged-in user, so it
 *   authenticates with a shared secret in the `X-Gateway-Token` header rather
 *   than a JWT. The secret lives in the OTP_GATEWAY_TOKEN env var. Following
 *   the same ops-only convention as OtpAdminRouting: when OTP_GATEWAY_TOKEN is
 *   UNSET the entire route group is 404'd (its existence is not advertised);
 *   when set, every request must present a matching token (constant-time
 *   compared) or it is 403'd.
 *
 * COLLABORATORS (no DI on the server — see Notify.kt / NotificationRouting.kt)
 *   Module-level singletons hold the two repositories. They are stateless
 *   beyond their DB handles.
 */
package com.littlebridge.enrollplus.feature.gateway.api

import com.littlebridge.enrollplus.core.fail
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.feature.gateway.dto.GatewayHeartbeatRequest
import com.littlebridge.enrollplus.feature.gateway.dto.GatewayHeartbeatResponse
import com.littlebridge.enrollplus.feature.gateway.dto.GatewayRegisterRequest
import com.littlebridge.enrollplus.feature.gateway.dto.GatewayRegisterResponse
import com.littlebridge.enrollplus.feature.gateway.dto.GatewayStatusUpdateRequest
import com.littlebridge.enrollplus.feature.gateway.dto.GatewayStatusUpdateResponse
import com.littlebridge.enrollplus.feature.gateway.dto.PendingRequestsResponse
import com.littlebridge.enrollplus.feature.gateway.dto.SmsRequestDto
import com.littlebridge.enrollplus.feature.gateway.repository.OtpGatewayDeviceRepository
import com.littlebridge.enrollplus.feature.gateway.repository.SmsRequestRepository
import com.littlebridge.enrollplus.feature.gateway.repository.SmsRequestRow
import com.littlebridge.enrollplus.feature.gateway.repository.SmsRequestStatus
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

// ------------------------------------------------------------------
// No-DI collaborators — module-level singletons.
// ------------------------------------------------------------------
private val gatewayDeviceRepository = OtpGatewayDeviceRepository()
private val smsRequestRepository = SmsRequestRepository()

private fun gatewayToken(): String? =
    System.getenv("OTP_GATEWAY_TOKEN")?.takeIf { it.isNotBlank() }

private fun isGatewayEnabled(): Boolean = gatewayToken() != null

/** Constant-time string compare — same pattern as OtpService / OtpAdminRouting. */
private fun ctEq(a: String, b: String): Boolean {
    if (a.length != b.length) return false
    var d = 0
    for (i in a.indices) d = d or (a[i].code xor b[i].code)
    return d == 0
}

/**
 * Validate the X-Gateway-Token header. Returns true when authorised; otherwise
 * writes a 403 and returns false. (The 404-when-disabled gate is applied once
 * at mount time in [gatewayRouting].)
 */
private suspend fun ApplicationCall.authorizeGateway(): Boolean {
    val tok = request.headers["X-Gateway-Token"]
    val expected = gatewayToken()
    if (expected == null || tok.isNullOrBlank() || !ctEq(tok, expected)) {
        fail("forbidden", HttpStatusCode.Forbidden, "GATEWAY_FORBIDDEN")
        return false
    }
    return true
}

private fun SmsRequestRow.toDto() = SmsRequestDto(
    requestId = requestId,
    phoneNumber = phoneNumber,
    otp = otp,
    message = message,
    status = status,
    deviceId = deviceId,
    purpose = purpose,
    createdAt = createdAt.toString(),
    dispatchedAt = dispatchedAt?.toString(),
    sentAt = sentAt?.toString(),
    errorMessage = errorMessage,
)

/**
 * Mounts the OTPSender gateway endpoints. The whole group is invisible (404)
 * unless OTP_GATEWAY_TOKEN is configured.
 */
fun Route.gatewayRouting() {
    if (!isGatewayEnabled()) return  // entire surface is unmounted without OTP_GATEWAY_TOKEN

    route("/api/v1/gateway") {

        // -------- register / refresh a gateway device --------
        post("/register") {
            if (!call.authorizeGateway()) return@post

            val req = runCatching { call.receive<GatewayRegisterRequest>() }.getOrNull()
                ?: run {
                    call.fail("Invalid body: { deviceId, fcmToken, deviceName?, appVersion? }")
                    return@post
                }
            if (req.deviceId.isBlank() || req.fcmToken.isBlank()) {
                call.fail("deviceId and fcmToken are required")
                return@post
            }

            gatewayDeviceRepository.register(
                deviceId = req.deviceId,
                fcmToken = req.fcmToken,
                deviceName = req.deviceName,
                appVersion = req.appVersion,
            )

            call.ok(
                GatewayRegisterResponse(deviceId = req.deviceId, registered = true),
                message = "Gateway device registered",
            )
        }

        // -------- heartbeat --------
        post("/heartbeat") {
            if (!call.authorizeGateway()) return@post

            val req = runCatching { call.receive<GatewayHeartbeatRequest>() }.getOrNull()
                ?: run {
                    call.fail("Invalid body: { deviceId, batteryLevel?, networkType? }")
                    return@post
                }
            if (req.deviceId.isBlank()) {
                call.fail("deviceId is required")
                return@post
            }

            val updated = gatewayDeviceRepository.heartbeat(
                deviceId = req.deviceId,
                batteryLevel = req.batteryLevel,
                networkType = req.networkType,
            )
            if (updated == 0) {
                call.fail(
                    "Unknown device — call /register first.",
                    HttpStatusCode.NotFound, "GATEWAY_DEVICE_NOT_FOUND",
                )
                return@post
            }

            call.ok(
                GatewayHeartbeatResponse(deviceId = req.deviceId, acknowledged = true),
                message = "Heartbeat acknowledged",
            )
        }

        // -------- pending requests (recovery flow) --------
        // NOTE: declared BEFORE the parametrised "/requests/{requestId}" route
        // so "/pending" is never shadowed by the path parameter.
        get("/pending") {
            if (!call.authorizeGateway()) return@get

            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 100)
                .coerceIn(1, 500)
            val rows = smsRequestRepository.listPending(limit)
            call.ok(
                PendingRequestsResponse(count = rows.size, requests = rows.map { it.toDto() }),
                message = "Pending SMS requests",
            )
        }

        // -------- fetch one SMS request --------
        get("/requests/{requestId}") {
            if (!call.authorizeGateway()) return@get

            val requestId = call.parameters["requestId"]
                ?.takeIf { it.isNotBlank() }
                ?: run { call.fail("requestId is required"); return@get }

            val row = smsRequestRepository.findByRequestId(requestId)
                ?: run {
                    call.fail(
                        "No SMS request with that id.",
                        HttpStatusCode.NotFound, "SMS_REQUEST_NOT_FOUND",
                    )
                    return@get
                }

            call.ok(row.toDto(), message = "SMS request")
        }

        // -------- status callback (SENT | FAILED) --------
        post("/requests/{requestId}/status") {
            if (!call.authorizeGateway()) return@post

            val requestId = call.parameters["requestId"]
                ?.takeIf { it.isNotBlank() }
                ?: run { call.fail("requestId is required"); return@post }

            val req = runCatching { call.receive<GatewayStatusUpdateRequest>() }.getOrNull()
                ?: run { call.fail("Invalid body: { status, errorMessage? }"); return@post }

            val normalized = when (req.status.trim().uppercase()) {
                "SENT" -> SmsRequestStatus.SENT
                "FAILED" -> SmsRequestStatus.FAILED
                else -> {
                    call.fail(
                        "status must be SENT or FAILED",
                        HttpStatusCode.BadRequest, "BAD_STATUS",
                    )
                    return@post
                }
            }

            val updated = smsRequestRepository.applyStatus(
                requestId = requestId,
                status = normalized,
                errorMessage = req.errorMessage,
            )
            if (updated == 0) {
                call.fail(
                    "No SMS request with that id.",
                    HttpStatusCode.NotFound, "SMS_REQUEST_NOT_FOUND",
                )
                return@post
            }

            call.ok(
                GatewayStatusUpdateResponse(
                    requestId = requestId,
                    status = normalized,
                    updated = true,
                ),
                message = "Status updated",
            )
        }
    }
}
