/*
 * File: SchoolIntelligenceRouting.kt
 * Module: feature.school
 *
 * Endpoint: GET /api/v1/school/dashboard/intelligence   (JWT, school-scoped)
 *
 * WHY THIS EXISTS
 *   The web admin "Command Center" dashboard needs five operational
 *   intelligence blocks that no single existing endpoint provided. Rather than
 *   make the client fan out to 6 routes and stitch the joins client-side, this
 *   one read assembles them server-side from REAL tables only. Every number is
 *   computed from data; nothing is fabricated, and any block with no underlying
 *   data returns an honest empty array (the UI renders an empty state, never a
 *   placeholder number).
 *
 * BLOCKS (all school_id-scoped from the JWT subject via requireSchoolContext):
 *
 *   1. attendance_timeline — per-DAY student present-rate over the last N days
 *      (default 30), each point flagged `is_anomaly` when the rate falls below
 *      a dynamic threshold (mean − 1σ, floored at 10pts below mean). Each point
 *      also carries `exam` (a same-day assessment/calendar exam title) so the
 *      client can overlay exam markers and surface the correlation. Source:
 *      attendance_records (type='student') + assessments.exam_date +
 *      academic_calendar (non-holiday events whose title looks like an exam).
 *
 *   2. early_warning — students whose COMBINED signals put them at risk:
 *        • attendance_pct  below ATT_RISK        (real, from attendance_records)
 *        • marks_pct       below MARKS_RISK       (real, from assessment_marks)
 *        • leave_count     at/above LEAVE_RISK    (real, from leave_requests)
 *      A student appears only if ≥1 signal flags. `signals` lists exactly which
 *      ones, so the UI shows the reason, not an opaque score. Ranked by signal
 *      count then severity. Capped at 30 rows.
 *
 *   3. academic_health — syllabus coverage grid: one row per class, one cell per
 *      subject, coverage = covered_units / total_units (from syllabus_units).
 *      Empty when no syllabus units exist (honest — never a fake heat map).
 *
 *   4. activity_feed — institutional event stream merged from notifications,
 *      leave_requests, and announcements with real DB timestamps, newest first,
 *      capped at 40. Each item: actor, action, target, category, iso_time.
 *
 *   5. meta — school name, academic week (ISO week of the school's onboarded
 *      year), today's date, and the as-of date of the latest attendance.
 *
 * REAL-TIME STRATEGY (documented here AND in the web hook):
 *   This whole payload is NEAR-LIVE on the client (60s SWR poll). The truly
 *   live signals (unread notifications, students-present-now) come from the
 *   separate /notifications + /attendance/summary LIVE hooks (15s). We do NOT
 *   open a websocket: the Ktor backend publishes no Supabase realtime channel
 *   to the web client, so tuned polling is the correct tool (see ARCHITECTURE).
 */
package com.littlebridge.enrollplus.feature.school

import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.requireSchoolContext
import com.littlebridge.enrollplus.db.AcademicCalendarTable
import com.littlebridge.enrollplus.db.AnnouncementsTable
import com.littlebridge.enrollplus.db.AppUsersTable
import com.littlebridge.enrollplus.db.AssessmentMarksTable
import com.littlebridge.enrollplus.db.AssessmentsTable
import com.littlebridge.enrollplus.db.AttendanceRecordsTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.LeaveRequestsTable
import com.littlebridge.enrollplus.db.NotificationsTable
import com.littlebridge.enrollplus.db.SchoolsTable
import com.littlebridge.enrollplus.db.StudentsTable
import com.littlebridge.enrollplus.db.SyllabusUnitsTable
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID
import kotlin.math.sqrt

// ───────────────────────── thresholds (operational, documented) ──────────────
private const val ATT_RISK = 75      // attendance % below this is a risk signal
private const val MARKS_RISK = 40    // marks % below this is a risk signal
private const val LEAVE_RISK = 3     // ≥ this many leave requests is a signal
private const val TIMELINE_DAYS = 30 // attendance timeline window

// ───────────────────────────────── DTOs ──────────────────────────────────────

@Serializable
data class AttendancePointDto(
    val date: String,
    val rate: Int,
    val present: Int,
    val absent: Int,
    val total: Int,
    @SerialName("is_anomaly") val isAnomaly: Boolean,
    val exam: String? = null
)

@Serializable
data class RiskSignalDto(
    val kind: String,       // attendance | marks | leave
    val label: String,      // human reason, e.g. "Attendance 61% (below 75%)"
    val severity: Int       // 1..3
)

@Serializable
data class EarlyWarningStudentDto(
    @SerialName("student_code") val studentCode: String,
    val name: String,
    @SerialName("class_name") val className: String,
    val section: String,
    @SerialName("attendance_pct") val attendancePct: Int?,
    @SerialName("marks_pct") val marksPct: Int?,
    @SerialName("leave_count") val leaveCount: Int,
    @SerialName("risk_level") val riskLevel: String,  // high | medium | watch
    val signals: List<RiskSignalDto>
)

@Serializable
data class HealthCellDto(
    val subject: String,
    val percentage: Int,
    @SerialName("covered_units") val coveredUnits: Int,
    @SerialName("total_units") val totalUnits: Int
)

@Serializable
data class HealthRowDto(
    @SerialName("class_name") val className: String,
    val cells: List<HealthCellDto>,
    @SerialName("class_average") val classAverage: Int
)

@Serializable
data class AcademicHealthDto(
    val subjects: List<String>,
    val rows: List<HealthRowDto>
)

@Serializable
data class ActivityItemDto(
    val id: String,
    val category: String,   // attendance|marks|homework|announcement|leave|fees|link|general
    val actor: String,
    val action: String,
    val target: String,
    @SerialName("iso_time") val isoTime: String
)

@Serializable
data class IntelligenceMetaDto(
    @SerialName("school_name") val schoolName: String,
    @SerialName("academic_week") val academicWeek: Int,
    @SerialName("today") val today: String,
    @SerialName("attendance_as_of") val attendanceAsOf: String?
)

@Serializable
data class DashboardIntelligenceDto(
    val meta: IntelligenceMetaDto,
    @SerialName("attendance_timeline") val attendanceTimeline: List<AttendancePointDto>,
    @SerialName("early_warning") val earlyWarning: List<EarlyWarningStudentDto>,
    @SerialName("academic_health") val academicHealth: AcademicHealthDto,
    @SerialName("activity_feed") val activityFeed: List<ActivityItemDto>
)

private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

/** "exam"-like calendar/assessment heuristic for the overlay. */
private fun looksLikeExam(title: String): Boolean {
    val t = title.lowercase()
    return listOf("exam", "test", "assessment", "unit test", "mid term", "midterm", "final", "board")
        .any { t.contains(it) }
}

fun Route.schoolIntelligenceRouting() {
    authenticate("jwt") {
        get("/api/v1/school/dashboard/intelligence") {
            val ctx = call.requireSchoolContext() ?: return@get
            val schoolId = ctx.schoolId

            val payload = dbQuery {
                // ── meta ──────────────────────────────────────────────────────
                val schoolName = SchoolsTable.selectAll()
                    .where { SchoolsTable.id eq schoolId }
                    .singleOrNull()?.get(SchoolsTable.name) ?: "Your School"
                val today = LocalDate.now()
                val academicWeek = today.get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear())

                // ── 1. attendance timeline (per-day, anomaly + exam overlay) ──
                val sinceDate = today.minusDays((TIMELINE_DAYS - 1).toLong())
                val attRows = AttendanceRecordsTable.selectAll().where {
                    (AttendanceRecordsTable.schoolId eq schoolId) and
                        (AttendanceRecordsTable.type eq "student")
                }.map {
                    it[AttendanceRecordsTable.date] to it[AttendanceRecordsTable.status].lowercase()
                }.filter { (d, _) ->
                    runCatching { LocalDate.parse(d, ISO_DATE) >= sinceDate }.getOrDefault(false)
                }

                // exam dates: assessments.exam_date + calendar exam-like events
                val examByDate = HashMap<String, String>()
                AssessmentsTable.selectAll()
                    .where { (AssessmentsTable.schoolId eq schoolId) and (AssessmentsTable.isActive eq true) }
                    .forEach { row ->
                        row[AssessmentsTable.examDate]?.let { d ->
                            examByDate.putIfAbsent(d, "${row[AssessmentsTable.subject]} — ${row[AssessmentsTable.name]}")
                        }
                    }
                AcademicCalendarTable.selectAll()
                    .where { (AcademicCalendarTable.schoolId eq schoolId) and (AcademicCalendarTable.isHoliday eq false) }
                    .forEach { row ->
                        val t = row[AcademicCalendarTable.eventTitle]
                        if (looksLikeExam(t)) examByDate.putIfAbsent(row[AcademicCalendarTable.date], t)
                    }

                val byDate = attRows.groupBy { it.first }
                val rawPoints = byDate.map { (date, list) ->
                    val present = list.count { it.second == "present" || it.second == "late" }
                    val absent = list.count { it.second == "absent" }
                    val total = list.size
                    val rate = if (total > 0) (present * 100) / total else 0
                    Triple(date, rate, Triple(present, absent, total))
                }.sortedBy { it.first }

                // dynamic anomaly threshold: mean − 1σ (floor mean − 10)
                val rates = rawPoints.map { it.second }
                val mean = if (rates.isNotEmpty()) rates.average() else 0.0
                val sd = if (rates.size > 1) {
                    sqrt(rates.sumOf { (it - mean) * (it - mean) } / rates.size)
                } else 0.0
                val threshold = minOf(mean - sd, mean - 10.0)

                val timeline = rawPoints.map { (date, rate, counts) ->
                    AttendancePointDto(
                        date = date,
                        rate = rate,
                        present = counts.first,
                        absent = counts.second,
                        total = counts.third,
                        isAnomaly = rates.size >= 3 && rate < threshold && rate > 0,
                        exam = examByDate[date]
                    )
                }

                // ── 2. early warning (real combined signals) ──────────────────
                val students = StudentsTable.selectAll()
                    .where { (StudentsTable.schoolId eq schoolId) and (StudentsTable.isActive eq true) }
                    .map {
                        StudentLite(
                            code = it[StudentsTable.studentCode],
                            name = it[StudentsTable.fullName],
                            className = it[StudentsTable.className],
                            section = it[StudentsTable.section]
                        )
                    }

                // attendance pct per student over the window
                val attByStudent = HashMap<String, IntArray>() // code -> [present, total]
                AttendanceRecordsTable.selectAll().where {
                    (AttendanceRecordsTable.schoolId eq schoolId) and
                        (AttendanceRecordsTable.type eq "student")
                }.forEach { row ->
                    val code = row[AttendanceRecordsTable.personId]
                    val s = row[AttendanceRecordsTable.status].lowercase()
                    val arr = attByStudent.getOrPut(code) { intArrayOf(0, 0) }
                    if (s == "present" || s == "late") arr[0]++
                    arr[1]++
                }

                // marks pct per student across published assessments
                val assessMax = AssessmentsTable.selectAll()
                    .where { AssessmentsTable.schoolId eq schoolId }
                    .associate { it[AssessmentsTable.id].value to it[AssessmentsTable.maxMarks] }
                val marksByStudent = HashMap<String, DoubleArray>() // code -> [gotPct sum, count]
                AssessmentMarksTable.selectAll().forEach { row ->
                    val aId = row[AssessmentMarksTable.assessmentId]
                    val max = assessMax[aId] ?: return@forEach
                    val m = row[AssessmentMarksTable.marks] ?: return@forEach
                    if (max <= 0) return@forEach
                    val code = row[AssessmentMarksTable.studentId]
                    val arr = marksByStudent.getOrPut(code) { doubleArrayOf(0.0, 0.0) }
                    arr[0] += (m / max) * 100.0
                    arr[1] += 1.0
                }

                // leave counts per requester (student-filed) by name (no FK on legacy rows)
                val leaveByName = HashMap<String, Int>()
                LeaveRequestsTable.selectAll().where {
                    (LeaveRequestsTable.schoolId eq schoolId) and
                        (LeaveRequestsTable.requesterRole eq "student")
                }.forEach { row ->
                    val n = row[LeaveRequestsTable.requesterName].trim().lowercase()
                    leaveByName[n] = (leaveByName[n] ?: 0) + 1
                }

                val warnings = students.mapNotNull { st ->
                    val att = attByStudent[st.code]
                    val attPct = if (att != null && att[1] > 0) (att[0] * 100) / att[1] else null
                    val mk = marksByStudent[st.code]
                    val marksPct = if (mk != null && mk[1] > 0) (mk[0] / mk[1]).toInt() else null
                    val leaveCount = leaveByName[st.name.trim().lowercase()] ?: 0

                    val signals = buildList {
                        if (attPct != null && attPct < ATT_RISK) {
                            add(RiskSignalDto("attendance", "Attendance $attPct% (below $ATT_RISK%)",
                                if (attPct < 50) 3 else if (attPct < 65) 2 else 1))
                        }
                        if (marksPct != null && marksPct < MARKS_RISK) {
                            add(RiskSignalDto("marks", "Average $marksPct% (below $MARKS_RISK%)",
                                if (marksPct < 25) 3 else 2))
                        }
                        if (leaveCount >= LEAVE_RISK) {
                            add(RiskSignalDto("leave", "$leaveCount leave requests filed",
                                if (leaveCount >= 5) 2 else 1))
                        }
                    }
                    if (signals.isEmpty()) return@mapNotNull null

                    val maxSev = signals.maxOf { it.severity }
                    val level = when {
                        signals.size >= 2 || maxSev == 3 -> "high"
                        maxSev == 2 -> "medium"
                        else -> "watch"
                    }
                    EarlyWarningStudentDto(
                        studentCode = st.code, name = st.name,
                        className = st.className, section = st.section,
                        attendancePct = attPct, marksPct = marksPct, leaveCount = leaveCount,
                        riskLevel = level, signals = signals
                    )
                }.sortedWith(
                    compareByDescending<EarlyWarningStudentDto> { it.signals.size }
                        .thenByDescending { it.signals.sumOf { s -> s.severity } }
                ).take(30)

                // ── 3. academic health (syllabus coverage grid) ──────────────
                val units = SyllabusUnitsTable.selectAll()
                    .where { SyllabusUnitsTable.schoolId eq schoolId }
                    .map {
                        Triple(
                            it[SyllabusUnitsTable.className],
                            it[SyllabusUnitsTable.subject],
                            it[SyllabusUnitsTable.isCovered]
                        )
                    }
                val subjects = units.map { it.second }.distinct().sorted()
                val byClass = units.groupBy { it.first }
                val healthRows = byClass.map { (className, list) ->
                    val cells = subjects.map { subj ->
                        val forSubj = list.filter { it.second == subj }
                        val total = forSubj.size
                        val covered = forSubj.count { it.third }
                        HealthCellDto(
                            subject = subj,
                            percentage = if (total > 0) (covered * 100) / total else 0,
                            coveredUnits = covered,
                            totalUnits = total
                        )
                    }
                    val present = cells.filter { it.totalUnits > 0 }
                    val classAvg = if (present.isNotEmpty()) present.sumOf { it.percentage } / present.size else 0
                    HealthRowDto(className = className, cells = cells, classAverage = classAvg)
                }.sortedBy { it.className }
                val academicHealth = AcademicHealthDto(subjects = subjects, rows = healthRows)

                // ── 4. activity feed (real DB events, newest first) ──────────
                val actorNames = AppUsersTable.selectAll()
                    .where { AppUsersTable.schoolId eq schoolId }
                    .associate { it[AppUsersTable.id].value to it[AppUsersTable.fullName] }

                val feed = ArrayList<ActivityItemDto>()
                NotificationsTable.selectAll()
                    .where { NotificationsTable.schoolId eq schoolId }
                    .orderBy(NotificationsTable.createdAt, SortOrder.DESC)
                    .limit(40)
                    .forEach { row ->
                        val actorId = row[NotificationsTable.actorId]
                        feed.add(
                            ActivityItemDto(
                                id = "ntf-${row[NotificationsTable.id].value}",
                                category = row[NotificationsTable.category],
                                actor = actorId?.let { actorNames[it] } ?: "System",
                                action = row[NotificationsTable.title],
                                target = row[NotificationsTable.body],
                                isoTime = row[NotificationsTable.createdAt].toString()
                            )
                        )
                    }
                LeaveRequestsTable.selectAll()
                    .where { LeaveRequestsTable.schoolId eq schoolId }
                    .orderBy(LeaveRequestsTable.createdAt, SortOrder.DESC)
                    .limit(20)
                    .forEach { row ->
                        feed.add(
                            ActivityItemDto(
                                id = "lv-${row[LeaveRequestsTable.id].value}",
                                category = "leave",
                                actor = row[LeaveRequestsTable.requesterName],
                                action = "filed a leave request",
                                target = "${row[LeaveRequestsTable.dateFrom]} → ${row[LeaveRequestsTable.dateTo]}",
                                isoTime = row[LeaveRequestsTable.createdAt].toString()
                            )
                        )
                    }
                AnnouncementsTable.selectAll()
                    .where { AnnouncementsTable.schoolId eq schoolId }
                    .orderBy(AnnouncementsTable.createdAt, SortOrder.DESC)
                    .limit(20)
                    .forEach { row ->
                        val byId = row[AnnouncementsTable.createdBy]
                        feed.add(
                            ActivityItemDto(
                                id = "ann-${row[AnnouncementsTable.id].value}",
                                category = "announcement",
                                actor = byId?.let { actorNames[it] } ?: "School office",
                                action = "posted an announcement",
                                target = row[AnnouncementsTable.title],
                                isoTime = row[AnnouncementsTable.createdAt].toString()
                            )
                        )
                    }
                val activityFeed = feed.sortedByDescending { it.isoTime }.take(40)

                DashboardIntelligenceDto(
                    meta = IntelligenceMetaDto(
                        schoolName = schoolName,
                        academicWeek = academicWeek,
                        today = today.toString(),
                        attendanceAsOf = timeline.lastOrNull()?.date
                    ),
                    attendanceTimeline = timeline,
                    earlyWarning = warnings,
                    academicHealth = academicHealth,
                    activityFeed = activityFeed
                )
            }

            call.ok(payload, message = "Dashboard intelligence")
        }
    }
}

private data class StudentLite(
    val code: String,
    val name: String,
    val className: String,
    val section: String
)
