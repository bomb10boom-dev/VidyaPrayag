package com.littlebridge.enrollplus.feature.admin.domain.repository

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.admin.domain.model.AnalyticsOverviewResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.StudentAnalyticsResponse
import kotlinx.serialization.json.JsonElement

interface AnalyticsRepository {
    suspend fun getOverview(token: String): NetworkResult<ApiResponse<AnalyticsOverviewResponse>>
    suspend fun getStudentAnalytics(token: String, studentId: String): NetworkResult<ApiResponse<StudentAnalyticsResponse>>
    suspend fun getClassPerformance(token: String, className: String? = null): NetworkResult<ApiResponse<JsonElement>>
    suspend fun getTeacherPerformance(token: String): NetworkResult<ApiResponse<JsonElement>>
    suspend fun getSyllabusCoverage(token: String): NetworkResult<ApiResponse<JsonElement>>
    suspend fun getStudentCohort(token: String): NetworkResult<ApiResponse<JsonElement>>
}
