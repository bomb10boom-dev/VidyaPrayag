/*
 * File: NotificationRepository.kt
 * Module: feature.notification.domain.repository
 *
 * Abstraction over [NotificationApi] so ViewModels / the Android token
 * registrar can depend on the interface (testable, mockable) rather than the
 * concrete Ktor client. Mirrors the PtmRepository pattern.
 */
package com.littlebridge.enrollplus.feature.notification.domain.repository

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.notification.domain.model.RegisterDeviceTokenRequest
import com.littlebridge.enrollplus.feature.notification.domain.model.RegisterDeviceTokenResponse

interface NotificationRepository {
    /**
     * Register (or refresh) the device's FCM token with the backend. Safe to
     * call repeatedly — the server upserts by token (multi-device aware).
     */
    suspend fun registerDeviceToken(
        request: RegisterDeviceTokenRequest
    ): NetworkResult<ApiResponse<RegisterDeviceTokenResponse>>
}
