/*
 * File: ParentAcademicsRouting.kt
 * Module: feature.parent
 *
 * RA-43 + RA-56: the parent academic read plane. Teachers WRITE attendance_records,
 * assessments + assessment_marks and syllabus_units; before this file NO parent
 * endpoint READ them, so ParentAcademicsScreenV2 rendered VComingSoon for
 * Attendance / Marks / Syllabus — the single most important parent value did not
 * exist. These endpoints are all CHILD-SCOPED (RA-56): a `child_id` path segment
 * resolves a child that must belong to the calling parent, then joins via
 * children.student_code → attendance_records.person_id / assessment_marks.student_id
 * and children.current_grade → syllabus_units.class_name.
 *
 * Endpoints (JWT, parent):
 *   GET /api/v1/parent/child/{id}/attendance  → monthly summary + day records
 *   GET /api/v1/parent/child/{id}/marks       → published assessments + child's score
 *   GET /api/v1/parent/child/{id}/syllabus    → per-subject coverage for the child's class
 */
package com.littlebridge.enrollplus.feature.parent

import com.littlebridge.enrollplus.core.fail
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.principalUserId
import com.littlebridge.enrollplus.db.AppUsersTable
import com.littlebridge.enrollplus.db.AssessmentMarksTable
import com.littlebridge.enrollplus.db.AssessmentsTable
import com.littlebridge.enrollplus.db.AttendanceRecordsTable
import com.littlebridge.enrollplus.db.ChildrenTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.HolidayListTable
import com.littlebridge.enrollplus.db.StudentsTable
import com.littlebridge.enrollplus.db.SyllabusUnitsTable
import com.littlebridge.enrollplus.db.TeacherPeriodsTable
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

// ── DTOs ─────────────────────────────────────────────────────────────────────

@Serializable
data class ParentAttendanceDayDto(
    val date: String,
    val status: String, // present | absent | late
)

/**
 * A non-instructional day for the child's school — the canonical representation of
 * "not a school day" so the parent dashboard can tell a holiday/vacation apart from a
 * genuine absence. Sourced from `holiday_list` (type Public|School, frequency
 * weekly|monthly|yearly). Weekly Sundays are derived client-side from the weekday, so
 * a school need not enumerate every Sunday for the calendar to read correctly.
 */
@Serializable
data class ParentHolidayDto(
    val date: String,           // "YYYY-MM-DD"; empty when it's a recurring (weekly) rule
    val title: String,
    val type: String,           // Public | School
    val frequency: String,      // weekly | monthly | yearly
)

@Serializable
data class ParentAttendanceData(
    @SerialName("child_name") val childName: String,
    @SerialName("present_days") val presentDays: Int,
    @SerialName("absent_days") val absentDays: Int,
    @SerialName("late_days") val lateDays: Int,
    @SerialName("total_days") val totalDays: Int,
    @SerialName("attendance_rate") val attendanceRate: Int, // 0..100
    val records: List<ParentAttendanceDayDto> = emptyList(),
    // RA-PP1: declared non-school days for the child's school, so the parent dashboard
    // can render holidays / vacations distinctly from real absences. Honest empty when a
    // school hasn't published a holiday list.
    val holidays: List<ParentHolidayDto> = emptyList(),
)

// ── Timetable (RA-PP1: parent read of the child's class weekly schedule) ──────

@Serializable
data class ParentPeriodDto(
    @SerialName("start_time") val startTime: String, // "HH:mm"
    @SerialName("end_time") val endTime: String,      // "HH:mm"
    val subject: String,
    val room: String,
    @SerialName("teacher_name") val teacherName: String, // "" when unassigned/unknown
)

@Serializable
data class ParentTimetableDayDto(
    val weekday: Int,                              // 1=Mon … 7=Sun
    val periods: List<ParentPeriodDto>,
)

@Serializable
data class ParentTimetableData(
    @SerialName("child_name") val childName: String,
    @SerialName("class_name") val className: String,
    val weekdays: List<ParentTimetableDayDto> = emptyList(),
)

@Serializable
data class ParentMarkDto(
    @SerialName("exam_name") val examName: String,
    val subject: String,
    val marks: Double?, // null = not yet entered for this child
    @SerialName("max_marks") val maxMarks: Int,
    @SerialName("exam_date") val examDate: String? = null,
)

@Serializable
data class ParentMarksData(
    @SerialName("child_name") val childName: String,
    val results: List<ParentMarkDto> = emptyList(),
)

@Serializable
data class ParentSyllabusUnitDto(
    val title: String,
    @SerialName("is_covered") val isCovered: Boolean,
    @SerialName("covered_on") val coveredOn: String? = null,
)

@Serializable
data class ParentSyllabusSubjectDto(
    val subject: String,
    @SerialName("progress") val progress: Int, // 0..100
    val units: List<ParentSyllabusUnitDto> = emptyList(),
)

@Serializable
data class ParentSyllabusData(
    @SerialName("child_name") val childName: String,
    @SerialName("class_name") val className: String,
    val subjects: List<ParentSyllabusSubjectDto> = emptyList(),
)

// ── Resolved + authorized child (RA-56 ownership guard) ───────────────────────

private data class ResolvedChild(
    val childName: String,
    val schoolId: UUID?,
    val studentCode: String?,
    val grade: String?,
    val section: String,
    // RA-S19: true only when `section` came from a linked `students` row. When the
    // child is linked but the students row is missing/mismatched we fall back to
    // "A" — a section filter on that fallback silently hides marks. Callers MUST
    // relax the section filter to class-level when this is false.
    val sectionResolved: Boolean,
)

/**
 * Resolve the {id} child, asserting it belongs to the calling parent. Responds
 * with the right error envelope and returns null on any failure. This is the
 * RA-56 ownership gate — a parent can never read another family's child.
 */
private suspend fun ApplicationCall.requireOwnedChild(): ResolvedChild? {
    val uid = principalUserId()?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: run {
        fail("Invalid token", HttpStatusCode.Unauthorized); return null
    }
    val childId = parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: run {
        fail("A valid child id is required", HttpStatusCode.BadRequest, "BAD_CHILD_ID"); return null
    }
    val row = dbQuery {
        ChildrenTable.selectAll().where {
            (ChildrenTable.id eq childId) and
                (ChildrenTable.parentId eq uid) and
                (ChildrenTable.isActive eq true)
        }.singleOrNull()
    } ?: run {
        fail("Child not found", HttpStatusCode.NotFound, "CHILD_NOT_FOUND"); return null
    }
    // The student row (if linked) carries the canonical class+section, which is
    // more authoritative than the parent-typed currentGrade for syllabus joins.
    val studentCode = row[ChildrenTable.studentCode]
    val student = if (studentCode != null) dbQuery {
        StudentsTable.selectAll().where { StudentsTable.studentCode eq studentCode }.singleOrNull()
    } else null
    val linkedSection = student?.get(StudentsTable.section)
    return ResolvedChild(
        childName = row[ChildrenTable.childName],
        schoolId = row[ChildrenTable.schoolId],
        studentCode = studentCode,
        grade = student?.get(StudentsTable.className) ?: row[ChildrenTable.currentGrade],
        section = linkedSection ?: "A",
        sectionResolved = linkedSection != null,
    )
}

fun Route.parentAcademicsRouting() {
    authenticate("jwt") {
        route("/api/v1/parent/child/{id}") {

            // ── Attendance — month summary + per-day records ─────────────────
            get("/attendance") {
                val child = call.requireOwnedChild() ?: return@get
                if (child.schoolId == null || child.studentCode == null) {
                    // Child not yet linked to a school/student — honest empty, not an error.
                    call.ok(
                        ParentAttendanceData(
                            childName = child.childName,
                            presentDays = 0, absentDays = 0, lateDays = 0,
                            totalDays = 0, attendanceRate = 0, records = emptyList(),
                        ),
                        message = "No attendance feed yet",
                    )
                    return@get
                }
                val rows = dbQuery {
                    AttendanceRecordsTable.selectAll().where {
                        (AttendanceRecordsTable.schoolId eq child.schoolId) and
                            (AttendanceRecordsTable.type eq "student") and
                            (AttendanceRecordsTable.personId eq child.studentCode)
                    }.orderBy(AttendanceRecordsTable.date, SortOrder.DESC).map {
                        ParentAttendanceDayDto(
                            date = it[AttendanceRecordsTable.date],
                            status = it[AttendanceRecordsTable.status].lowercase(),
                        )
                    }
                }
                val present = rows.count { it.status == "present" }
                val absent = rows.count { it.status == "absent" }
                val late = rows.count { it.status == "late" }
                val total = rows.size
                val rate = if (total > 0) (((present + late) * 100) / total) else 0
                // RA-PP1: the child's school holiday list — lets the parent dashboard tell a
                // declared non-school day apart from a real absence. Honest empty when none.
                val holidays = dbQuery {
                    HolidayListTable.selectAll().where {
                        HolidayListTable.schoolId eq child.schoolId
                    }.map {
                        ParentHolidayDto(
                            date = it[HolidayListTable.date],
                            title = it[HolidayListTable.title],
                            type = it[HolidayListTable.type],
                            frequency = it[HolidayListTable.frequency],
                        )
                    }
                }
                call.ok(
                    ParentAttendanceData(
                        childName = child.childName,
                        presentDays = present,
                        absentDays = absent,
                        lateDays = late,
                        totalDays = total,
                        attendanceRate = rate,
                        records = rows,
                        holidays = holidays,
                    ),
                    message = "Attendance loaded",
                )
            }

            // ── Marks — published assessments + this child's score ───────────
            get("/marks") {
                val child = call.requireOwnedChild() ?: return@get
                if (child.schoolId == null || child.studentCode == null) {
                    call.ok(ParentMarksData(childName = child.childName, results = emptyList()), message = "No published results yet")
                    return@get
                }
                val results = dbQuery {
                    // Only PUBLISHED assessments for the child's class are parent-visible (RA-43).
                    // RA-S19: the section filter is applied ONLY when the section came from a
                    // linked `students` row. When section is the "A" fallback (child linked but
                    // students row missing/mismatched), filtering on it silently hides every
                    // mark; we relax to class-level so the parent still sees their child's
                    // published results instead of an empty screen.
                    val assessments = AssessmentsTable.selectAll().where {
                        var cond = (AssessmentsTable.schoolId eq child.schoolId) and
                            (AssessmentsTable.className eq (child.grade ?: "")) and
                            (AssessmentsTable.isPublished eq true) and
                            (AssessmentsTable.isActive eq true)
                        if (child.sectionResolved) {
                            cond = cond and (AssessmentsTable.section eq child.section)
                        }
                        cond
                    }.orderBy(AssessmentsTable.createdAt, SortOrder.DESC).toList()

                    assessments.map { a ->
                        val aId = a[AssessmentsTable.id].value
                        val mark = AssessmentMarksTable.selectAll().where {
                            (AssessmentMarksTable.assessmentId eq aId) and
                                (AssessmentMarksTable.studentId eq child.studentCode)
                        }.singleOrNull()?.get(AssessmentMarksTable.marks)
                        ParentMarkDto(
                            examName = a[AssessmentsTable.name],
                            subject = a[AssessmentsTable.subject],
                            marks = mark,
                            maxMarks = a[AssessmentsTable.maxMarks],
                            examDate = a[AssessmentsTable.examDate],
                        )
                    }
                }
                call.ok(ParentMarksData(childName = child.childName, results = results), message = "Marks loaded")
            }

            // ── Syllabus — per-subject coverage for the child's class ────────
            get("/syllabus") {
                val child = call.requireOwnedChild() ?: return@get
                if (child.schoolId == null || child.grade == null) {
                    call.ok(ParentSyllabusData(childName = child.childName, className = child.grade ?: "", subjects = emptyList()), message = "No syllabus feed yet")
                    return@get
                }
                val subjects = dbQuery {
                    // RA-S19: same section-fallback hardening as the marks join — only filter
                    // on section when it came from a linked student row, else relax to class.
                    val units = SyllabusUnitsTable.selectAll().where {
                        var cond = (SyllabusUnitsTable.schoolId eq child.schoolId) and
                            (SyllabusUnitsTable.className eq child.grade)
                        if (child.sectionResolved) {
                            cond = cond and (SyllabusUnitsTable.section eq child.section)
                        }
                        cond
                    }.orderBy(SyllabusUnitsTable.position, SortOrder.ASC).toList()

                    units.groupBy { it[SyllabusUnitsTable.subject] }.map { (subject, list) ->
                        val covered = list.count { it[SyllabusUnitsTable.isCovered] }
                        val progress = if (list.isNotEmpty()) (covered * 100) / list.size else 0
                        ParentSyllabusSubjectDto(
                            subject = subject,
                            progress = progress,
                            units = list.map {
                                ParentSyllabusUnitDto(
                                    title = it[SyllabusUnitsTable.title],
                                    isCovered = it[SyllabusUnitsTable.isCovered],
                                    coveredOn = it[SyllabusUnitsTable.coveredOn],
                                )
                            },
                        )
                    }
                }
                call.ok(
                    ParentSyllabusData(childName = child.childName, className = child.grade, subjects = subjects),
                    message = "Syllabus loaded",
                )
            }

            // ── Timetable — the child's class weekly schedule (recurring) ────
            // RA-PP1: the parent dashboard's "today's schedule" + "weekly timetable"
            // cards read REAL rows from teacher_periods, scoped to the child's class.
            // teacher_periods is a recurring weekly pattern keyed by weekday (1=Mon…7=Sun);
            // the client paints today's column live and reveals the six-day grid on swipe.
            // Honest empty payload when the school hasn't entered a timetable — never faked.
            get("/timetable") {
                val child = call.requireOwnedChild() ?: return@get
                if (child.schoolId == null || child.grade == null) {
                    call.ok(
                        ParentTimetableData(childName = child.childName, className = child.grade ?: "", weekdays = emptyList()),
                        message = "No timetable feed yet",
                    )
                    return@get
                }
                val data = dbQuery {
                    // Teacher display names for this school (id → full name).
                    val teacherNames = AppUsersTable.selectAll()
                        .where { AppUsersTable.schoolId eq child.schoolId }
                        .associate { it[AppUsersTable.id].value to it[AppUsersTable.fullName] }

                    val rows = TeacherPeriodsTable.selectAll().where {
                        var cond = (TeacherPeriodsTable.schoolId eq child.schoolId) and
                            (TeacherPeriodsTable.className eq child.grade)
                        // RA-S19: only constrain to the section when it's authoritative
                        // (came from a linked students row); else relax to class-level.
                        if (child.sectionResolved) {
                            cond = cond and (TeacherPeriodsTable.section eq child.section)
                        }
                        cond
                    }.map { r ->
                        val tId = r[TeacherPeriodsTable.teacherId]
                        Triple(
                            r[TeacherPeriodsTable.weekday],
                            r[TeacherPeriodsTable.startTime],
                            ParentPeriodDto(
                                startTime = r[TeacherPeriodsTable.startTime],
                                endTime = r[TeacherPeriodsTable.endTime],
                                subject = r[TeacherPeriodsTable.subject],
                                room = r[TeacherPeriodsTable.room],
                                teacherName = teacherNames[tId] ?: "",
                            ),
                        )
                    }

                    val weekdays = rows.groupBy { it.first }
                        .map { (weekday, list) ->
                            ParentTimetableDayDto(
                                weekday = weekday,
                                periods = list.sortedWith(compareBy({ it.second }, { it.third.endTime }))
                                    .map { it.third },
                            )
                        }
                        .sortedBy { it.weekday }

                    ParentTimetableData(
                        childName = child.childName,
                        className = child.grade,
                        weekdays = weekdays,
                    )
                }
                call.ok(data, message = "Timetable loaded")
            }
        }
    }
}
