/*
 * File: AcademicCalendarModels.kt
 * Module: feature.admin.domain.model
 *
 * Client DTOs for the Academic Calendar platform (VP-CAL). These mirror the
 * server payloads in:
 *   - feature.calendar.AcademicCalendarCore.AcademicCalendarEventDto
 *   - feature.calendar.AcademicCalendarRouting (dashboard / list / requests)
 *   - feature.calendar.AcademicYearRouting (academic years)
 *
 * snake_case JSON ⇄ camelCase Kotlin via @SerialName, matching the rest of the
 * shared admin models.
 */
package com.littlebridge.enrollplus.feature.admin.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────────────────────
// Client-side enums (string-valued, mirrored 1:1 with the server)
// ─────────────────────────────────────────────────────────────────────────────

object CalEventSource {
    const val MANUAL = "MANUAL"
    const val ANNOUNCEMENT = "ANNOUNCEMENT"
}

object CalEventStatus {
    const val DRAFT = "DRAFT"
    const val PUBLISHED = "PUBLISHED"
    const val CANCELLED = "CANCELLED"
    const val COMPLETED = "COMPLETED"
}

object CalEventType {
    const val EXAM = "EXAM"
    const val HOLIDAY = "HOLIDAY"
    const val PTM = "PTM"
    const val SCHOOL_EVENT = "SCHOOL_EVENT"
    const val ACTIVITY = "ACTIVITY"
    const val ADMINISTRATIVE = "ADMINISTRATIVE"
    const val MILESTONE = "MILESTONE"
    val ALL = listOf(EXAM, HOLIDAY, PTM, SCHOOL_EVENT, ACTIVITY, ADMINISTRATIVE, MILESTONE)

    /** Human label for a type code. */
    fun label(type: String): String = when (type.uppercase()) {
        EXAM -> "Exam"
        HOLIDAY -> "Holiday"
        PTM -> "PTM"
        SCHOOL_EVENT -> "School Event"
        ACTIVITY -> "Activity"
        ADMINISTRATIVE -> "Administrative"
        MILESTONE -> "Milestone"
        else -> type
    }
}

object CalEventAudience {
    const val ALL_SCHOOL = "ALL_SCHOOL"
    const val GRADES = "GRADES"
    const val CLASSES = "CLASSES"
    const val SECTIONS = "SECTIONS"
    const val TEACHERS = "TEACHERS"
    const val PARENTS = "PARENTS"
    const val STUDENTS = "STUDENTS"
    val ALL = listOf(ALL_SCHOOL, GRADES, CLASSES, SECTIONS, TEACHERS, PARENTS, STUDENTS)

    fun label(a: String): String = when (a.uppercase()) {
        ALL_SCHOOL -> "Entire School"
        GRADES -> "Grades"
        CLASSES -> "Classes"
        SECTIONS -> "Sections"
        TEACHERS -> "Teachers"
        PARENTS -> "Parents"
        STUDENTS -> "Students"
        else -> a
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Event DTO + requests
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class AcademicCalendarEventDto(
    val id: String,
    val title: String,
    val description: String = "",
    val type: String,
    val status: String,
    val source: String,
    @SerialName("source_ref") val sourceRef: String? = null,
    @SerialName("academic_year_id") val academicYearId: String? = null,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    @SerialName("all_day") val allDay: Boolean = true,
    @SerialName("banner_url") val bannerUrl: String? = null,
    val icon: String? = null,
    val audience: String = CalEventAudience.ALL_SCHOOL,
    @SerialName("class_ids") val classIds: List<String> = emptyList(),
    @SerialName("section_ids") val sectionIds: List<String> = emptyList(),
    @SerialName("notify_students") val notifyStudents: Boolean = false,
    @SerialName("notify_parents") val notifyParents: Boolean = false,
    @SerialName("notify_teachers") val notifyTeachers: Boolean = false,
    @SerialName("is_milestone") val isMilestone: Boolean = false,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("updated_by") val updatedBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("conflict_warnings") val conflictWarnings: List<String> = emptyList()
) {
    val isMultiDay: Boolean get() = startDate != endDate
    val hasConflicts: Boolean get() = conflictWarnings.isNotEmpty()
}

@Serializable
data class CreateCalendarEventRequest(
    val title: String,
    val description: String = "",
    val type: String,
    val status: String = CalEventStatus.DRAFT,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("all_day") val allDay: Boolean = true,
    @SerialName("banner_url") val bannerUrl: String? = null,
    val icon: String? = null,
    val audience: String = CalEventAudience.ALL_SCHOOL,
    @SerialName("class_ids") val classIds: List<String> = emptyList(),
    @SerialName("section_ids") val sectionIds: List<String> = emptyList(),
    @SerialName("notify_students") val notifyStudents: Boolean = false,
    @SerialName("notify_parents") val notifyParents: Boolean = false,
    @SerialName("notify_teachers") val notifyTeachers: Boolean = false,
    @SerialName("is_milestone") val isMilestone: Boolean = false
)

@Serializable
data class UpdateCalendarEventRequest(
    val title: String? = null,
    val description: String? = null,
    val type: String? = null,
    val status: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("all_day") val allDay: Boolean? = null,
    @SerialName("banner_url") val bannerUrl: String? = null,
    val icon: String? = null,
    val audience: String? = null,
    @SerialName("class_ids") val classIds: List<String>? = null,
    @SerialName("section_ids") val sectionIds: List<String>? = null,
    @SerialName("notify_students") val notifyStudents: Boolean? = null,
    @SerialName("notify_parents") val notifyParents: Boolean? = null,
    @SerialName("notify_teachers") val notifyTeachers: Boolean? = null,
    @SerialName("is_milestone") val isMilestone: Boolean? = null
)

@Serializable
data class DuplicateCalendarEventRequest(
    val title: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val audience: String? = null,
    @SerialName("class_ids") val classIds: List<String>? = null,
    @SerialName("section_ids") val sectionIds: List<String>? = null
)

@Serializable
data class CalendarEventsListResponse(
    val events: List<AcademicCalendarEventDto> = emptyList(),
    val total: Int = 0
)

// ─────────────────────────────────────────────────────────────────────────────
// Dashboard payloads
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class CalendarHeroDto(
    @SerialName("academic_year") val academicYear: String? = null,
    @SerialName("academic_days") val academicDays: Int = 0,
    @SerialName("holiday_days") val holidayDays: Int = 0,
    @SerialName("total_events") val totalEvents: Int = 0,
    @SerialName("next_event") val nextEvent: AcademicCalendarEventDto? = null
)

@Serializable
data class CalendarKpiDto(
    val key: String,
    val label: String,
    val value: Int,
    val accent: String = "teal"
)

@Serializable
data class CalendarDashboardDto(
    val hero: CalendarHeroDto = CalendarHeroDto(),
    @SerialName("upcoming_highlights") val upcomingHighlights: List<AcademicCalendarEventDto> = emptyList(),
    @SerialName("upcoming_timeline") val upcomingTimeline: List<AcademicCalendarEventDto> = emptyList(),
    @SerialName("draft_events") val draftEvents: List<AcademicCalendarEventDto> = emptyList(),
    @SerialName("published_events") val publishedEvents: List<AcademicCalendarEventDto> = emptyList(),
    val milestones: List<AcademicCalendarEventDto> = emptyList(),
    val analytics: List<CalendarKpiDto> = emptyList()
)

// ─────────────────────────────────────────────────────────────────────────────
// Academic year payloads
// ─────────────────────────────────────────────────────────────────────────────

object AcademicYearStatusC {
    const val DRAFT = "DRAFT"
    const val ACTIVE = "ACTIVE"
    const val ARCHIVED = "ARCHIVED"
}

@Serializable
data class AcademicYearDto(
    val id: String,
    val name: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    @SerialName("is_active") val isActive: Boolean = false,
    val status: String = AcademicYearStatusC.DRAFT,
    @SerialName("academic_days") val academicDays: Int? = null,
    @SerialName("holiday_days") val holidayDays: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class AcademicYearsListResponse(
    val years: List<AcademicYearDto> = emptyList(),
    val total: Int = 0
)

@Serializable
data class CreateAcademicYearRequest(
    val name: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    @SerialName("academic_days") val academicDays: Int? = null,
    @SerialName("holiday_days") val holidayDays: Int? = null,
    val activate: Boolean = false
)

@Serializable
data class UpdateAcademicYearRequest(
    val name: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("academic_days") val academicDays: Int? = null,
    @SerialName("holiday_days") val holidayDays: Int? = null,
    val status: String? = null
)
