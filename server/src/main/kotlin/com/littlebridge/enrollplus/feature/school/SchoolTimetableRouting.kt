/*
 * File: SchoolTimetableRouting.kt
 * Module: feature.school
 *
 * Endpoint: GET /api/v1/school/timetable        (JWT, school-scoped)
 *           optional ?class=<class_name>        (server-side pre-filter)
 *
 * WHY THIS EXISTS
 *   The web admin "Command Center" calendar must render EVERY class's weekly
 *   schedule (all teachers, all subjects), but the only existing read of
 *   `teacher_periods` is TEACHER-scoped (TeacherRouting.kt filters on
 *   teacher_id = the caller). An admin has no way to see the whole school's
 *   timetable. This route closes that gap with a purely additive, read-only,
 *   school-scoped read assembled from REAL rows only.
 *
 *   The timetable is a RECURRING WEEKLY PATTERN: teacher_periods is keyed by
 *   weekday (1=Mon … 7=Sun, matching java.time.DayOfWeek.value), not by a
 *   calendar date. The client paints that pattern onto the dates of whatever
 *   week/month it is viewing. Date-specific overrides (holidays, exams) come
 *   from the separate /calendar endpoint and are layered on the client.
 *
 *   When a school hasn't entered a timetable the response is an honest empty
 *   payload (weekdays: [], classes: []) — the calendar then renders its
 *   designed empty state. Nothing is fabricated.
 *
 * REAL-TIME STRATEGY (documented here AND in the web hook useTimetable):
 *   SLOW (300s SWR poll, focus-revalidate). Timetables change on the order of
 *   terms, not minutes; second-by-second metrics (present-now, unread) ride the
 *   separate LIVE hooks. No websocket — the Ktor backend publishes no Supabase
 *   realtime channel to the web client (see ARCHITECTURE §Real-time).
 *
 * SOURCE: teacher_periods (school-scoped) left-joined to app_users for the
 *   teacher display name. weekday/start_time/position drive the ordering.
 */
package com.littlebridge.enrollplus.feature.school

import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.requireSchoolContext
import com.littlebridge.enrollplus.db.AppUsersTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.TeacherPeriodsTable
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll

// ───────────────────────────────── DTOs ──────────────────────────────────────

@Serializable
data class TimetablePeriodDto(
    val id: String,
    @SerialName("start_time") val startTime: String,   // "HH:mm"
    @SerialName("end_time") val endTime: String,        // "HH:mm"
    @SerialName("class_name") val className: String,
    val section: String,
    val subject: String,
    val room: String,
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("teacher_name") val teacherName: String // "" when unassigned/unknown
)

@Serializable
data class TimetableWeekdayDto(
    val weekday: Int,                                   // 1=Mon … 7=Sun
    val periods: List<TimetablePeriodDto>
)

@Serializable
data class TimetableDto(
    val weekdays: List<TimetableWeekdayDto>,
    val classes: List<String>                           // distinct class_name in view, sorted
)

fun Route.schoolTimetableRouting() {
    authenticate("jwt") {
        get("/api/v1/school/timetable") {
            val ctx = call.requireSchoolContext() ?: return@get
            val schoolId = ctx.schoolId
            val classFilter = call.request.queryParameters["class"]?.trim()?.takeIf { it.isNotBlank() }

            val payload = dbQuery {
                // Teacher display names for this school (id → full name).
                val teacherNames = AppUsersTable.selectAll()
                    .where { AppUsersTable.schoolId eq schoolId }
                    .associate { it[AppUsersTable.id].value to it[AppUsersTable.fullName] }

                val rows = TeacherPeriodsTable.selectAll()
                    .where {
                        if (classFilter != null) {
                            (TeacherPeriodsTable.schoolId eq schoolId) and
                                (TeacherPeriodsTable.className eq classFilter)
                        } else {
                            TeacherPeriodsTable.schoolId eq schoolId
                        }
                    }
                    .map { r ->
                        val tId = r[TeacherPeriodsTable.teacherId]
                        TimetablePeriodDto(
                            id = r[TeacherPeriodsTable.id].value.toString(),
                            startTime = r[TeacherPeriodsTable.startTime],
                            endTime = r[TeacherPeriodsTable.endTime],
                            className = r[TeacherPeriodsTable.className],
                            section = r[TeacherPeriodsTable.section],
                            subject = r[TeacherPeriodsTable.subject],
                            room = r[TeacherPeriodsTable.room],
                            teacherId = tId.toString(),
                            teacherName = teacherNames[tId] ?: "",
                        ) to r[TeacherPeriodsTable.weekday]
                    }

                // Group by weekday; sort periods by start time then a stable id
                // (position isn't selected into the DTO but start_time is the
                // operationally meaningful order for a calendar column).
                val byWeekday = rows.groupBy { it.second }
                val weekdays = byWeekday
                    .map { (weekday, list) ->
                        TimetableWeekdayDto(
                            weekday = weekday,
                            periods = list.map { it.first }
                                .sortedWith(compareBy({ it.startTime }, { it.endTime }, { it.id }))
                        )
                    }
                    .sortedBy { it.weekday }

                val classes = rows.map { it.first.className }.distinct().sorted()

                TimetableDto(weekdays = weekdays, classes = classes)
            }

            call.ok(payload, message = "School timetable fetched")
        }
    }
}
