package com.littlebridge.enrollplus.feature.admin.data.repository

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.admin.data.remote.ResultsApi
import com.littlebridge.enrollplus.feature.admin.domain.model.PublishResultsRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.PublishResultsResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.ResultsResponse
import com.littlebridge.enrollplus.feature.admin.domain.repository.ResultsRepository

class ResultsRepositoryImpl(private val api: ResultsApi) : ResultsRepository {
    override suspend fun getResults(token: String, test: String?, className: String?, subject: String?): NetworkResult<ApiResponse<ResultsResponse>> =
        api.getResults(token, test, className, subject)

    override suspend fun publishResults(token: String, request: PublishResultsRequest): NetworkResult<ApiResponse<PublishResultsResponse>> =
        api.publishResults(token, request)
}
