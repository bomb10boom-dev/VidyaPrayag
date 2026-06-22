/*
 * File: UserProfileApi.kt
 * Module: feature.admin.data.remote
 *
 * Network client for the institutional-profile / school-profile endpoints.
 * Server routes:
 *   GET  /api/v1/user/profile
 *   PUT  /api/v1/user/profile/philosophy
 *   PUT  /api/v1/user/profile/tour-videos
 *   PUT  /api/v1/user/profile/gallery
 *   PUT  /api/v1/user/profile/visibility
 */
package com.littlebridge.enrollplus.feature.admin.data.remote

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.network.safeApiCall
import com.littlebridge.enrollplus.feature.admin.domain.model.GalleryRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.GalleryUpdateResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.PhilosophyDetailsDto
import com.littlebridge.enrollplus.feature.admin.domain.model.TourVideosRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.UserProfileResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.VisibilityRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.VisibilityResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class UserProfileApi(
    private val client: HttpClient,
    private val baseUrl: String
) {

    private fun getUrl(path: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cleanPath = if (path.startsWith("/")) path.substring(1) else path
        return "$base$cleanPath"
    }

    suspend fun getProfile(
        token: String
    ): NetworkResult<ApiResponse<UserProfileResponse>> = safeApiCall {
        client.get(getUrl("api/v1/user/profile"))
    }

    suspend fun updatePhilosophy(
        token: String,
        body: PhilosophyDetailsDto
    ): NetworkResult<ApiResponse<Unit>> = safeApiCall {
        client.put(getUrl("api/v1/user/profile/philosophy")) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    suspend fun updateTourVideos(
        token: String,
        body: TourVideosRequest
    ): NetworkResult<ApiResponse<Unit>> = safeApiCall {
        client.put(getUrl("api/v1/user/profile/tour-videos")) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    suspend fun updateGallery(
        token: String,
        body: GalleryRequest
    ): NetworkResult<ApiResponse<GalleryUpdateResponse>> = safeApiCall {
        client.put(getUrl("api/v1/user/profile/gallery")) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    suspend fun updateVisibility(
        token: String,
        body: VisibilityRequest
    ): NetworkResult<ApiResponse<VisibilityResponse>> = safeApiCall {
        client.put(getUrl("api/v1/user/profile/visibility")) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }
}
