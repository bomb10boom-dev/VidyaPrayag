/*
 * File: LeaveRequestsApi.kt
 * Module: feature.admin.data.remote
 *
 * Network client for leave-requests endpoints.
 *
 * Server routes:
 *   GET   /api/v1/school/leave-requests?type=student|teacher&status=...
 *   POST  /api/v1/school/leave-requests
 *   PATCH /api/v1/school/leave-requests/{id}/status
 */
package com.littlebridge.enrollplus.feature.admin.data.remote

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.network.safeApiCall
import com.littlebridge.enrollplus.feature.admin.domain.model.CreateLeaveRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.LeaveRequestDto
import com.littlebridge.enrollplus.feature.admin.domain.model.LeaveRequestsResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.UpdateLeaveStatusRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class LeaveRequestsApi(
    private val client: HttpClient,
    private val baseUrl: String
) {

    private fun getUrl(path: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cleanPath = if (path.startsWith("/")) path.substring(1) else path
        return "$base$cleanPath"
    }

    suspend fun getLeaveRequests(
        token: String,
        type: String = "student",
        status: String? = null
    ): NetworkResult<ApiResponse<LeaveRequestsResponse>> = safeApiCall {
        // RA-64: URL-encode via parameter(...).
        client.get(getUrl("api/v1/school/leave-requests")) {
            parameter("type", type)
            status?.let { parameter("status", it) }
        }
    }

    suspend fun createLeaveRequest(
        token: String,
        request: CreateLeaveRequest
    ): NetworkResult<ApiResponse<LeaveRequestDto>> = safeApiCall {
        client.post(getUrl("api/v1/school/leave-requests")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun updateLeaveStatus(
        token: String,
        id: String,
        status: String
    ): NetworkResult<ApiResponse<LeaveRequestDto>> = safeApiCall {
        client.patch(getUrl("api/v1/school/leave-requests/$id/status")) {
            contentType(ContentType.Application.Json)
            setBody(UpdateLeaveStatusRequest(status))
        }
    }
}
