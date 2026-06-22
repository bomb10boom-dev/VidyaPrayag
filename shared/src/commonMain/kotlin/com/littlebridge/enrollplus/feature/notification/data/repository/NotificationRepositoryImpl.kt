/*
 * File: NotificationRepositoryImpl.kt
 * Module: feature.notification.data.repository
 *
 * Thin delegating implementation of [NotificationRepository] over
 * [NotificationApi]. Mirrors the PtmRepositoryImpl pattern: the repository
 * layer exists so ViewModels / the Android token registrar depend on the
 * interface (mockable in tests) rather than the concrete Ktor client.
 */
package com.littlebridge.enrollplus.feature.notification.data.repository

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.notification.data.remote.NotificationApi
import com.littlebridge.enrollplus.feature.notification.domain.model.RegisterDeviceTokenRequest
import com.littlebridge.enrollplus.feature.notification.domain.model.RegisterDeviceTokenResponse
import com.littlebridge.enrollplus.feature.notification.domain.repository.NotificationRepository

class NotificationRepositoryImpl(
    private val api: NotificationApi
) : NotificationRepository {

    override suspend fun registerDeviceToken(
        request: RegisterDeviceTokenRequest
    ): NetworkResult<ApiResponse<RegisterDeviceTokenResponse>> =
        api.registerDeviceToken(request)
}
