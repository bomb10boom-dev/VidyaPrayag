package com.littlebridge.enrollplus.feature.admin.data.repository

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.admin.data.remote.AnalyticsApi
import com.littlebridge.enrollplus.feature.admin.domain.model.AnalyticsOverviewResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.StudentAnalyticsResponse
import com.littlebridge.enrollplus.feature.admin.domain.repository.AnalyticsRepository
import kotlinx.serialization.json.JsonElement

class AnalyticsRepositoryImpl(private val api: AnalyticsApi) : AnalyticsRepository {
    override suspend fun getOverview(token: String): NetworkResult<ApiResponse<AnalyticsOverviewResponse>> =
        api.getOverview(token)

    override suspend fun getStudentAnalytics(token: String, studentId: String): NetworkResult<ApiResponse<StudentAnalyticsResponse>> =
        api.getStudentAnalytics(token, studentId)

    override suspend fun getClassPerformance(token: String, className: String?): NetworkResult<ApiResponse<JsonElement>> =
        api.getClassPerformance(token, className)

    override suspend fun getTeacherPerformance(token: String): NetworkResult<ApiResponse<JsonElement>> =
        api.getTeacherPerformance(token)

    override suspend fun getSyllabusCoverage(token: String): NetworkResult<ApiResponse<JsonElement>> =
        api.getSyllabusCoverage(token)

    override suspend fun getStudentCohort(token: String): NetworkResult<ApiResponse<JsonElement>> =
        api.getStudentCohort(token)
}
