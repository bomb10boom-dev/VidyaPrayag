/*
 * File: StaffApi.kt
 * Module: feature.admin.data.remote
 *
 * RA-S17: network client for the admin Non-teaching-staff vertical. Bearer
 * token is attached by the shared HttpClient Auth plugin.
 *
 * Server routes (feature.school.NonTeachingStaffRouting.kt):
 *   GET    /api/v1/school/staff          (?q=&department=)
 *   POST   /api/v1/school/staff
 *   GET    /api/v1/school/staff/{id}
 *   PATCH  /api/v1/school/staff/{id}
 *   DELETE /api/v1/school/staff/{id}
 */
package com.littlebridge.enrollplus.feature.admin.data.remote

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.network.safeApiCall
import com.littlebridge.enrollplus.feature.admin.domain.model.CreateStaffRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.StaffDto
import com.littlebridge.enrollplus.feature.admin.domain.model.StaffListResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.UpdateStaffRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class StaffApi(
    private val client: HttpClient,
    private val baseUrl: String
) {

    private fun getUrl(path: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cleanPath = if (path.startsWith("/")) path.substring(1) else path
        return "$base$cleanPath"
    }

    suspend fun getStaff(
        token: String,
        query: String? = null,
        department: String? = null
    ): NetworkResult<ApiResponse<StaffListResponse>> = safeApiCall {
        client.get(getUrl("api/v1/school/staff")) {
            if (!query.isNullOrBlank()) parameter("q", query)
            if (!department.isNullOrBlank()) parameter("department", department)
        }
    }

    suspend fun createStaff(
        token: String,
        request: CreateStaffRequest
    ): NetworkResult<ApiResponse<StaffDto>> = safeApiCall {
        client.post(getUrl("api/v1/school/staff")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun getStaffProfile(
        token: String,
        staffId: String
    ): NetworkResult<ApiResponse<StaffDto>> = safeApiCall {
        client.get(getUrl("api/v1/school/staff/$staffId"))
    }

    suspend fun updateStaff(
        token: String,
        staffId: String,
        request: UpdateStaffRequest
    ): NetworkResult<ApiResponse<StaffDto>> = safeApiCall {
        client.patch(getUrl("api/v1/school/staff/$staffId")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun deleteStaff(
        token: String,
        staffId: String
    ): NetworkResult<ApiResponse<Unit>> = safeApiCall {
        client.delete(getUrl("api/v1/school/staff/$staffId"))
    }
}
