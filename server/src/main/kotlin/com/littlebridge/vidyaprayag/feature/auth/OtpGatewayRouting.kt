/*
 * File: OtpGatewayRouting.kt
 * Module: feature.auth
 *
 * OTPSender GATEWAY DEVICE APIs — the backend half of the OTP-via-FCM
 * device-SIM flow.
 *
 * The 7-step flow (steps 1-4 happen inside OtpService.send via the existing
 * /api/v1/auth/send-otp endpoint; this file owns steps 5 & 7 + device
 * lifecycle + recovery):
 *
 *   1. Backend generates OTP.                         (OtpService.send)
 *   2. Backend stores OTP in auth_otps.               (OtpService.send)
 *   3. Backend creates otp_sms_requests row (PENDING).(OtpService.send — Task 8)
 *   4. Backend pushes FCM SMS_REQUEST to OTPSender.   (OTPSenderGatewayProvider)
 *   5. OTPSender fetches request details.             (GET  /gateway/requests/{id}) ← here
 *   6. OTPSender sends SMS through device SIM.        (device-side, out of scope)
 *   7. OTPSender reports status back to backend.      (POST /gateway/requests/{id}/status) ← here
 *
 * Endpoints (all under /api/v1/gateway unless noted):
 *   POST   /register                      — device registers / re-registers its FCM token
 *   POST   /deregister                    — device marks itself inactive (logout / uninstall)
 *   POST   /heartbeat                     — refresh last_seen_at + battery + network
 *   GET    /requests/{requestId}          — fetch request details (claims PENDING → PROCESSING)
 *   POST   /requests/{requestId}/status   — report SENT / FAILED (step 7)
 *   GET    /pending                       — list PENDING requests for recovery polling
 *
 * Admin monitoring (reuses the OTP_ADMIN_TOKEN gate from OtpAdminRouting):
 *   GET    /api/v1/admin/otp/gateway      — devices snapshot + request counts + FCM config
 *
 * Authentication
 * ---------------
 *   Gateway endpoints: `OTP_GATEWAY_TOKEN` env + `X-Gateway-Token` header
 *   (constant-time compare). When the env var is unset the entire gateway
 *   surface is 404'd — same stealth pattern as the admin routes, so we don't
 *   advertise the endpoints to the world. Non-register endpoints additionally
 *   validate the caller is a known active device via the `X-Device-Id` header
 *   (must be within the 5-min freshness window).
 *
 *   Admin endpoint: `OTP_ADMIN_TOKEN` env + `X-Admin-Token` header (same
 *   pattern as OtpAdminRouting.kt). The helper fns are duplicated locally so
 *   this file is self-contained and doesn't touch OtpAdminRouting.kt.
 *
 * No Koin/DI — routes call `object` repository singletons directly.
 */
package com.littlebridge.vidyaprayag.feature.auth

import com.littlebridge.vidyaprayag.core.fail
import com.littlebridge.vidyaprayag.core.ok
import com.littlebridge.vidyaprayag.feature.auth.gateway.FirebaseAdminInitializer
import com.littlebridge.vidyaprayag.feature.auth.gateway.OtpGatewayDeviceRepository
import com.littlebridge.vidyaprayag.feature.auth.gateway.OtpSmsRequestRepository
import com.littlebridge.vidyaprayag.feature.auth.gateway.SmsRequest
import com.littlebridge.vidyaprayag.feature.auth.gateway.SmsRequestStatus
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

// ============================================================
// Auth helpers — gateway token (X-Gateway-Token)
// ============================================================

private fun isGatewayEnabled(): Boolean =
    !System.getenv("OTP_GATEWAY_TOKEN").isNullOrBlank()

private fun gatewayToken(): String? =
    System.getenv("OTP_GATEWAY_TOKEN")?.takeIf { it.isNotBlank() }

/** Admin token helpers — duplicated from OtpAdminRouting so this file is standalone. */
private fun isAdminEnabled(): Boolean =
    !System.getenv("OTP_ADMIN_TOKEN").isNullOrBlank()

private fun adminToken(): String? =
    System.getenv("OTP_ADMIN_TOKEN")?.takeIf { it.isNotBlank() }

/** Constant-time string compare — guards against timing side-channels. */
private fun ctEq(a: String, b: String): Boolean {
    if (a.length != b.length) return false
    var d = 0
    for (i in a.indices) d = d or (a[i].code xor b[i].code)
    return d == 0
}

/** Validate the gateway token header; on mismatch respond 403 and return false. */
private fun Route.checkGatewayToken(): Boolean {
    val tok = call.request.headers["X-Gateway-Token"]
    if (tok.isNullOrBlank() || !ctEq(tok, gatewayToken()!!)) {
        call.fail("forbidden", HttpStatusCode.Forbidden, "GATEWAY_FORBIDDEN")
        return false
    }
    return true
}

/** Validate the admin token header; on mismatch respond 403 and return false. */
private fun Route.checkAdminToken(): Boolean {
    val tok = call.request.headers["X-Admin-Token"]
    if (tok.isNullOrBlank() || !ctEq(tok, adminToken()!!)) {
        call.fail("forbidden", HttpStatusCode.Forbidden, "ADMIN_FORBIDDEN")
        return false
    }
    return true
}

// ============================================================
// Request / response DTOs
// ============================================================

@Serializable
data class GatewayRegisterRequest(
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("fcm_token") val fcmToken: String,
    @SerialName("app_version") val appVersion: String? = null,
)

@Serializable
data class GatewayRegisterResponse(
    val registered: Boolean,
    @SerialName("device_id") val deviceId: String,
)

@Serializable
data class GatewayDeregisterRequest(
    @SerialName("device_id") val deviceId: String,
)

@Serializable
data class GatewayDeregisterResponse(
    val deregistered: Boolean,
    @SerialName("device_id") val deviceId: String,
)

@Serializable
data class GatewayHeartbeatRequest(
    @SerialName("device_id") val deviceId: String,
    @SerialName("battery_level") val batteryLevel: Int? = null,
    @SerialName("network_type") val networkType: String? = null,
)

@Serializable
data class GatewayHeartbeatResponse(
    val ok: Boolean,
    @SerialName("last_seen_at") val lastSeenAt: String,
)

@Serializable
data class GatewaySmsRequestDto(
    val id: String,
    @SerialName("phone_number") val phoneNumber: String,
    val message: String,
    val status: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class GatewayStatusReportRequest(
    /** "SENT" or "FAILED". */
    val status: String,
    @SerialName("error_message") val errorMessage: String? = null,
)

@Serializable
data class GatewayStatusReportResponse(
    val ok: Boolean,
    val status: String,
)

@Serializable
data class GatewayPendingResponse(
    val count: Int,
    val requests: List<GatewaySmsRequestDto>,
)

@Serializable
data class GatewayDeviceDto(
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String?,
    @SerialName("app_version") val appVersion: String?,
    @SerialName("last_seen_at") val lastSeenAt: String,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("battery_level") val batteryLevel: Int?,
    @SerialName("network_type") val networkType: String?,
)

@Serializable
data class GatewayRequestCounts(
    val pending: Long,
    val processing: Long,
    val sent: Long,
    val failed: Long,
)

@Serializable
data class GatewayAdminSnapshotResponse(
    val devices: List<GatewayDeviceDto>,
    @SerialName("active_device_count") val activeDeviceCount: Int,
    val counts: GatewayRequestCounts,
    @SerialName("fcm_configured") val fcmConfigured: Boolean,
    @SerialName("gateway_enabled") val gatewayEnabled: Boolean,
)

// ============================================================
// Routing
// ============================================================

fun Route.otpGatewayRouting() {
    // Entire gateway surface is invisible (404) without OTP_GATEWAY_TOKEN.
    if (!isGatewayEnabled()) return

    route("/api/v1/gateway") {

        // ----- register / re-register -----
        post("/register") {
            if (!checkGatewayToken()) return@post
            val body = runCatching { call.receive<GatewayRegisterRequest>() }.getOrNull()
                ?: run { call.fail("Invalid body: { device_id, fcm_token, device_name?, app_version? }"); return@post }
            if (body.deviceId.isBlank() || body.fcmToken.isBlank()) {
                call.fail("device_id and fcm_token are required"); return@post
            }
            OtpGatewayDeviceRepository.register(
                deviceId = body.deviceId.trim().take(128),
                deviceName = body.deviceName?.takeIf { it.isNotBlank() }?.take(128),
                fcmToken = body.fcmToken.trim(),
                appVersion = body.appVersion?.takeIf { it.isNotBlank() }?.take(32),
            )
            call.ok(
                GatewayRegisterResponse(registered = true, deviceId = body.deviceId.trim()),
                message = "Device registered",
            )
        }

        // ----- deregister (logout / uninstall) -----
        post("/deregister") {
            if (!checkGatewayToken()) return@post
            val body = runCatching { call.receive<GatewayDeregisterRequest>() }.getOrNull()
                ?: run { call.fail("Invalid body: { device_id }"); return@post }
            if (body.deviceId.isBlank()) {
                call.fail("device_id is required"); return@post
            }
            val deactivated = OtpGatewayDeviceRepository.deactivate(body.deviceId.trim())
            call.ok(
                GatewayDeregisterResponse(deregistered = deactivated, deviceId = body.deviceId.trim()),
                message = if (deactivated) "Device deregistered" else "Device not found (no-op)",
            )
        }

        // ----- heartbeat -----
        post("/heartbeat") {
            if (!checkGatewayToken()) return@post
            val body = runCatching { call.receive<GatewayHeartbeatRequest>() }.getOrNull()
                ?: run { call.fail("Invalid body: { device_id, battery_level?, network_type? }"); return@post }
            if (body.deviceId.isBlank()) {
                call.fail("device_id is required"); return@post
            }
            val ok = OtpGatewayDeviceRepository.heartbeat(
                deviceId = body.deviceId.trim(),
                batteryLevel = body.batteryLevel?.coerceIn(0, 100),
                networkType = body.networkType?.takeIf { it.isNotBlank() }?.take(16),
            )
            if (!ok) {
                call.fail("Unknown device — please re-register first", HttpStatusCode.NotFound, "DEVICE_UNKNOWN")
                return@post
            }
            call.ok(
                GatewayHeartbeatResponse(ok = true, lastSeenAt = java.time.Instant.now().toString()),
                message = "Heartbeat acknowledged",
            )
        }

        // ----- fetch request details (step 5) — also claims PENDING → PROCESSING -----
        get("/requests/{requestId}") {
            if (!checkGatewayToken()) return@get
            val deviceId = call.request.headers["X-Device-Id"]?.takeIf { it.isNotBlank() }
            if (deviceId.isNullOrBlank()) {
                call.fail("X-Device-Id header is required", HttpStatusCode.BadRequest, "DEVICE_ID_REQUIRED")
                return@get
            }
            // Validate the caller is a known active device within the freshness window.
            val device = OtpGatewayDeviceRepository.findActiveByDeviceId(deviceId)
            if (device == null) {
                call.fail("Device not active or stale — please re-register + heartbeat",
                    HttpStatusCode.Forbidden, "DEVICE_NOT_ACTIVE")
                return@get
            }
            val requestId = parseUuid(call.parameters["requestId"])
                ?: run { call.fail("Invalid requestId (expected UUID)"); return@get }

            val row = OtpSmsRequestRepository.findById(requestId)
                ?: run { call.fail("SMS request not found", HttpStatusCode.NotFound, "REQUEST_NOT_FOUND"); return@get }

            // Recovery path: if still PENDING, atomically claim it for this device
            // (PENDING → PROCESSING) so a second gateway doesn't also send the SMS.
            if (row.status == SmsRequestStatus.PENDING) {
                OtpSmsRequestRepository.markProcessing(
                    id = requestId,
                    assignedDeviceId = device.id,
                    providerName = "gateway",
                )
            }
            // Re-read to reflect the (possibly) new status.
            val current = if (row.status == SmsRequestStatus.PENDING) {
                OtpSmsRequestRepository.findById(requestId) ?: row
            } else row

            call.ok(current.toDto(), message = "SMS request details")
        }

        // ----- report status (step 7) -----
        post("/requests/{requestId}/status") {
            if (!checkGatewayToken()) return@post
            val deviceId = call.request.headers["X-Device-Id"]?.takeIf { it.isNotBlank() }
            if (deviceId.isNullOrBlank()) {
                call.fail("X-Device-Id header is required", HttpStatusCode.BadRequest, "DEVICE_ID_REQUIRED")
                return@post
            }
            val device = OtpGatewayDeviceRepository.findActiveByDeviceId(deviceId)
            if (device == null) {
                call.fail("Device not active or stale — please re-register + heartbeat",
                    HttpStatusCode.Forbidden, "DEVICE_NOT_ACTIVE")
                return@post
            }
            val requestId = parseUuid(call.parameters["requestId"])
                ?: run { call.fail("Invalid requestId (expected UUID)"); return@post }
            val body = runCatching { call.receive<GatewayStatusReportRequest>() }.getOrNull()
                ?: run { call.fail("Invalid body: { status, error_message? }"); return@post }

            val normalized = body.status.trim().uppercase()
            if (normalized != SmsRequestStatus.SENT && normalized != SmsRequestStatus.FAILED) {
                call.fail("status must be SENT or FAILED"); return@post
            }

            val updated = OtpSmsRequestRepository.reportStatus(
                id = requestId,
                status = normalized,
                errorMessage = body.errorMessage?.takeIf { it.isNotBlank() }?.take(500),
            )
            if (updated == null) {
                call.fail(
                    "Request not found or not in PROCESSING state (already terminal or not claimed)",
                    HttpStatusCode.Conflict, "STATUS_REPORT_CONFLICT",
                )
                return@post
            }
            call.ok(
                GatewayStatusReportResponse(ok = true, status = updated.status),
                message = "Status updated",
            )
        }

        // ----- pending (recovery polling) -----
        get("/pending") {
            if (!checkGatewayToken()) return@get
            val deviceId = call.request.headers["X-Device-Id"]?.takeIf { it.isNotBlank() }
            if (deviceId.isNullOrBlank()) {
                call.fail("X-Device-Id header is required", HttpStatusCode.BadRequest, "DEVICE_ID_REQUIRED")
                return@get
            }
            val device = OtpGatewayDeviceRepository.findActiveByDeviceId(deviceId)
            if (device == null) {
                call.fail("Device not active or stale — please re-register + heartbeat",
                    HttpStatusCode.Forbidden, "DEVICE_NOT_ACTIVE")
                return@get
            }
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 100).coerceIn(1, 500)
            val pending = OtpSmsRequestRepository.listPending(limit)
            call.ok(
                GatewayPendingResponse(count = pending.size, requests = pending.map { it.toDto() }),
                message = "Pending SMS requests",
            )
        }
    }

    // ----- admin monitoring (gated by OTP_ADMIN_TOKEN, same as OtpAdminRouting) -----
    if (isAdminEnabled()) {
        route("/api/v1/admin/otp") {
            get("/gateway") {
                if (!checkAdminToken()) return@get
                val devices = OtpGatewayDeviceRepository.listAll(limit = 100)
                val activeCount = devices.count { it.isActive }
                val counts = GatewayRequestCounts(
                    pending = OtpSmsRequestRepository.countByStatus(SmsRequestStatus.PENDING),
                    processing = OtpSmsRequestRepository.countByStatus(SmsRequestStatus.PROCESSING),
                    sent = OtpSmsRequestRepository.countByStatus(SmsRequestStatus.SENT),
                    failed = OtpSmsRequestRepository.countByStatus(SmsRequestStatus.FAILED),
                )
                call.ok(
                    GatewayAdminSnapshotResponse(
                        devices = devices.map {
                            GatewayDeviceDto(
                                deviceId = it.deviceId,
                                deviceName = it.deviceName,
                                appVersion = it.appVersion,
                                lastSeenAt = it.lastSeenAt.toString(),
                                isActive = it.isActive,
                                batteryLevel = it.batteryLevel,
                                networkType = it.networkType,
                            )
                        },
                        activeDeviceCount = activeCount,
                        counts = counts,
                        fcmConfigured = FirebaseAdminInitializer.isConfigured(),
                        gatewayEnabled = isGatewayEnabled(),
                    ),
                    message = "OTP gateway snapshot",
                )
            }
        }
    }
}

// ============================================================
// Helpers
// ============================================================

private fun parseUuid(raw: String?): UUID? =
    raw?.takeIf { it.isNotBlank() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }

private fun SmsRequest.toDto(): GatewaySmsRequestDto = GatewaySmsRequestDto(
    id = id.toString(),
    phoneNumber = phoneNumber,
    message = message,
    status = status,
    createdAt = createdAt.toString(),
)
