package com.littlebridge.enrollplus.feature.admin.data.repository

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.admin.data.remote.LinkRequestsApi
import com.littlebridge.enrollplus.feature.admin.domain.model.LinkDecisionResult
import com.littlebridge.enrollplus.feature.admin.domain.model.LinkRequestsResponse
import com.littlebridge.enrollplus.feature.admin.domain.repository.LinkRequestsRepository

class LinkRequestsRepositoryImpl(
    private val api: LinkRequestsApi
) : LinkRequestsRepository {

    override suspend fun getLinkRequests(
        token: String,
        status: String
    ): NetworkResult<ApiResponse<LinkRequestsResponse>> = api.getLinkRequests(token, status)

    override suspend fun approve(
        token: String,
        id: String
    ): NetworkResult<ApiResponse<LinkDecisionResult>> = api.approve(token, id)

    override suspend fun reject(
        token: String,
        id: String
    ): NetworkResult<ApiResponse<LinkDecisionResult>> = api.reject(token, id)
}
