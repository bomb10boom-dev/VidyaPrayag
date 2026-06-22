/*
 * File: StudentModels.kt
 * Module: feature.admin.domain.model
 *
 * RA-45: DTOs for the admin student roster + student profile + teacher profile
 * detail. Mirror server: feature.school.SchoolStudentsRouting.kt
 *   GET    /api/v1/school/students
 *   POST   /api/v1/school/students
 *   DELETE /api/v1/school/students/{id}
 *   GET    /api/v1/school/students/{id}
 *   GET    /api/v1/school/teachers/{id}
 *
 * @SerialName mirrors the server so the same JSON decodes on both sides.
 */
package com.littlebridge.enrollplus.feature.admin.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StudentDto(
    val id: String,
    @SerialName("student_code") val studentCode: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("class_name") val className: String,
    val section: String,
    @SerialName("roll_number") val rollNumber: String,
    @SerialName("profile_photo_url") val profilePhotoUrl: String? = null,
    // RA-SP: listing-card enrichment. All defaulted so existing JSON decodes; all
    // DERIVED server-side by StudentAggregationService.
    @SerialName("attendance_percent") val attendancePercent: Float = 0f,
    @SerialName("teacher_count") val teacherCount: Int = 0,
    @SerialName("parent_count") val parentCount: Int = 0,
    @SerialName("is_new_admission") val isNewAdmission: Boolean = false,
    val status: String = "active"
)

@Serializable
data class StudentListResponse(val students: List<StudentDto>)

// RA-SP: a teacher connected to a student, derived from class assignments.
@Serializable
data class StudentTeacherDto(
    val id: String,
    val name: String,
    val subject: String,
    val designation: String? = null
)

// RA-SP: a parent linked to a student. Supports multiple; one primary guardian.
@Serializable
data class StudentParentDto(
    val id: String,
    val name: String,
    val relation: String,
    @SerialName("is_primary_guardian") val isPrimaryGuardian: Boolean = false,
    val phone: String? = null
)

// RA-SP: a single recent-activity timeline entry (newest first).
@Serializable
data class StudentActivityDto(
    val title: String,
    @SerialName("created_at") val createdAt: String,
    val type: String
)

@Serializable
data class CreateStudentRequest(
    @SerialName("full_name") val fullName: String,
    @SerialName("class_name") val className: String,
    val section: String? = null,
    @SerialName("roll_number") val rollNumber: String,
    @SerialName("student_code") val studentCode: String? = null
)

/**
 * Bulk import request. Send EITHER a parsed [students] list (manual multi-add)
 * OR raw [csv] text (file upload / paste). Mirrors server
 * feature.school.SchoolStudentsRouting.BulkImportStudentsRequest.
 */
@Serializable
data class BulkImportStudentsRequest(
    val students: List<CreateStudentRequest>? = null,
    val csv: String? = null
)

@Serializable
data class BulkImportRowResult(
    val row: Int,
    val success: Boolean,
    @SerialName("student_code") val studentCode: String? = null,
    val error: String? = null
)

@Serializable
data class BulkImportStudentsResponse(
    val total: Int,
    val inserted: Int,
    val failed: Int,
    val results: List<BulkImportRowResult> = emptyList()
)

@Serializable
data class AttendanceDayDto(val date: String, val status: String)

@Serializable
data class StudentMarkDto(
    val subject: String,
    @SerialName("assessment") val assessmentName: String,
    val marks: Double? = null,
    @SerialName("max_marks") val maxMarks: Int,
    @SerialName("exam_date") val examDate: String? = null
)

@Serializable
data class StudentLeaveDto(
    @SerialName("date_from") val dateFrom: String,
    @SerialName("date_to") val dateTo: String,
    val reason: String,
    val status: String
)

@Serializable
data class StudentFeeDto(
    val title: String,
    val amount: Double,
    val currency: String,
    val status: String,
    @SerialName("due_date") val dueDate: String? = null
)

@Serializable
data class StudentProfileDto(
    val student: StudentDto,
    @SerialName("present_days") val presentDays: Int,
    @SerialName("absent_days") val absentDays: Int,
    @SerialName("late_days") val lateDays: Int,
    @SerialName("attendance_rate") val attendanceRate: Int,
    @SerialName("recent_attendance") val recentAttendance: List<AttendanceDayDto>,
    val marks: List<StudentMarkDto>,
    val leave: List<StudentLeaveDto>,
    val fees: List<StudentFeeDto>,
    // RA-SP: dashboard enrichment — relationship-aware sections + KPI carousel
    // metrics + backend-generated narrative. Defaulted for compatibility; every
    // value is DERIVED server-side by StudentAggregationService.
    @SerialName("admission_date") val admissionDate: String? = null,
    @SerialName("attendance_percent") val attendancePercent: Float = 0f,
    @SerialName("teacher_count") val teacherCount: Int = 0,
    @SerialName("parent_count") val parentCount: Int = 0,
    @SerialName("subject_count") val subjectCount: Int = 0,
    @SerialName("academic_score") val academicScore: Float? = null,
    @SerialName("is_new_admission") val isNewAdmission: Boolean = false,
    val status: String = "active",
    val teachers: List<StudentTeacherDto> = emptyList(),
    val parents: List<StudentParentDto> = emptyList(),
    val insights: List<String> = emptyList(),
    val activities: List<StudentActivityDto> = emptyList()
)

@Serializable
data class TeacherAssignmentDto(
    @SerialName("class_name") val className: String,
    val section: String,
    val subject: String,
    // RA-PP: per-assignment student count powers the Teaching Portfolio carousel.
    @SerialName("student_count") val studentCount: Int = 0
)

// RA-PP: surfaced as modern highlight cards in the redesigned profile.
@Serializable
data class TeacherAchievementDto(
    val title: String,
    val description: String
)

// RA-PP: chronological timeline entries (newest first) for the Recent Activity feed.
@Serializable
data class TeacherActivityDto(
    val title: String,
    @SerialName("created_at") val createdAt: String,
    val type: String
)

@Serializable
data class TeacherProfileDto(
    val id: String,
    val name: String,

    val email: String? = null,
    val phone: String? = null,

    val role: String,

    // RA-PP: identity / hero-banner enrichment.
    val designation: String? = null,
    @SerialName("joined_on") val joinedOn: String? = null,
    @SerialName("experience_years") val experienceYears: Int? = null,

    // RA-PP: KPI carousel + performance overview metrics.
    @SerialName("student_count") val studentCount: Int = 0,
    @SerialName("class_count") val classCount: Int,
    @SerialName("subject_count") val subjectCount: Int,
    @SerialName("attendance_percent") val attendancePercent: Float = 0f,
    @SerialName("assignment_completion_percent") val assignmentCompletionPercent: Float = 0f,
    @SerialName("parent_satisfaction_percent") val parentSatisfactionPercent: Float = 0f,

    val status: String = "active",

    val assignments: List<TeacherAssignmentDto>,

    // RA-PP: backend-generated narrative sections.
    val insights: List<String> = emptyList(),
    val achievements: List<TeacherAchievementDto> = emptyList(),
    @SerialName("recent_activities") val recentActivities: List<TeacherActivityDto> = emptyList()
)
