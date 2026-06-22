/*
 * File: CalendarApi.kt
 * Module: feature.admin.data.remote
 *
 * Network client for academic calendar endpoint.
 * Server route: GET /api/v1/school/calendar
 */
package com.littlebridge.enrollplus.feature.admin.data.remote

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.network.safeApiCall
import com.littlebridge.enrollplus.feature.admin.domain.model.CalendarResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class CalendarApi(
    private val client: HttpClient,
    private val baseUrl: String
) {

    private fun getUrl(path: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cleanPath = if (path.startsWith("/")) path.substring(1) else path
        return "$base$cleanPath"
    }

    suspend fun getCalendar(
        token: String,
        date: String? = null,
        viewType: String = "month"
    ): NetworkResult<ApiResponse<CalendarResponse>> = safeApiCall {
        // RA-64: URL-encode via parameter(...).
        client.get(getUrl("api/v1/school/calendar")) {
            date?.let { parameter("date", it) }
            parameter("view_type", viewType)
        }
    }
}
