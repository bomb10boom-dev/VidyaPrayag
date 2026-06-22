/*
 * File: AdmissionApi.kt
 * Module: feature.admin.data.remote
 *
 * Network client for the admission-CRM endpoints. Mirrors [OnboardingApi] - the
 * caller passes the JWT token in and we attach it as an Authorization header.
 *
 * Server routes:
 *   GET   /api/v1/admissions/enquiries/summary
 *   GET   /api/v1/admissions/enquiries?page=&limit=
 *   POST  /api/v1/admissions/enquiries
 *   PATCH /api/v1/admissions/enquiries/{id}/status
 */
package com.littlebridge.enrollplus.feature.admin.data.remote

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.network.safeApiCall
import com.littlebridge.enrollplus.feature.admin.domain.model.CreateEnquiryRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.Enquiry
import com.littlebridge.enrollplus.feature.admin.domain.model.EnquiryListResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.EnquirySummary
import com.littlebridge.enrollplus.feature.admin.domain.model.UpdateEnquiryStatusRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class AdmissionApi(
    private val client: HttpClient,
    private val baseUrl: String
) {

    private fun getUrl(path: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cleanPath = if (path.startsWith("/")) path.substring(1) else path
        return "$base$cleanPath"
    }

    suspend fun getSummary(
        token: String
    ): NetworkResult<ApiResponse<EnquirySummary>> = safeApiCall {
        client.get(getUrl("api/v1/admissions/enquiries/summary"))
    }

    suspend fun listEnquiries(
        token: String,
        page: Int,
        limit: Int
    ): NetworkResult<ApiResponse<EnquiryListResponse>> = safeApiCall {
        client.get(getUrl("api/v1/admissions/enquiries")) {
            parameter("page", page)
            parameter("limit", limit)
        }
    }

    suspend fun createEnquiry(
        token: String,
        request: CreateEnquiryRequest
    ): NetworkResult<ApiResponse<Enquiry>> = safeApiCall {
        client.post(getUrl("api/v1/admissions/enquiries")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    /**
     * The server replies with `{ success, message }` (no `data`) for this
     * endpoint, so we type the envelope as `ApiResponse<Unit>` - the
     * [com.littlebridge.enrollplus.feature.admin.data.repository.AdmissionRepositoryImpl]
     * just inspects `success`.
     */
    suspend fun updateEnquiryStatus(
        token: String,
        enquiryId: String,
        status: String
    ): NetworkResult<ApiResponse<Unit>> = safeApiCall {
        client.patch(getUrl("api/v1/admissions/enquiries/$enquiryId/status")) {
            contentType(ContentType.Application.Json)
            setBody(UpdateEnquiryStatusRequest(status = status))
        }
    }
}
