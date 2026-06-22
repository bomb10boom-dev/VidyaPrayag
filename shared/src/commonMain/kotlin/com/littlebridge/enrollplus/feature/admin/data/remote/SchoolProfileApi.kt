/*
 * File: SchoolProfileApi.kt
 * Module: feature.admin.data.remote
 *
 * RA-47: network client for the institutional-profile (schools row) endpoints.
 * The bearer token is attached by the shared HttpClient's Auth plugin
 * (di/Koin.kt install(Auth)); we keep `token` for parity with the other admin
 * APIs.
 *
 * Server routes (feature.school.SchoolProfileRouting.kt):
 *   GET /api/v1/school/profile
 *   PUT /api/v1/school/profile
 */
package com.littlebridge.enrollplus.feature.admin.data.remote

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.network.safeApiCall
import com.littlebridge.enrollplus.feature.admin.domain.model.SchoolProfileDto
import com.littlebridge.enrollplus.feature.admin.domain.model.UpdateSchoolProfileRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class SchoolProfileApi(
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
    ): NetworkResult<ApiResponse<SchoolProfileDto>> = safeApiCall {
        client.get(getUrl("api/v1/school/profile"))
    }

    suspend fun updateProfile(
        token: String,
        request: UpdateSchoolProfileRequest
    ): NetworkResult<ApiResponse<SchoolProfileDto>> = safeApiCall {
        client.put(getUrl("api/v1/school/profile")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }
}
