/*
 * File: DeviceTokenDtos.kt
 * Module: feature.notification.domain.model
 *
 * Client-side DTOs for the device-token registration endpoint
 * (POST /api/device-tokens). These mirror the server-side
 * com.littlebridge.enrollplus.feature.notification.dto.* shapes so the JSON
 * round-trips without explicit @SerialName mappings.
 *
 * Only the registration DTOs are needed on the client — the admin
 * SendNotificationRequest/Response shapes are server-only (a school admin
 * triggers broadcasts through the backend, never directly from the app).
 */
package com.littlebridge.enrollplus.feature.notification.domain.model

import kotlinx.serialization.Serializable

/**
 * Body for POST /api/device-tokens. The client sends its freshly-fetched FCM
 * token plus device metadata so the server can record / refresh the row.
 *
 * @param token       The FCM registration token returned by FirebaseMessaging.
 * @param platform    "android" / "ios" / "web" — defaults to "android" on the
 *                    server when blank.
 * @param appVersion  Optional BuildConfig.VERSION_NAME — helps the admin see
 *                    which app versions are still in the field.
 * @param deviceModel Optional android.os.Build.MODEL — same metadata purpose.
 */
@Serializable
data class RegisterDeviceTokenRequest(
    val token: String,
    val platform: String,
    val appVersion: String? = null,
    val deviceModel: String? = null
)

/** Response envelope data for POST /api/device-tokens. */
@Serializable
data class RegisterDeviceTokenResponse(
    val success: Boolean
)
