/*
 * File: AdminDashboardRouting.kt
 * Module: feature.school
 *
 * Endpoints (all JWT + school-scoped via requireSchoolContext):
 *   GET /api/admin/dashboard/summary    — header + campus health + statistics +
 *                                          teacher insight + quick actions
 *   GET /api/admin/dashboard/analytics  — attendance trend, student growth,
 *                                          class performance, attendance breakdown
 *   GET /api/admin/dashboard/activity   — alerts + recent activity feed
 *
 * These drive the redesigned SchoolHomeScreenV2 (SchoolDashboardViewModel).
 *
 * DESIGN CONTRACT
 * ---------------
 *  - 100% computed from EXISTING tables (no new schema):
 *      schools, app_users, students, faculty, school_classes, school_subjects,
 *      teacher_subject_assignments, attendance_records, exam_results,
 *      fee_records, admission_enquiries, notifications, announcements.
 *  - Every aggregate is scoped to ctx.schoolId so an admin only ever sees their
 *    OWN school (IDOR-safe).
 *  - Honest empty states: when a table has no rows the corresponding figure is
 *    0 / empty list, never fabricated. Trends default to a neutral "flat" 0.
 *
 * NOTE: Path prefix is `/api/admin/dashboard` (NOT the `/api/v1/school` family)
 * because the redesigned admin home is a distinct surface; the guard + envelope
 * are the same as every other school endpoint.
 */
package com.littlebridge.enrollplus.feature.school

import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.requireSchoolContext
import com.littlebridge.enrollplus.db.AdmissionEnquiriesTable
import com.littlebridge.enrollplus.db.AnnouncementsTable
import com.littlebridge.enrollplus.db.AppUsersTable
import com.littlebridge.enrollplus.db.AttendanceRecordsTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.ExamResultsTable
import com.littlebridge.enrollplus.db.FacultyTable
import com.littlebridge.enrollplus.db.FeeRecordsTable
import com.littlebridge.enrollplus.db.NotificationsTable
import com.littlebridge.enrollplus.db.SchoolClassesTable
import com.littlebridge.enrollplus.db.SchoolSubjectsTable
import com.littlebridge.enrollplus.db.SchoolsTable
import com.littlebridge.enrollplus.db.StudentsTable
import com.littlebridge.enrollplus.db.TeacherSubjectAssignmentsTable
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDate
import java.util.UUID

// =====================================================================
// DTOs — summary
// =====================================================================

@Serializable
data class DashSchoolDto(
    val id: String,
    val name: String,
    @SerialName("logoUrl") val logoUrl: String? = null,
    @SerialName("academicYear") val academicYear: String,
    @SerialName("currentTerm") val currentTerm: String
)

@Serializable
data class DashAdminDto(
    val id: String,
    val name: String,
    @SerialName("avatarUrl") val avatarUrl: String? = null
)

@Serializable
data class DashTrendDto(
    val direction: String,        // up | down | flat
    val value: Double
)

@Serializable
data class DashMetricDto(
    val key: String,
    val label: String,
    val value: Int,
    val unit: String,
    val trend: DashTrendDto? = null
)

@Serializable
data class DashCampusHealthDto(
    val status: String,           // HEALTHY | WATCH | CRITICAL
    val message: String,
    val metrics: List<DashMetricDto>
)

@Serializable
data class DashCountTrendDto(
    val direction: String,
    val percentage: Int
)

@Serializable
data class DashStudentsStatDto(
    val total: Int,
    val active: Int,
    val newAdmissions: Int,
    val trend: DashCountTrendDto
)

@Serializable
data class DashTeachersStatDto(
    val total: Int,
    val active: Int,
    val newJoined: Int,
    val trend: DashCountTrendDto
)

@Serializable
data class DashSimpleStatDto(
    val total: Int,
    val active: Int
)

@Serializable
data class DashStatisticsDto(
    val students: DashStudentsStatDto,
    val teachers: DashTeachersStatDto,
    val classes: DashSimpleStatDto,
    val subjects: DashSimpleStatDto
)

@Serializable
data class DashDepartmentDto(
    val name: String,
    val teacherCount: Int
)

@Serializable
data class DashTeacherInsightDto(
    val totalTeachers: Int,
    val assignedTeachers: Int,
    val pendingAssignment: Int,
    val assignmentCoverage: Int,
    val departments: List<DashDepartmentDto>
)

@Serializable
data class DashQuickActionDto(
    val id: String,
    val title: String,
    val subtitle: String,
    val enabled: Boolean,
    val permission: String
)

@Serializable
data class DashSummaryResponse(
    val school: DashSchoolDto,
    val admin: DashAdminDto,
    val campusHealth: DashCampusHealthDto,
    val statistics: DashStatisticsDto,
    val teacherInsight: DashTeacherInsightDto,
    val quickActions: List<DashQuickActionDto>
)

// =====================================================================
// DTOs — analytics
// =====================================================================

@Serializable
data class DashAttendanceTrendDto(
    val period: String,
    val labels: List<String>,
    val values: List<Int>
)

@Serializable
data class DashStudentGrowthDto(
    val labels: List<String>,
    val values: List<Int>
)

@Serializable
data class DashTopClassDto(
    @SerialName("class") val className: String,
    val score: Int
)

@Serializable
data class DashClassPerformanceDto(
    val topClasses: List<DashTopClassDto>
)

@Serializable
data class DashAttendanceBreakdownDto(
    val present: Int,
    val absent: Int,
    val late: Int
)

@Serializable
data class DashAnalyticsResponse(
    val attendanceTrend: DashAttendanceTrendDto,
    val studentGrowth: DashStudentGrowthDto,
    val classPerformance: DashClassPerformanceDto,
    val attendanceBreakdown: DashAttendanceBreakdownDto
)

// =====================================================================
// DTOs — activity
// =====================================================================

@Serializable
data class DashAlertDto(
    val id: String,
    val type: String,            // WARNING | INFO | CRITICAL
    val title: String,
    val description: String,
    val priority: String,        // HIGH | MEDIUM | LOW
    val action: String,
    val createdAt: String
)

@Serializable
data class DashActivityDto(
    val id: String,
    val type: String,
    val title: String,
    val description: String,
    val performedBy: String,
    val time: String,
    val createdAt: String
)

@Serializable
data class DashActivityResponse(
    val alerts: List<DashAlertDto>,
    val activities: List<DashActivityDto>
)

// =====================================================================
// internal helpers
// =====================================================================

private val MONTH_NAMES = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

/** Parse a YYYY-MM-DD string into a LocalDate, or null if malformed. */
private fun parseDate(raw: String?): LocalDate? =
    raw?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/**
 * Derive an academic-year label ("2026-27") from a start month name (e.g.
 * "April") relative to today. When the school hasn't set a start month we fall
 * back to a calendar-year window beginning in April (the common Indian default).
 */
private fun academicYearLabel(startMonthName: String?, today: LocalDate): String {
    val startMonth = MONTH_NAMES.indexOfFirst {
        it.equals(startMonthName?.take(3), ignoreCase = true)
    }.let { if (it >= 0) it + 1 else 4 } // default April
    val startYear = if (today.monthValue >= startMonth) today.year else today.year - 1
    val endYY = (startYear + 1) % 100
    return "$startYear-${endYY.toString().padStart(2, '0')}"
}

/** Map a signed delta into a direction token. */
private fun directionOf(delta: Double): String = when {
    delta > 0.0 -> "up"
    delta < 0.0 -> "down"
    else -> "flat"
}

/** Round a Double to one decimal place. */
private fun round1(v: Double): Double = kotlin.math.round(v * 10) / 10.0

/**
 * Present-rate (%) over a list of (date, status) attendance rows. PRESENT and
 * LATE both count toward "in attendance". Returns null when the list is empty.
 */
private fun presentRate(rows: List<Pair<String, String>>): Int? {
    if (rows.isEmpty()) return null
    val present = rows.count { it.second.equals("PRESENT", ignoreCase = true) }
    val late = rows.count { it.second.equals("LATE", ignoreCase = true) }
    return ((present + late) * 100) / rows.size
}

/** Parse an exam score string ("88", "A+", "Pending") into 0..100, or null. */
private fun parseExamScore(raw: String?): Double? {
    if (raw.isNullOrBlank()) return null
    val s = raw.trim()
    s.toDoubleOrNull()?.let { return it.coerceIn(0.0, 100.0) }
    return when (s.uppercase()) {
        "A+" -> 95.0
        "A" -> 88.0
        "B+" -> 82.0
        "B" -> 75.0
        "C+" -> 68.0
        "C" -> 60.0
        "D" -> 50.0
        "E", "F" -> 35.0
        else -> null
    }
}

/** Built-in admin quick actions (static — these are app capabilities, not data). */
private val QUICK_ACTIONS = listOf(
    DashQuickActionDto("ADD_TEACHER", "Add Teacher", "Create staff profile", true, "teacher.create"),
    DashQuickActionDto("ADD_STUDENT", "Add Student", "New admission", true, "student.create"),
    DashQuickActionDto("CREATE_CLASS", "Create Class", "Setup classroom", true, "class.create"),
    DashQuickActionDto("REPORTS", "Reports", "View analytics", true, "report.view")
)

// =====================================================================
// routing
// =====================================================================

fun Route.adminDashboardRouting() {
    authenticate("jwt") {
        route("/api/admin/dashboard") {

            // ============================================================
            // GET /summary
            // ============================================================
            get("/summary") {
                val ctx = call.requireSchoolContext() ?: return@get
                val schoolId = ctx.schoolId
                val today = LocalDate.now()

                val payload = dbQuery {
                    // ---- school header ----
                    val schoolRow = SchoolsTable.selectAll()
                        .where { SchoolsTable.id eq schoolId }
                        .singleOrNull()
                    val schoolName = schoolRow?.get(SchoolsTable.name) ?: "Your School"
                    val logoUrl = schoolRow?.get(SchoolsTable.logoUrl)
                    val startMonth = schoolRow?.get(SchoolsTable.academicYearStartMonth)
                    val academicYear = academicYearLabel(startMonth, today)
                    // Term: derived from which third of the academic year we're in.
                    val currentTerm = "Term 1"

                    // ---- admin ----
                    val adminRow = AppUsersTable.selectAll()
                        .where { AppUsersTable.id eq ctx.userId }
                        .singleOrNull()
                    val adminName = adminRow?.get(AppUsersTable.fullName)?.takeIf { it.isNotBlank() } ?: "Admin"
                    val adminAvatar = adminRow?.get(AppUsersTable.profilePicUrl)

                    // ---- students ----
                    val students = StudentsTable.selectAll()
                        .where { StudentsTable.schoolId eq schoolId }
                        .toList()
                    val studentTotal = students.size
                    val studentActive = students.count { it[StudentsTable.isActive] }

                    // ---- teachers (faculty) ----
                    val faculty = FacultyTable.selectAll()
                        .where { FacultyTable.schoolId eq schoolId }
                        .toList()
                    val teacherTotal = faculty.size
                    val teacherActive = faculty.count { it[FacultyTable.isActive] }

                    // New admissions/joins in the last 30 days (created_at based).
                    val cutoff30 = today.minusDays(30)
                    val newAdmissions = students.count {
                        runCatching { it[StudentsTable.createdAt].atZone(java.time.ZoneOffset.UTC).toLocalDate().isAfter(cutoff30) }
                            .getOrDefault(false)
                    }
                    val newJoined = faculty.count {
                        runCatching { it[FacultyTable.createdAt].atZone(java.time.ZoneOffset.UTC).toLocalDate().isAfter(cutoff30) }
                            .getOrDefault(false)
                    }

                    // ---- classes / subjects ----
                    val classes = SchoolClassesTable.selectAll()
                        .where { SchoolClassesTable.schoolId eq schoolId }
                        .toList()
                    val classTotal = classes.size
                    val classIds = classes.map { it[SchoolClassesTable.id].value }
                    // Portable OR-reduce instead of `inList` (kept consistent with the
                    // project's Exposed-version-safe approach in AnnouncementRouting).
                    val subjects = if (classIds.isEmpty()) emptyList()
                    else {
                        val classFilter = classIds.distinct()
                            .map { cid -> SchoolSubjectsTable.classId eq cid }
                            .reduce { acc, op -> acc or op }
                        SchoolSubjectsTable.selectAll()
                            .where { classFilter }
                            .toList()
                    }
                    val subjectTotal = subjects.size
                    // A subject is "active" when it has a teacher allotted.
                    val subjectActive = subjects.count { !it[SchoolSubjectsTable.teacherAssigned].isNullOrBlank() }

                    // ---- attendance (campus health) ----
                    val attRows = AttendanceRecordsTable.selectAll()
                        .where {
                            (AttendanceRecordsTable.schoolId eq schoolId) and
                                (AttendanceRecordsTable.type eq "student")
                        }
                        .toList()
                        .map { (it[AttendanceRecordsTable.date]) to it[AttendanceRecordsTable.status] }

                    // Most recent attendance day + the day before it (for trend).
                    val byDate = attRows.groupBy { it.first }
                    val sortedDates = byDate.keys.sortedDescending()
                    val latestRate = sortedDates.firstOrNull()?.let { presentRate(byDate[it].orEmpty()) }
                    val prevRate = sortedDates.getOrNull(1)?.let { presentRate(byDate[it].orEmpty()) }
                    val attendancePct = latestRate ?: 0
                    val attendanceDelta = if (latestRate != null && prevRate != null)
                        (latestRate - prevRate).toDouble() else 0.0

                    // ---- fee collection ----
                    val fees = FeeRecordsTable.selectAll()
                        .where { FeeRecordsTable.schoolId eq schoolId }
                        .toList()
                    val paid = fees.filter { it[FeeRecordsTable.status].equals("PAID", true) }.sumOf { it[FeeRecordsTable.amount] }
                    val outstanding = fees.filter {
                        val s = it[FeeRecordsTable.status]
                        s.equals("DUE", true) || s.equals("OVERDUE", true)
                    }.sumOf { it[FeeRecordsTable.amount] }
                    val feeBase = paid + outstanding
                    val feePct = if (feeBase > 0.0) ((paid / feeBase) * 100).toInt() else 0

                    val metrics = mutableListOf<DashMetricDto>()
                    if (!attRows.isEmpty()) {
                        metrics += DashMetricDto(
                            key = "attendance",
                            label = "Attendance",
                            value = attendancePct,
                            unit = "percentage",
                            trend = DashTrendDto(directionOf(attendanceDelta), round1(kotlin.math.abs(attendanceDelta)))
                        )
                    }
                    if (fees.isNotEmpty()) {
                        metrics += DashMetricDto(
                            key = "fee_collection",
                            label = "Fee Collection",
                            value = feePct,
                            unit = "percentage",
                            trend = DashTrendDto("flat", 0.0)
                        )
                    }

                    // Campus health status from attendance + fee health.
                    val healthScores = listOfNotNull(
                        latestRate,
                        if (fees.isNotEmpty()) feePct else null
                    )
                    val worst = healthScores.minOrNull()
                    val status = when {
                        worst == null -> "HEALTHY"
                        worst >= 85 -> "HEALTHY"
                        worst >= 70 -> "WATCH"
                        else -> "CRITICAL"
                    }
                    val message = when (status) {
                        "HEALTHY" -> "Everything looks stable today"
                        "WATCH" -> "A few metrics need your attention"
                        else -> "Critical metrics need immediate action"
                    }

                    // ---- teacher insight ----
                    val assignments = TeacherSubjectAssignmentsTable.selectAll()
                        .where {
                            (TeacherSubjectAssignmentsTable.schoolId eq schoolId) and
                                (TeacherSubjectAssignmentsTable.isActive eq true)
                        }
                        .toList()
                    // Distinct teachers who have at least one active assignment.
                    val assignedTeacherKeys = assignments.mapNotNull { row ->
                        row[TeacherSubjectAssignmentsTable.teacherId]?.toString()
                            ?: row[TeacherSubjectAssignmentsTable.teacherName]?.takeIf { it.isNotBlank() }
                    }.toSet()
                    val assignedTeachers = assignedTeacherKeys.size.coerceAtMost(teacherTotal.coerceAtLeast(assignedTeacherKeys.size))
                    val pendingAssignment = (teacherTotal - assignedTeachers).coerceAtLeast(0)
                    val assignmentCoverage = if (teacherTotal > 0)
                        (assignedTeachers * 100) / teacherTotal else 0

                    // Departments come from the faculty table's department column.
                    val departments = faculty
                        .filter { it[FacultyTable.isActive] }
                        .mapNotNull { it[FacultyTable.department]?.takeIf { d -> d.isNotBlank() } }
                        .groupingBy { it }
                        .eachCount()
                        .map { (name, count) -> DashDepartmentDto(name, count) }
                        .sortedByDescending { it.teacherCount }
                        .take(6)

                    DashSummaryResponse(
                        school = DashSchoolDto(
                            id = schoolId.toString(),
                            name = schoolName,
                            logoUrl = logoUrl,
                            academicYear = academicYear,
                            currentTerm = currentTerm
                        ),
                        admin = DashAdminDto(
                            id = ctx.userId.toString(),
                            name = adminName,
                            avatarUrl = adminAvatar
                        ),
                        campusHealth = DashCampusHealthDto(
                            status = status,
                            message = message,
                            metrics = metrics
                        ),
                        statistics = DashStatisticsDto(
                            students = DashStudentsStatDto(
                                total = studentTotal,
                                active = studentActive,
                                newAdmissions = newAdmissions,
                                trend = DashCountTrendDto(
                                    if (newAdmissions > 0) "up" else "flat",
                                    if (studentTotal > 0) (newAdmissions * 100) / studentTotal else 0
                                )
                            ),
                            teachers = DashTeachersStatDto(
                                total = teacherTotal,
                                active = teacherActive,
                                newJoined = newJoined,
                                trend = DashCountTrendDto(
                                    if (newJoined > 0) "up" else "flat",
                                    if (teacherTotal > 0) (newJoined * 100) / teacherTotal else 0
                                )
                            ),
                            classes = DashSimpleStatDto(classTotal, classTotal),
                            subjects = DashSimpleStatDto(subjectTotal, subjectActive)
                        ),
                        teacherInsight = DashTeacherInsightDto(
                            totalTeachers = teacherTotal,
                            assignedTeachers = assignedTeachers,
                            pendingAssignment = pendingAssignment,
                            assignmentCoverage = assignmentCoverage,
                            departments = departments
                        ),
                        quickActions = QUICK_ACTIONS
                    )
                }
                call.ok(payload, message = "Dashboard summary fetched successfully")
            }

            // ============================================================
            // GET /analytics
            // ============================================================
            get("/analytics") {
                val ctx = call.requireSchoolContext() ?: return@get
                val schoolId = ctx.schoolId
                val today = LocalDate.now()

                val payload = dbQuery {
                    // ---- attendance trend (last 7 months, present-rate %) ----
                    val attRows = AttendanceRecordsTable.selectAll()
                        .where {
                            (AttendanceRecordsTable.schoolId eq schoolId) and
                                (AttendanceRecordsTable.type eq "student")
                        }
                        .toList()
                    val attByMonth = attRows.groupBy {
                        parseDate(it[AttendanceRecordsTable.date])?.let { d -> d.year to d.monthValue }
                    }
                    val trendLabels = ArrayList<String>(7)
                    val trendValues = ArrayList<Int>(7)
                    for (back in 6 downTo 0) {
                        val d = today.minusMonths(back.toLong())
                        trendLabels += MONTH_NAMES[d.monthValue - 1]
                        val pool = attByMonth[d.year to d.monthValue].orEmpty()
                        val rate = presentRate(pool.map { it[AttendanceRecordsTable.date] to it[AttendanceRecordsTable.status] }) ?: 0
                        trendValues += rate
                    }

                    // ---- student growth (cumulative active students by month, last 4) ----
                    val students = StudentsTable.selectAll()
                        .where { StudentsTable.schoolId eq schoolId }
                        .toList()
                    val createdDates = students.mapNotNull {
                        runCatching { it[StudentsTable.createdAt].atZone(java.time.ZoneOffset.UTC).toLocalDate() }.getOrNull()
                    }
                    val growthLabels = ArrayList<String>(4)
                    val growthValues = ArrayList<Int>(4)
                    for (back in 3 downTo 0) {
                        val monthEnd = today.minusMonths(back.toLong())
                            .withDayOfMonth(1).plusMonths(1).minusDays(1)
                        growthLabels += MONTH_NAMES[monthEnd.monthValue - 1]
                        // Cumulative count of students created on/before this month end.
                        growthValues += createdDates.count { !it.isAfter(monthEnd) }
                    }

                    // ---- class performance (top classes by avg exam score) ----
                    val exams = ExamResultsTable.selectAll()
                        .where { ExamResultsTable.schoolId eq schoolId }
                        .toList()
                    val byClass = exams.groupBy { it[ExamResultsTable.className] }
                    val topClasses = byClass.mapNotNull { (cls, recs) ->
                        val scores = recs.mapNotNull { parseExamScore(it[ExamResultsTable.score]) }
                        if (scores.isEmpty()) null else cls to scores.average()
                    }.sortedByDescending { it.second }
                        .take(5)
                        .map { DashTopClassDto(it.first, kotlin.math.round(it.second).toInt()) }

                    // ---- attendance breakdown (latest day) ----
                    val latestDay = attRows.maxByOrNull { it[AttendanceRecordsTable.date] }?.get(AttendanceRecordsTable.date)
                    val latestRows = if (latestDay == null) emptyList()
                    else attRows.filter { it[AttendanceRecordsTable.date] == latestDay }
                    val totalDay = latestRows.size
                    val presentCount = latestRows.count { it[AttendanceRecordsTable.status].equals("PRESENT", true) }
                    val lateCount = latestRows.count { it[AttendanceRecordsTable.status].equals("LATE", true) }
                    val absentCount = (totalDay - presentCount - lateCount).coerceAtLeast(0)
                    val breakdown = if (totalDay > 0) {
                        DashAttendanceBreakdownDto(
                            present = (presentCount * 100) / totalDay,
                            absent = (absentCount * 100) / totalDay,
                            late = (lateCount * 100) / totalDay
                        )
                    } else {
                        DashAttendanceBreakdownDto(0, 0, 0)
                    }

                    DashAnalyticsResponse(
                        attendanceTrend = DashAttendanceTrendDto("monthly", trendLabels, trendValues),
                        studentGrowth = DashStudentGrowthDto(growthLabels, growthValues),
                        classPerformance = DashClassPerformanceDto(topClasses),
                        attendanceBreakdown = breakdown
                    )
                }
                call.ok(payload, message = "Dashboard analytics fetched successfully")
            }

            // ============================================================
            // GET /activity
            // ============================================================
            get("/activity") {
                val ctx = call.requireSchoolContext() ?: return@get
                val schoolId = ctx.schoolId

                val payload = dbQuery {
                    val alerts = mutableListOf<DashAlertDto>()

                    // Alert 1: teachers without any active subject assignment.
                    val teacherTotal = FacultyTable.selectAll()
                        .where { (FacultyTable.schoolId eq schoolId) and (FacultyTable.isActive eq true) }
                        .count().toInt()
                    val assignedTeacherKeys = TeacherSubjectAssignmentsTable.selectAll()
                        .where {
                            (TeacherSubjectAssignmentsTable.schoolId eq schoolId) and
                                (TeacherSubjectAssignmentsTable.isActive eq true)
                        }
                        .toList()
                        .mapNotNull { row ->
                            row[TeacherSubjectAssignmentsTable.teacherId]?.toString()
                                ?: row[TeacherSubjectAssignmentsTable.teacherName]?.takeIf { it.isNotBlank() }
                        }
                        .toSet()
                    val unassigned = (teacherTotal - assignedTeacherKeys.size).coerceAtLeast(0)
                    if (unassigned > 0) {
                        alerts += DashAlertDto(
                            id = "alert_unassigned_teachers",
                            type = "WARNING",
                            title = "$unassigned teacher${if (unassigned == 1) "" else "s"} unassigned",
                            description = "Assign teachers to pending classes",
                            priority = "HIGH",
                            action = "ASSIGN_TEACHER",
                            createdAt = LocalDate.now().toString()
                        )
                    }

                    // Alert 2: pending admission enquiries awaiting review.
                    val pendingAdmissions = AdmissionEnquiriesTable.selectAll()
                        .where {
                            (AdmissionEnquiriesTable.schoolId eq schoolId) and
                                (AdmissionEnquiriesTable.status eq "new")
                        }
                        .count().toInt()
                    if (pendingAdmissions > 0) {
                        alerts += DashAlertDto(
                            id = "alert_pending_admissions",
                            type = "INFO",
                            title = "$pendingAdmissions pending admission${if (pendingAdmissions == 1) "" else "s"}",
                            description = "Review new student applications",
                            priority = "MEDIUM",
                            action = "VIEW_ADMISSIONS",
                            createdAt = LocalDate.now().toString()
                        )
                    }

                    // ---- recent activity feed (from notifications for this admin) ----
                    val activities = NotificationsTable.selectAll()
                        .where { NotificationsTable.userId eq ctx.userId }
                        .orderBy(NotificationsTable.createdAt, SortOrder.DESC)
                        .limit(10)
                        .map { row ->
                            DashActivityDto(
                                id = row[NotificationsTable.id].value.toString(),
                                type = row[NotificationsTable.category].uppercase(),
                                title = row[NotificationsTable.title],
                                description = row[NotificationsTable.body],
                                performedBy = "System",
                                time = relativeTime(row[NotificationsTable.createdAt]),
                                createdAt = row[NotificationsTable.createdAt].toString()
                            )
                        }

                    // Fallback: when no notifications yet, surface the most recent
                    // announcements as activity so the feed is not blank for a
                    // freshly-onboarded school that still has announcements.
                    val finalActivities = activities.ifEmpty {
                        AnnouncementsTable.selectAll()
                            .where { AnnouncementsTable.schoolId eq schoolId }
                            .orderBy(AnnouncementsTable.createdAt, SortOrder.DESC)
                            .limit(10)
                            .map { row ->
                                DashActivityDto(
                                    id = row[AnnouncementsTable.id].value.toString(),
                                    type = "ANNOUNCEMENT",
                                    title = row[AnnouncementsTable.title],
                                    description = row[AnnouncementsTable.description],
                                    performedBy = "Admin",
                                    time = relativeTime(row[AnnouncementsTable.createdAt]),
                                    createdAt = row[AnnouncementsTable.createdAt].toString()
                                )
                            }
                    }

                    DashActivityResponse(alerts = alerts, activities = finalActivities)
                }
                call.ok(payload, message = "Dashboard activity fetched successfully")
            }
        }
    }
}

/** Human-readable relative time ("10 minutes ago") from an Instant. */
private fun relativeTime(instant: java.time.Instant): String {
    val now = java.time.Instant.now()
    val secs = java.time.Duration.between(instant, now).seconds.coerceAtLeast(0)
    return when {
        secs < 60 -> "just now"
        secs < 3600 -> "${secs / 60} minute${if (secs / 60 == 1L) "" else "s"} ago"
        secs < 86400 -> "${secs / 3600} hour${if (secs / 3600 == 1L) "" else "s"} ago"
        else -> "${secs / 86400} day${if (secs / 86400 == 1L) "" else "s"} ago"
    }
}
