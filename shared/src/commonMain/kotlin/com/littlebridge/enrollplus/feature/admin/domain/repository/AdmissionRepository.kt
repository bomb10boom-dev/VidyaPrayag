/*
 * File: AdmissionRepository.kt
 * Module: feature.admin.domain.repository
 *
 * Domain-layer abstraction over the admissions endpoints. Implementations are
 * responsible for talking to the network layer (typically via
 * [com.littlebridge.enrollplus.feature.admin.data.remote.AdmissionApi]) and
 * unwrapping the server's ApiResponse envelope so callers (ViewModels) only
 * deal with the inner data types.
 *
 * Mirrors the design of OnboardingRepository.
 */
package com.littlebridge.enrollplus.feature.admin.domain.repository

import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.admin.domain.model.CreateEnquiryRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.Enquiry
import com.littlebridge.enrollplus.feature.admin.domain.model.EnquiryListResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.EnquirySummary

interface AdmissionRepository {

    /** GET /api/v1/admissions/enquiries/summary */
    suspend fun getSummary(token: String): NetworkResult<EnquirySummary>

    /**
     * GET /api/v1/admissions/enquiries?page=&limit=
     *
     * Server clamps `limit` to [1, 100] and `page` to >= 1.
     */
    suspend fun listEnquiries(
        token: String,
        page: Int = 1,
        limit: Int = 20
    ): NetworkResult<EnquiryListResponse>

    /** POST /api/v1/admissions/enquiries */
    suspend fun createEnquiry(
        token: String,
        request: CreateEnquiryRequest
    ): NetworkResult<Enquiry>

    /**
     * PATCH /api/v1/admissions/enquiries/{id}/status
     *
     * The server returns just a success message (no data). On success we
     * surface [Unit] so the VM can decide whether to optimistically patch
     * its in-memory list or refetch.
     */
    suspend fun updateEnquiryStatus(
        token: String,
        enquiryId: String,
        status: String
    ): NetworkResult<Unit>
}
