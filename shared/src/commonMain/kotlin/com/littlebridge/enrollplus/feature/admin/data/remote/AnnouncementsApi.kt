/*
 * File: AnnouncementsApi.kt
 * Module: feature.admin.data.remote
 *
 * Network client for school-announcements endpoints.
 *
 * Server routes:
 *   GET  /api/v1/school/announcements
 *   GET  /api/v1/school/announcements/search?query=...
 *   POST /api/v1/school/announcements
 *   POST /api/v1/school/announcements/sync-whatsapp
 */
package com.littlebridge.enrollplus.feature.admin.data.remote

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.network.safeApiCall
import com.littlebridge.enrollplus.feature.admin.domain.model.AnnouncementListResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.CreateAnnouncementRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.AnnouncementDto
import com.littlebridge.enrollplus.feature.admin.domain.model.SyncWhatsAppResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class AnnouncementsApi(
    private val client: HttpClient,
    private val baseUrl: String
) {

    private fun getUrl(path: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cleanPath = if (path.startsWith("/")) path.substring(1) else path
        return "$base$cleanPath"
    }

    suspend fun getAnnouncements(
        token: String
    ): NetworkResult<ApiResponse<AnnouncementListResponse>> = safeApiCall {
        client.get(getUrl("api/v1/school/announcements"))
    }

    suspend fun searchAnnouncements(
        token: String,
        query: String
    ): NetworkResult<ApiResponse<AnnouncementListResponse>> = safeApiCall {
        // RA-64: URL-encode the search term (may contain spaces/'#'/'&').
        client.get(getUrl("api/v1/school/announcements/search")) {
            parameter("query", query)
        }
    }

    suspend fun createAnnouncement(
        token: String,
        request: CreateAnnouncementRequest
    ): NetworkResult<ApiResponse<AnnouncementDto>> = safeApiCall {
        client.post(getUrl("api/v1/school/announcements")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun syncWhatsApp(
        token: String
    ): NetworkResult<ApiResponse<SyncWhatsAppResponse>> = safeApiCall {
        client.post(getUrl("api/v1/school/announcements/sync-whatsapp")) {
            contentType(ContentType.Application.Json)
            setBody(emptyMap<String, String>())
        }
    }
}
