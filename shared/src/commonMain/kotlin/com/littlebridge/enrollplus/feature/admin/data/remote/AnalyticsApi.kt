/*
 * File: AnalyticsApi.kt
 * Module: feature.admin.data.remote
 *
 * Network client for school analytics endpoints.
 *
 * Server routes:
 *   GET /api/v1/school/analytics/overview
 *   GET /api/v1/school/analytics/class-performance?class=<optional>
 *   GET /api/v1/school/analytics/teacher-performance
 *   GET /api/v1/school/analytics/student/{studentId}
 *   GET /api/v1/school/analytics/syllabus-coverage
 *   GET /api/v1/school/analytics/student-cohort
 *
 * For the four "opaque" endpoints (class-performance, teacher-performance,
 * syllabus-coverage, student-cohort) the server returns a CMS-driven JsonObject under `data`.
 * We model that as `ApiResponse<JsonElement>` so the ViewModel can parse only
 * the keys it currently uses without forcing a DTO change every time ops adds
 * a new field on the server side.
 */
package com.littlebridge.enrollplus.feature.admin.data.remote

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.network.safeApiCall
import com.littlebridge.enrollplus.feature.admin.domain.model.AnalyticsOverviewResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.StudentAnalyticsResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.json.JsonElement

class AnalyticsApi(
    private val client: HttpClient,
    private val baseUrl: String
) {

    private fun getUrl(path: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cleanPath = if (path.startsWith("/")) path.substring(1) else path
        return "$base$cleanPath"
    }

    suspend fun getOverview(
        token: String
    ): NetworkResult<ApiResponse<AnalyticsOverviewResponse>> = safeApiCall {
        client.get(getUrl("api/v1/school/analytics/overview"))
    }

    suspend fun getStudentAnalytics(
        token: String,
        studentId: String
    ): NetworkResult<ApiResponse<StudentAnalyticsResponse>> = safeApiCall {
        client.get(getUrl("api/v1/school/analytics/student/$studentId"))
    }

    /**
     * Returns the raw `school_class_performance` CMS blob (with live
     * `summary.active_students` overlay). Caller is responsible for picking
     * out keys it cares about.
     */
    suspend fun getClassPerformance(
        token: String,
        className: String? = null
    ): NetworkResult<ApiResponse<JsonElement>> = safeApiCall {
        client.get(getUrl("api/v1/school/analytics/class-performance")) {
            if (!className.isNullOrBlank()) parameter("class", className)
        }
    }

    /**
     * Returns the raw `school_teacher_performance` CMS blob plus a live
     * `star_faculty` array computed from the `faculty` table.
     */
    suspend fun getTeacherPerformance(
        token: String
    ): NetworkResult<ApiResponse<JsonElement>> = safeApiCall {
        client.get(getUrl("api/v1/school/analytics/teacher-performance"))
    }

    /**
     * Returns the raw `school_syllabus_coverage` CMS blob verbatim.
     */
    suspend fun getSyllabusCoverage(
        token: String
    ): NetworkResult<ApiResponse<JsonElement>> = safeApiCall {
        client.get(getUrl("api/v1/school/analytics/syllabus-coverage"))
    }

    /**
     * Cohort-level student analytics — the dashboard view, NOT the per-student
     * drilldown (which is `getStudentAnalytics`).
     */
    suspend fun getStudentCohort(
        token: String
    ): NetworkResult<ApiResponse<JsonElement>> = safeApiCall {
        client.get(getUrl("api/v1/school/analytics/student-cohort"))
    }
}
