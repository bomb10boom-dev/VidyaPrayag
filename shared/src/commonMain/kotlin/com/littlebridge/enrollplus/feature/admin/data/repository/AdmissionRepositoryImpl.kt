/*
 * File: AdmissionRepositoryImpl.kt
 * Module: feature.admin.data.repository
 *
 * Default [AdmissionRepository] implementation. Mirrors the unwrapping pattern
 * used by OnboardingRepositoryImpl: take the ApiResponse envelope returned by
 * the server, surface a clean error if `success=false` (or `data` was missing
 * for endpoints that should return a body), otherwise return the inner data.
 */
package com.littlebridge.enrollplus.feature.admin.data.repository

import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.admin.data.remote.AdmissionApi
import com.littlebridge.enrollplus.feature.admin.domain.model.CreateEnquiryRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.Enquiry
import com.littlebridge.enrollplus.feature.admin.domain.model.EnquiryListResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.EnquirySummary
import com.littlebridge.enrollplus.feature.admin.domain.repository.AdmissionRepository

class AdmissionRepositoryImpl(
    private val api: AdmissionApi
) : AdmissionRepository {

    override suspend fun getSummary(token: String): NetworkResult<EnquirySummary> {
        return when (val result = api.getSummary(token)) {
            is NetworkResult.Success -> {
                val envelope = result.data
                val data = envelope.data
                when {
                    !envelope.success -> NetworkResult.Error(
                        envelope.message.ifBlank { "Failed to fetch enquiry summary" }
                    )
                    data == null -> NetworkResult.Error("No data in response")
                    else -> NetworkResult.Success(data)
                }
            }
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }

    override suspend fun listEnquiries(
        token: String,
        page: Int,
        limit: Int
    ): NetworkResult<EnquiryListResponse> {
        return when (val result = api.listEnquiries(token, page, limit)) {
            is NetworkResult.Success -> {
                val envelope = result.data
                val data = envelope.data
                when {
                    !envelope.success -> NetworkResult.Error(
                        envelope.message.ifBlank { "Failed to fetch enquiries" }
                    )
                    data == null -> NetworkResult.Error("No data in response")
                    else -> NetworkResult.Success(data)
                }
            }
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }

    override suspend fun createEnquiry(
        token: String,
        request: CreateEnquiryRequest
    ): NetworkResult<Enquiry> {
        return when (val result = api.createEnquiry(token, request)) {
            is NetworkResult.Success -> {
                val envelope = result.data
                val data = envelope.data
                when {
                    !envelope.success -> NetworkResult.Error(
                        envelope.message.ifBlank { "Failed to create enquiry" }
                    )
                    data == null -> NetworkResult.Error("No data in response")
                    else -> NetworkResult.Success(data)
                }
            }
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }

    override suspend fun updateEnquiryStatus(
        token: String,
        enquiryId: String,
        status: String
    ): NetworkResult<Unit> {
        return when (val result = api.updateEnquiryStatus(token, enquiryId, status)) {
            is NetworkResult.Success -> {
                val envelope = result.data
                if (!envelope.success) {
                    NetworkResult.Error(
                        envelope.message.ifBlank { "Failed to update enquiry status" }
                    )
                } else {
                    NetworkResult.Success(Unit)
                }
            }
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }
}
