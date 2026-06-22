/*
 * File: NotificationApi.kt
 * Module: feature.notification.data.remote
 *
 * Ktor client for the notification FOUNDATION endpoints exposed by the server.
 *
 *   POST /api/device-tokens  — register / refresh the caller's FCM token.
 *
 * The admin broadcast endpoint (POST /api/admin/notifications/send) is
 * intentionally NOT exposed here — it is a server-side-only operation (a
 * school admin triggers broadcasts through the backend, never directly from
 * the app). The client's only notification-foundation responsibility is to
 * hand its FCM token to the server so the server can later push to it.
 *
 * Auth is handled centrally by the shared HttpClient's TokenAuthenticator
 * (installTokenAuth in Koin.kt), so no per-call bearer wiring is needed —
 * the same pattern as PtmApi / ParentApi / etc.
 */
package com.littlebridge.enrollplus.feature.notification.data.remote

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.network.safeApiCall
import com.littlebridge.enrollplus.feature.notification.domain.model.RegisterDeviceTokenRequest
import com.littlebridge.enrollplus.feature.notification.domain.model.RegisterDeviceTokenResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class NotificationApi(
    private val client: HttpClient,
    private val baseUrl: String
) {

    private fun getUrl(path: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cleanPath = if (path.startsWith("/")) path.substring(1) else path
        return "$base$cleanPath"
    }

    /**
     * Register (or refresh) the device's FCM token with the backend. Safe to
     * call repeatedly — the server upserts by token (multi-device aware), so
     * re-registering the same token just refreshes metadata + last_seen_at.
     */
    suspend fun registerDeviceToken(
        request: RegisterDeviceTokenRequest
    ): NetworkResult<ApiResponse<RegisterDeviceTokenResponse>> = safeApiCall {
        client.post(getUrl("api/device-tokens")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }
}
