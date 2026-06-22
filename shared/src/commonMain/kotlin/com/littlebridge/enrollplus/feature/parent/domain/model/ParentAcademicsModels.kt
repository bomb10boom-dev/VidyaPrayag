package com.littlebridge.enrollplus.feature.parent.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────────────────────
// RA-43 + RA-56: child-scoped parent academic reads. Mirror the server DTOs in
// server/.../feature/parent/ParentAcademicsRouting.kt. Every read is for ONE
// child (resolved + ownership-checked server-side), so a multi-child parent sees
// per-child data instead of silently the first child only.
// ─────────────────────────────────────────────────────────────────────────────

// ── Attendance ──
@Serializable
data class ParentAttendanceResponse(
    val success: Boolean,
    val data: ParentAttendanceData,
)

@Serializable
data class ParentAttendanceData(
    @SerialName("child_name") val childName: String,
    @SerialName("present_days") val presentDays: Int = 0,
    @SerialName("absent_days") val absentDays: Int = 0,
    @SerialName("late_days") val lateDays: Int = 0,
    @SerialName("total_days") val totalDays: Int = 0,
    @SerialName("attendance_rate") val attendanceRate: Int = 0,
    val records: List<ParentAttendanceDayDto> = emptyList(),
    // RA-PP1: declared non-school days for the child's school (holidays/vacations),
    // so the dashboard renders them distinctly from real absences. Defaulted so older
    // server builds that omit the field still deserialize.
    val holidays: List<ParentHolidayDto> = emptyList(),
)

@Serializable
data class ParentAttendanceDayDto(
    val date: String,
    val status: String, // present | absent | late
)

@Serializable
data class ParentHolidayDto(
    val date: String = "",      // "YYYY-MM-DD"; empty for recurring (weekly) rules
    val title: String = "",
    val type: String = "",      // Public | School
    val frequency: String = "", // weekly | monthly | yearly
)

// ── Timetable (RA-PP1: the child's class weekly schedule, recurring) ──
@Serializable
data class ParentTimetableResponse(
    val success: Boolean,
    val data: ParentTimetableData,
)

@Serializable
data class ParentTimetableData(
    @SerialName("child_name") val childName: String = "",
    @SerialName("class_name") val className: String = "",
    val weekdays: List<ParentTimetableDayDto> = emptyList(),
)

@Serializable
data class ParentTimetableDayDto(
    val weekday: Int, // 1=Mon … 7=Sun
    val periods: List<ParentPeriodDto> = emptyList(),
)

@Serializable
data class ParentPeriodDto(
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    val subject: String,
    val room: String = "",
    @SerialName("teacher_name") val teacherName: String = "",
)

// ── Marks ──
@Serializable
data class ParentMarksResponse(
    val success: Boolean,
    val data: ParentMarksData,
)

@Serializable
data class ParentMarksData(
    @SerialName("child_name") val childName: String,
    val results: List<ParentMarkDto> = emptyList(),
)

@Serializable
data class ParentMarkDto(
    @SerialName("exam_name") val examName: String,
    val subject: String,
    val marks: Double? = null,
    @SerialName("max_marks") val maxMarks: Int,
    @SerialName("exam_date") val examDate: String? = null,
)

// ── Syllabus ──
@Serializable
data class ParentSyllabusResponse(
    val success: Boolean,
    val data: ParentSyllabusData,
)

@Serializable
data class ParentSyllabusData(
    @SerialName("child_name") val childName: String,
    @SerialName("class_name") val className: String,
    val subjects: List<ParentSyllabusSubjectDto> = emptyList(),
)

@Serializable
data class ParentSyllabusSubjectDto(
    val subject: String,
    val progress: Int = 0,
    val units: List<ParentSyllabusUnitDto> = emptyList(),
)

@Serializable
data class ParentSyllabusUnitDto(
    val title: String,
    @SerialName("is_covered") val isCovered: Boolean,
    @SerialName("covered_on") val coveredOn: String? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// RA-44: parent leave workflow. Mirror server/.../parent/ParentLeaveRouting.kt.
// A parent applies on behalf of an owned child; the request routes to the
// child's class teacher and is decided by that teacher or an admin.
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class CreateParentLeaveRequest(
    @SerialName("child_id") val childId: String,
    @SerialName("date_from") val dateFrom: String, // YYYY-MM-DD
    @SerialName("date_to") val dateTo: String,      // YYYY-MM-DD
    val reason: String,
    @SerialName("image_url") val imageUrl: String? = null,
)

@Serializable
data class ParentLeaveDto(
    val id: String,
    @SerialName("child_name") val childName: String,
    @SerialName("date_from") val dateFrom: String,
    @SerialName("date_to") val dateTo: String,
    val reason: String,
    @SerialName("image_url") val imageUrl: String? = null,
    val status: String,
)

@Serializable
data class ParentLeaveListResponse(
    val success: Boolean = true,
    val data: ParentLeaveListData = ParentLeaveListData(),
)

@Serializable
data class ParentLeaveListData(
    val requests: List<ParentLeaveDto> = emptyList(),
)

@Serializable
data class ParentLeaveCreateResponse(
    val success: Boolean = true,
    val data: ParentLeaveDto? = null,
    val message: String? = null,
)
