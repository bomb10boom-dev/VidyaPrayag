/*
 * File: TeacherRoutingTasks.kt
 * Module: feature.teacher
 *
 * The teacher TASK surface — attendance, marks, syllabus and homework — split
 * out of TeacherRouting.kt for readability. Mounted as child routes of
 * `/api/v1/teacher` by TeacherRouting.teacherRouting() via teacherTaskRoutes().
 *
 * Every handler:
 *   - resolves a trusted TeacherContext (401/403/404 on failure)
 *   - for class-scoped calls, resolves the client class_id to an OwnedAssignment
 *     (400/403/404) so a teacher can only touch their own classes (gap G1)
 *   - reads/writes via dbQuery and replies with the canonical envelope.
 *
 * Storage:
 *   attendance → attendance_records (type=student, grade="Class-Section")
 *   marks      → assessments + assessment_marks (numeric max_marks + exam id, G4)
 *   syllabus   → syllabus_units
 *   homework   → homework + homework_submissions
 *
 * No fabricated data: empty rosters / unit lists are returned honestly.
 */
package com.littlebridge.enrollplus.feature.teacher

import com.littlebridge.enrollplus.core.created
import com.littlebridge.enrollplus.feature.notifications.Notify
import com.littlebridge.enrollplus.feature.notifications.NotifyRecipients
import com.littlebridge.enrollplus.core.fail
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.okMessage
import com.littlebridge.enrollplus.core.requireOwnedAssignment
import com.littlebridge.enrollplus.core.requireTeacherContext
import com.littlebridge.enrollplus.core.teacherAssignmentsFor
import com.littlebridge.enrollplus.db.AssessmentMarksTable
import com.littlebridge.enrollplus.db.AssessmentsTable
import com.littlebridge.enrollplus.db.AttendanceRecordsTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.HomeworkSubmissionsTable
import com.littlebridge.enrollplus.db.HomeworkTable
import com.littlebridge.enrollplus.db.StudentsTable
import com.littlebridge.enrollplus.db.SyllabusUnitsTable
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// DTOs — mirror shared/.../teacher/domain/model/TeacherModels.kt field-for-field.
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class AttendanceEntryDto(
    @SerialName("student_id") val studentId: String,
    val name: String,
    @SerialName("roll_no") val rollNo: String = "",
    val status: String = "present",
)

@Serializable
data class TeacherAttendanceData(
    @SerialName("class_name") val className: String,
    val date: String,
    val students: List<AttendanceEntryDto> = emptyList(),
)

@Serializable
data class AttendanceMarkDto(
    @SerialName("student_id") val studentId: String,
    val status: String,
)

@Serializable
data class SubmitAttendanceRequest(
    @SerialName("class_id") val classId: String,
    val date: String,
    val entries: List<AttendanceMarkDto> = emptyList(),
)

@Serializable
data class MarksEntryDto(
    @SerialName("student_id") val studentId: String,
    val name: String,
    @SerialName("roll_no") val rollNo: String = "",
    val marks: Float? = null,
)

@Serializable
data class TeacherMarksData(
    @SerialName("class_name") val className: String,
    val subject: String,
    @SerialName("exam_name") val examName: String,
    @SerialName("max_marks") val maxMarks: Int,
    val students: List<MarksEntryDto> = emptyList(),
)

@Serializable
data class MarkScoreDto(
    @SerialName("student_id") val studentId: String,
    val marks: Float,
)

@Serializable
data class SubmitMarksRequest(
    @SerialName("class_id") val classId: String,
    @SerialName("exam_id") val examId: String,
    val entries: List<MarkScoreDto> = emptyList(),
)

@Serializable
data class SyllabusUnitDto(
    val id: String,
    val title: String,
    @SerialName("is_covered") val isCovered: Boolean = false,
    @SerialName("covered_on") val coveredOn: String? = null,
)

@Serializable
data class TeacherSyllabusData(
    @SerialName("class_name") val className: String,
    val subject: String,
    @SerialName("overall_progress") val overallProgress: Float = 0f,
    val units: List<SyllabusUnitDto> = emptyList(),
)

@Serializable
data class UpdateSyllabusRequest(
    @SerialName("unit_id") val unitId: String,
    @SerialName("is_covered") val isCovered: Boolean,
)

@Serializable
data class HomeworkDto(
    val id: String,
    val title: String,
    val description: String = "",
    @SerialName("class_name") val className: String,
    val subject: String,
    @SerialName("due_date") val dueDate: String,
    @SerialName("submitted_count") val submittedCount: Int = 0,
    @SerialName("total_count") val totalCount: Int = 0,
)

@Serializable
data class TeacherHomeworkData(
    val items: List<HomeworkDto> = emptyList(),
)

@Serializable
data class CreateHomeworkRequest(
    @SerialName("class_id") val classId: String,
    val title: String,
    val description: String = "",
    @SerialName("due_date") val dueDate: String,
)

@kotlinx.serialization.Serializable
data class TeacherAssessmentDto(
    val id: String,
    val name: String,
    val subject: String,
    @SerialName("max_marks") val maxMarks: Int,
    @SerialName("exam_date") val examDate: String? = null,
    @SerialName("is_published") val isPublished: Boolean = false,
)

@kotlinx.serialization.Serializable
data class TeacherAssessmentsData(
    val assessments: List<TeacherAssessmentDto> = emptyList(),
)

@kotlinx.serialization.Serializable
data class CreateAssessmentRequest(
    @SerialName("class_id") val classId: String,
    val name: String,
    @SerialName("max_marks") val maxMarks: Int? = null,
    @SerialName("exam_date") val examDate: String? = null,
)

private val VALID_ATTENDANCE = setOf("present", "absent", "late")

/** Mounts the attendance/marks/syllabus/homework child routes under /api/v1/teacher. */
fun Route.teacherTaskRoutes() {

    // ── Attendance ──────────────────────────────────────────────────────────
    route("/attendance") {
        // GET ?class_id=&date= → roster pre-filled with the day's status.
        get {
            val ctx = call.requireTeacherContext() ?: return@get
            val classId = call.request.queryParameters["class_id"]
            val date = call.request.queryParameters["date"]?.takeIf { it.isNotBlank() } ?: todayIso()
            val asg = call.requireOwnedAssignment(ctx, classId) ?: return@get
            val grade = "${asg.className}-${asg.section}"

            val data = dbQuery {
                val students = StudentsTable.selectAll().where {
                    (StudentsTable.schoolId eq ctx.schoolId) and
                        (StudentsTable.className eq asg.className) and
                        (StudentsTable.section eq asg.section) and
                        (StudentsTable.isActive eq true)
                }.orderBy(StudentsTable.rollNumber, SortOrder.ASC).toList()

                val marked = AttendanceRecordsTable.selectAll().where {
                    (AttendanceRecordsTable.schoolId eq ctx.schoolId) and
                        (AttendanceRecordsTable.date eq date) and
                        (AttendanceRecordsTable.type eq "student") and
                        (AttendanceRecordsTable.grade eq grade)
                }.associate { it[AttendanceRecordsTable.personId] to it[AttendanceRecordsTable.status] }

                students.map { s ->
                    val code = s[StudentsTable.studentCode]
                    AttendanceEntryDto(
                        studentId = code,
                        name = s[StudentsTable.fullName],
                        rollNo = s[StudentsTable.rollNumber],
                        status = marked[code] ?: "present",
                    )
                }
            }
            call.ok(
                TeacherAttendanceData(className = grade, date = date, students = data),
                message = "Attendance roster loaded",
            )
        }

        // POST → upsert one attendance_records row per entry for the day.
        post {
            val ctx = call.requireTeacherContext() ?: return@post
            val req = call.receive<SubmitAttendanceRequest>()
            val asg = call.requireOwnedAssignment(ctx, req.classId) ?: return@post
            val grade = "${asg.className}-${asg.section}"
            val date = req.date.takeIf { it.isNotBlank() } ?: todayIso()

            val bad = req.entries.firstOrNull { it.status.lowercase() !in VALID_ATTENDANCE }
            if (bad != null) {
                call.fail("Invalid status '${bad.status}' (expected present|absent|late)", HttpStatusCode.BadRequest)
                return@post
            }

            val now = Instant.now()
            dbQuery {
                for (e in req.entries) {
                    val status = e.status.lowercase()
                    val existing = AttendanceRecordsTable.selectAll().where {
                        (AttendanceRecordsTable.schoolId eq ctx.schoolId) and
                            (AttendanceRecordsTable.date eq date) and
                            (AttendanceRecordsTable.type eq "student") and
                            (AttendanceRecordsTable.personId eq e.studentId)
                    }.firstOrNull()
                    if (existing != null) {
                        AttendanceRecordsTable.update({
                            (AttendanceRecordsTable.schoolId eq ctx.schoolId) and
                                (AttendanceRecordsTable.date eq date) and
                                (AttendanceRecordsTable.type eq "student") and
                                (AttendanceRecordsTable.personId eq e.studentId)
                        }) {
                            it[AttendanceRecordsTable.status] = status
                            it[AttendanceRecordsTable.grade] = grade
                            it[markedBy] = ctx.userId
                        }
                    } else {
                        AttendanceRecordsTable.insert {
                            it[id] = UUID.randomUUID()
                            it[schoolId] = ctx.schoolId
                            it[AttendanceRecordsTable.date] = date
                            it[type] = "student"
                            it[personId] = e.studentId
                            it[AttendanceRecordsTable.grade] = grade
                            it[AttendanceRecordsTable.status] = status
                            it[markedBy] = ctx.userId
                            it[createdAt] = now
                        }
                    }
                }
            }

            // RA-41: alert each affected parent when their child is absent/late.
            // Recipients resolved per student_code within this school (multi-tenant).
            val flagged = req.entries.filter { it.status.lowercase() in setOf("absent", "late") }
            for (e in flagged) {
                val parents = NotifyRecipients.parentsOfStudent(ctx.schoolId, e.studentId)
                if (parents.isNotEmpty()) {
                    val verb = if (e.status.lowercase() == "absent") "marked absent" else "marked late"
                    Notify.toUsers(
                        userIds = parents,
                        category = "attendance",
                        title = "Attendance update",
                        body = "Your child was $verb on $date.",
                        schoolId = ctx.schoolId,
                        actorId = ctx.userId,
                        deepLink = "parent/academics/attendance",
                        refType = "attendance",
                        refId = e.studentId,
                    )
                }
            }
            call.okMessage("Attendance saved for ${req.entries.size} student(s)")
        }
    }

    // ── Marks ─────────────────────────────────────────────────────────────────
    route("/marks") {
        // GET ?class_id=&exam_id= → assessment roster with current scores.
        get {
            val ctx = call.requireTeacherContext() ?: return@get
            val classId = call.request.queryParameters["class_id"]
            val examId = call.request.queryParameters["exam_id"]
            val asg = call.requireOwnedAssignment(ctx, classId) ?: return@get
            val grade = "${asg.className}-${asg.section}"

            val assessmentId = examId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            if (assessmentId == null) {
                call.fail("A valid exam_id is required", HttpStatusCode.BadRequest, "BAD_EXAM_ID")
                return@get
            }

            val assessment = dbQuery {
                AssessmentsTable.selectAll().where {
                    (AssessmentsTable.id eq assessmentId) and
                        (AssessmentsTable.schoolId eq ctx.schoolId)
                }.singleOrNull()
            }
            if (assessment == null) {
                call.fail("Exam not found in your school", HttpStatusCode.NotFound, "EXAM_NOT_FOUND")
                return@get
            }

            val data = dbQuery {
                val existing = AssessmentMarksTable.selectAll().where {
                    AssessmentMarksTable.assessmentId eq assessmentId
                }.associateBy { it[AssessmentMarksTable.studentId] }

                val students = StudentsTable.selectAll().where {
                    (StudentsTable.schoolId eq ctx.schoolId) and
                        (StudentsTable.className eq asg.className) and
                        (StudentsTable.section eq asg.section) and
                        (StudentsTable.isActive eq true)
                }.orderBy(StudentsTable.rollNumber, SortOrder.ASC).toList()

                students.map { s ->
                    val code = s[StudentsTable.studentCode]
                    MarksEntryDto(
                        studentId = code,
                        name = s[StudentsTable.fullName],
                        rollNo = s[StudentsTable.rollNumber],
                        marks = existing[code]?.get(AssessmentMarksTable.marks)?.toFloat(),
                    )
                }
            }
            call.ok(
                TeacherMarksData(
                    className = grade,
                    subject = assessment[AssessmentsTable.subject],
                    examName = assessment[AssessmentsTable.name],
                    maxMarks = assessment[AssessmentsTable.maxMarks],
                    students = data,
                ),
                message = "Marks roster loaded",
            )
        }

        // POST → upsert one assessment_marks row per entry, clamped to maxMarks.
        post {
            val ctx = call.requireTeacherContext() ?: return@post
            val req = call.receive<SubmitMarksRequest>()
            val asg = call.requireOwnedAssignment(ctx, req.classId) ?: return@post

            val assessmentId = runCatching { UUID.fromString(req.examId) }.getOrNull()
            if (assessmentId == null) {
                call.fail("A valid exam_id is required", HttpStatusCode.BadRequest, "BAD_EXAM_ID")
                return@post
            }

            val assessment = dbQuery {
                AssessmentsTable.selectAll().where {
                    (AssessmentsTable.id eq assessmentId) and
                        (AssessmentsTable.schoolId eq ctx.schoolId)
                }.singleOrNull()
            }
            if (assessment == null) {
                call.fail("Exam not found in your school", HttpStatusCode.NotFound, "EXAM_NOT_FOUND")
                return@post
            }
            val maxMarks = assessment[AssessmentsTable.maxMarks].toDouble()

            // Map student_code → display name for denormalised storage.
            val names = dbQuery {
                StudentsTable.selectAll().where {
                    (StudentsTable.schoolId eq ctx.schoolId) and
                        (StudentsTable.className eq asg.className) and
                        (StudentsTable.section eq asg.section)
                }.associate { it[StudentsTable.studentCode] to it[StudentsTable.fullName] }
            }

            val now = Instant.now()
            dbQuery {
                for (e in req.entries) {
                    val clamped = e.marks.toDouble().coerceIn(0.0, maxMarks)
                    val existing = AssessmentMarksTable.selectAll().where {
                        (AssessmentMarksTable.assessmentId eq assessmentId) and
                            (AssessmentMarksTable.studentId eq e.studentId)
                    }.firstOrNull()
                    if (existing != null) {
                        AssessmentMarksTable.update({
                            (AssessmentMarksTable.assessmentId eq assessmentId) and
                                (AssessmentMarksTable.studentId eq e.studentId)
                        }) {
                            it[marks] = clamped
                            it[enteredBy] = ctx.userId
                            it[updatedAt] = now
                        }
                    } else {
                        AssessmentMarksTable.insert {
                            it[id] = UUID.randomUUID()
                            it[AssessmentMarksTable.assessmentId] = assessmentId
                            it[studentId] = e.studentId
                            it[studentName] = names[e.studentId] ?: e.studentId
                            it[marks] = clamped
                            it[enteredBy] = ctx.userId
                            it[createdAt] = now
                            it[updatedAt] = now
                        }
                    }
                }
                // RA-43: entering marks publishes the assessment so parents can read
                // it. The parent marks endpoint filters on isPublished = true, so an
                // unpublished (draft) assessment stays invisible until a teacher submits.
                AssessmentsTable.update({ AssessmentsTable.id eq assessmentId }) {
                    it[isPublished] = true
                    it[publishedAt] = now
                    it[updatedAt] = now
                }
            }

            // RA-41: notify the class parents that a result was published. Scoped
            // to the owned class within this school (multi-tenant isolation).
            val examName = assessment[AssessmentsTable.name]
            val parents = NotifyRecipients.parentsOfClass(ctx.schoolId, asg.className)
            if (parents.isNotEmpty()) {
                Notify.toUsers(
                    userIds = parents,
                    category = "marks",
                    title = "Results published",
                    body = "Marks for \"$examName\" (${asg.subject}) have been published.",
                    schoolId = ctx.schoolId,
                    actorId = ctx.userId,
                    deepLink = "parent/academics/marks",
                    refType = "assessment",
                    refId = assessmentId.toString(),
                )
            }
            call.okMessage("Marks saved for ${req.entries.size} student(s)")
        }
    }

    // ── Assessments (exams) — list + create, so the marks flow is reachable ───
    // RA-40: the marks screen needs a valid exam_id. Without a way to list or
    // create assessments the teacher could never get one, so the marks plane
    // was unreachable even once a class was selected. These two routes close it.
    route("/assessments") {
        // GET ?class_id= → exams the teacher can mark for this owned class.
        get {
            val ctx = call.requireTeacherContext() ?: return@get
            val classId = call.request.queryParameters["class_id"]
            val asg = call.requireOwnedAssignment(ctx, classId) ?: return@get
            val rows = dbQuery {
                AssessmentsTable.selectAll().where {
                    (AssessmentsTable.schoolId eq ctx.schoolId) and
                        (AssessmentsTable.className eq asg.className) and
                        (AssessmentsTable.section eq asg.section) and
                        (AssessmentsTable.subject eq asg.subject) and
                        (AssessmentsTable.isActive eq true)
                }.orderBy(AssessmentsTable.createdAt, SortOrder.DESC).map { a ->
                    TeacherAssessmentDto(
                        id = a[AssessmentsTable.id].value.toString(),
                        name = a[AssessmentsTable.name],
                        subject = a[AssessmentsTable.subject],
                        maxMarks = a[AssessmentsTable.maxMarks],
                        examDate = a[AssessmentsTable.examDate],
                        isPublished = a[AssessmentsTable.isPublished],
                    )
                }
            }
            call.ok(TeacherAssessmentsData(assessments = rows), message = "Assessments loaded")
        }

        // POST → create a new assessment for an owned class.
        post {
            val ctx = call.requireTeacherContext() ?: return@post
            val req = call.receive<CreateAssessmentRequest>()
            val asg = call.requireOwnedAssignment(ctx, req.classId) ?: return@post
            if (req.name.isBlank()) {
                call.fail("Exam name is required", HttpStatusCode.BadRequest, "BAD_NAME"); return@post
            }
            val now = Instant.now()
            val newId = UUID.randomUUID()
            dbQuery {
                AssessmentsTable.insert {
                    it[id] = newId
                    it[schoolId] = ctx.schoolId
                    it[teacherId] = ctx.userId
                    it[className] = asg.className
                    it[section] = asg.section
                    it[subject] = asg.subject
                    it[name] = req.name.trim()
                    it[maxMarks] = req.maxMarks ?: 100
                    it[examDate] = req.examDate
                    it[isActive] = true
                    it[isPublished] = false
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }
            call.created(
                TeacherAssessmentDto(
                    id = newId.toString(),
                    name = req.name.trim(),
                    subject = asg.subject,
                    maxMarks = req.maxMarks ?: 100,
                    examDate = req.examDate,
                    isPublished = false,
                ),
                message = "Assessment created",
            )
        }
    }

    // ── Syllabus ────────────────────────────────────────────────────────────
    route("/syllabus") {
        // GET ?class_id=&subject= → unit list + overall progress.
        get {
            val ctx = call.requireTeacherContext() ?: return@get
            val classId = call.request.queryParameters["class_id"]
            val asg = call.requireOwnedAssignment(ctx, classId) ?: return@get
            // subject param defaults to the assignment's own subject.
            val subject = call.request.queryParameters["subject"]?.takeIf { it.isNotBlank() } ?: asg.subject
            val grade = "${asg.className}-${asg.section}"

            val units = dbQuery {
                SyllabusUnitsTable.selectAll().where {
                    (SyllabusUnitsTable.schoolId eq ctx.schoolId) and
                        (SyllabusUnitsTable.className eq asg.className) and
                        (SyllabusUnitsTable.section eq asg.section) and
                        (SyllabusUnitsTable.subject eq subject)
                }.orderBy(SyllabusUnitsTable.position, SortOrder.ASC).toList()
            }
            val dtos = units.map { u ->
                SyllabusUnitDto(
                    id = u[SyllabusUnitsTable.id].value.toString(),
                    title = u[SyllabusUnitsTable.title],
                    isCovered = u[SyllabusUnitsTable.isCovered],
                    coveredOn = u[SyllabusUnitsTable.coveredOn],
                )
            }
            val progress = if (dtos.isEmpty()) 0f else dtos.count { it.isCovered }.toFloat() / dtos.size.toFloat()
            call.ok(
                TeacherSyllabusData(className = grade, subject = subject, overallProgress = progress, units = dtos),
                message = "Syllabus loaded",
            )
        }

        // PATCH → toggle one unit's covered flag (scoped via its assignment).
        patch {
            val ctx = call.requireTeacherContext() ?: return@patch
            val req = call.receive<UpdateSyllabusRequest>()
            val unitId = runCatching { UUID.fromString(req.unitId) }.getOrNull()
            if (unitId == null) {
                call.fail("A valid unit_id is required", HttpStatusCode.BadRequest, "BAD_UNIT_ID")
                return@patch
            }

            val unit = dbQuery {
                SyllabusUnitsTable.selectAll().where {
                    (SyllabusUnitsTable.id eq unitId) and
                        (SyllabusUnitsTable.schoolId eq ctx.schoolId)
                }.singleOrNull()
            }
            if (unit == null) {
                call.fail("Syllabus unit not found in your school", HttpStatusCode.NotFound, "UNIT_NOT_FOUND")
                return@patch
            }

            // Authz: the teacher must be assigned to this unit's class+subject.
            val owns = teacherAssignmentsFor(ctx).any {
                it.className == unit[SyllabusUnitsTable.className] &&
                    it.section == unit[SyllabusUnitsTable.section] &&
                    it.subject == unit[SyllabusUnitsTable.subject]
            } || ctx.role == "school_admin" || ctx.role == "admin"
            if (!owns) {
                call.fail("You are not assigned to this class/subject", HttpStatusCode.Forbidden, "NOT_ASSIGNED")
                return@patch
            }

            dbQuery {
                SyllabusUnitsTable.update({ SyllabusUnitsTable.id eq unitId }) {
                    it[isCovered] = req.isCovered
                    it[coveredOn] = if (req.isCovered) todayIso() else null
                    it[coveredBy] = if (req.isCovered) ctx.userId else null
                    it[updatedAt] = Instant.now()
                }
            }
            call.okMessage(if (req.isCovered) "Unit marked covered" else "Unit marked not covered")
        }
    }

    // ── Homework ──────────────────────────────────────────────────────────────
    route("/homework") {
        // GET → all active homework authored by this teacher, with submit ratios.
        get {
            val ctx = call.requireTeacherContext() ?: return@get
            val privileged = ctx.role == "school_admin" || ctx.role == "admin"

            val rows = dbQuery {
                if (privileged) {
                    HomeworkTable.selectAll().where {
                        (HomeworkTable.schoolId eq ctx.schoolId) and (HomeworkTable.isActive eq true)
                    }.orderBy(HomeworkTable.dueDate, SortOrder.DESC).toList()
                } else {
                    HomeworkTable.selectAll().where {
                        (HomeworkTable.schoolId eq ctx.schoolId) and
                            (HomeworkTable.teacherId eq ctx.userId) and
                            (HomeworkTable.isActive eq true)
                    }.orderBy(HomeworkTable.dueDate, SortOrder.DESC).toList()
                }
            }

            val items = rows.map { hw ->
                val hwId = hw[HomeworkTable.id].value
                val className = hw[HomeworkTable.className]
                val section = hw[HomeworkTable.section]
                val submitted = dbQuery {
                    HomeworkSubmissionsTable.selectAll().where {
                        HomeworkSubmissionsTable.homeworkId eq hwId
                    }.count().toInt()
                }
                val total = studentCountFor(ctx.schoolId, className, section)
                HomeworkDto(
                    id = hwId.toString(),
                    title = hw[HomeworkTable.title],
                    description = hw[HomeworkTable.description],
                    className = "$className-$section",
                    subject = hw[HomeworkTable.subject],
                    dueDate = hw[HomeworkTable.dueDate],
                    submittedCount = submitted,
                    totalCount = total,
                )
            }
            call.ok(TeacherHomeworkData(items), message = "Homework loaded")
        }

        // POST → create a homework row for one of the teacher's classes.
        post {
            val ctx = call.requireTeacherContext() ?: return@post
            val req = call.receive<CreateHomeworkRequest>()
            val asg = call.requireOwnedAssignment(ctx, req.classId) ?: return@post
            if (req.title.isBlank()) {
                call.fail("title is required", HttpStatusCode.BadRequest)
                return@post
            }
            if (req.dueDate.isBlank()) {
                call.fail("due_date is required", HttpStatusCode.BadRequest)
                return@post
            }

            val now = Instant.now()
            val newId = UUID.randomUUID()
            dbQuery {
                HomeworkTable.insert {
                    it[id] = newId
                    it[schoolId] = ctx.schoolId
                    it[teacherId] = ctx.userId
                    it[className] = asg.className
                    it[section] = asg.section
                    it[subject] = asg.subject
                    it[title] = req.title
                    it[description] = req.description
                    it[dueDate] = req.dueDate
                    it[isActive] = true
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }

            // RA-41: notify the class parents that new homework was assigned.
            val parents = NotifyRecipients.parentsOfClass(ctx.schoolId, asg.className)
            if (parents.isNotEmpty()) {
                Notify.toUsers(
                    userIds = parents,
                    category = "homework",
                    title = "New homework",
                    body = "${asg.subject}: ${req.title} — due ${req.dueDate}.",
                    schoolId = ctx.schoolId,
                    actorId = ctx.userId,
                    deepLink = "parent/academics",
                    refType = "homework",
                    refId = newId.toString(),
                )
            }
            call.created(mapOf("id" to newId.toString()), message = "Homework created")
        }
    }
}
