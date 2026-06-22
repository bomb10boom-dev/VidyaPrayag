/*
 * File: AcademicCalendarRouting.kt
 * Module: feature.calendar
 *
 * The Academic Calendar platform (VP-CAL) REST surface. This is the school's
 * centralized planning & scheduling system: Academic Events, Holidays, PTMs,
 * School Events, Academic Milestones and Academic-Year planning all live here.
 *
 * Endpoints (all under /api/admin/calendar, JWT-guarded, school-scoped):
 *   GET    /api/admin/calendar/dashboard                      — hero + highlights + analytics
 *   GET    /api/admin/calendar/events?month=&status=&type=    — filtered list
 *   POST   /api/admin/calendar/events                         — create (DRAFT or PUBLISHED)
 *   GET    /api/admin/calendar/events/{eventId}               — single event (with conflicts)
 *   PUT    /api/admin/calendar/events/{eventId}               — edit / reschedule / publish / cancel
 *   DELETE /api/admin/calendar/events/{eventId}               — soft-delete
 *   POST   /api/admin/calendar/events/{eventId}/duplicate     — duplicate (e.g. PTM G8 → G9)
 *
 * Authorization:
 *   Reads use call.requireSchoolContext(); privileged writes (create/edit/delete/
 *   duplicate) use call.requireSchoolAdmin() so delegated school_staff cannot
 *   mutate the school plan. Every row is scoped by the resolved school_id.
 *
 * Conflict detection:
 *   create/update/duplicate run detectConflicts(...) over the school's active
 *   events and return human "Overlaps with …" warnings in conflict_warnings; the
 *   client surfaces them as "Potential Schedule Conflict" (non-blocking).
 */
package com.littlebridge.enrollplus.feature.calendar

import com.littlebridge.enrollplus.core.fail
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.created
import com.littlebridge.enrollplus.core.okMessage
import com.littlebridge.enrollplus.core.requireSchoolAdmin
import com.littlebridge.enrollplus.core.requireSchoolContext
import com.littlebridge.enrollplus.db.AcademicYearsTable
import com.littlebridge.enrollplus.db.CalendarEventsTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.feature.notifications.Notify
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// Request / response payloads
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class CreateCalendarEventRequest(
    val title: String,
    val description: String = "",
    val type: String,
    val status: String = EventStatus.DRAFT,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("all_day") val allDay: Boolean = true,
    @SerialName("banner_url") val bannerUrl: String? = null,
    val icon: String? = null,
    val audience: String = EventAudience.ALL_SCHOOL,
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
    val events: List<AcademicCalendarEventDto>,
    val total: Int
)

// --- dashboard models ---

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
    val hero: CalendarHeroDto,
    @SerialName("upcoming_highlights") val upcomingHighlights: List<AcademicCalendarEventDto> = emptyList(),
    @SerialName("upcoming_timeline") val upcomingTimeline: List<AcademicCalendarEventDto> = emptyList(),
    @SerialName("draft_events") val draftEvents: List<AcademicCalendarEventDto> = emptyList(),
    @SerialName("published_events") val publishedEvents: List<AcademicCalendarEventDto> = emptyList(),
    val milestones: List<AcademicCalendarEventDto> = emptyList(),
    val analytics: List<CalendarKpiDto> = emptyList()
)

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Today, used as the pivot for "upcoming". */
private fun today(): LocalDate = LocalDate.now()

/** Map a row to a DTO and attach freshly-computed conflict warnings. */
private fun org.jetbrains.exposed.sql.ResultRow.toDtoWithConflicts(schoolId: UUID): AcademicCalendarEventDto {
    val start = parseIso(this[CalendarEventsTable.startDate])
    val end = parseIso(this[CalendarEventsTable.endDate]) ?: start
    val warnings = if (start != null && end != null)
        detectConflicts(schoolId, start, end, excludeCode = this[CalendarEventsTable.eventCode])
    else emptyList()
    return toCalendarEventDto(warnings)
}

// ─────────────────────────────────────────────────────────────────────────────
// Routing
// ─────────────────────────────────────────────────────────────────────────────

fun Route.academicCalendarRouting() {
    authenticate("jwt") {
        route("/api/admin/calendar") {

            // ---------------------------------------------------------------
            // GET /dashboard — hero summary + highlights + analytics
            // ---------------------------------------------------------------
            get("/dashboard") {
                val ctx = call.requireSchoolContext() ?: return@get
                val schoolId = ctx.schoolId
                val todayIso = today().toString()
                val weekAheadIso = today().plusDays(7).toString()

                val dashboard = dbQuery {
                    // Active academic year (if any).
                    val yearRow = AcademicYearsTable.selectAll()
                        .where { (AcademicYearsTable.schoolId eq schoolId) and (AcademicYearsTable.isActive eq true) }
                        .firstOrNull()

                    val allRows = CalendarEventsTable.selectAll()
                        .where { (CalendarEventsTable.schoolId eq schoolId) and (CalendarEventsTable.isActive eq true) }
                        .orderBy(CalendarEventsTable.startDate, SortOrder.ASC)
                        .toList()

                    val all = allRows.map { it.toCalendarEventDto() }

                    val published = all.filter { it.status == EventStatus.PUBLISHED }
                    val drafts = all.filter { it.status == EventStatus.DRAFT }
                    val notCancelled = all.filter { it.status != EventStatus.CANCELLED }

                    val upcoming = notCancelled
                        .filter { (it.endDate.takeIf { d -> d.isNotBlank() } ?: it.startDate) >= todayIso }
                        .sortedBy { it.startDate }

                    val nextEvent = upcoming.firstOrNull { it.status == EventStatus.PUBLISHED }
                        ?: upcoming.firstOrNull()

                    val milestones = all.filter { it.isMilestone }.sortedBy { it.startDate }

                    val holidays = published.count { it.type == EventType.HOLIDAY }
                    val ptms = published.count { it.type == EventType.PTM }
                    val exams = published.count { it.type == EventType.EXAM }

                    // Events whose start falls within the next 7 days (home "This week").
                    val thisWeek = upcoming.count { it.startDate in todayIso..weekAheadIso }

                    val hero = CalendarHeroDto(
                        academicYear = yearRow?.get(AcademicYearsTable.name),
                        academicDays = yearRow?.get(AcademicYearsTable.academicDays) ?: 0,
                        holidayDays = yearRow?.get(AcademicYearsTable.holidayDays) ?: holidays,
                        totalEvents = notCancelled.size,
                        nextEvent = nextEvent
                    )

                    val analytics = listOf(
                        CalendarKpiDto("total", "Total Events", notCancelled.size, "teal"),
                        CalendarKpiDto("this_week", "This Week", thisWeek, "tealDeep"),
                        CalendarKpiDto("published", "Published", published.size, "success"),
                        CalendarKpiDto("draft", "Drafts", drafts.size, "warning"),
                        CalendarKpiDto("holidays", "Holidays", holidays, "navy"),
                        CalendarKpiDto("ptm", "PTMs", ptms, "tealDeep"),
                        CalendarKpiDto("exams", "Exams", exams, "danger"),
                        CalendarKpiDto("milestones", "Milestones", milestones.size, "ink")
                    )

                    CalendarDashboardDto(
                        hero = hero,
                        upcomingHighlights = upcoming.filter { it.status == EventStatus.PUBLISHED }.take(8),
                        upcomingTimeline = upcoming.take(12),
                        draftEvents = drafts.sortedBy { it.startDate }.take(20),
                        publishedEvents = published.sortedBy { it.startDate }.take(20),
                        milestones = milestones.take(12),
                        analytics = analytics
                    )
                }
                call.ok(dashboard, message = "Calendar dashboard fetched")
            }

            // ---------------------------------------------------------------
            // GET /events?month=YYYY-MM&status=&type=
            // ---------------------------------------------------------------
            get("/events") {
                val ctx = call.requireSchoolContext() ?: return@get
                val schoolId = ctx.schoolId
                val month = call.request.queryParameters["month"]?.trim()      // YYYY-MM
                val status = call.request.queryParameters["status"]?.trim()?.uppercase()
                val type = call.request.queryParameters["type"]?.trim()?.uppercase()

                if (status != null && status !in EventStatus.ALL) {
                    call.fail("Invalid status. Allowed: ${EventStatus.ALL.joinToString()}"); return@get
                }
                if (type != null && type !in EventType.ALL) {
                    call.fail("Invalid type. Allowed: ${EventType.ALL.joinToString()}"); return@get
                }

                val list = dbQuery {
                    CalendarEventsTable.selectAll()
                        .where { (CalendarEventsTable.schoolId eq schoolId) and (CalendarEventsTable.isActive eq true) }
                        .orderBy(CalendarEventsTable.startDate, SortOrder.ASC)
                        .map { it.toCalendarEventDto() }
                        .filter { ev -> status == null || ev.status == status }
                        .filter { ev -> type == null || ev.type == type }
                        .filter { ev ->
                            if (month == null) true
                            else {
                                // Event touches the requested month if either end overlaps it.
                                val s = ev.startDate.take(7)
                                val e = ev.endDate.take(7)
                                month in s..e || s == month || e == month
                            }
                        }
                }
                call.ok(CalendarEventsListResponse(list, list.size), message = "Events fetched")
            }

            // ---------------------------------------------------------------
            // POST /events — create (DRAFT or PUBLISHED)
            // ---------------------------------------------------------------
            post("/events") {
                val ctx = call.requireSchoolAdmin() ?: return@post
                val req = call.receive<CreateCalendarEventRequest>()

                val type = req.type.trim().uppercase()
                if (type !in EventType.ALL) {
                    call.fail("Invalid type. Allowed: ${EventType.ALL.joinToString()}"); return@post
                }
                val status = req.status.trim().uppercase()
                if (status !in setOf(EventStatus.DRAFT, EventStatus.PUBLISHED)) {
                    call.fail("status must be DRAFT or PUBLISHED on create"); return@post
                }
                val audience = req.audience.trim().uppercase()
                if (audience !in EventAudience.ALL) {
                    call.fail("Invalid audience. Allowed: ${EventAudience.ALL.joinToString()}"); return@post
                }
                val start = parseIso(req.startDate) ?: run {
                    call.fail("start_date must be YYYY-MM-DD"); return@post
                }
                val end = parseIso(req.endDate ?: req.startDate) ?: start
                if (end.isBefore(start)) { call.fail("end_date cannot precede start_date"); return@post }

                val code = createCalendarEvent(
                    schoolId = ctx.schoolId,
                    title = req.title.trim(),
                    description = req.description.trim(),
                    type = type,
                    status = status,
                    source = EventSource.MANUAL,
                    startDate = req.startDate,
                    endDate = req.endDate ?: req.startDate,
                    allDay = req.allDay,
                    bannerUrl = req.bannerUrl,
                    icon = req.icon,
                    audience = audience,
                    classIds = req.classIds,
                    sectionIds = req.sectionIds,
                    notifyStudents = req.notifyStudents,
                    notifyParents = req.notifyParents,
                    notifyTeachers = req.notifyTeachers,
                    isMilestone = req.isMilestone || type == EventType.MILESTONE,
                    createdBy = ctx.userId
                )

                // Re-read with conflict warnings, fire notifications if published.
                val dto = dbQuery {
                    CalendarEventsTable.selectAll()
                        .where { CalendarEventsTable.eventCode eq code }
                        .first()
                        .toDtoWithConflicts(ctx.schoolId)
                }
                if (status == EventStatus.PUBLISHED) {
                    notifyEvent(ctx.schoolId, ctx.userId, dto)
                }
                call.created(dto, message = "Event created")
            }

            // ---------------------------------------------------------------
            // GET /events/{eventId}
            // ---------------------------------------------------------------
            get("/events/{eventId}") {
                val ctx = call.requireSchoolContext() ?: return@get
                val code = call.parameters["eventId"].orEmpty()
                val dto = dbQuery {
                    CalendarEventsTable.selectAll()
                        .where {
                            (CalendarEventsTable.schoolId eq ctx.schoolId) and
                                (CalendarEventsTable.eventCode eq code) and
                                (CalendarEventsTable.isActive eq true)
                        }
                        .firstOrNull()
                        ?.toDtoWithConflicts(ctx.schoolId)
                }
                if (dto == null) { call.fail("Event not found", HttpStatusCode.NotFound); return@get }
                call.ok(dto, message = "Event fetched")
            }

            // ---------------------------------------------------------------
            // PUT /events/{eventId} — edit / reschedule / publish / cancel
            // ---------------------------------------------------------------
            put("/events/{eventId}") {
                val ctx = call.requireSchoolAdmin() ?: return@put
                val code = call.parameters["eventId"].orEmpty()
                val req = call.receive<UpdateCalendarEventRequest>()

                req.type?.let { if (it.uppercase() !in EventType.ALL) { return@put call.fail("Invalid type") } }
                req.status?.let { if (it.uppercase() !in EventStatus.ALL) { return@put call.fail("Invalid status") } }
                req.audience?.let { if (it.uppercase() !in EventAudience.ALL) { return@put call.fail("Invalid audience") } }
                req.startDate?.let { if (parseIso(it) == null) { return@put call.fail("start_date must be YYYY-MM-DD") } }
                req.endDate?.let { if (parseIso(it) == null) { return@put call.fail("end_date must be YYYY-MM-DD") } }

                val updatedDto = dbQuery {
                    val row = CalendarEventsTable.selectAll()
                        .where {
                            (CalendarEventsTable.schoolId eq ctx.schoolId) and
                                (CalendarEventsTable.eventCode eq code) and
                                (CalendarEventsTable.isActive eq true)
                        }
                        .firstOrNull() ?: return@dbQuery null

                    val now = Instant.now()
                    CalendarEventsTable.update({
                        (CalendarEventsTable.schoolId eq ctx.schoolId) and (CalendarEventsTable.eventCode eq code)
                    }) {
                        req.title?.let { v -> it[title] = v.trim() }
                        req.description?.let { v -> it[description] = v.trim() }
                        req.type?.let { v -> it[type] = v.uppercase() }
                        req.status?.let { v -> it[status] = v.uppercase() }
                        req.startDate?.let { v -> it[startDate] = v }
                        req.endDate?.let { v -> it[endDate] = v }
                        req.allDay?.let { v -> it[allDay] = v }
                        req.bannerUrl?.let { v -> it[bannerUrl] = v }
                        req.icon?.let { v -> it[icon] = v }
                        req.audience?.let { v -> it[audience] = v.uppercase() }
                        req.classIds?.let { v -> it[classIds] = encodeStringList(v) }
                        req.sectionIds?.let { v -> it[sectionIds] = encodeStringList(v) }
                        req.notifyStudents?.let { v -> it[notifyStudents] = v }
                        req.notifyParents?.let { v -> it[notifyParents] = v }
                        req.notifyTeachers?.let { v -> it[notifyTeachers] = v }
                        req.isMilestone?.let { v -> it[isMilestone] = v }
                        it[updatedBy] = ctx.userId
                        it[updatedAt] = now
                    }

                    CalendarEventsTable.selectAll()
                        .where { CalendarEventsTable.eventCode eq code }
                        .first()
                        .toDtoWithConflicts(ctx.schoolId)
                }

                if (updatedDto == null) { call.fail("Event not found", HttpStatusCode.NotFound); return@put }
                // Notify on a publish transition.
                if (req.status?.uppercase() == EventStatus.PUBLISHED) {
                    notifyEvent(ctx.schoolId, ctx.userId, updatedDto)
                }
                call.ok(updatedDto, message = "Event updated")
            }

            // ---------------------------------------------------------------
            // DELETE /events/{eventId} — soft-delete
            // ---------------------------------------------------------------
            delete("/events/{eventId}") {
                val ctx = call.requireSchoolAdmin() ?: return@delete
                val code = call.parameters["eventId"].orEmpty()
                val affected = dbQuery {
                    CalendarEventsTable.update({
                        (CalendarEventsTable.schoolId eq ctx.schoolId) and
                            (CalendarEventsTable.eventCode eq code) and
                            (CalendarEventsTable.isActive eq true)
                    }) {
                        it[isActive] = false
                        it[updatedBy] = ctx.userId
                        it[updatedAt] = Instant.now()
                    }
                }
                if (affected == 0) { call.fail("Event not found", HttpStatusCode.NotFound); return@delete }
                call.okMessage("Event deleted")
            }

            // ---------------------------------------------------------------
            // POST /events/{eventId}/duplicate — e.g. PTM Grade 8 → Grade 9
            // ---------------------------------------------------------------
            post("/events/{eventId}/duplicate") {
                val ctx = call.requireSchoolAdmin() ?: return@post
                val code = call.parameters["eventId"].orEmpty()
                val req = runCatching { call.receive<DuplicateCalendarEventRequest>() }
                    .getOrDefault(DuplicateCalendarEventRequest())

                val source = dbQuery {
                    CalendarEventsTable.selectAll()
                        .where {
                            (CalendarEventsTable.schoolId eq ctx.schoolId) and
                                (CalendarEventsTable.eventCode eq code) and
                                (CalendarEventsTable.isActive eq true)
                        }
                        .firstOrNull()
                        ?.toCalendarEventDto()
                }
                if (source == null) { call.fail("Event not found", HttpStatusCode.NotFound); return@post }

                req.startDate?.let { if (parseIso(it) == null) { return@post call.fail("start_date must be YYYY-MM-DD") } }
                req.audience?.let { if (it.uppercase() !in EventAudience.ALL) { return@post call.fail("Invalid audience") } }

                val newCode = createCalendarEvent(
                    schoolId = ctx.schoolId,
                    title = (req.title ?: "${source.title} (Copy)").trim(),
                    description = source.description,
                    type = source.type,
                    status = EventStatus.DRAFT,                 // duplicates start as drafts
                    source = EventSource.MANUAL,
                    startDate = req.startDate ?: source.startDate,
                    endDate = req.endDate ?: req.startDate ?: source.endDate,
                    allDay = source.allDay,
                    bannerUrl = source.bannerUrl,
                    icon = source.icon,
                    audience = (req.audience ?: source.audience).uppercase(),
                    classIds = req.classIds ?: source.classIds,
                    sectionIds = req.sectionIds ?: source.sectionIds,
                    notifyStudents = source.notifyStudents,
                    notifyParents = source.notifyParents,
                    notifyTeachers = source.notifyTeachers,
                    isMilestone = source.isMilestone,
                    createdBy = ctx.userId
                )

                val dto = dbQuery {
                    CalendarEventsTable.selectAll()
                        .where { CalendarEventsTable.eventCode eq newCode }
                        .first()
                        .toDtoWithConflicts(ctx.schoolId)
                }
                call.created(dto, message = "Event duplicated")
            }
        }
    }
}

/**
 * Fire in-app notifications for a published event to the audiences the admin
 * selected. Best-effort: a notification failure must never fail the request.
 */
private suspend fun notifyEvent(
    schoolId: UUID,
    actorId: UUID,
    dto: AcademicCalendarEventDto
) {
    if (!dto.notifyStudents && !dto.notifyParents && !dto.notifyTeachers) return
    val recipients = dbQuery {
        com.littlebridge.enrollplus.db.AppUsersTable.selectAll()
            .where {
                (com.littlebridge.enrollplus.db.AppUsersTable.schoolId eq schoolId) and
                    (com.littlebridge.enrollplus.db.AppUsersTable.isActive eq true)
            }
            .filter {
                val r = it[com.littlebridge.enrollplus.db.AppUsersTable.role]
                when (r) {
                    "parent" -> dto.notifyParents
                    "student" -> dto.notifyStudents
                    else -> dto.notifyTeachers && r.startsWith("teacher")
                }
            }
            .map { it[com.littlebridge.enrollplus.db.AppUsersTable.id].value }
    }
    if (recipients.isEmpty()) return
    runCatching {
        Notify.toUsers(
            userIds = recipients,
            category = "calendar",
            title = dto.title,
            body = dto.description.take(140),
            schoolId = schoolId,
            actorId = actorId,
            deepLink = "calendar/${dto.id}",
            refType = "calendar_event",
            refId = dto.id
        )
    }
}
