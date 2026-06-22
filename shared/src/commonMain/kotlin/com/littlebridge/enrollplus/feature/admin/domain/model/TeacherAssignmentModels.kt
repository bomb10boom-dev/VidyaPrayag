/*
 * File: TeacherAssignmentModels.kt
 * Module: feature.admin.domain.model
 *
 * RA-TAM: DTOs for the Teacher Assignment Management feature. These mirror the
 * server payloads in feature.school.TeacherAssignmentRouting.kt so the same JSON
 * decodes on both sides:
 *   GET    /api/v1/school/teacher-assignments/overview/{teacherId}
 *   GET    /api/v1/school/teacher-assignments/options
 *   POST   /api/v1/school/teacher-assignments/bulk/{teacherId}
 *   DELETE /api/v1/school/teacher-assignments/{teacherId}/items/{assignmentId}
 *
 * The feature is reused by Teacher Listing, Teacher Profile and (optionally)
 * onboarding — a single source of truth for class/subject assignment.
 */
package com.littlebridge.enrollplus.feature.admin.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A single current class/subject/section assignment with a live student count. */
@Serializable
data class TeacherClassAssignmentDto(
    val id: String,
    @SerialName("class_id") val classId: String? = null,
    @SerialName("class_name") val className: String,
    @SerialName("section_id") val sectionId: String? = null,
    val section: String = "A",
    @SerialName("subject_id") val subjectId: String? = null,
    val subject: String,
    @SerialName("teacher_id") val teacherId: String? = null,
    @SerialName("teacher_name") val teacherName: String? = null,
    @SerialName("student_count") val studentCount: Int = 0
)

/** Server-side workload summary for the KPI cards. */
@Serializable
data class TeacherAssignmentSummaryDto(
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("teacher_name") val teacherName: String,
    @SerialName("class_count") val classCount: Int,
    @SerialName("subject_count") val subjectCount: Int,
    @SerialName("student_count") val studentCount: Int,
    @SerialName("section_count") val sectionCount: Int
)

/** One per-subject bucket for the lightweight distribution visual. */
@Serializable
data class SubjectDistributionDto(
    val subject: String,
    @SerialName("class_count") val classCount: Int,
    @SerialName("student_count") val studentCount: Int
)

/** Full overview payload returned by the overview endpoint. */
@Serializable
data class TeacherAssignmentOverviewDto(
    val summary: TeacherAssignmentSummaryDto,
    val assignments: List<TeacherClassAssignmentDto> = emptyList(),
    val insights: List<String> = emptyList(),
    val distribution: List<SubjectDistributionDto> = emptyList()
)

/** A class option (with its sections) for the add-assignment selector. */
@Serializable
data class AssignmentClassOptionDto(
    @SerialName("class_id") val classId: String,
    val code: String,
    val name: String,
    val sections: List<String> = emptyList()
)

/** A subject option for the add-assignment subject chip selector. */
@Serializable
data class AssignmentSubjectOptionDto(
    @SerialName("subject_id") val subjectId: String,
    val name: String,
    val code: String
)

/** Options payload for the selector UI. */
@Serializable
data class AssignmentOptionsDto(
    val classes: List<AssignmentClassOptionDto> = emptyList(),
    val subjects: List<AssignmentSubjectOptionDto> = emptyList()
)

/** One class+section target inside a bulk-assign request. */
@Serializable
data class AssignmentTarget(
    @SerialName("class_id") val classId: String? = null,
    @SerialName("class_name") val className: String? = null,
    @SerialName("section_id") val sectionId: String? = null,
    val section: String? = null
)

/** Bulk-assign request body: one subject → many class+section targets. */
@Serializable
data class AssignTeacherClassesRequest(
    @SerialName("subject_id") val subjectId: String? = null,
    @SerialName("subject_name") val subjectName: String? = null,
    val assignments: List<AssignmentTarget> = emptyList()
)

/** Per-target outcome (conflict detection) from a bulk save. */
@Serializable
data class BulkAssignResultItemDto(
    @SerialName("class_name") val className: String,
    val section: String,
    val status: String, // created | duplicate | invalid
    val message: String? = null,
    val assignment: TeacherClassAssignmentDto? = null
)

/** Structured bulk-assign response: created rows + per-target results. */
@Serializable
data class BulkAssignResponseDto(
    val created: List<TeacherClassAssignmentDto> = emptyList(),
    val results: List<BulkAssignResultItemDto> = emptyList(),
    @SerialName("created_count") val createdCount: Int = 0,
    @SerialName("conflict_count") val conflictCount: Int = 0
)
