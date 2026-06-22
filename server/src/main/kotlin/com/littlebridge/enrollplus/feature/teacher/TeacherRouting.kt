/*
 * File: TeacherRouting.kt
 * Module: feature.teacher
 *
 * The teacher vertical (master rebuild doc Step 7 / gap G1). Implements the
 * exact contract the KMP client already speaks via
 *   shared/.../feature/teacher/data/remote/TeacherApi.kt
 *   shared/.../feature/teacher/domain/model/TeacherModels.kt
 *
 * Routes (all JWT-guarded + scoped via core/TeacherAccess):
 *   GET   /api/v1/teacher/home
 *   GET   /api/v1/teacher/classes
 *   GET   /api/v1/teacher/profile
 *   GET   /api/v1/teacher/attendance?class_id=&date=
 *   POST  /api/v1/teacher/attendance
 *   GET   /api/v1/teacher/marks?class_id=&exam_id=
 *   POST  /api/v1/teacher/marks
 *   GET   /api/v1/teacher/syllabus?class_id=&subject=
 *   PATCH /api/v1/teacher/syllabus
 *   GET   /api/v1/teacher/homework
 *   POST  /api/v1/teacher/homework
 *
 * DTOs are defined server-side (the :server module does NOT depend on :shared)
 * with @SerialName matching the client field-for-field. Read responses use the
 * { success, data } envelope the client decodes; writes use the canonical
 * { success, message } envelope via call.ok / call.created / call.okMessage.
 *
 * Honesty rule (master doc): when a school hasn't entered data (e.g. no
 * timetable, no syllabus units) we return honest empty lists — never fabricated
 * rows.
 *
 * This file holds the READ surface (home / classes / profile). Attendance,
 * marks, syllabus and homework live in TeacherRoutingTasks.kt.
 */
package com.littlebridge.enrollplus.feature.teacher

import com.littlebridge.enrollplus.core.TeacherContext
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.requireTeacherContext
import com.littlebridge.enrollplus.core.teacherAssignmentsFor
import com.littlebridge.enrollplus.db.AppUsersTable
import com.littlebridge.enrollplus.db.AssessmentMarksTable
import com.littlebridge.enrollplus.db.AssessmentsTable
import com.littlebridge.enrollplus.db.AttendanceRecordsTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.HomeworkTable
import com.littlebridge.enrollplus.db.SchoolsTable
import com.littlebridge.enrollplus.db.StudentsTable
import com.littlebridge.enrollplus.db.SyllabusUnitsTable
import com.littlebridge.enrollplus.db.TeacherPeriodsTable
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ─────────────────────────────────────────────────────────────────────────────
// Server-side DTOs — field names mirror the client (TeacherModels.kt) exactly.
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class TeacherPeriodDto(
    val id: String,
    val time: String,
    @SerialName("class_name") val className: String,
    val subject: String,
    val room: String = "",
    @SerialName("is_current") val isCurrent: Boolean = false,
    val status: String = "upcoming",
)

@Serializable
data class TeacherTaskDto(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val type: String,
    @SerialName("class_name") val className: String = "",
    @SerialName("is_done") val isDone: Boolean = false,
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
data class TeacherClassesData(
    val classes: List<TeacherClassDto> = emptyList(),
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

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Today's date in YYYY-MM-DD, matching the varchar(12) date columns. */
internal fun todayIso(): String = LocalDate.now().toString()

/**
 * Count of distinct active students in a class+section, used for student_count /
 * homework totalCount / marks rosters. Reads the read-only students mirror.
 */
internal suspend fun studentCountFor(schoolId: java.util.UUID, className: String, section: String): Int = dbQuery {
    StudentsTable.selectAll().where {
        (StudentsTable.schoolId eq schoolId) and
            (StudentsTable.className eq className) and
            (StudentsTable.section eq section) and
            (StudentsTable.isActive eq true)
    }.count().toInt()
}

/** Overall syllabus progress (covered / total) for a class+section+subject; 0f if none. */
internal suspend fun syllabusProgressFor(
    schoolId: java.util.UUID, className: String, section: String, subject: String,
): Float = dbQuery {
    val units = SyllabusUnitsTable.selectAll().where {
        (SyllabusUnitsTable.schoolId eq schoolId) and
            (SyllabusUnitsTable.className eq className) and
            (SyllabusUnitsTable.section eq section) and
            (SyllabusUnitsTable.subject eq subject)
    }.toList()
    if (units.isEmpty()) 0f
    else units.count { it[SyllabusUnitsTable.isCovered] }.toFloat() / units.size.toFloat()
}

fun Route.teacherRouting() {
    authenticate("jwt") {
        route("/api/v1/teacher") {

            // ── GET /home ───────────────────────────────────────────────────
            get("/home") {
                val ctx = call.requireTeacherContext() ?: return@get
                val assignments = teacherAssignmentsFor(ctx)

                val schoolName = dbQuery {
                    SchoolsTable.selectAll().where { SchoolsTable.id eq ctx.schoolId }
                        .singleOrNull()?.get(SchoolsTable.name)
                } ?: ""

                val today = todayIso()
                val weekday = LocalDate.now().dayOfWeek.value // 1..7

                // Today's periods from the (optional) timetable. Honest empty if none.
                val periods = dbQuery {
                    TeacherPeriodsTable.selectAll().where {
                        (TeacherPeriodsTable.schoolId eq ctx.schoolId) and
                            (TeacherPeriodsTable.teacherId eq ctx.userId) and
                            (TeacherPeriodsTable.weekday eq weekday)
                    }.toList()
                }.sortedBy { it[TeacherPeriodsTable.startTime] }

                val now = java.time.LocalTime.now()
                val periodDtos = periods.map { r ->
                    val start = runCatching { java.time.LocalTime.parse(r[TeacherPeriodsTable.startTime], HHMM) }.getOrNull()
                    val end = runCatching { java.time.LocalTime.parse(r[TeacherPeriodsTable.endTime], HHMM) }.getOrNull()
                    val status = when {
                        start != null && end != null && !now.isBefore(start) && now.isBefore(end) -> "current"
                        end != null && !now.isBefore(end) -> "done"
                        else -> "upcoming"
                    }
                    TeacherPeriodDto(
                        id = r[TeacherPeriodsTable.id].value.toString(),
                        time = "${r[TeacherPeriodsTable.startTime]} - ${r[TeacherPeriodsTable.endTime]}",
                        className = "${r[TeacherPeriodsTable.className]}-${r[TeacherPeriodsTable.section]}",
                        subject = r[TeacherPeriodsTable.subject],
                        room = r[TeacherPeriodsTable.room],
                        isCurrent = status == "current",
                        status = status,
                    )
                }

                // Pending attendance: classes the teacher handles that have NO
                // student attendance row for today.
                var pendingAttendance = 0
                for (a in assignments) {
                    val grade = "${a.className}-${a.section}"
                    val marked = dbQuery {
                        AttendanceRecordsTable.selectAll().where {
                            (AttendanceRecordsTable.schoolId eq ctx.schoolId) and
                                (AttendanceRecordsTable.date eq today) and
                                (AttendanceRecordsTable.type eq "student") and
                                (AttendanceRecordsTable.grade eq grade)
                        }.limit(1).firstOrNull() != null
                    }
                    if (!marked) pendingAttendance++
                }

                // Pending marks: active assessments owned by this teacher that
                // still have at least one student without a score.
                val pendingMarks = dbQuery {
                    val mine = AssessmentsTable.selectAll().where {
                        (AssessmentsTable.schoolId eq ctx.schoolId) and
                            (AssessmentsTable.teacherId eq ctx.userId) and
                            (AssessmentsTable.isActive eq true)
                    }.toList()
                    mine.count { asg ->
                        AssessmentMarksTable.selectAll().where {
                            (AssessmentMarksTable.assessmentId eq asg[AssessmentsTable.id].value) and
                                (AssessmentMarksTable.marks eq null)
                        }.limit(1).firstOrNull() != null
                    }
                }

                // Homework due today or later, authored by this teacher.
                val homeworkDue = dbQuery {
                    HomeworkTable.selectAll().where {
                        (HomeworkTable.schoolId eq ctx.schoolId) and
                            (HomeworkTable.teacherId eq ctx.userId) and
                            (HomeworkTable.isActive eq true) and
                            (HomeworkTable.dueDate greaterEq today)
                    }.count().toInt()
                }

                // Actionable task cards — one per class still needing attendance.
                val tasks = assignments
                    .map { a ->
                        TeacherTaskDto(
                            id = "att-${a.assignmentId}",
                            title = "Mark attendance",
                            subtitle = "${a.className}-${a.section} · ${a.subject}",
                            type = "attendance",
                            className = "${a.className}-${a.section}",
                            isDone = false,
                        )
                    }

                call.ok(
                    TeacherHomeData(
                        teacherName = ctx.fullName,
                        schoolName = schoolName,
                        classesToday = periodDtos.size.takeIf { it > 0 } ?: assignments.map { "${it.className}-${it.section}" }.distinct().size,
                        pendingAttendance = pendingAttendance,
                        pendingMarks = pendingMarks,
                        homeworkDue = homeworkDue,
                        todayPeriods = periodDtos,
                        tasks = tasks,
                    ),
                    message = "Teacher home loaded",
                )
            }

            // ── GET /classes ──────────────────────────────────────────────────
            get("/classes") {
                val ctx = call.requireTeacherContext() ?: return@get
                val assignments = teacherAssignmentsFor(ctx)

                val classes = assignments.map { a ->
                    val count = studentCountFor(ctx.schoolId, a.className, a.section)
                    val progress = syllabusProgressFor(ctx.schoolId, a.className, a.section, a.subject)
                    val avgAtt = avgAttendanceFor(ctx.schoolId, a.className, a.section)
                    TeacherClassDto(
                        id = a.assignmentId.toString(),
                        className = "${a.className}-${a.section}",
                        subject = a.subject,
                        studentCount = count,
                        isClassTeacher = false,
                        syllabusProgress = progress,
                        avgAttendance = avgAtt,
                    )
                }
                call.ok(TeacherClassesData(classes), message = "Classes loaded")
            }

            // ── GET /profile ──────────────────────────────────────────────────
            get("/profile") {
                val ctx = call.requireTeacherContext() ?: return@get
                val assignments = teacherAssignmentsFor(ctx)

                val userRow = dbQuery {
                    AppUsersTable.selectAll().where { AppUsersTable.id eq ctx.userId }.singleOrNull()
                }
                val schoolName = dbQuery {
                    SchoolsTable.selectAll().where { SchoolsTable.id eq ctx.schoolId }
                        .singleOrNull()?.get(SchoolsTable.name)
                } ?: ""

                val subjects = assignments.map { it.subject }.distinct().sorted()
                val classes = assignments.map { "${it.className}-${it.section}" }.distinct().sorted()

                call.ok(
                    TeacherProfileData(
                        id = ctx.userId.toString(),
                        name = ctx.fullName,
                        username = userRow?.get(AppUsersTable.email)
                            ?: userRow?.get(AppUsersTable.phone)
                            ?: "",
                        schoolName = schoolName,
                        subjects = subjects,
                        classes = classes,
                        photoUrl = userRow?.get(AppUsersTable.profilePicUrl),
                        email = userRow?.get(AppUsersTable.email) ?: "",
                        phone = userRow?.get(AppUsersTable.phone) ?: "",
                    ),
                    message = "Profile loaded",
                )
            }

            // Task endpoints (attendance / marks / syllabus / homework).
            teacherTaskRoutes()
        }
    }
}

/** Average attendance ratio (present / total marked) for a class over its history; 0f if none. */
internal suspend fun avgAttendanceFor(schoolId: java.util.UUID, className: String, section: String): Float = dbQuery {
    val grade = "$className-$section"
    val rows = AttendanceRecordsTable.selectAll().where {
        (AttendanceRecordsTable.schoolId eq schoolId) and
            (AttendanceRecordsTable.type eq "student") and
            (AttendanceRecordsTable.grade eq grade)
    }.toList()
    if (rows.isEmpty()) 0f
    else rows.count { it[AttendanceRecordsTable.status].equals("present", ignoreCase = true) }
        .toFloat() / rows.size.toFloat()
}
