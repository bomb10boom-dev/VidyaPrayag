/*
 * File: TeacherAssignmentRouting.kt
 * Module: feature.school
 *
 * Structured teacher ⇄ class+section ⇄ subject assignment management.
 *
 * Replaces the free-text `school_subjects.teacher_assigned` column with a real
 * assignment graph (teacher_subject_assignments) so the backend can:
 *   - render per-class subject pools with their actual teacher,
 *   - scope teacher broadcasts to the classes/subjects they teach
 *     (consumed by AnnouncementRouting.resolveRecipientPhones),
 *   - audit who teaches what.
 *
 * Endpoints (all JWT-guarded + school-scoped via requireSchoolContext):
 *   GET    /api/v1/school/teacher-assignments
 *   GET    /api/v1/school/teacher-assignments?class_name=Grade%205
 *   POST   /api/v1/school/teacher-assignments          (create/upsert)
 *   DELETE /api/v1/school/teacher-assignments/{id}      (soft delete)
 *
 * Spec ref: SCHOOL_SIDE_STATUS_REPORT.md §5.5 (class/subject/teacher model).
 */
package com.littlebridge.enrollplus.feature.school

import com.littlebridge.enrollplus.core.created
import com.littlebridge.enrollplus.core.fail
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.requireSchoolAdmin
import com.littlebridge.enrollplus.core.requireSchoolContext
import com.littlebridge.enrollplus.db.AppUsersTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.SchoolClassesTable
import com.littlebridge.enrollplus.db.SchoolSubjectsTable
import com.littlebridge.enrollplus.db.StudentsTable
import com.littlebridge.enrollplus.db.TeacherSubjectAssignmentsTable
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

@Serializable
data class TeacherAssignmentDto(
    val id: String,
    @SerialName("class_id") val classId: String? = null,
    @SerialName("class_name") val className: String,
    @SerialName("section_id") val sectionId: String? = null,
    val section: String = "A",
    @SerialName("subject_id") val subjectId: String? = null,
    val subject: String,
    @SerialName("teacher_id") val teacherId: String? = null,
    @SerialName("teacher_name") val teacherName: String? = null,
    // RA-TAM: live headcount of active students in this class+section.
    @SerialName("student_count") val studentCount: Int = 0
)

@Serializable
data class TeacherAssignmentsListResponse(
    val assignments: List<TeacherAssignmentDto>
)

// ───────────────── RA-TAM: Teacher Assignment Management DTOs ─────────────────

/** Server-side summary aggregation for a single teacher's workload. */
@Serializable
data class TeacherAssignmentSummaryDto(
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("teacher_name") val teacherName: String,
    @SerialName("class_count") val classCount: Int,
    @SerialName("subject_count") val subjectCount: Int,
    @SerialName("student_count") val studentCount: Int,
    @SerialName("section_count") val sectionCount: Int
)

/** A single per-subject distribution bucket for the workload visual. */
@Serializable
data class SubjectDistributionDto(
    @SerialName("subject") val subject: String,
    @SerialName("class_count") val classCount: Int,
    @SerialName("student_count") val studentCount: Int
)

/** Full overview payload: summary + current assignments + insights + distribution. */
@Serializable
data class TeacherAssignmentOverviewDto(
    val summary: TeacherAssignmentSummaryDto,
    val assignments: List<TeacherAssignmentDto>,
    val insights: List<String> = emptyList(),
    val distribution: List<SubjectDistributionDto> = emptyList()
)

/** Selector option for a class (with its sections) used by the add-assignment flow. */
@Serializable
data class AssignmentClassOptionDto(
    @SerialName("class_id") val classId: String,
    val code: String,
    val name: String,
    val sections: List<String> = emptyList()
)

/** Selector option for a subject used by the add-assignment flow. */
@Serializable
data class AssignmentSubjectOptionDto(
    @SerialName("subject_id") val subjectId: String,
    val name: String,
    val code: String
)

/** Options payload for the assignment selector UI. */
@Serializable
data class AssignmentOptionsDto(
    val classes: List<AssignmentClassOptionDto> = emptyList(),
    val subjects: List<AssignmentSubjectOptionDto> = emptyList()
)

/** One class+section target inside a bulk-assign request. */
@Serializable
data class AssignmentTargetDto(
    @SerialName("class_id") val classId: String? = null,
    @SerialName("class_name") val className: String? = null,
    @SerialName("section_id") val sectionId: String? = null,
    val section: String? = null
)

/** Bulk-assign request: one subject → many class+section targets in a single save. */
@Serializable
data class BulkAssignTeacherClassesRequest(
    @SerialName("subject_id") val subjectId: String? = null,
    @SerialName("subject_name") val subjectName: String? = null,
    val assignments: List<AssignmentTargetDto> = emptyList()
)

/** Per-target outcome (conflict detection) returned from a bulk save. */
@Serializable
data class BulkAssignResultItemDto(
    @SerialName("class_name") val className: String,
    val section: String,
    val status: String,            // created | duplicate | invalid
    val message: String? = null,
    val assignment: TeacherAssignmentDto? = null
)

/** Structured bulk-assign response: created rows + per-target results. */
@Serializable
data class BulkAssignResponseDto(
    val created: List<TeacherAssignmentDto>,
    val results: List<BulkAssignResultItemDto>,
    @SerialName("created_count") val createdCount: Int,
    @SerialName("conflict_count") val conflictCount: Int
)

@Serializable
data class CreateTeacherAssignmentDto(
    @SerialName("class_id") val classId: String? = null,
    @SerialName("class_name") val className: String,
    val section: String? = null,
    @SerialName("subject_id") val subjectId: String? = null,
    val subject: String,
    @SerialName("teacher_id") val teacherId: String? = null,
    @SerialName("teacher_name") val teacherName: String? = null
)

private fun org.jetbrains.exposed.sql.ResultRow.toAssignmentDto() = TeacherAssignmentDto(
    id = this[TeacherSubjectAssignmentsTable.id].value.toString(),
    classId = this[TeacherSubjectAssignmentsTable.classId]?.toString(),
    className = this[TeacherSubjectAssignmentsTable.className],
    section = this[TeacherSubjectAssignmentsTable.section],
    subjectId = this[TeacherSubjectAssignmentsTable.subjectId]?.toString(),
    subject = this[TeacherSubjectAssignmentsTable.subject],
    teacherId = this[TeacherSubjectAssignmentsTable.teacherId]?.toString(),
    teacherName = this[TeacherSubjectAssignmentsTable.teacherName]
)

private fun parseUuid(s: String?): UUID? = s?.let { runCatching { UUID.fromString(it) }.getOrNull() }

private val ASSIGNMENT_JSON = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

/** Parse the JSON-text `sections` column ("[\"A\",\"B\"]") into a clean list. */
private fun parseSections(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        (ASSIGNMENT_JSON.parseToJsonElement(raw) as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull() }
            ?: emptyList()
    }.getOrElse { emptyList() }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
    runCatching { this.content }.getOrNull()?.takeIf { it.isNotBlank() }

/**
 * RA-TAM: count active students in a class+section for [schoolId]. Used to power
 * the per-assignment student headcount and the workload summary. Runs inside an
 * Exposed transaction.
 */
private fun studentCountFor(schoolId: UUID, className: String, section: String): Int =
    StudentsTable.selectAll().where {
        (StudentsTable.schoolId eq schoolId) and
            (StudentsTable.isActive eq true) and
            (StudentsTable.className eq className) and
            (StudentsTable.section eq section)
    }.count().toInt()

/** RA-TAM: load this teacher's active assignment rows as DTOs (with live counts). */
private fun loadTeacherAssignments(schoolId: UUID, teacherId: UUID): List<TeacherAssignmentDto> {
    val rows = TeacherSubjectAssignmentsTable.selectAll().where {
        (TeacherSubjectAssignmentsTable.schoolId eq schoolId) and
            (TeacherSubjectAssignmentsTable.teacherId eq teacherId) and
            (TeacherSubjectAssignmentsTable.isActive eq true)
    }.toList()
    // Cache headcounts per (class, section) so we don't re-query duplicates.
    val countCache = HashMap<Pair<String, String>, Int>()
    return rows.map { r ->
        val cls = r[TeacherSubjectAssignmentsTable.className]
        val sec = r[TeacherSubjectAssignmentsTable.section]
        val count = countCache.getOrPut(cls to sec) { studentCountFor(schoolId, cls, sec) }
        r.toAssignmentDto().copy(studentCount = count)
    }.sortedWith(compareBy({ it.subject }, { it.className }, { it.section }))
}

/** RA-TAM: server-side workload summary aggregation. */
private fun summarize(
    teacherId: UUID,
    teacherName: String,
    assignments: List<TeacherAssignmentDto>
): TeacherAssignmentSummaryDto {
    val classKeys = assignments.map { it.className to it.section }.distinct()
    return TeacherAssignmentSummaryDto(
        teacherId = teacherId.toString(),
        teacherName = teacherName,
        classCount = classKeys.size,
        subjectCount = assignments.map { it.subject }.distinct().size,
        // Total students = sum of distinct class+section headcounts (a student in
        // two of the teacher's subjects in the same class counts once).
        studentCount = classKeys.sumOf { key ->
            assignments.firstOrNull { (it.className to it.section) == key }?.studentCount ?: 0
        },
        sectionCount = assignments.map { it.section }.distinct().size
    )
}

/** RA-TAM: per-subject distribution buckets for the lightweight workload visual. */
private fun distributionOf(assignments: List<TeacherAssignmentDto>): List<SubjectDistributionDto> =
    assignments.groupBy { it.subject }.map { (subject, list) ->
        SubjectDistributionDto(
            subject = subject,
            classCount = list.map { it.className to it.section }.distinct().size,
            studentCount = list.map { it.className to it.section }.distinct()
                .sumOf { key -> list.first { (it.className to it.section) == key }.studentCount }
        )
    }.sortedByDescending { it.studentCount }

/** RA-TAM: simple rule-based workload insights (3–5 short strings). */
private fun assignmentInsights(summary: TeacherAssignmentSummaryDto): List<String> {
    val out = mutableListOf<String>()
    if (summary.studentCount > 0) out += "Handles ${summary.studentCount} students across ${summary.classCount} classes."
    if (summary.classCount > 0) out += "Assigned to ${summary.classCount} active class${if (summary.classCount == 1) "" else "es"}."
    if (summary.subjectCount > 0) out += "Covers ${summary.subjectCount} subject${if (summary.subjectCount == 1) "" else "s"}."
    if (summary.sectionCount > 0) out += "Teaches across ${summary.sectionCount} section${if (summary.sectionCount == 1) "" else "s"}."
    if (summary.classCount >= 12) out += "Workload is above the typical school average."
    return out.take(5)
}

fun Route.teacherAssignmentRouting() {
    authenticate("jwt") {
        route("/api/v1/school/teacher-assignments") {

            // ---- list (optionally filtered by class) ----
            get {
                val ctx = call.requireSchoolContext() ?: return@get
                val classFilter = call.request.queryParameters["class_name"]?.takeIf { it.isNotBlank() }
                val list = dbQuery {
                    val base = TeacherSubjectAssignmentsTable.selectAll()
                        .where {
                            (TeacherSubjectAssignmentsTable.schoolId eq ctx.schoolId) and
                                (TeacherSubjectAssignmentsTable.isActive eq true)
                        }
                    val rows = if (classFilter != null) {
                        base.filter { it[TeacherSubjectAssignmentsTable.className].equals(classFilter, ignoreCase = true) }
                    } else {
                        base.toList()
                    }
                    rows.sortedWith(
                        compareBy(
                            { it[TeacherSubjectAssignmentsTable.className] },
                            { it[TeacherSubjectAssignmentsTable.section] },
                            { it[TeacherSubjectAssignmentsTable.subject] }
                        )
                    ).map { it.toAssignmentDto() }
                }
                call.ok(
                    TeacherAssignmentsListResponse(list),
                    message = "Teacher assignments fetched successfully"
                )
            }

            // ---- create / upsert (allot a subject+class to a teacher) ----
            // RA-39: allotting/un-allotting teachers is a PRIVILEGED write, so it
            // requires a school admin (school_staff is rejected). When a
            // teacher_id is supplied we validate it belongs to THIS school and
            // resolve the display name from app_users so the stored teacher_name
            // is always authoritative (never a client-spoofed label).
            post {
                val ctx = call.requireSchoolAdmin() ?: return@post
                val req = call.receive<CreateTeacherAssignmentDto>()

                if (req.className.isBlank() || req.subject.isBlank()) {
                    call.fail("class_name and subject are required", HttpStatusCode.BadRequest)
                    return@post
                }
                if (req.teacherId == null && req.teacherName.isNullOrBlank()) {
                    call.fail("Either teacher_id or teacher_name is required", HttpStatusCode.BadRequest)
                    return@post
                }

                val section = req.section?.takeIf { it.isNotBlank() } ?: "A"
                val now = Instant.now()
                val teacherUuid = parseUuid(req.teacherId)

                val result = dbQuery {
                    // When linking by id, the teacher MUST be a real teacher in the
                    // caller's school (IDOR-safe) — and we adopt its name.
                    var resolvedName = req.teacherName
                    if (teacherUuid != null) {
                        val teacherRow = AppUsersTable.selectAll()
                            .where {
                                (AppUsersTable.id eq teacherUuid) and
                                    (AppUsersTable.schoolId eq ctx.schoolId) and
                                    (AppUsersTable.role eq "teacher")
                            }
                            .firstOrNull() ?: return@dbQuery null   // unknown / cross-school teacher
                        resolvedName = teacherRow[AppUsersTable.fullName]
                    }

                    // Upsert on (school, class, section, subject).
                    val existing = TeacherSubjectAssignmentsTable.selectAll()
                        .where {
                            (TeacherSubjectAssignmentsTable.schoolId eq ctx.schoolId) and
                                (TeacherSubjectAssignmentsTable.className eq req.className) and
                                (TeacherSubjectAssignmentsTable.section eq section) and
                                (TeacherSubjectAssignmentsTable.subject eq req.subject)
                        }
                        .firstOrNull()

                    val rowId: UUID = if (existing != null) {
                        val rid = existing[TeacherSubjectAssignmentsTable.id].value
                        TeacherSubjectAssignmentsTable.update({ TeacherSubjectAssignmentsTable.id eq rid }) {
                            it[classId] = parseUuid(req.classId)
                            it[subjectId] = parseUuid(req.subjectId)
                            it[teacherId] = teacherUuid
                            it[teacherName] = resolvedName
                            it[isActive] = true
                            it[updatedAt] = now
                        }
                        rid
                    } else {
                        val newId = UUID.randomUUID()
                        TeacherSubjectAssignmentsTable.insert {
                            it[id] = newId
                            it[schoolId] = ctx.schoolId
                            it[classId] = parseUuid(req.classId)
                            it[className] = req.className
                            it[TeacherSubjectAssignmentsTable.section] = section
                            it[subjectId] = parseUuid(req.subjectId)
                            it[subject] = req.subject
                            it[teacherId] = teacherUuid
                            it[teacherName] = resolvedName
                            it[isActive] = true
                            it[createdAt] = now
                            it[updatedAt] = now
                        }
                        newId
                    }

                    // RA-LINK: an assignment was created/re-pointed → re-derive the
                    // affected teacher metrics through the centralized reconciler so
                    // every student of (class, section) is now linked to this teacher
                    // and the teacher's workload reflects the change. No manual link.
                    StudentAggregationService.recalcForAssignmentChange(
                        ctx.schoolId, req.className, section, teacherUuid
                    )

                    TeacherSubjectAssignmentsTable.selectAll()
                        .where { TeacherSubjectAssignmentsTable.id eq rowId }
                        .first()
                        .toAssignmentDto()
                }

                if (result == null) {
                    call.fail("Teacher not found in your school", HttpStatusCode.NotFound, "TEACHER_NOT_FOUND")
                    return@post
                }
                call.created(result, message = "Teacher assignment saved")
            }

            // ---- un-allot (soft delete) — privileged admin write (RA-39) ----
            delete("/{id}") {
                val ctx = call.requireSchoolAdmin() ?: return@delete
                val id = parseUuid(call.parameters["id"])
                if (id == null) {
                    call.fail("Invalid assignment id", HttpStatusCode.BadRequest)
                    return@delete
                }
                val updated = dbQuery {
                    // Capture the relationship coordinates BEFORE soft-deleting so we
                    // can re-derive the affected teacher metrics afterwards.
                    val row = TeacherSubjectAssignmentsTable.selectAll().where {
                        (TeacherSubjectAssignmentsTable.id eq id) and
                            (TeacherSubjectAssignmentsTable.schoolId eq ctx.schoolId)
                    }.firstOrNull() ?: return@dbQuery 0
                    val affectedClass = row[TeacherSubjectAssignmentsTable.className]
                    val affectedSection = row[TeacherSubjectAssignmentsTable.section]
                    val affectedTeacher = row[TeacherSubjectAssignmentsTable.teacherId]

                    val n = TeacherSubjectAssignmentsTable.update({
                        (TeacherSubjectAssignmentsTable.id eq id) and
                            (TeacherSubjectAssignmentsTable.schoolId eq ctx.schoolId)
                    }) {
                        it[isActive] = false   // soft-delete: relationship history preserved
                        it[updatedAt] = Instant.now()
                    }

                    // RA-LINK: assignment de-activated → re-derive the affected
                    // teacher metrics so this teacher is dropped from those students'
                    // teacher lists and remaining counts stay accurate.
                    if (n > 0) {
                        StudentAggregationService.recalcForAssignmentChange(
                            ctx.schoolId, affectedClass, affectedSection, affectedTeacher
                        )
                    }
                    n
                }
                if (updated == 0) {
                    call.fail("Assignment not found in your school", HttpStatusCode.NotFound)
                    return@delete
                }
                call.ok(mapOf("id" to id.toString()), message = "Teacher assignment removed")
            }

            // ═══════════ RA-TAM: Teacher Assignment Management surface ═══════════
            // These sub-routes power the dedicated assignment-management screen
            // (Teacher Listing / Teacher Profile / onboarding reuse). They extend
            // the existing prefix rather than introducing a parallel API tree, and
            // leave the onboarding-time POST above untouched.

            // ---- assignment overview (summary + assignments + insights) ----
            get("/overview/{teacherId}") {
                val ctx = call.requireSchoolContext() ?: return@get
                val teacherId = parseUuid(call.parameters["teacherId"])
                    ?: run { call.fail("Invalid teacher id", HttpStatusCode.BadRequest); return@get }

                val overview = dbQuery {
                    val teacher = AppUsersTable.selectAll().where {
                        (AppUsersTable.id eq teacherId) and
                            (AppUsersTable.schoolId eq ctx.schoolId) and
                            (AppUsersTable.role eq "teacher")
                    }.firstOrNull() ?: return@dbQuery null

                    val assignments = loadTeacherAssignments(ctx.schoolId, teacherId)
                    val summary = summarize(teacherId, teacher[AppUsersTable.fullName], assignments)
                    TeacherAssignmentOverviewDto(
                        summary = summary,
                        assignments = assignments,
                        insights = assignmentInsights(summary),
                        distribution = distributionOf(assignments)
                    )
                }
                if (overview == null) {
                    call.fail("Teacher not found in your school", HttpStatusCode.NotFound, "TEACHER_NOT_FOUND")
                    return@get
                }
                call.ok(overview, message = "Teacher assignment overview fetched")
            }

            // ---- selector options (classes + sections + subjects) ----
            get("/options") {
                val ctx = call.requireSchoolContext() ?: return@get
                val options = dbQuery {
                    val classes = SchoolClassesTable.selectAll()
                        .where { SchoolClassesTable.schoolId eq ctx.schoolId }
                        .map {
                            AssignmentClassOptionDto(
                                classId = it[SchoolClassesTable.id].value.toString(),
                                code = it[SchoolClassesTable.code],
                                name = it[SchoolClassesTable.name],
                                sections = parseSections(it[SchoolClassesTable.sections])
                            )
                        }
                        .sortedBy { it.name }

                    val classIdByValue = classes.associateBy { it.classId }
                    // Subjects belong to a class; surface distinct subject names
                    // across the school's classes for the subject chip selector.
                    val subjects = SchoolSubjectsTable.selectAll()
                        .where { SchoolSubjectsTable.classId inList classIds(classIdByValue.keys) }
                        .map {
                            AssignmentSubjectOptionDto(
                                subjectId = it[SchoolSubjectsTable.id].value.toString(),
                                name = it[SchoolSubjectsTable.subName],
                                code = it[SchoolSubjectsTable.subCode]
                            )
                        }
                        .distinctBy { it.name.lowercase() }
                        .sortedBy { it.name }

                    AssignmentOptionsDto(classes = classes, subjects = subjects)
                }
                call.ok(options, message = "Assignment options fetched")
            }

            // ---- bulk assign (one subject → many class+section targets) ----
            post("/bulk/{teacherId}") {
                val ctx = call.requireSchoolAdmin() ?: return@post
                val teacherId = parseUuid(call.parameters["teacherId"])
                    ?: run { call.fail("Invalid teacher id", HttpStatusCode.BadRequest); return@post }
                val req = call.receive<BulkAssignTeacherClassesRequest>()

                if (req.subjectId == null && req.subjectName.isNullOrBlank()) {
                    call.fail("subject_id or subject_name is required", HttpStatusCode.BadRequest)
                    return@post
                }
                if (req.assignments.isEmpty()) {
                    call.fail("At least one class+section target is required", HttpStatusCode.BadRequest)
                    return@post
                }

                val response = dbQuery {
                    // Teacher must be a real teacher in this school (IDOR-safe).
                    val teacher = AppUsersTable.selectAll().where {
                        (AppUsersTable.id eq teacherId) and
                            (AppUsersTable.schoolId eq ctx.schoolId) and
                            (AppUsersTable.role eq "teacher")
                    }.firstOrNull() ?: return@dbQuery null
                    val teacherName = teacher[AppUsersTable.fullName]
                    val now = Instant.now()

                    // Resolve the subject + its valid class options from the school.
                    val classRows = SchoolClassesTable.selectAll()
                        .where { SchoolClassesTable.schoolId eq ctx.schoolId }
                        .associate { row ->
                            row[SchoolClassesTable.id].value.toString() to
                                Triple(
                                    row[SchoolClassesTable.name],
                                    row[SchoolClassesTable.code],
                                    parseSections(row[SchoolClassesTable.sections])
                                )
                        }
                    val subjectUuid = parseUuid(req.subjectId)
                    val resolvedSubjectName = req.subjectName?.takeIf { it.isNotBlank() }
                        ?: subjectUuid?.let { sid ->
                            SchoolSubjectsTable.selectAll()
                                .where { SchoolSubjectsTable.id eq sid }
                                .firstOrNull()?.get(SchoolSubjectsTable.subName)
                        }

                    if (resolvedSubjectName.isNullOrBlank()) {
                        return@dbQuery BulkAssignResponseDto(
                            created = emptyList(),
                            results = listOf(
                                BulkAssignResultItemDto("", "", "invalid", "Subject could not be resolved")
                            ),
                            createdCount = 0,
                            conflictCount = 1
                        )
                    }

                    val created = mutableListOf<TeacherAssignmentDto>()
                    val results = mutableListOf<BulkAssignResultItemDto>()
                    // RA-LINK: every (class, section) that gained/re-pointed an
                    // assignment in this batch, so we reconcile each one ONCE after.
                    val touchedClasses = LinkedHashSet<Pair<String, String>>()

                    req.assignments.forEach { target ->
                        // Resolve class name + validate section against the school.
                        val classMeta = target.classId?.let { classRows[it] }
                        val className = classMeta?.first
                            ?: target.className?.takeIf { it.isNotBlank() }
                        val section = target.section?.takeIf { it.isNotBlank() } ?: "A"

                        if (className.isNullOrBlank()) {
                            results += BulkAssignResultItemDto(
                                className = target.className ?: "?",
                                section = section,
                                status = "invalid",
                                message = "Unknown class reference"
                            )
                            return@forEach
                        }
                        // Validation: section must belong to the class when known.
                        if (classMeta != null && classMeta.third.isNotEmpty() &&
                            classMeta.third.none { it.equals(section, ignoreCase = true) }
                        ) {
                            results += BulkAssignResultItemDto(
                                className = className,
                                section = section,
                                status = "invalid",
                                message = "Section $section is not configured for $className"
                            )
                            return@forEach
                        }

                        // Conflict detection: duplicate active assignment for the
                        // same teacher+class+section+subject.
                        val existing = TeacherSubjectAssignmentsTable.selectAll().where {
                            (TeacherSubjectAssignmentsTable.schoolId eq ctx.schoolId) and
                                (TeacherSubjectAssignmentsTable.className eq className) and
                                (TeacherSubjectAssignmentsTable.section eq section) and
                                (TeacherSubjectAssignmentsTable.subject eq resolvedSubjectName)
                        }.firstOrNull()

                        if (existing != null) {
                            val existingTeacher = existing[TeacherSubjectAssignmentsTable.teacherId]
                            val existingActive = existing[TeacherSubjectAssignmentsTable.isActive]
                            if (existingActive && existingTeacher == teacherId) {
                                results += BulkAssignResultItemDto(
                                    className = className,
                                    section = section,
                                    status = "duplicate",
                                    message = "Already assigned to this teacher"
                                )
                                return@forEach
                            }
                            // Re-activate / re-point an existing row to this teacher.
                            val rid = existing[TeacherSubjectAssignmentsTable.id].value
                            TeacherSubjectAssignmentsTable.update({ TeacherSubjectAssignmentsTable.id eq rid }) {
                                it[classId] = parseUuid(target.classId)
                                it[subjectId] = subjectUuid
                                it[TeacherSubjectAssignmentsTable.teacherId] = teacherId
                                it[TeacherSubjectAssignmentsTable.teacherName] = teacherName
                                it[isActive] = true
                                it[updatedAt] = now
                            }
                            val dto = TeacherSubjectAssignmentsTable.selectAll()
                                .where { TeacherSubjectAssignmentsTable.id eq rid }
                                .first().toAssignmentDto()
                                .copy(studentCount = studentCountFor(ctx.schoolId, className, section))
                            created += dto
                            touchedClasses += className to section
                            results += BulkAssignResultItemDto(className, section, "created", assignment = dto)
                            return@forEach
                        }

                        // Fresh insert.
                        val newId = UUID.randomUUID()
                        TeacherSubjectAssignmentsTable.insert {
                            it[id] = newId
                            it[schoolId] = ctx.schoolId
                            it[classId] = parseUuid(target.classId)
                            it[TeacherSubjectAssignmentsTable.className] = className
                            it[TeacherSubjectAssignmentsTable.section] = section
                            it[subjectId] = subjectUuid
                            it[subject] = resolvedSubjectName
                            it[TeacherSubjectAssignmentsTable.teacherId] = teacherId
                            it[TeacherSubjectAssignmentsTable.teacherName] = teacherName
                            it[isActive] = true
                            it[createdAt] = now
                            it[updatedAt] = now
                        }
                        val dto = TeacherSubjectAssignmentsTable.selectAll()
                            .where { TeacherSubjectAssignmentsTable.id eq newId }
                            .first().toAssignmentDto()
                            .copy(studentCount = studentCountFor(ctx.schoolId, className, section))
                        created += dto
                        touchedClasses += className to section
                        results += BulkAssignResultItemDto(className, section, "created", assignment = dto)
                    }

                    // RA-LINK: reconcile every affected (class, section) once so all
                    // students in those classes are linked to this teacher and the
                    // teacher's workload reflects the full batch. No manual linking.
                    touchedClasses.forEach { (cls, sec) ->
                        StudentAggregationService.recalcForAssignmentChange(
                            ctx.schoolId, cls, sec, teacherId
                        )
                    }

                    BulkAssignResponseDto(
                        created = created,
                        results = results,
                        createdCount = created.size,
                        conflictCount = results.count { it.status != "created" }
                    )
                }

                if (response == null) {
                    call.fail("Teacher not found in your school", HttpStatusCode.NotFound, "TEACHER_NOT_FOUND")
                    return@post
                }
                call.created(response, message = "Assignments processed")
            }

            // ---- remove a single assignment scoped to a teacher (RA-TAM) ----
            delete("/{teacherId}/items/{assignmentId}") {
                val ctx = call.requireSchoolAdmin() ?: return@delete
                val teacherId = parseUuid(call.parameters["teacherId"])
                val assignmentId = parseUuid(call.parameters["assignmentId"])
                if (teacherId == null || assignmentId == null) {
                    call.fail("Invalid teacher or assignment id", HttpStatusCode.BadRequest)
                    return@delete
                }
                val updated = dbQuery {
                    // Capture coordinates BEFORE soft-deleting so we can re-derive
                    // the affected teacher metrics afterwards.
                    val row = TeacherSubjectAssignmentsTable.selectAll().where {
                        (TeacherSubjectAssignmentsTable.id eq assignmentId) and
                            (TeacherSubjectAssignmentsTable.schoolId eq ctx.schoolId) and
                            (TeacherSubjectAssignmentsTable.teacherId eq teacherId)
                    }.firstOrNull() ?: return@dbQuery 0
                    val affectedClass = row[TeacherSubjectAssignmentsTable.className]
                    val affectedSection = row[TeacherSubjectAssignmentsTable.section]

                    val n = TeacherSubjectAssignmentsTable.update({
                        (TeacherSubjectAssignmentsTable.id eq assignmentId) and
                            (TeacherSubjectAssignmentsTable.schoolId eq ctx.schoolId) and
                            (TeacherSubjectAssignmentsTable.teacherId eq teacherId)
                    }) {
                        it[isActive] = false   // soft-delete: relationship history preserved
                        it[updatedAt] = Instant.now()
                    }

                    // RA-LINK: assignment removed → re-derive affected teacher metrics
                    // so this teacher is dropped from those students' teacher lists and
                    // the teacher's workload refreshes.
                    if (n > 0) {
                        StudentAggregationService.recalcForAssignmentChange(
                            ctx.schoolId, affectedClass, affectedSection, teacherId
                        )
                    }
                    n
                }
                if (updated == 0) {
                    call.fail("Assignment not found for this teacher", HttpStatusCode.NotFound)
                    return@delete
                }
                call.ok(mapOf("id" to assignmentId.toString()), message = "Teacher assignment removed")
            }
        }
    }
}

/** Helper: map a set of UUID-strings into a List<UUID> for `inList` predicates. */
private fun classIds(ids: Collection<String>): List<UUID> = ids.mapNotNull { parseUuid(it) }
