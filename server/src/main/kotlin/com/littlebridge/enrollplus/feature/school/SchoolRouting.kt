/*
 * File: SchoolRouting.kt
 * Module: feature.school
 *
 * Endpoints (Drawer Options in spec):
 *   GET /api/v1/school/analytics                                  (endpoint index)
 *   GET /api/v1/school/calendar?date=&view_type=&standard=
 *   GET /api/v1/school/holidays?filter_type=weekly|monthly|yearly
 *   GET /api/v1/school/attendance/daily?type=student|faculty&grade=
 *
 * Spec ref: vidya_prayag_api_spec2.artifact.md §Drawer Options
 *
 * Calendar filtering:
 *   view_type=week  → events within ±3 days of `date`
 *   view_type=month → events starting with YYYY-MM of `date`
 *   standard (opt)  → filter to that grade; null-standard rows are global
 *
 * Holidays: `filter_type` selects rows by frequency column (default yearly).
 *
 * Attendance: today's date by default; pass `?date=YYYY-MM-DD` for historical.
 *
 * Authorization:
 *   Every data endpoint is guarded by
 *   call.requireSchoolContext(): the caller must hold a school role and have a
 *   school, and every query is scoped to that resolved school_id.
 *
 * Calendar summary correctness:
 *   public_holidays / school_holidays count ONLY holidays falling inside the
 *   requested calendar range [rangeStart, rangeEnd] — not every holiday the
 *   school has ever recorded — so the summary matches what the user is viewing.
 */
package com.littlebridge.enrollplus.feature.school

import com.littlebridge.enrollplus.core.fail
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.requireSchoolContext
import com.littlebridge.enrollplus.db.AcademicCalendarTable
import com.littlebridge.enrollplus.db.AttendanceRecordsTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.FacultyTable
import com.littlebridge.enrollplus.db.HolidayListTable
import com.littlebridge.enrollplus.db.StudentsTable
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDate

@Serializable
data class AnalyticsResponse(
    @SerialName("is_available") val isAvailable: Boolean,
    @SerialName("expected_release") val expectedRelease: String
)

@Serializable
data class CalendarEventDto(
    val date: String,
    val day: String,
    @SerialName("event_id") val eventId: String,
    @SerialName("event_title") val eventTitle: String,
    @SerialName("event_description") val eventDescription: String
)

@Serializable
data class CalendarSummary(
    // `working_days` is the canonical field the client decodes. `total_working_days`
    // is emitted as a backward-compatible alias so older clients keep working.
    @SerialName("working_days") val workingDays: Int,
    @SerialName("total_working_days") val totalWorkingDays: Int,
    @SerialName("public_holidays") val publicHolidays: Int,
    @SerialName("school_holidays") val schoolHolidays: Int
)

@Serializable
data class CalendarResponse(
    @SerialName("calendar_events") val calendarEvents: List<CalendarEventDto>,
    val summary: CalendarSummary
)

@Serializable
data class HolidayDto(val date: String, val title: String, val type: String)

@Serializable
data class HolidaysResponse(val holidays: List<HolidayDto>)

@Serializable
data class AttendanceEntry(
    @SerialName("profile_pic") val profilePic: String? = null,
    val name: String,
    val id: String,
    val status: String
)

@Serializable
data class AttendanceResponse(
    val type: String,
    val grade: String? = null,
    @SerialName("present_count") val presentCount: Int,
    @SerialName("absent_count") val absentCount: Int,
    @SerialName("total_count") val totalCount: Int,
    @SerialName("attendance_percentage") val attendancePercentage: String,
    @SerialName("attendance_list") val attendanceList: List<AttendanceEntry>
)

fun Route.schoolRouting() {
    route("/api/v1/school") {

        // ---- analytics endpoint index ----
        get("/analytics") {
            call.ok(
                AnalyticsResponse(isAvailable = true, expectedRelease = "Available now at /api/v1/school/analytics/overview"),
                message = "Use /api/v1/school/analytics/overview, /class-performance, /teacher-performance, /student/{studentId}, /student-cohort or /syllabus-coverage."
            )
        }

        authenticate("jwt") {

            // ---- calendar ----
            get("/calendar") {
                val ctx = call.requireSchoolContext() ?: return@get
                val schoolId = ctx.schoolId
                val dateStr = call.request.queryParameters["date"]
                    ?: LocalDate.now().toString()
                val viewType = call.request.queryParameters["view_type"]?.lowercase() ?: "month"
                val standard = call.request.queryParameters["standard"]

                val refDate = runCatching { LocalDate.parse(dateStr) }.getOrNull()
                    ?: run { call.fail("Invalid date format (YYYY-MM-DD)"); return@get }

                val (rangeStart, rangeEnd) = when (viewType) {
                    "week" -> refDate.minusDays(3) to refDate.plusDays(3)
                    else  -> refDate.withDayOfMonth(1) to refDate.withDayOfMonth(refDate.lengthOfMonth())
                }

                val events = dbQuery {
                    AcademicCalendarTable.selectAll()
                        .where { AcademicCalendarTable.schoolId eq schoolId }
                        .filter { row ->
                            val d = runCatching { LocalDate.parse(row[AcademicCalendarTable.date]) }.getOrNull()
                                ?: return@filter false
                            val inRange = !d.isBefore(rangeStart) && !d.isAfter(rangeEnd)
                            val stdOk = standard.isNullOrBlank() ||
                                row[AcademicCalendarTable.standard] == null ||
                                row[AcademicCalendarTable.standard] == standard
                            inRange && stdOk
                        }
                        .map {
                            CalendarEventDto(
                                date = it[AcademicCalendarTable.date],
                                day = it[AcademicCalendarTable.day],
                                eventId = it[AcademicCalendarTable.eventId],
                                eventTitle = it[AcademicCalendarTable.eventTitle],
                                eventDescription = it[AcademicCalendarTable.eventDescription] ?: ""
                            )
                        }
                }

                // Working-day math is approximate (Mon-Fri count in range).
                val workingDays = generateSequence(rangeStart) { it.plusDays(1) }
                    .takeWhile { !it.isAfter(rangeEnd) }
                    .count { it.dayOfWeek.value < 6 }

                // Count holidays that actually fall inside the viewed range, by
                // type. `date` is a YYYY-MM-DD varchar, so we parse + range-check
                // in memory (portable across SQLite/Postgres).
                val holidayRows = dbQuery {
                    HolidayListTable.selectAll()
                        .where { HolidayListTable.schoolId eq schoolId }
                        .map { it[HolidayListTable.date] to it[HolidayListTable.type] }
                }
                val holidaysInRange = holidayRows.filter { (dateStr, _) ->
                    val d = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return@filter false
                    !d.isBefore(rangeStart) && !d.isAfter(rangeEnd)
                }
                val pubHolidays = holidaysInRange.count { it.second.equals("Public", ignoreCase = true) }
                val schoolHolidays = holidaysInRange.count { it.second.equals("School", ignoreCase = true) }
                call.ok(
                    CalendarResponse(
                        calendarEvents = events,
                        summary = CalendarSummary(
                            workingDays = workingDays,
                            totalWorkingDays = workingDays,
                            publicHolidays = pubHolidays,
                            schoolHolidays = schoolHolidays
                        )
                    ),
                    message = "Academic calendar fetched successfully"
                )
            }

            // ---- holidays ----
            get("/holidays") {
                val ctx = call.requireSchoolContext() ?: return@get
                val schoolId = ctx.schoolId
                val filter = call.request.queryParameters["filter_type"]?.lowercase() ?: "yearly"
                if (filter !in setOf("weekly", "monthly", "yearly")) {
                    call.fail("filter_type must be weekly|monthly|yearly"); return@get
                }
                val list = dbQuery {
                    HolidayListTable.selectAll()
                        .where { (HolidayListTable.schoolId eq schoolId) and (HolidayListTable.frequency eq filter) }
                        .map { HolidayDto(it[HolidayListTable.date], it[HolidayListTable.title], it[HolidayListTable.type]) }
                }
                call.ok(HolidaysResponse(list), message = "Holidays list fetched")
            }

            // ---- attendance/daily ----
            get("/attendance/daily") {
                val ctx = call.requireSchoolContext() ?: return@get
                val schoolId = ctx.schoolId
                val type  = call.request.queryParameters["type"]?.lowercase() ?: "student"
                val grade = call.request.queryParameters["grade"]
                val date  = call.request.queryParameters["date"] ?: LocalDate.now().toString()

                if (type !in setOf("student", "faculty")) {
                    call.fail("type must be 'student' or 'faculty'"); return@get
                }
                if (type == "student" && grade.isNullOrBlank()) {
                    call.fail("'grade' is required for type=student"); return@get
                }

                val resp = dbQuery {
                    // Pull people list (students of that grade, or all faculty).
                    val people: List<Triple<String, String, String?>> = if (type == "student") {
                        StudentsTable.selectAll()
                            .where { (StudentsTable.schoolId eq schoolId) and (StudentsTable.className eq grade!!) and (StudentsTable.isActive eq true) }
                            .map { Triple(it[StudentsTable.studentCode], it[StudentsTable.fullName], it[StudentsTable.profilePhotoUrl]) }
                    } else {
                        FacultyTable.selectAll()
                            .where { (FacultyTable.schoolId eq schoolId) and (FacultyTable.isActive eq true) }
                            .map { Triple(it[FacultyTable.externalId], it[FacultyTable.name], it[FacultyTable.profilePic]) }
                    }

                    // Pull today's records once.
                    val records = AttendanceRecordsTable.selectAll()
                        .where {
                            (AttendanceRecordsTable.schoolId eq schoolId) and
                                (AttendanceRecordsTable.date eq date) and
                                (AttendanceRecordsTable.type eq type)
                        }
                        .associate { it[AttendanceRecordsTable.personId] to it[AttendanceRecordsTable.status] }

                    val rows = people.map { (id, name, pic) ->
                        val status = records[id] ?: "absent"
                        AttendanceEntry(profilePic = pic, name = name, id = id, status = status)
                    }
                    val present = rows.count { it.status == "present" || it.status == "late" || it.status == "half_day" }
                    val absent  = rows.size - present
                    val pct = if (rows.isEmpty()) "0%" else "${present * 100 / rows.size}%"

                    AttendanceResponse(
                        type = type,
                        grade = if (type == "student") grade else null,
                        presentCount = present,
                        absentCount = absent,
                        totalCount = rows.size,
                        attendancePercentage = pct,
                        attendanceList = rows
                    )
                }
                call.ok(resp, message = "Daily attendance fetched successfully")
            }
        }
    }
}
