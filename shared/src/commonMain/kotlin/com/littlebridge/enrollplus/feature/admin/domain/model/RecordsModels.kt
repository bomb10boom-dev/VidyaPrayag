/*
 * File: RecordsModels.kt
 * Module: feature.admin.domain.model
 *
 * RA-52: DTOs for the admin Records rollups. Mirror server:
 * feature.school.SchoolRecordsRouting.kt
 *   GET /api/v1/school/attendance/summary
 *   GET /api/v1/school/marks/summary
 *   GET /api/v1/school/fees/ledger
 */
package com.littlebridge.enrollplus.feature.admin.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AttendanceClassRow(
    val grade: String,
    val present: Int,
    val absent: Int,
    val late: Int,
    val total: Int,
    val rate: Int
)

@Serializable
data class AttendanceSummaryDto(
    @SerialName("latest_date") val latestDate: String? = null,
    val present: Int = 0,
    val absent: Int = 0,
    val late: Int = 0,
    val total: Int = 0,
    val rate: Int = 0,
    @SerialName("by_class") val byClass: List<AttendanceClassRow> = emptyList()
)

@Serializable
data class MarksAssessmentRow(
    val subject: String,
    @SerialName("assessment") val assessmentName: String,
    @SerialName("class_name") val className: String,
    val average: Double,
    @SerialName("max_marks") val maxMarks: Int,
    @SerialName("graded_count") val gradedCount: Int,
    @SerialName("exam_date") val examDate: String? = null,
    @SerialName("is_published") val isPublished: Boolean
)

@Serializable
data class MarksSummaryDto(
    @SerialName("assessment_count") val assessmentCount: Int = 0,
    @SerialName("overall_average_pct") val overallAveragePct: Int = 0,
    val assessments: List<MarksAssessmentRow> = emptyList()
)

@Serializable
data class FeeRow(
    val title: String,
    val amount: Double,
    val currency: String,
    val status: String,
    @SerialName("due_date") val dueDate: String? = null,
    val category: String
)

@Serializable
data class FeeLedgerDto(
    @SerialName("paid_total") val paidTotal: Double = 0.0,
    @SerialName("due_total") val dueTotal: Double = 0.0,
    @SerialName("overdue_total") val overdueTotal: Double = 0.0,
    @SerialName("paid_count") val paidCount: Int = 0,
    @SerialName("due_count") val dueCount: Int = 0,
    @SerialName("overdue_count") val overdueCount: Int = 0,
    val currency: String = "INR",
    val recent: List<FeeRow> = emptyList()
)
