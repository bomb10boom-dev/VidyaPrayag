package com.littlebridge.enrollplus.feature.admin.domain.repository

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.admin.domain.model.PublishResultsRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.PublishResultsResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.ResultsResponse

interface ResultsRepository {
    suspend fun getResults(token: String, test: String? = null, className: String? = null, subject: String? = null): NetworkResult<ApiResponse<ResultsResponse>>
    suspend fun publishResults(token: String, request: PublishResultsRequest): NetworkResult<ApiResponse<PublishResultsResponse>>
}
