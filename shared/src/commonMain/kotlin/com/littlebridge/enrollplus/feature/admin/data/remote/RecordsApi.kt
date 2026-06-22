/*
 * File: RecordsApi.kt
 * Module: feature.admin.data.remote
 *
 * RA-52: network client for the admin Records rollups. Bearer token attached by
 * the shared HttpClient Auth plugin.
 *
 * Server routes (feature.school.SchoolRecordsRouting.kt):
 *   GET /api/v1/school/attendance/summary
 *   GET /api/v1/school/marks/summary
 *   GET /api/v1/school/fees/ledger
 */
package com.littlebridge.enrollplus.feature.admin.data.remote

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.network.safeApiCall
import com.littlebridge.enrollplus.feature.admin.domain.model.AttendanceSummaryDto
import com.littlebridge.enrollplus.feature.admin.domain.model.FeeLedgerDto
import com.littlebridge.enrollplus.feature.admin.domain.model.MarksSummaryDto
import io.ktor.client.HttpClient
import io.ktor.client.request.get

class RecordsApi(
    private val client: HttpClient,
    private val baseUrl: String
) {

    private fun getUrl(path: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cleanPath = if (path.startsWith("/")) path.substring(1) else path
        return "$base$cleanPath"
    }

    suspend fun getAttendanceSummary(
        token: String
    ): NetworkResult<ApiResponse<AttendanceSummaryDto>> = safeApiCall {
        client.get(getUrl("api/v1/school/attendance/summary"))
    }

    suspend fun getMarksSummary(
        token: String
    ): NetworkResult<ApiResponse<MarksSummaryDto>> = safeApiCall {
        client.get(getUrl("api/v1/school/marks/summary"))
    }

    suspend fun getFeeLedger(
        token: String
    ): NetworkResult<ApiResponse<FeeLedgerDto>> = safeApiCall {
        client.get(getUrl("api/v1/school/fees/ledger"))
    }
}
