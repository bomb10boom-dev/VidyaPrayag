/*
 * File: GatewayDtos.kt
 * Module: feature.gateway.dto
 *
 * Request/response DTOs for the OTPSender gateway API. These shapes match the
 * EXISTING OTPSender Android contract (the agreed integration) — they are NOT
 * redesigned here:
 *
 *   POST /api/v1/gateway/register
 *   POST /api/v1/gateway/heartbeat
 *   GET  /api/v1/gateway/requests/{requestId}
 *   POST /api/v1/gateway/requests/{requestId}/status
 *   GET  /api/v1/gateway/pending
 *
 * All field names are camelCase to match the OTPSender client JSON.
 */
package com.littlebridge.enrollplus.feature.gateway.dto

import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// POST /api/v1/gateway/register
// ---------------------------------------------------------------------------

/**
 * Body of `POST /api/v1/gateway/register`. The OTPSender app registers itself
 * (and refreshes its FCM token) so the backend can route SMS requests to it.
 * `deviceId` is the natural key (one OTPSender install).
 */
@Serializable
data class GatewayRegisterRequest(
    val deviceId: String,
    val fcmToken: String,
    val deviceName: String? = null,
    val appVersion: String? = null,
)

@Serializable
data class GatewayRegisterResponse(
    val deviceId: String,
    val registered: Boolean,
)

// ---------------------------------------------------------------------------
// POST /api/v1/gateway/heartbeat
// ---------------------------------------------------------------------------

/**
 * Body of `POST /api/v1/gateway/heartbeat`. The OTPSender app pings periodically
 * to keep itself in the active-device window and report live telemetry.
 */
@Serializable
data class GatewayHeartbeatRequest(
    val deviceId: String,
    val batteryLevel: Int? = null,
    val networkType: String? = null,
)

@Serializable
data class GatewayHeartbeatResponse(
    val deviceId: String,
    val acknowledged: Boolean,
)

// ---------------------------------------------------------------------------
// GET /api/v1/gateway/requests/{requestId}   &   GET /api/v1/gateway/pending
// ---------------------------------------------------------------------------

/**
 * One SMS request as the OTPSender app consumes it. Carries everything the
 * gateway needs to send the SMS: the destination, the body, and (for
 * convenience) the OTP itself.
 */
@Serializable
data class SmsRequestDto(
    val requestId: String,
    val phoneNumber: String,
    val otp: String? = null,
    val message: String,
    val status: String,
    val deviceId: String? = null,
    val purpose: String,
    val createdAt: String,
    val dispatchedAt: String? = null,
    val sentAt: String? = null,
    val errorMessage: String? = null,
)

/**
 * Body of `GET /api/v1/gateway/pending` — the recovery list a restarted
 * gateway polls to pick up in-flight (pending / dispatched) requests.
 */
@Serializable
data class PendingRequestsResponse(
    val count: Int,
    val requests: List<SmsRequestDto>,
)

// ---------------------------------------------------------------------------
// POST /api/v1/gateway/requests/{requestId}/status
// ---------------------------------------------------------------------------

/**
 * Body of `POST /api/v1/gateway/requests/{requestId}/status`. The OTPSender app
 * reports the outcome of an SMS send. `status` is "SENT" or "FAILED"
 * (case-insensitive); `errorMessage` is populated on FAILED.
 */
@Serializable
data class GatewayStatusUpdateRequest(
    val status: String,
    val errorMessage: String? = null,
)

@Serializable
data class GatewayStatusUpdateResponse(
    val requestId: String,
    val status: String,
    val updated: Boolean,
)
