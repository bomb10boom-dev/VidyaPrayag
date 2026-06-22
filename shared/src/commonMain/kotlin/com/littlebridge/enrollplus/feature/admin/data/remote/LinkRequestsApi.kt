/*
 * File: LinkRequestsApi.kt
 * Module: feature.admin.data.remote
 *
 * RA-48: network client for the school-admin link-request queue. The bearer
 * token is attached by the shared HttpClient's Auth plugin (see di/Koin.kt
 * install(Auth)); we pass `token` for parity with the other admin APIs.
 *
 * Server routes:
 *   GET  /api/v1/school/link-requests?status=pending|approved|rejected
 *   POST /api/v1/school/link-requests/{id}/approve
 *   POST /api/v1/school/link-requests/{id}/reject
 */
package com.littlebridge.enrollplus.feature.admin.data.remote

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.network.safeApiCall
import com.littlebridge.enrollplus.feature.admin.domain.model.LinkDecisionResult
import com.littlebridge.enrollplus.feature.admin.domain.model.LinkRequestsResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post

class LinkRequestsApi(
    private val client: HttpClient,
    private val baseUrl: String
) {

    private fun getUrl(path: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cleanPath = if (path.startsWith("/")) path.substring(1) else path
        return "$base$cleanPath"
    }

    suspend fun getLinkRequests(
        token: String,
        status: String = "pending"
    ): NetworkResult<ApiResponse<LinkRequestsResponse>> = safeApiCall {
        // RA-64: URL-encode via parameter(...).
        client.get(getUrl("api/v1/school/link-requests")) {
            parameter("status", status)
        }
    }

    suspend fun approve(
        token: String,
        id: String
    ): NetworkResult<ApiResponse<LinkDecisionResult>> = safeApiCall {
        client.post(getUrl("api/v1/school/link-requests/$id/approve"))
    }

    suspend fun reject(
        token: String,
        id: String
    ): NetworkResult<ApiResponse<LinkDecisionResult>> = safeApiCall {
        client.post(getUrl("api/v1/school/link-requests/$id/reject"))
    }
}
