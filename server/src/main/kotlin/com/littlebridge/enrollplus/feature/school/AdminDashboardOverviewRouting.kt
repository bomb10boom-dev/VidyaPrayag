/*
 * File: AdminDashboardOverviewRouting.kt
 * Module: feature.school
 *
 * Endpoint (JWT + school-scoped via requireSchoolContext):
 *   GET /api/admin/dashboard/overview
 *
 * Powers the REDESIGNED, analytics-driven SchoolHomeScreenV2 "command center".
 * This is a SINGLE consolidated read so the home tab makes ONE network call for
 * all of its flagship widgets instead of N small requests:
 *
 *   - School Pulse (0..100 composite health score + 5 sub-scores)
 *   - KPI cards (students / teachers / attendance / fee / active parents /
 *     pending approvals / upcoming events / transport) with week-over-week deltas
 *   - Parent Engagement Center (active %, most-engaged class, class leaderboard)
 *   - Communication Center (unread messages, pending queries, announcements,
 *     notice acknowledgements)
 *   - Event Dashboard (upcoming + recently completed, with day countdowns)
 *   - Teacher Spotlight (top teacher by a measurable composite)
 *   - Student Achievement Showcase (top exam performers as achievements)
 *   - Fee Collection Analytics (collected / pending / rate / monthly trend)
 *   - Birthdays (today / upcoming, students from children.date_of_birth)
 *
 * DESIGN CONTRACT
 * ---------------
 *  - 100% computed from EXISTING tables — NO new schema:
 *      schools, app_users, students, faculty, school_classes, school_subjects,
 *      teacher_subject_assignments, attendance_records, exam_results,
 *      fee_records, admission_enquiries, announcements, message_threads,
 *      academic_calendar, ptm_events, ptm_class_progress, children,
 *      parent_child_links, leave_requests, notifications.
 *  - Every aggregate scoped to ctx.schoolId (IDOR-safe).
 *  - Honest empty states: missing data → 0 / empty list / null block, NEVER
 *    fabricated. A module with no source table (e.g. transport, houses) reports
 *    `available = false` so the client hides it gracefully.
 *  - Trends are real week-over-week / month-over-month comparisons, never random.
 *
 * The legacy /summary, /analytics, /activity endpoints are UNCHANGED so nothing
 * else regresses; this endpoint is additive.
 */
package com.littlebridge.enrollplus.feature.school

import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.requireSchoolContext
import com.littlebridge.enrollplus.db.AdmissionEnquiriesTable
import com.littlebridge.enrollplus.db.AnnouncementsTable
import com.littlebridge.enrollplus.db.AppUsersTable
import com.littlebridge.enrollplus.db.AcademicCalendarTable
import com.littlebridge.enrollplus.db.AttendanceRecordsTable
import com.littlebridge.enrollplus.db.ChildrenTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.ExamResultsTable
import com.littlebridge.enrollplus.db.FacultyTable
import com.littlebridge.enrollplus.db.FeeRecordsTable
import com.littlebridge.enrollplus.db.LeaveRequestsTable
import com.littlebridge.enrollplus.db.MessageThreadsTable
import com.littlebridge.enrollplus.db.ParentChildLinksTable
import com.littlebridge.enrollplus.db.PtmEventsTable
import com.littlebridge.enrollplus.db.SchoolClassesTable
import com.littlebridge.enrollplus.db.SchoolsTable
import com.littlebridge.enrollplus.db.StudentsTable
import com.littlebridge.enrollplus.db.TeacherSubjectAssignmentsTable
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.roundToInt

// =====================================================================
// DTOs — overview
// =====================================================================

@Serializable
data class OvHeaderDto(
    val schoolId: String,
    val schoolName: String,
    val logoUrl: String? = null,
    val academicYear: String,
    val currentTerm: String,
    val adminName: String,
    val adminAvatarUrl: String? = null,
    val greeting: String,            // "Good Morning" etc. (server-localised to school day-part)
    val lastUpdated: String          // ISO instant
)

/** A single 0..100 contributor to the composite School Pulse. */
@Serializable
data class OvPulseCategoryDto(
    val key: String,                 // attendance | fees | teachers | parents | events
    val label: String,
    val score: Int,                  // 0..100
    val weight: Int,                 // relative weight used in composite (sums to 100)
    val available: Boolean           // false → no source data yet (excluded from score)
)

@Serializable
data class OvSchoolPulseDto(
    val score: Int,                  // 0..100 composite
    val status: String,              // EXCELLENT | HEALTHY | WATCH | CRITICAL
    val message: String,
    val categories: List<OvPulseCategoryDto>
)

/** A KPI with a real week-over-week (or period) delta. */
@Serializable
data class OvKpiDto(
    val key: String,
    val label: String,
    val value: Int,
    val unit: String,                // "" | "%"
    val deltaDirection: String,      // up | down | flat
    val deltaValue: Double,          // magnitude of the change (already abs)
    val deltaLabel: String,          // human label e.g. "from last week"
    val available: Boolean
)

@Serializable
data class OvInsightDto(
    val id: String,
    val type: String,                // ALERT | INFO | ACHIEVEMENT | REMINDER
    val severity: String,            // HIGH | MEDIUM | LOW
    val title: String,
    val description: String,
    val action: String               // a client-side action token (may be empty)
)

@Serializable
data class OvLeaderClassDto(
    val className: String,
    val score: Int,                  // 0..100 engagement score
    val direction: String            // up | down | flat (vs previous period)
)

@Serializable
data class OvParentEngagementDto(
    val available: Boolean,
    val activeParentsPct: Int,       // 0..100
    val activeParents: Int,
    val totalParents: Int,
    val mostEngagedClass: String,
    val leaderboard: List<OvLeaderClassDto>
)

@Serializable
data class OvCommunicationDto(
    val unreadMessages: Int,
    val pendingQueries: Int,         // open message threads awaiting reply
    val announcements: Int,          // announcements in the last 30 days
    val noticeAcknowledgements: Int  // PTM read receipts (proxy for acks)
)

@Serializable
data class OvEventDto(
    val id: String,
    val title: String,
    val date: String,                // YYYY-MM-DD
    val daysAway: Int,               // negative = past
    val type: String,                // CALENDAR | PTM | HOLIDAY
    val isHoliday: Boolean
)

@Serializable
data class OvEventDashboardDto(
    val available: Boolean,
    val upcoming: List<OvEventDto>,
    val recentlyCompleted: List<OvEventDto>
)

@Serializable
data class OvTeacherSpotlightDto(
    val available: Boolean,
    val teacherId: String? = null,
    val name: String = "",
    val department: String = "",
    val avatarUrl: String? = null,
    val score: Int = 0,              // 0..100 composite
    val highlight: String = "",      // e.g. "Top assignment coverage"
    val subjectsTaught: Int = 0
)

@Serializable
data class OvAchievementDto(
    val id: String,
    val studentName: String,
    val title: String,               // e.g. "Topper — Unit Test I"
    val category: String,            // ACADEMIC | SPORTS | COMPETITION
    val detail: String,              // e.g. "98 in Maths · Grade 5"
    val imageUrl: String? = null
)

@Serializable
data class OvAchievementShowcaseDto(
    val available: Boolean,
    val items: List<OvAchievementDto>
)

@Serializable
data class OvFeePointDto(
    val label: String,
    val value: Int                   // collection rate % for that month
)

@Serializable
data class OvFeeAnalyticsDto(
    val available: Boolean,
    val totalCollected: Double,
    val pending: Double,
    val collectionRate: Int,         // 0..100
    val currency: String,
    val trend: List<OvFeePointDto>
)

@Serializable
data class OvBirthdayDto(
    val name: String,
    val role: String,                // STUDENT | TEACHER
    val date: String,                // MM-DD
    val isToday: Boolean,
    val daysAway: Int,
    val avatarUrl: String? = null
)

@Serializable
data class OvBirthdaysDto(
    val available: Boolean,
    val today: List<OvBirthdayDto>,
    val upcoming: List<OvBirthdayDto>
)

/** Modules with NO backing data source in this build — client hides gracefully. */
@Serializable
data class OvModuleAvailabilityDto(
    val transport: Boolean,
    val houses: Boolean
)

@Serializable
data class AdminDashboardOverviewResponse(
    val header: OvHeaderDto,
    val schoolPulse: OvSchoolPulseDto,
    val kpis: List<OvKpiDto>,
    val insights: List<OvInsightDto>,
    val parentEngagement: OvParentEngagementDto,
    val communication: OvCommunicationDto,
    val events: OvEventDashboardDto,
    val teacherSpotlight: OvTeacherSpotlightDto,
    val achievements: OvAchievementShowcaseDto,
    val feeAnalytics: OvFeeAnalyticsDto,
    val birthdays: OvBirthdaysDto,
    val modules: OvModuleAvailabilityDto
)

// =====================================================================
// helpers (local to overview; do not clash with AdminDashboardRouting)
// =====================================================================

private val OV_MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

private fun ovParseDate(raw: String?): LocalDate? =
    raw?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }

private fun ovDirection(delta: Double): String = when {
    delta > 0.0001 -> "up"
    delta < -0.0001 -> "down"
    else -> "flat"
}

private fun ovRound1(v: Double): Double = (v * 10).roundToInt() / 10.0

/** Present-rate (%) over (date,status) rows; LATE counts as in-attendance. null when empty. */
private fun ovPresentRate(rows: List<Pair<String, String>>): Int? {
    if (rows.isEmpty()) return null
    val inAtt = rows.count {
        it.second.equals("PRESENT", true) || it.second.equals("LATE", true)
    }
    return (inAtt * 100) / rows.size
}

/** Parse exam score ("88" / "A+" / "Pending") → 0..100, else null. */
private fun ovExamScore(raw: String?): Double? {
    if (raw.isNullOrBlank()) return null
    val s = raw.trim()
    s.toDoubleOrNull()?.let { return it.coerceIn(0.0, 100.0) }
    return when (s.uppercase()) {
        "A+" -> 95.0; "A" -> 88.0; "B+" -> 82.0; "B" -> 75.0
        "C+" -> 68.0; "C" -> 60.0; "D" -> 50.0; "E", "F" -> 35.0
        else -> null
    }
}

private fun ovAcademicYear(startMonthName: String?, today: LocalDate): String {
    val startMonth = OV_MONTHS.indexOfFirst {
        it.equals(startMonthName?.take(3), ignoreCase = true)
    }.let { if (it >= 0) it + 1 else 4 }
    val startYear = if (today.monthValue >= startMonth) today.year else today.year - 1
    val endYY = (startYear + 1) % 100
    return "$startYear-${endYY.toString().padStart(2, '0')}"
}

/** Coarse term from which third of a 12-month academic year we're in. */
private fun ovCurrentTerm(startMonthName: String?, today: LocalDate): String {
    val startMonth = OV_MONTHS.indexOfFirst {
        it.equals(startMonthName?.take(3), ignoreCase = true)
    }.let { if (it >= 0) it + 1 else 4 }
    val monthsIn = ((today.monthValue - startMonth) + 12) % 12
    return when {
        monthsIn < 4 -> "Term 1"
        monthsIn < 8 -> "Term 2"
        else -> "Term 3"
    }
}

/** Time-of-day greeting computed from the server clock (UTC day-part is fine for India default). */
private fun ovGreeting(now: java.time.ZonedDateTime): String {
    val h = now.hour
    return when {
        h < 12 -> "Good Morning"
        h < 17 -> "Good Afternoon"
        else -> "Good Evening"
    }
}

// =====================================================================
// routing
// =====================================================================

fun Route.adminDashboardOverviewRouting() {
    authenticate("jwt") {
        route("/api/admin/dashboard") {

            get("/overview") {
                val ctx = call.requireSchoolContext() ?: return@get
                val schoolId = ctx.schoolId
                val today = LocalDate.now()
                val nowZ = java.time.ZonedDateTime.now(ZoneOffset.UTC)

                val payload = dbQuery {
                    // ---------------- header ----------------
                    val schoolRow = SchoolsTable.selectAll()
                        .where { SchoolsTable.id eq schoolId }
                        .singleOrNull()
                    val schoolName = schoolRow?.get(SchoolsTable.name) ?: "Your School"
                    val logoUrl = schoolRow?.get(SchoolsTable.logoUrl)
                    val startMonth = schoolRow?.get(SchoolsTable.academicYearStartMonth)

                    val adminRow = AppUsersTable.selectAll()
                        .where { AppUsersTable.id eq ctx.userId }
                        .singleOrNull()
                    val adminName = adminRow?.get(AppUsersTable.fullName)
                        ?.takeIf { it.isNotBlank() } ?: "Admin"
                    val adminAvatar = adminRow?.get(AppUsersTable.profilePicUrl)

                    // ---------------- raw pulls (each once) ----------------
                    val students = StudentsTable.selectAll()
                        .where { StudentsTable.schoolId eq schoolId }
                        .toList()
                    val studentTotal = students.size
                    val studentActive = students.count { it[StudentsTable.isActive] }

                    val faculty = FacultyTable.selectAll()
                        .where { FacultyTable.schoolId eq schoolId }
                        .toList()
                    val teacherActive = faculty.filter { it[FacultyTable.isActive] }
                    val teacherTotal = teacherActive.size

                    val classes = SchoolClassesTable.selectAll()
                        .where { SchoolClassesTable.schoolId eq schoolId }
                        .toList()

                    val attRows = AttendanceRecordsTable.selectAll()
                        .where {
                            (AttendanceRecordsTable.schoolId eq schoolId) and
                                (AttendanceRecordsTable.type eq "student")
                        }
                        .toList()
                        .map {
                            Triple(
                                it[AttendanceRecordsTable.date],
                                it[AttendanceRecordsTable.status],
                                it[AttendanceRecordsTable.grade]
                            )
                        }

                    val fees = FeeRecordsTable.selectAll()
                        .where { FeeRecordsTable.schoolId eq schoolId }
                        .toList()

                    val assignments = TeacherSubjectAssignmentsTable.selectAll()
                        .where {
                            (TeacherSubjectAssignmentsTable.schoolId eq schoolId) and
                                (TeacherSubjectAssignmentsTable.isActive eq true)
                        }
                        .toList()

                    // ===========================================================
                    // ATTENDANCE: this-week vs last-week present rate
                    // ===========================================================
                    val byDate = attRows.groupBy { it.first }
                    val thisWeekStart = today.minusDays(6)
                    val lastWeekStart = today.minusDays(13)
                    val lastWeekEnd = today.minusDays(7)

                    fun rateForRange(start: LocalDate, end: LocalDate): Int? {
                        val pool = attRows.filter {
                            val d = ovParseDate(it.first) ?: return@filter false
                            !d.isBefore(start) && !d.isAfter(end)
                        }.map { it.first to it.second }
                        return ovPresentRate(pool)
                    }

                    val thisWeekAtt = rateForRange(thisWeekStart, today)
                    val lastWeekAtt = rateForRange(lastWeekStart, lastWeekEnd)
                    // Fall back to the latest single recorded day if there's no rolling week data.
                    val latestDayRate = byDate.keys.maxOrNull()
                        ?.let { ovPresentRate(byDate[it].orEmpty().map { p -> p.first to p.second }) }
                    val attendancePct = thisWeekAtt ?: latestDayRate ?: 0
                    val attendanceDelta = if (thisWeekAtt != null && lastWeekAtt != null)
                        (thisWeekAtt - lastWeekAtt).toDouble() else 0.0
                    val attendanceAvailable = attRows.isNotEmpty()

                    // ===========================================================
                    // FEES: collection rate + monthly trend (last 6 months)
                    // ===========================================================
                    val paid = fees.filter { it[FeeRecordsTable.status].equals("PAID", true) }
                        .sumOf { it[FeeRecordsTable.amount] }
                    val outstanding = fees.filter {
                        val s = it[FeeRecordsTable.status]
                        s.equals("DUE", true) || s.equals("OVERDUE", true)
                    }.sumOf { it[FeeRecordsTable.amount] }
                    val feeBase = paid + outstanding
                    val feePct = if (feeBase > 0.0) ((paid / feeBase) * 100).roundToInt() else 0
                    val feeCurrency = fees.firstOrNull()?.get(FeeRecordsTable.currency) ?: "INR"
                    val feeAvailable = fees.isNotEmpty()

                    // Monthly fee collection-rate trend by fee_records.created_at.
                    val feesWithMonth = fees.mapNotNull { row ->
                        val created = runCatching {
                            row[FeeRecordsTable.createdAt].atZone(ZoneOffset.UTC).toLocalDate()
                        }.getOrNull() ?: return@mapNotNull null
                        Triple(
                            created.year to created.monthValue,
                            row[FeeRecordsTable.status],
                            row[FeeRecordsTable.amount]
                        )
                    }
                    val feeTrend = ArrayList<OvFeePointDto>(6)
                    for (back in 5 downTo 0) {
                        val d = today.minusMonths(back.toLong())
                        val key = d.year to d.monthValue
                        val pool = feesWithMonth.filter { it.first == key }
                        val p = pool.filter { it.second.equals("PAID", true) }.sumOf { it.third }
                        val o = pool.filter {
                            it.second.equals("DUE", true) || it.second.equals("OVERDUE", true)
                        }.sumOf { it.third }
                        val base = p + o
                        feeTrend += OvFeePointDto(
                            OV_MONTHS[d.monthValue - 1],
                            if (base > 0.0) ((p / base) * 100).roundToInt() else 0
                        )
                    }

                    // ===========================================================
                    // TEACHERS: assignment coverage (a measurable activity proxy)
                    // ===========================================================
                    val assignedTeacherKeys = assignments.mapNotNull { row ->
                        row[TeacherSubjectAssignmentsTable.teacherId]?.toString()
                            ?: row[TeacherSubjectAssignmentsTable.teacherName]?.takeIf { it.isNotBlank() }
                    }.toSet()
                    val assignedTeachers = assignedTeacherKeys.size.coerceAtMost(
                        teacherTotal.coerceAtLeast(assignedTeacherKeys.size)
                    )
                    val teacherCoverage = if (teacherTotal > 0)
                        (assignedTeachers * 100) / teacherTotal else 0
                    val teacherActivityScore = teacherCoverage
                    val teachersAvailable = teacherTotal > 0

                    // ===========================================================
                    // PARENTS: active parents % + class engagement leaderboard
                    // ===========================================================
                    // "Parents" of this school = approved parent_child_links rows
                    // (or children that belong to the school). "Active" = parent has
                    // a recent message thread OR an approved link (engagement signal).
                    val schoolChildren = ChildrenTable.selectAll()
                        .where { ChildrenTable.schoolId eq schoolId }
                        .toList()
                    val approvedLinks = ParentChildLinksTable.selectAll()
                        .where {
                            (ParentChildLinksTable.schoolId eq schoolId) and
                                (ParentChildLinksTable.status eq "approved")
                        }
                        .toList()
                    val parentIdsFromLinks = approvedLinks
                        .map { it[ParentChildLinksTable.parentId] }.toSet()
                    val parentIdsFromChildren = schoolChildren
                        .map { it[ChildrenTable.parentId] }.toSet()
                    val allParentIds = (parentIdsFromLinks + parentIdsFromChildren)
                    val totalParents = allParentIds.size

                    // Active = parents who own a message thread in this school in last 30 days.
                    val cutoff30Instant = nowZ.minusDays(30).toInstant()
                    val activeParentThreadOwners = if (allParentIds.isEmpty()) emptySet()
                    else {
                        val ownerFilter = allParentIds
                            .map { pid -> MessageThreadsTable.ownerUserId eq pid }
                            .reduce { acc, op -> acc or op }
                        MessageThreadsTable.selectAll()
                            .where { (MessageThreadsTable.schoolId eq schoolId) and ownerFilter }
                            .toList()
                            .filter { it[MessageThreadsTable.lastMessageAt].isAfter(cutoff30Instant) }
                            .map { it[MessageThreadsTable.ownerUserId] }
                            .toSet()
                    }
                    val activeParents = activeParentThreadOwners.size
                    val activeParentsPct = if (totalParents > 0)
                        (activeParents * 100) / totalParents else 0
                    val parentsAvailable = totalParents > 0

                    // Class engagement leaderboard: per-class attendance present-rate this
                    // week is a robust, always-available engagement proxy (more present →
                    // more engaged families). Ranked, top 5.
                    val perClassAttendance = attRows
                        .filter {
                            val d = ovParseDate(it.first) ?: return@filter false
                            !d.isBefore(thisWeekStart) && !d.isAfter(today) && !it.third.isNullOrBlank()
                        }
                        .groupBy { it.third!! }
                        .mapNotNull { (grade, rows) ->
                            val rate = ovPresentRate(rows.map { it.first to it.second }) ?: return@mapNotNull null
                            grade to rate
                        }
                    val perClassPrevAttendance = attRows
                        .filter {
                            val d = ovParseDate(it.first) ?: return@filter false
                            !d.isBefore(lastWeekStart) && !d.isAfter(lastWeekEnd) && !it.third.isNullOrBlank()
                        }
                        .groupBy { it.third!! }
                        .mapValues { (_, rows) -> ovPresentRate(rows.map { it.first to it.second }) }
                    val leaderboard = perClassAttendance
                        .sortedByDescending { it.second }
                        .take(5)
                        .map { (grade, rate) ->
                            val prev = perClassPrevAttendance[grade]
                            val dir = if (prev != null) ovDirection((rate - prev).toDouble()) else "flat"
                            OvLeaderClassDto(grade, rate, dir)
                        }
                    val mostEngagedClass = leaderboard.firstOrNull()?.className ?: ""

                    // ===========================================================
                    // EVENTS: academic_calendar + ptm_events (upcoming/completed)
                    // ===========================================================
                    val calendarEvents = AcademicCalendarTable.selectAll()
                        .where { AcademicCalendarTable.schoolId eq schoolId }
                        .toList()
                        .mapNotNull { row ->
                            val d = ovParseDate(row[AcademicCalendarTable.date]) ?: return@mapNotNull null
                            val holiday = row[AcademicCalendarTable.isHoliday]
                            OvEventDto(
                                id = row[AcademicCalendarTable.eventId],
                                title = row[AcademicCalendarTable.eventTitle],
                                date = d.toString(),
                                daysAway = (d.toEpochDay() - today.toEpochDay()).toInt(),
                                type = if (holiday) "HOLIDAY" else "CALENDAR",
                                isHoliday = holiday
                            )
                        }
                    val ptmEvents = PtmEventsTable.selectAll()
                        .where { PtmEventsTable.schoolId eq schoolId }
                        .toList()
                        .mapNotNull { row ->
                            val d = ovParseDate(row[PtmEventsTable.date]) ?: return@mapNotNull null
                            OvEventDto(
                                id = row[PtmEventsTable.id].value.toString(),
                                title = row[PtmEventsTable.title],
                                date = d.toString(),
                                daysAway = (d.toEpochDay() - today.toEpochDay()).toInt(),
                                type = "PTM",
                                isHoliday = false
                            )
                        }
                    val allEvents = calendarEvents + ptmEvents
                    val upcomingEvents = allEvents
                        .filter { it.daysAway >= 0 }
                        .sortedBy { it.daysAway }
                        .take(6)
                    val completedEvents = allEvents
                        .filter { it.daysAway < 0 }
                        .sortedByDescending { it.daysAway }
                        .take(4)
                    val eventsAvailable = allEvents.isNotEmpty()

                    // ===========================================================
                    // COMMUNICATION: unread / pending / announcements / acks
                    // ===========================================================
                    val adminThreads = MessageThreadsTable.selectAll()
                        .where {
                            (MessageThreadsTable.schoolId eq schoolId) and
                                (MessageThreadsTable.ownerUserId eq ctx.userId)
                        }
                        .toList()
                    val unreadMessages = adminThreads.sumOf { it[MessageThreadsTable.unreadCount] }
                    val pendingQueries = adminThreads.count { it[MessageThreadsTable.unreadCount] > 0 }
                    val announcements30 = AnnouncementsTable.selectAll()
                        .where { AnnouncementsTable.schoolId eq schoolId }
                        .toList()
                        .count {
                            val created = runCatching {
                                it[AnnouncementsTable.createdAt].atZone(ZoneOffset.UTC).toInstant()
                            }.getOrNull()
                            created != null && created.isAfter(cutoff30Instant)
                        }
                    val noticeAcks = PtmEventsTable.selectAll()
                        .where { PtmEventsTable.schoolId eq schoolId }
                        .toList()
                        .sumOf { it[PtmEventsTable.readReceipts] }

                    // ===========================================================
                    // TEACHER SPOTLIGHT: highest measurable composite
                    // ===========================================================
                    // Per-teacher: subjects taught (assignment count). Combined with
                    // a normalised coverage signal. Picks the single top teacher.
                    val assignmentsByTeacher = assignments
                        .filter { it[TeacherSubjectAssignmentsTable.teacherId] != null }
                        .groupBy { it[TeacherSubjectAssignmentsTable.teacherId]!! }
                    val spotlight = run {
                        if (faculty.isEmpty()) return@run OvTeacherSpotlightDto(available = false)
                        // Match faculty to assignments via faculty.userId when present.
                        val ranked = teacherActive.map { f ->
                            val uid = f[FacultyTable.userId]
                            val subjects = uid?.let { assignmentsByTeacher[it]?.size } ?: 0
                            // Composite: subjects taught (scaled) — a measurable activity.
                            val score = (subjects * 20).coerceIn(0, 100)
                            Triple(f, subjects, score)
                        }.sortedByDescending { it.third }
                        val top = ranked.firstOrNull { it.second > 0 } ?: ranked.firstOrNull()
                        if (top == null) OvTeacherSpotlightDto(available = false)
                        else OvTeacherSpotlightDto(
                            available = true,
                            teacherId = top.first[FacultyTable.id].value.toString(),
                            name = top.first[FacultyTable.name],
                            department = top.first[FacultyTable.department].orEmpty(),
                            avatarUrl = top.first[FacultyTable.profilePic],
                            score = top.third,
                            highlight = if (top.second > 0)
                                "Teaching ${top.second} subject${if (top.second == 1) "" else "s"}"
                            else "Active faculty member",
                            subjectsTaught = top.second
                        )
                    }

                    // ===========================================================
                    // ACHIEVEMENTS: top exam performers → achievement cards
                    // ===========================================================
                    val exams = ExamResultsTable.selectAll()
                        .where { ExamResultsTable.schoolId eq schoolId }
                        .toList()
                    val achievements = exams
                        .mapNotNull { row ->
                            val score = ovExamScore(row[ExamResultsTable.score]) ?: return@mapNotNull null
                            Triple(row, score, row[ExamResultsTable.studentName])
                        }
                        .filter { it.second >= 85.0 }            // only genuine high achievers
                        .sortedByDescending { it.second }
                        .take(8)
                        .map { (row, score, name) ->
                            OvAchievementDto(
                                id = row[ExamResultsTable.id].value.toString(),
                                studentName = name,
                                title = "Topper — ${row[ExamResultsTable.test]}",
                                category = "ACADEMIC",
                                detail = "${score.roundToInt()} in ${row[ExamResultsTable.subject]} · ${row[ExamResultsTable.className]}",
                                imageUrl = row[ExamResultsTable.imageUrl]
                            )
                        }

                    // ===========================================================
                    // BIRTHDAYS: students (children.date_of_birth) within school
                    // ===========================================================
                    fun birthdayDaysAway(dob: LocalDate): Int {
                        var next = dob.withYear(today.year)
                        if (next.isBefore(today)) next = next.withYear(today.year + 1)
                        return (next.toEpochDay() - today.toEpochDay()).toInt()
                    }
                    val childBirthdays = schoolChildren
                        .filter { it[ChildrenTable.isActive] }
                        .mapNotNull { row ->
                            val dob = ovParseDate(row[ChildrenTable.dateOfBirth]) ?: return@mapNotNull null
                            val days = birthdayDaysAway(dob)
                            OvBirthdayDto(
                                name = row[ChildrenTable.childName],
                                role = "STUDENT",
                                date = "${dob.monthValue.toString().padStart(2, '0')}-${dob.dayOfMonth.toString().padStart(2, '0')}",
                                isToday = days == 0,
                                daysAway = days,
                                avatarUrl = row[ChildrenTable.profilePic]
                            )
                        }
                    val todayBirthdays = childBirthdays.filter { it.isToday }.take(10)
                    val upcomingBirthdays = childBirthdays
                        .filter { !it.isToday && it.daysAway <= 14 }
                        .sortedBy { it.daysAway }
                        .take(10)
                    val birthdaysAvailable = childBirthdays.isNotEmpty()

                    // ===========================================================
                    // PENDING APPROVALS: admissions(new) + link requests + leaves
                    // ===========================================================
                    val pendingAdmissions = AdmissionEnquiriesTable.selectAll()
                        .where {
                            (AdmissionEnquiriesTable.schoolId eq schoolId) and
                                (AdmissionEnquiriesTable.status eq "new")
                        }
                        .count().toInt()
                    val pendingLinks = ParentChildLinksTable.selectAll()
                        .where {
                            (ParentChildLinksTable.schoolId eq schoolId) and
                                (ParentChildLinksTable.status eq "pending")
                        }
                        .count().toInt()
                    val pendingLeaves = LeaveRequestsTable.selectAll()
                        .where {
                            (LeaveRequestsTable.schoolId eq schoolId) and
                                (LeaveRequestsTable.status eq "Pending")
                        }
                        .count().toInt()
                    val pendingApprovals = pendingAdmissions + pendingLinks + pendingLeaves

                    // ===========================================================
                    // SCHOOL PULSE: weighted composite of available categories
                    // ===========================================================
                    val eventParticipationScore = run {
                        // Latest PTM turnout (checked-in / expected) as participation proxy.
                        val latestPtm = PtmEventsTable.selectAll()
                            .where { PtmEventsTable.schoolId eq schoolId }
                            .orderBy(PtmEventsTable.date, SortOrder.DESC)
                            .limit(1)
                            .singleOrNull()
                        if (latestPtm == null) null
                        else {
                            val expected = latestPtm[PtmEventsTable.expectedParents]
                            val checked = latestPtm[PtmEventsTable.checkedInParents]
                            if (expected > 0) ((checked * 100) / expected).coerceIn(0, 100) else null
                        }
                    }

                    val pulseCategories = mutableListOf<OvPulseCategoryDto>()
                    pulseCategories += OvPulseCategoryDto(
                        "attendance", "Attendance",
                        if (attendanceAvailable) attendancePct else 0, 30, attendanceAvailable
                    )
                    pulseCategories += OvPulseCategoryDto(
                        "fees", "Fee Collection",
                        if (feeAvailable) feePct else 0, 20, feeAvailable
                    )
                    pulseCategories += OvPulseCategoryDto(
                        "teachers", "Teacher Activity",
                        if (teachersAvailable) teacherActivityScore else 0, 20, teachersAvailable
                    )
                    pulseCategories += OvPulseCategoryDto(
                        "parents", "Parent Engagement",
                        if (parentsAvailable) activeParentsPct else 0, 20, parentsAvailable
                    )
                    pulseCategories += OvPulseCategoryDto(
                        "events", "Event Participation",
                        eventParticipationScore ?: 0, 10, eventParticipationScore != null
                    )

                    val availableCats = pulseCategories.filter { it.available }
                    val pulseScore = if (availableCats.isEmpty()) 0
                    else {
                        val weightSum = availableCats.sumOf { it.weight }
                        val weighted = availableCats.sumOf { it.score * it.weight }
                        if (weightSum > 0) (weighted / weightSum) else 0
                    }
                    val pulseStatus = when {
                        availableCats.isEmpty() -> "WATCH"
                        pulseScore >= 90 -> "EXCELLENT"
                        pulseScore >= 75 -> "HEALTHY"
                        pulseScore >= 60 -> "WATCH"
                        else -> "CRITICAL"
                    }
                    val pulseMessage = when (pulseStatus) {
                        "EXCELLENT" -> "Your campus is thriving"
                        "HEALTHY" -> "Everything looks stable today"
                        "WATCH" -> "A few metrics need your attention"
                        else -> "Critical metrics need immediate action"
                    }

                    // ===========================================================
                    // KPI cards (8) — each with a real delta where computable
                    // ===========================================================
                    // Student growth week-over-week using created_at.
                    val createdDates = students.mapNotNull {
                        runCatching {
                            it[StudentsTable.createdAt].atZone(ZoneOffset.UTC).toLocalDate()
                        }.getOrNull()
                    }
                    val newThisWeek = createdDates.count { !it.isBefore(thisWeekStart) }
                    val newLastWeek = createdDates.count {
                        !it.isBefore(lastWeekStart) && it.isBefore(thisWeekStart)
                    }
                    val studentDelta = (newThisWeek - newLastWeek).toDouble()

                    val kpis = listOf(
                        OvKpiDto(
                            "students", "Total Students", studentTotal, "",
                            ovDirection(studentDelta), abs(studentDelta), "new this week", true
                        ),
                        OvKpiDto(
                            "teachers", "Total Teachers", teacherTotal, "",
                            "flat", 0.0, "active faculty", teachersAvailable || teacherTotal >= 0
                        ),
                        OvKpiDto(
                            "attendance", "Attendance", attendancePct, "%",
                            ovDirection(attendanceDelta), ovRound1(abs(attendanceDelta)),
                            "from last week", attendanceAvailable
                        ),
                        OvKpiDto(
                            "fees", "Fee Collection", feePct, "%",
                            "flat", 0.0, "collected", feeAvailable
                        ),
                        OvKpiDto(
                            "parents", "Active Parents", activeParentsPct, "%",
                            "flat", 0.0, "engaged this month", parentsAvailable
                        ),
                        OvKpiDto(
                            "approvals", "Pending Approvals", pendingApprovals, "",
                            "flat", 0.0, "need review", true
                        ),
                        OvKpiDto(
                            "events", "Upcoming Events", upcomingEvents.size, "",
                            "flat", 0.0, "scheduled", eventsAvailable
                        ),
                        OvKpiDto(
                            "transport", "Transport", 0, "",
                            "flat", 0.0, "not configured", false
                        )
                    )

                    // ===========================================================
                    // INSIGHTS: actionable, prioritised, from real signals
                    // ===========================================================
                    val insights = mutableListOf<OvInsightDto>()
                    if (attendanceAvailable && attendancePct < 75) {
                        insights += OvInsightDto(
                            "ins_attendance_low", "ALERT", "HIGH",
                            "Attendance Alert",
                            "Weekly attendance is $attendancePct%. Review low-attendance classes.",
                            "VIEW_ATTENDANCE"
                        )
                    }
                    if (parentsAvailable && activeParentsPct < 40) {
                        insights += OvInsightDto(
                            "ins_parent_drop", "ALERT", "MEDIUM",
                            "Parent Engagement Drop",
                            "Only $activeParentsPct% of parents are active this month.",
                            "VIEW_PARENTS"
                        )
                    }
                    if (feeAvailable && feePct < 70) {
                        insights += OvInsightDto(
                            "ins_fee_low", "ALERT", "HIGH",
                            "Fee Collection Alert",
                            "Collection is at $feePct%. ${outstanding.roundToInt()} $feeCurrency outstanding.",
                            "VIEW_FEES"
                        )
                    }
                    if (pendingApprovals > 0) {
                        insights += OvInsightDto(
                            "ins_approvals", "REMINDER", "MEDIUM",
                            "Pending Approvals",
                            "$pendingApprovals item${if (pendingApprovals == 1) "" else "s"} awaiting your review.",
                            "VIEW_APPROVALS"
                        )
                    }
                    upcomingEvents.firstOrNull { it.daysAway in 0..3 }?.let { ev ->
                        insights += OvInsightDto(
                            "ins_event_${ev.id}", "REMINDER", "LOW",
                            "Upcoming: ${ev.title}",
                            if (ev.daysAway == 0) "Happening today." else "In ${ev.daysAway} day${if (ev.daysAway == 1) "" else "s"}.",
                            "VIEW_EVENTS"
                        )
                    }
                    achievements.firstOrNull()?.let { a ->
                        insights += OvInsightDto(
                            "ins_achievement", "ACHIEVEMENT", "LOW",
                            "Achievement Highlight",
                            "${a.studentName} — ${a.detail}.",
                            "VIEW_ACHIEVEMENTS"
                        )
                    }
                    if (todayBirthdays.isNotEmpty()) {
                        insights += OvInsightDto(
                            "ins_birthday", "INFO", "LOW",
                            "Birthdays Today",
                            "${todayBirthdays.size} celebration${if (todayBirthdays.size == 1) "" else "s"} today 🎉",
                            "VIEW_BIRTHDAYS"
                        )
                    }

                    AdminDashboardOverviewResponse(
                        header = OvHeaderDto(
                            schoolId = schoolId.toString(),
                            schoolName = schoolName,
                            logoUrl = logoUrl,
                            academicYear = ovAcademicYear(startMonth, today),
                            currentTerm = ovCurrentTerm(startMonth, today),
                            adminName = adminName,
                            adminAvatarUrl = adminAvatar,
                            greeting = ovGreeting(nowZ),
                            lastUpdated = nowZ.toInstant().toString()
                        ),
                        schoolPulse = OvSchoolPulseDto(
                            score = pulseScore,
                            status = pulseStatus,
                            message = pulseMessage,
                            categories = pulseCategories
                        ),
                        kpis = kpis,
                        insights = insights,
                        parentEngagement = OvParentEngagementDto(
                            available = parentsAvailable,
                            activeParentsPct = activeParentsPct,
                            activeParents = activeParents,
                            totalParents = totalParents,
                            mostEngagedClass = mostEngagedClass,
                            leaderboard = leaderboard
                        ),
                        communication = OvCommunicationDto(
                            unreadMessages = unreadMessages,
                            pendingQueries = pendingQueries,
                            announcements = announcements30,
                            noticeAcknowledgements = noticeAcks
                        ),
                        events = OvEventDashboardDto(
                            available = eventsAvailable,
                            upcoming = upcomingEvents,
                            recentlyCompleted = completedEvents
                        ),
                        teacherSpotlight = spotlight,
                        achievements = OvAchievementShowcaseDto(
                            available = achievements.isNotEmpty(),
                            items = achievements
                        ),
                        feeAnalytics = OvFeeAnalyticsDto(
                            available = feeAvailable,
                            totalCollected = ovRound1(paid),
                            pending = ovRound1(outstanding),
                            collectionRate = feePct,
                            currency = feeCurrency,
                            trend = feeTrend
                        ),
                        birthdays = OvBirthdaysDto(
                            available = birthdaysAvailable,
                            today = todayBirthdays,
                            upcoming = upcomingBirthdays
                        ),
                        modules = OvModuleAvailabilityDto(
                            transport = false,   // no transport table in this build
                            houses = false       // no house system in this build
                        )
                    )
                }

                call.ok(payload, message = "Dashboard overview fetched successfully")
            }
        }
    }
}
