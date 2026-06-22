package com.littlebridge.enrollplus.feature.admin.data.repository

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.admin.data.remote.PtmApi
import com.littlebridge.enrollplus.feature.admin.domain.model.CreatePtmRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.PtmActiveEventDto
import com.littlebridge.enrollplus.feature.admin.domain.model.PtmResponse
import com.littlebridge.enrollplus.feature.admin.domain.repository.PtmRepository

class PtmRepositoryImpl(
    private val api: PtmApi
) : PtmRepository {

    override suspend fun getPtm(token: String): NetworkResult<ApiResponse<PtmResponse>> =
        api.getPtm(token)

    override suspend fun createPtm(token: String, request: CreatePtmRequest): NetworkResult<ApiResponse<PtmActiveEventDto>> =
        api.createPtm(token, request)
}
