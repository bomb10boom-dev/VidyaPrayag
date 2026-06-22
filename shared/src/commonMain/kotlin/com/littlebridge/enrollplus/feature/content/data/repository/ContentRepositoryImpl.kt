package com.littlebridge.enrollplus.feature.content.data.repository

import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.content.data.remote.ContentApi
import com.littlebridge.enrollplus.feature.content.domain.model.LandingData
import com.littlebridge.enrollplus.feature.content.domain.repository.ContentRepository

class ContentRepositoryImpl(
    private val api: ContentApi
) : ContentRepository {
    override suspend fun getLandingContent(): NetworkResult<LandingData> {
        return when (val result = api.getLandingContent()) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.data)
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }
}
