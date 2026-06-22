package com.littlebridge.enrollplus.feature.teacher.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

// ─────────────────────────────────────────────────────────────────────────────
// Teacher Home — dashboard glance + today's periods + pending task counts
// Backs Teacher.tsx → Home tab.
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class TeacherHomeResponse(
    val success: Boolean,
    val data: TeacherHomeData,
)

@Serializable
data class TeacherHomeData(
    @SerialName("teacher_name") val teacherName: String,
    @SerialName("school_name") val schoolName: String,
    @SerialName("classes_today") val classesToday: Int,
    @SerialName("pending_attendance") val pendingAttendance: Int,
    @SerialName("pending_marks") val pendingMarks: Int,
    @SerialName("homework_due") val homeworkDue: Int,
    @SerialName("today_periods") val todayPeriods: List<TeacherPeriodDto> = emptyList(),
    val tasks: List<TeacherTaskDto> = emptyList(),
)

@Serializable
data class TeacherPeriodDto(
    val id: String,
    val time: String,
    @SerialName("class_name") val className: String,
    val subject: String,
    val room: String = "",
    @SerialName("is_current") val isCurrent: Boolean = false,
    val status: String = "upcoming", // "done" | "current" | "upcoming"
)

@Serializable
data class TeacherTaskDto(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val type: String, // "attendance" | "marks" | "syllabus" | "homework"
    @SerialName("class_name") val className: String = "",
    @SerialName("is_done") val isDone: Boolean = false,
)

// ─────────────────────────────────────────────────────────────────────────────
// My Classes — list of classes the teacher handles, + class detail roster
// Backs Teacher.tsx → MyClasses tab + ClassDetail.
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class TeacherClassesResponse(
    val success: Boolean,
    val data: TeacherClassesData,
)

@Serializable
data class TeacherClassesData(
    val classes: List<TeacherClassDto> = emptyList(),
)

@Serializable
data class TeacherClassDto(
    val id: String,
    @SerialName("class_name") val className: String,
    val subject: String,
    @SerialName("student_count") val studentCount: Int,
    @SerialName("is_class_teacher") val isClassTeacher: Boolean = false,
    @SerialName("syllabus_progress") val syllabusProgress: Float = 0f,
    @SerialName("avg_attendance") val avgAttendance: Float = 0f,
)

@Serializable
data class TeacherStudentDto(
    val id: String,
    val name: String,
    @SerialName("roll_no") val rollNo: String = "",
    @SerialName("photo_url") val photoUrl: String? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// Attendance — load the day's roster, then submit marks.
// Backs Teacher.tsx → Update › Attendance.
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class TeacherAttendanceResponse(
    val success: Boolean,
    val data: TeacherAttendanceData,
)

@Serializable
data class TeacherAttendanceData(
    @SerialName("class_name") val className: String,
    val date: String,
    val students: List<AttendanceEntryDto> = emptyList(),
)

@Serializable
data class AttendanceEntryDto(
    @SerialName("student_id") val studentId: String,
    val name: String,
    @SerialName("roll_no") val rollNo: String = "",
    val status: String = "present", // "present" | "absent" | "late"
)

@Serializable
data class SubmitAttendanceRequest(
    @SerialName("class_id") val classId: String,
    val date: String,
    val entries: List<AttendanceMarkDto>,
)

@Serializable
data class AttendanceMarkDto(
    @SerialName("student_id") val studentId: String,
    val status: String,
)

// ─────────────────────────────────────────────────────────────────────────────
// Marks — load students for an exam/subject, then submit scores.
// Backs Teacher.tsx → Update › Marks.
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class TeacherMarksResponse(
    val success: Boolean,
    val data: TeacherMarksData,
)

@Serializable
data class TeacherMarksData(
    @SerialName("class_name") val className: String,
    val subject: String,
    @SerialName("exam_name") val examName: String,
    @SerialName("max_marks") val maxMarks: Int,
    val students: List<MarksEntryDto> = emptyList(),
)

@Serializable
data class MarksEntryDto(
    @SerialName("student_id") val studentId: String,
    val name: String,
    @SerialName("roll_no") val rollNo: String = "",
    val marks: Float? = null,
)

@Serializable
data class SubmitMarksRequest(
    @SerialName("class_id") val classId: String,
    @SerialName("exam_id") val examId: String,
    val entries: List<MarkScoreDto>,
)

@Serializable
data class MarkScoreDto(
    @SerialName("student_id") val studentId: String,
    val marks: Float,
)

// ─────────────────────────────────────────────────────────────────────────────
// Syllabus — chapter/topic coverage per class+subject, with progress update.
// Backs Teacher.tsx → Update › Syllabus.
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class TeacherSyllabusResponse(
    val success: Boolean,
    val data: TeacherSyllabusData,
)

@Serializable
data class TeacherSyllabusData(
    @SerialName("class_name") val className: String,
    val subject: String,
    @SerialName("overall_progress") val overallProgress: Float = 0f,
    val units: List<SyllabusUnitDto> = emptyList(),
)

@Serializable
data class SyllabusUnitDto(
    val id: String,
    val title: String,
    @SerialName("is_covered") val isCovered: Boolean = false,
    @SerialName("covered_on") val coveredOn: String? = null,
)

@Serializable
data class UpdateSyllabusRequest(
    @SerialName("unit_id") val unitId: String,
    @SerialName("is_covered") val isCovered: Boolean,
)

// ─────────────────────────────────────────────────────────────────────────────
// Homework — list assignments + create a new one.
// Backs Teacher.tsx → Update › Homework.
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class TeacherHomeworkResponse(
    val success: Boolean,
    val data: TeacherHomeworkData,
)

@Serializable
data class TeacherHomeworkData(
    val items: List<HomeworkDto> = emptyList(),
)

@Serializable
data class HomeworkDto(
    val id: String,
    val title: String,
    val description: String = "",
    @SerialName("class_name") val className: String,
    val subject: String,
    @SerialName("due_date") val dueDate: String,
    @SerialName("submitted_count") val submittedCount: Int = 0,
    @SerialName("total_count") val totalCount: Int = 0,
)

@Serializable
data class CreateHomeworkRequest(
    @SerialName("class_id") val classId: String,
    val title: String,
    val description: String = "",
    @SerialName("due_date") val dueDate: String,
)

// ─────────────────────────────────────────────────────────────────────────────
// Teacher Profile
// Backs Teacher.tsx → Profile tab.
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class TeacherProfileResponse(
    val success: Boolean,
    val data: TeacherProfileData,
)

@Serializable
data class TeacherProfileData(
    val id: String,
    val name: String,
    val username: String,
    @SerialName("school_name") val schoolName: String,
    val subjects: List<String> = emptyList(),
    val classes: List<String> = emptyList(),
    @SerialName("photo_url") val photoUrl: String? = null,
    val email: String = "",
    val phone: String = "",
)

// ─────────────────────────────────────────────────────────────────────────────
// Assessments (exams) — list + create. RA-40: the marks plane needs a valid
// exam_id; these models back the exam selector that feeds SubmitMarksRequest.
// Mirror server TeacherAssessmentDto / TeacherAssessmentsData / CreateAssessmentRequest.
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class TeacherAssessmentsResponse(
    val success: Boolean,
    val data: TeacherAssessmentsData,
)

@Serializable
data class TeacherAssessmentsData(
    val assessments: List<TeacherAssessmentDto> = emptyList(),
)

@Serializable
data class TeacherAssessmentDto(
    val id: String,
    val name: String,
    val subject: String,
    @SerialName("max_marks") val maxMarks: Int,
    @SerialName("exam_date") val examDate: String? = null,
    @SerialName("is_published") val isPublished: Boolean = false,
)

@Serializable
data class CreateAssessmentRequest(
    @SerialName("class_id") val classId: String,
    val name: String,
    @SerialName("max_marks") val maxMarks: Int? = null,
    @SerialName("exam_date") val examDate: String? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// RA-44: teacher leave workflow. Mirror server/.../teacher/TeacherLeaveRouting.kt.
// A teacher lists leave requests routed to their classes and approves/rejects.
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class TeacherLeaveListResponse(
    val success: Boolean = true,
    val data: TeacherLeaveListData = TeacherLeaveListData(),
)

@Serializable
data class TeacherLeaveListData(
    @SerialName("pending_count") val pendingCount: Int = 0,
    val requests: List<TeacherLeaveDto> = emptyList(),
)

@Serializable
data class TeacherLeaveDto(
    val id: String,
    @SerialName("student_name") val studentName: String,
    @SerialName("class_name") val className: String? = null,
    val section: String? = null,
    @SerialName("date_from") val dateFrom: String,
    @SerialName("date_to") val dateTo: String,
    val reason: String,
    @SerialName("image_url") val imageUrl: String? = null,
    val status: String,
)

@Serializable
data class TeacherLeaveDecisionRequest(val status: String) // Approved | Rejected

// ─────────────────────────────────────────────────────────────────────────────
// RA-51 — teacher messaging (1:1 + "message class parents" broadcast).
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class TeacherClassBroadcastRequest(
    @SerialName("class_name") val className: String,
    val section: String? = null,
    val body: String,
)

@Serializable
data class TeacherClassBroadcastData(
    val recipients: Int = 0,
)

@Serializable
data class TeacherClassBroadcastResponse(
    val success: Boolean = false,
    val message: String? = null,
    val data: TeacherClassBroadcastData? = null,
)
