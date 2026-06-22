package com.littlebridge.enrollplus.feature.content.data.remote

import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.network.safeApiCall
import com.littlebridge.enrollplus.feature.content.domain.model.LandingData
import com.littlebridge.enrollplus.feature.content.domain.model.LandingResponse
import io.ktor.client.*
import io.ktor.client.request.*

class ContentApi(
    private val client: HttpClient,
    private val baseUrl: String
) {
    suspend fun getLandingContent(): NetworkResult<LandingResponse> {
        val url = if (baseUrl.endsWith("/")) "${baseUrl}api/v1/content/landing" else "$baseUrl/api/v1/content/landing"
        return safeApiCall {
            client.get(url)
        }
    }
}
