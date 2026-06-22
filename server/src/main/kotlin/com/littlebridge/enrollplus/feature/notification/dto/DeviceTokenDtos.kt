/*
 * File: DeviceTokenDtos.kt
 * Module: feature.notification.dto
 *
 * Request/response DTOs for the device-token registration endpoint
 * (POST /api/device-tokens). These are the canonical shapes every platform
 * client (Android / iOS / Web) sends up to register itself for push delivery.
 *
 * IMPORTANT — multi-device support:
 *   A user may own several devices (phone + tablet + another phone). The
 *   registration handler keys off the `token` value itself: re-registering an
 *   existing token only refreshes its metadata + last_seen_at, it NEVER
 *   overwrites or removes the user's other tokens. See DeviceTokenRepository.
 */
package com.littlebridge.enrollplus.feature.notification.dto

import kotlinx.serialization.Serializable

/**
 * Body of `POST /api/device-tokens`. `platform` is "android" | "ios" | "web".
 * `appVersion` / `deviceModel` are optional diagnostics carried for debugging
 * delivery issues (e.g. a regression pinned to one OEM model).
 */
@Serializable
data class RegisterDeviceTokenRequest(
    val token: String,
    val platform: String,
    val appVersion: String? = null,
    val deviceModel: String? = null
)

/**
 * Body of `POST /api/device-tokens` response. Minimal on purpose: the client
 * only needs to know whether registration succeeded; it already holds the
 * token locally so we do not echo it back.
 */
@Serializable
data class RegisterDeviceTokenResponse(
    val success: Boolean
)
