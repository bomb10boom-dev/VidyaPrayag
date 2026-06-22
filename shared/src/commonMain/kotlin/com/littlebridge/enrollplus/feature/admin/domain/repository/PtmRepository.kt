package com.littlebridge.enrollplus.feature.admin.domain.repository

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.admin.domain.model.CreatePtmRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.PtmActiveEventDto
import com.littlebridge.enrollplus.feature.admin.domain.model.PtmResponse

interface PtmRepository {
    suspend fun getPtm(token: String): NetworkResult<ApiResponse<PtmResponse>>
    suspend fun createPtm(token: String, request: CreatePtmRequest): NetworkResult<ApiResponse<PtmActiveEventDto>>
}
