/*
 * File: PtmApi.kt
 * Module: feature.admin.data.remote
 *
 * Network client for PTM endpoints.
 *
 * Server routes:
 *   GET  /api/v1/school/ptm
 *   POST /api/v1/school/ptm
 */
package com.littlebridge.enrollplus.feature.admin.data.remote

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.network.safeApiCall
import com.littlebridge.enrollplus.feature.admin.domain.model.CreatePtmRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.PtmActiveEventDto
import com.littlebridge.enrollplus.feature.admin.domain.model.PtmResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class PtmApi(
    private val client: HttpClient,
    private val baseUrl: String
) {

    private fun getUrl(path: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cleanPath = if (path.startsWith("/")) path.substring(1) else path
        return "$base$cleanPath"
    }

    suspend fun getPtm(
        token: String
    ): NetworkResult<ApiResponse<PtmResponse>> = safeApiCall {
        client.get(getUrl("api/v1/school/ptm"))
    }

    suspend fun createPtm(
        token: String,
        request: CreatePtmRequest
    ): NetworkResult<ApiResponse<PtmActiveEventDto>> = safeApiCall {
        client.post(getUrl("api/v1/school/ptm")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }
}
