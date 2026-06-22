/*
 * File: AcademicYearApi.kt
 * Module: feature.admin.data.remote
 *
 * Network client for Academic Year management (VP-CAL).
 *
 * Server routes (feature.calendar.AcademicYearRouting):
 *   GET  /api/admin/academic-years
 *   POST /api/admin/academic-years
 *   PUT  /api/admin/academic-years/{id}
 */
package com.littlebridge.enrollplus.feature.admin.data.remote

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.network.safeApiCall
import com.littlebridge.enrollplus.feature.admin.domain.model.AcademicYearDto
import com.littlebridge.enrollplus.feature.admin.domain.model.AcademicYearsListResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.CreateAcademicYearRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.UpdateAcademicYearRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class AcademicYearApi(
    private val client: HttpClient,
    private val baseUrl: String
) {
    private fun url(path: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val clean = if (path.startsWith("/")) path.substring(1) else path
        return "$base$clean"
    }

    suspend fun getYears(
        token: String
    ): NetworkResult<ApiResponse<AcademicYearsListResponse>> = safeApiCall {
        client.get(url("api/admin/academic-years"))
    }

    suspend fun createYear(
        token: String,
        request: CreateAcademicYearRequest
    ): NetworkResult<ApiResponse<AcademicYearDto>> = safeApiCall {
        client.post(url("api/admin/academic-years")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun updateYear(
        token: String,
        yearId: String,
        request: UpdateAcademicYearRequest
    ): NetworkResult<ApiResponse<AcademicYearDto>> = safeApiCall {
        client.put(url("api/admin/academic-years/$yearId")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }
}
