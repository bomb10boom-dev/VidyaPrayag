/*
 * File: AcademicCalendarCore.kt
 * Module: feature.calendar
 *
 * Shared, transaction-aware building blocks for the Academic Calendar platform
 * (VP-CAL). Kept separate from the routing layer so other features (notably the
 * Announcement system) can reuse the exact same event-creation + conflict logic
 * WITHOUT duplicating workflows.
 *
 * Design goals captured here:
 *  - Single source of truth for the event type / status / source enums.
 *  - One `createCalendarEvent(...)` primitive that BOTH the calendar routes and
 *    the announcement auto-sync call, so an admin never creates the same thing
 *    twice (Holiday/PTM/Event announcements → calendar event automatically).
 *  - Conflict detection (date-range overlap) returned as human warnings.
 */
package com.littlebridge.enrollplus.feature.calendar

import com.littlebridge.enrollplus.db.AcademicYearsTable
import com.littlebridge.enrollplus.db.CalendarEventsTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// Enums (string-valued; persisted as varchar, mirrored 1:1 on the client)
// ─────────────────────────────────────────────────────────────────────────────

/** Where a calendar event originated. */
object EventSource {
    const val MANUAL = "MANUAL"
    const val ANNOUNCEMENT = "ANNOUNCEMENT"
    val ALL = setOf(MANUAL, ANNOUNCEMENT)
}

/** Lifecycle status of a calendar event. */
object EventStatus {
    const val DRAFT = "DRAFT"
    const val PUBLISHED = "PUBLISHED"
    const val CANCELLED = "CANCELLED"
    const val COMPLETED = "COMPLETED"
    val ALL = setOf(DRAFT, PUBLISHED, CANCELLED, COMPLETED)
}

/** The kind of planned item. */
object EventType {
    const val EXAM = "EXAM"
    const val HOLIDAY = "HOLIDAY"
    const val PTM = "PTM"
    const val SCHOOL_EVENT = "SCHOOL_EVENT"
    const val ACTIVITY = "ACTIVITY"
    const val ADMINISTRATIVE = "ADMINISTRATIVE"
    const val MILESTONE = "MILESTONE"
    val ALL = setOf(EXAM, HOLIDAY, PTM, SCHOOL_EVENT, ACTIVITY, ADMINISTRATIVE, MILESTONE)
}

/** Who an event is targeted at. */
object EventAudience {
    const val ALL_SCHOOL = "ALL_SCHOOL"
    const val GRADES = "GRADES"
    const val CLASSES = "CLASSES"
    const val SECTIONS = "SECTIONS"
    const val TEACHERS = "TEACHERS"
    const val PARENTS = "PARENTS"
    const val STUDENTS = "STUDENTS"
    val ALL = setOf(ALL_SCHOOL, GRADES, CLASSES, SECTIONS, TEACHERS, PARENTS, STUDENTS)
}

/** Map an announcement `type` to a calendar event type (used by the auto-sync). */
fun calendarTypeForAnnouncement(announcementType: String): String? =
    when (announcementType.trim().lowercase()) {
        "holiday", "holidays" -> EventType.HOLIDAY
        "ptm" -> EventType.PTM
        "event", "events" -> EventType.SCHOOL_EVENT
        else -> null // "update" / "reminder" → no calendar event (announcement feed only)
    }

// ─────────────────────────────────────────────────────────────────────────────
// DTOs (the canonical event payload returned by the API)
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
    @SerialName("all_day") val allDay: Boolean,
    @SerialName("banner_url") val bannerUrl: String? = null,
    val icon: String? = null,
    val audience: String,
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
)

// ─────────────────────────────────────────────────────────────────────────────
// JSON list helpers (class_ids / section_ids are stored as a JSON-ish text)
// ─────────────────────────────────────────────────────────────────────────────

/** Encode a string list into the simple JSON array text we persist. */
fun encodeStringList(values: List<String>?): String? {
    val clean = values?.map { it.trim() }?.filter { it.isNotBlank() } ?: return null
    if (clean.isEmpty()) return null
    return clean.joinToString(prefix = "[", postfix = "]") { "\"" + it.replace("\"", "\\\"") + "\"" }
}

/** Decode the persisted JSON array text back into a string list (tolerant). */
fun decodeStringList(text: String?): List<String> {
    if (text.isNullOrBlank()) return emptyList()
    val trimmed = text.trim().removePrefix("[").removeSuffix("]")
    if (trimmed.isBlank()) return emptyList()
    return trimmed.split(",").map { it.trim().trim('"').replace("\\\"", "\"") }.filter { it.isNotBlank() }
}

// ─────────────────────────────────────────────────────────────────────────────
// Row → DTO mapping
// ─────────────────────────────────────────────────────────────────────────────

fun org.jetbrains.exposed.sql.ResultRow.toCalendarEventDto(
    conflictWarnings: List<String> = emptyList()
): AcademicCalendarEventDto = AcademicCalendarEventDto(
    id = this[CalendarEventsTable.eventCode],
    title = this[CalendarEventsTable.title],
    description = this[CalendarEventsTable.description],
    type = this[CalendarEventsTable.type],
    status = this[CalendarEventsTable.status],
    source = this[CalendarEventsTable.eventSource],
    sourceRef = this[CalendarEventsTable.sourceRef],
    academicYearId = this[CalendarEventsTable.academicYearId]?.toString(),
    startDate = this[CalendarEventsTable.startDate],
    endDate = this[CalendarEventsTable.endDate],
    allDay = this[CalendarEventsTable.allDay],
    bannerUrl = this[CalendarEventsTable.bannerUrl],
    icon = this[CalendarEventsTable.icon],
    audience = this[CalendarEventsTable.audience],
    classIds = decodeStringList(this[CalendarEventsTable.classIds]),
    sectionIds = decodeStringList(this[CalendarEventsTable.sectionIds]),
    notifyStudents = this[CalendarEventsTable.notifyStudents],
    notifyParents = this[CalendarEventsTable.notifyParents],
    notifyTeachers = this[CalendarEventsTable.notifyTeachers],
    isMilestone = this[CalendarEventsTable.isMilestone],
    createdBy = this[CalendarEventsTable.createdBy]?.toString(),
    updatedBy = this[CalendarEventsTable.updatedBy]?.toString(),
    createdAt = this[CalendarEventsTable.createdAt].toString(),
    updatedAt = this[CalendarEventsTable.updatedAt].toString(),
    conflictWarnings = conflictWarnings
)

// ─────────────────────────────────────────────────────────────────────────────
// Conflict detection
// ─────────────────────────────────────────────────────────────────────────────

/** Parse a YYYY-MM-DD string, null on bad input. */
fun parseIso(date: String?): LocalDate? =
    date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/**
 * Detect schedule conflicts: active events whose [start,end] range overlaps the
 * candidate [start,end]. Returns human-readable warnings the client surfaces as
 * "Potential Schedule Conflict". Caller supplies an optional [excludeCode] to
 * skip the event being edited.
 *
 * MUST be called inside a dbQuery {} transaction.
 */
fun detectConflicts(
    schoolId: UUID,
    start: LocalDate,
    end: LocalDate,
    excludeCode: String? = null
): List<String> {
    val rows = CalendarEventsTable.selectAll()
        .where {
            (CalendarEventsTable.schoolId eq schoolId) and
                (CalendarEventsTable.isActive eq true)
        }
    val warnings = mutableListOf<String>()
    rows.forEach { row ->
        val code = row[CalendarEventsTable.eventCode]
        if (excludeCode != null && code == excludeCode) return@forEach
        val status = row[CalendarEventsTable.status]
        if (status == EventStatus.CANCELLED) return@forEach
        val s = parseIso(row[CalendarEventsTable.startDate]) ?: return@forEach
        val e = parseIso(row[CalendarEventsTable.endDate]) ?: s
        // Overlap test: !(end < s || start > e)
        val overlaps = !(end.isBefore(s) || start.isAfter(e))
        if (overlaps) {
            val title = row[CalendarEventsTable.title]
            val range = if (s == e) s.toString() else "$s – $e"
            warnings += "Overlaps with \"$title\" ($range)"
        }
    }
    return warnings
}

// ─────────────────────────────────────────────────────────────────────────────
// Event creation primitive (shared by routing + announcement auto-sync)
// ─────────────────────────────────────────────────────────────────────────────

/** Generate a stable external event code, e.g. CAL_AB12CD34. */
fun newEventCode(): String = "CAL_" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase()

/**
 * Resolve the active academic year id for a school (null when none active).
 * MUST be called inside a dbQuery {} transaction.
 */
fun activeAcademicYearId(schoolId: UUID): UUID? =
    AcademicYearsTable.selectAll()
        .where { (AcademicYearsTable.schoolId eq schoolId) and (AcademicYearsTable.isActive eq true) }
        .firstOrNull()
        ?.get(AcademicYearsTable.id)
        ?.value

/**
 * Insert a calendar event and return its generated event code. Opens its own
 * transaction so it is safe to call from anywhere (routing OR announcement sync).
 */
suspend fun createCalendarEvent(
    schoolId: UUID,
    title: String,
    description: String,
    type: String,
    status: String,
    source: String,
    sourceRef: String? = null,
    startDate: String,
    endDate: String? = null,
    allDay: Boolean = true,
    bannerUrl: String? = null,
    icon: String? = null,
    audience: String = EventAudience.ALL_SCHOOL,
    classIds: List<String> = emptyList(),
    sectionIds: List<String> = emptyList(),
    notifyStudents: Boolean = false,
    notifyParents: Boolean = false,
    notifyTeachers: Boolean = false,
    isMilestone: Boolean = false,
    createdBy: UUID? = null,
    academicYearId: UUID? = null
): String = dbQuery {
    val now = Instant.now()
    val code = newEventCode()
    val resolvedYear = academicYearId ?: activeAcademicYearId(schoolId)
    CalendarEventsTable.insert {
        it[CalendarEventsTable.schoolId] = schoolId
        it[eventCode] = code
        it[CalendarEventsTable.academicYearId] = resolvedYear
        it[CalendarEventsTable.title] = title
        it[CalendarEventsTable.description] = description
        it[CalendarEventsTable.type] = type
        it[CalendarEventsTable.status] = status
        it[CalendarEventsTable.eventSource] = source
        it[CalendarEventsTable.sourceRef] = sourceRef
        it[CalendarEventsTable.startDate] = startDate
        it[CalendarEventsTable.endDate] = endDate ?: startDate
        it[CalendarEventsTable.allDay] = allDay
        it[CalendarEventsTable.bannerUrl] = bannerUrl
        it[CalendarEventsTable.icon] = icon
        it[CalendarEventsTable.audience] = audience
        it[CalendarEventsTable.classIds] = encodeStringList(classIds)
        it[CalendarEventsTable.sectionIds] = encodeStringList(sectionIds)
        it[CalendarEventsTable.notifyStudents] = notifyStudents
        it[CalendarEventsTable.notifyParents] = notifyParents
        it[CalendarEventsTable.notifyTeachers] = notifyTeachers
        it[CalendarEventsTable.isMilestone] = isMilestone
        it[CalendarEventsTable.isActive] = true
        it[CalendarEventsTable.createdBy] = createdBy
        it[CalendarEventsTable.updatedBy] = createdBy
        it[CalendarEventsTable.createdAt] = now
        it[CalendarEventsTable.updatedAt] = now
    }
    code
}

/**
 * VP-CAL ↔ Announcement bridge. When an admin creates a Holiday/PTM/Event
 * announcement with "Add To Academic Calendar" enabled, this mirrors it into a
 * calendar event tagged source = ANNOUNCEMENT. Idempotent: if an event already
 * exists for this announcement's [announcementEventId] it is left untouched.
 *
 * Returns the calendar event code, or null when the announcement type does not
 * map to a calendar event (e.g. a plain Update).
 */
suspend fun syncAnnouncementToCalendar(
    schoolId: UUID,
    announcementEventId: String,
    announcementType: String,
    title: String,
    description: String,
    date: String,
    eventImage: String?,
    createdBy: UUID?,
    publish: Boolean = true
): String? {
    val calType = calendarTypeForAnnouncement(announcementType) ?: return null

    // Idempotency: never create a second event for the same announcement.
    val existing = dbQuery {
        CalendarEventsTable.selectAll()
            .where {
                (CalendarEventsTable.schoolId eq schoolId) and
                    (CalendarEventsTable.eventSource eq EventSource.ANNOUNCEMENT) and
                    (CalendarEventsTable.sourceRef eq announcementEventId)
            }
            .firstOrNull()
            ?.get(CalendarEventsTable.eventCode)
    }
    if (existing != null) return existing

    return createCalendarEvent(
        schoolId = schoolId,
        title = title,
        description = description,
        type = calType,
        status = if (publish) EventStatus.PUBLISHED else EventStatus.DRAFT,
        source = EventSource.ANNOUNCEMENT,
        sourceRef = announcementEventId,
        startDate = date,
        endDate = date,
        allDay = true,
        bannerUrl = eventImage,
        audience = EventAudience.ALL_SCHOOL,
        notifyParents = true,
        notifyTeachers = calType != EventType.HOLIDAY,
        createdBy = createdBy
    )
}
