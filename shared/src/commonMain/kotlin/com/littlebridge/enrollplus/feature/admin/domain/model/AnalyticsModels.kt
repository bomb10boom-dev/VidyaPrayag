/*
 * File: AnalyticsModels.kt
 * Module: feature.admin.domain.model
 *
 * DTOs for school analytics endpoints.
 * Matches server: feature.school.SchoolAnalyticsRouting.kt
 *
 * GET /api/v1/school/analytics/overview            → AnalyticsOverviewResponse
 * GET /api/v1/school/analytics/student/{studentId} → StudentAnalyticsResponse
 * GET /api/v1/school/analytics/class-performance   → opaque JsonObject (forwarded as-is)
 * GET /api/v1/school/analytics/teacher-performance → opaque JsonObject (forwarded as-is)
 * GET /api/v1/school/analytics/syllabus-coverage   → opaque JsonObject (forwarded as-is)
 *
 * The "opaque" endpoints return CMS-driven blobs so we store them as
 * Map<String,String>? and let the UI handle unknown keys.
 */
package com.littlebridge.enrollplus.feature.admin.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AnalyticsOverviewResponse(
    @SerialName("performance_trend") val performanceTrend: List<Double> = emptyList(),
    @SerialName("trend_labels") val trendLabels: List<String> = emptyList(),
    @SerialName("current_growth") val currentGrowth: String = "0%",
    val cards: List<JsonElement> = emptyList(),
    val insights: List<JsonElement> = emptyList()
)

@Serializable
data class StudentAnalyticsHeader(
    val id: String,
    val name: String,
    @SerialName("class") val className: String,
    @SerialName("roll_number") val rollNumber: String,
    @SerialName("profile_pic") val profilePic: String? = null
)

@Serializable
data class StudentAnalyticsKpi(
    val attendance: String,
    val average: String,
    val rank: Int
)

@Serializable
data class StudentAnalyticsResponse(
    val student: StudentAnalyticsHeader,
    val kpi: StudentAnalyticsKpi,
    val subjects: List<JsonElement> = emptyList(),
    val milestones: List<JsonElement> = emptyList(),
    val narrative: String = ""
)
