/*
 * File: ResultsApi.kt
 * Module: feature.admin.data.remote
 *
 * Server routes:
 *   GET  /api/v1/school/results?test=...&class=...&subject=...
 *   POST /api/v1/school/results
 */
package com.littlebridge.enrollplus.feature.admin.data.remote

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.network.safeApiCall
import com.littlebridge.enrollplus.feature.admin.domain.model.PublishResultsRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.PublishResultsResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.ResultsResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class ResultsApi(
    private val client: HttpClient,
    private val baseUrl: String
) {
    private fun getUrl(path: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cleanPath = if (path.startsWith("/")) path.substring(1) else path
        return "$base$cleanPath"
    }

    suspend fun getResults(
        token: String,
        test: String? = null,
        className: String? = null,
        subject: String? = null
    ): NetworkResult<ApiResponse<ResultsResponse>> = safeApiCall {
        // RA-64: URL-encode via parameter(...) (test/class/subject may contain spaces).
        client.get(getUrl("api/v1/school/results")) {
            test?.let { parameter("test", it) }
            className?.let { parameter("class", it) }
            subject?.let { parameter("subject", it) }
        }
    }

    suspend fun publishResults(
        token: String,
        request: PublishResultsRequest
    ): NetworkResult<ApiResponse<PublishResultsResponse>> = safeApiCall {
        client.post(getUrl("api/v1/school/results")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }
}
