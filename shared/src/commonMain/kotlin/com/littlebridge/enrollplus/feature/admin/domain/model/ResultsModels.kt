/*
 * File: ResultsModels.kt
 * Module: feature.admin.domain.model
 *
 * DTOs for results endpoints.
 * Matches server: feature.school.ResultsRouting.kt
 *
 * GET  /api/v1/school/results?test=...&class=...&subject=... → ResultsResponse
 * POST /api/v1/school/results                                → PublishResultsResponse
 */
package com.littlebridge.enrollplus.feature.admin.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ResultsFiltersDto(
    @SerialName("selected_test") val selectedTest: String,
    @SerialName("selected_class") val selectedClass: String,
    @SerialName("selected_subject") val selectedSubject: String,
    @SerialName("available_tests") val availableTests: List<String> = emptyList(),
    @SerialName("available_classes") val availableClasses: List<String> = emptyList(),
    @SerialName("available_subjects") val availableSubjects: List<String> = emptyList()
)

@Serializable
data class ResultsSummaryDto(
    @SerialName("class_average") val classAverage: String,
    @SerialName("average_trend") val averageTrend: String,
    @SerialName("exceeding_count") val exceedingCount: Int,
    @SerialName("meeting_count") val meetingCount: Int,
    @SerialName("below_count") val belowCount: Int
)

@Serializable
data class ResultStudentDto(
    val id: String,
    val name: String,
    @SerialName("image_url") val imageUrl: String? = null,
    val attendance: String,
    val score: String,
    val status: String,
    val trend: String
)

@Serializable
data class ResultsResponse(
    val filters: ResultsFiltersDto,
    val summary: ResultsSummaryDto,
    val students: List<ResultStudentDto>
)

@Serializable
data class PublishResultsRow(
    @SerialName("student_id") val studentId: String,
    @SerialName("student_name") val studentName: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val attendance: String? = null,
    val score: String,
    val status: String? = null,
    val trend: String? = null
)

@Serializable
data class PublishResultsRequest(
    val test: String,
    @SerialName("class") val className: String,
    val subject: String,
    val results: List<PublishResultsRow>
)

@Serializable
data class PublishResultsResponse(val upserted: Int)
