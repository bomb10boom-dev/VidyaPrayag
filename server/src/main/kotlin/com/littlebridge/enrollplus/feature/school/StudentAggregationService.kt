/*
 * File: StudentAggregationService.kt
 * Module: feature.school
 *
 * RA-SP: the CENTRALIZED student aggregation service — the single source of
 * truth for every derived student/teacher/parent metric so the platform never
 * shows stale data.
 *
 * The school domain stores facts (students, teacher_subject_assignments,
 * parent_child_links, attendance_records, assessments + marks). Counts and
 * relationships that the UI needs — "how many teachers does this student have",
 * "how many students does this teacher have", "who are this student's parents",
 * "what is this student's attendance %" — are NOT stored as columns (which would
 * inevitably drift). Instead they are DERIVED on demand from those facts, in ONE
 * place, here. Every read path (student roster, student profile, teacher
 * profile) and every write path (add student, move class) funnels its
 * aggregation through this service, which guarantees:
 *
 *   • Teacher → student counts are always recomputed from the live roster, so
 *     adding/moving a student is reflected instantly with no manual updates.
 *   • Student → teachers are always derived from the class+section assignments,
 *     so a teacher-assignment change updates every affected student profile.
 *   • Student → parents are always read from the approved parent_child_links,
 *     with AT MOST ONE primary guardian enforced per student.
 *
 * Every function here is pure with respect to the DB transaction it runs in:
 * call them from inside `dbQuery { ... }`. They never write unless explicitly
 * named `…recalc…`/`enforce…`.
 */
package com.littlebridge.enrollplus.feature.school

import com.littlebridge.enrollplus.db.AppUsersTable
import com.littlebridge.enrollplus.db.AssessmentMarksTable
import com.littlebridge.enrollplus.db.AssessmentsTable
import com.littlebridge.enrollplus.db.AttendanceRecordsTable
import com.littlebridge.enrollplus.db.ParentChildLinksTable
import com.littlebridge.enrollplus.db.StudentsTable
import com.littlebridge.enrollplus.db.TeacherSubjectAssignmentsTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

/**
 * RA-SP: centralized aggregation logic. Stateless object — every method takes
 * the school scope explicitly and runs inside the caller's Exposed transaction.
 */
object StudentAggregationService {

    // ───────────────────────── Student → Teachers ─────────────────────────

    /**
     * Teachers connected to a student, DERIVED from the active teacher subject
     * assignments for the student's (class, section). No manual linking — when a
     * teacher's assignment changes the student's teacher list changes with it.
     *
     * Returns one row per (teacher, subject) so the profile can show "who teaches
     * what". `designation` is a light heuristic (a teacher who covers this exact
     * class+section for several subjects reads as a "Class Teacher").
     */
    fun teachersForStudent(
        schoolId: UUID,
        className: String,
        section: String
    ): List<StudentTeacherDto> {
        val rows = TeacherSubjectAssignmentsTable.selectAll().where {
            (TeacherSubjectAssignmentsTable.schoolId eq schoolId) and
                (TeacherSubjectAssignmentsTable.isActive eq true) and
                (TeacherSubjectAssignmentsTable.className eq className) and
                (TeacherSubjectAssignmentsTable.section eq section)
        }.toList()

        // How many subjects each teacher covers in THIS class+section — used to
        // derive a "Class Teacher" designation (covers the whole class) vs a
        // single-subject "Subject Teacher".
        val subjectsPerTeacher: Map<String, Int> = rows
            .groupBy { teacherKey(it) }
            .mapValues { (_, v) -> v.map { it[TeacherSubjectAssignmentsTable.subject] }.distinct().size }

        return rows.map { r ->
            val key = teacherKey(r)
            val teacherUuid = r[TeacherSubjectAssignmentsTable.teacherId]
            val name = r[TeacherSubjectAssignmentsTable.teacherName]
                ?.takeIf { it.isNotBlank() }
                ?: teacherUuid?.let { resolveTeacherName(schoolId, it) }
                ?: "Teacher"
            val coversCount = subjectsPerTeacher[key] ?: 1
            StudentTeacherDto(
                id = teacherUuid?.toString() ?: key,
                name = name,
                subject = r[TeacherSubjectAssignmentsTable.subject],
                designation = if (coversCount >= 3) "Class Teacher" else "Subject Teacher"
            )
        }.sortedBy { it.name.lowercase() }
    }

    /** Distinct teacher headcount for a student (for the listing `teacher_count`). */
    fun teacherCountForStudent(schoolId: UUID, className: String, section: String): Int =
        TeacherSubjectAssignmentsTable.selectAll().where {
            (TeacherSubjectAssignmentsTable.schoolId eq schoolId) and
                (TeacherSubjectAssignmentsTable.isActive eq true) and
                (TeacherSubjectAssignmentsTable.className eq className) and
                (TeacherSubjectAssignmentsTable.section eq section)
        }.map { teacherKey(it) }.distinct().size

    /** Distinct subject count taught to a student's class+section. */
    fun subjectCountForStudent(schoolId: UUID, className: String, section: String): Int =
        TeacherSubjectAssignmentsTable.selectAll().where {
            (TeacherSubjectAssignmentsTable.schoolId eq schoolId) and
                (TeacherSubjectAssignmentsTable.isActive eq true) and
                (TeacherSubjectAssignmentsTable.className eq className) and
                (TeacherSubjectAssignmentsTable.section eq section)
        }.map { it[TeacherSubjectAssignmentsTable.subject] }.distinct().size

    private fun teacherKey(r: org.jetbrains.exposed.sql.ResultRow): String =
        r[TeacherSubjectAssignmentsTable.teacherId]?.toString()
            ?: ("name:" + (r[TeacherSubjectAssignmentsTable.teacherName] ?: "?"))

    private fun resolveTeacherName(schoolId: UUID, teacherId: UUID): String? =
        AppUsersTable.selectAll().where {
            (AppUsersTable.id eq teacherId) and (AppUsersTable.schoolId eq schoolId)
        }.firstOrNull()?.get(AppUsersTable.fullName)

    // ───────────────────────── Student → Parents ──────────────────────────

    /**
     * Approved parents linked to a student, read from parent_child_links joined
     * to app_users for display name + phone. Supports MULTIPLE parents; the
     * stored `is_primary_guardian` flag marks the (single) primary guardian.
     */
    fun parentsForStudent(schoolId: UUID, studentCode: String): List<StudentParentDto> {
        val links = ParentChildLinksTable.selectAll().where {
            (ParentChildLinksTable.schoolId eq schoolId) and
                (ParentChildLinksTable.studentCode eq studentCode) and
                (ParentChildLinksTable.status eq "approved")
        }.toList()

        return links.map { link ->
            val pid = link[ParentChildLinksTable.parentId]
            val userRow = AppUsersTable.selectAll().where { AppUsersTable.id eq pid }.firstOrNull()
            val name = userRow?.get(AppUsersTable.fullName)
                ?: link[ParentChildLinksTable.childName] // last-resort display
                ?: "Parent"
            StudentParentDto(
                id = pid.toString(),
                name = name,
                relation = link[ParentChildLinksTable.relation]?.takeIf { it.isNotBlank() } ?: "Guardian",
                isPrimaryGuardian = link[ParentChildLinksTable.isPrimaryGuardian],
                phone = userRow?.get(AppUsersTable.phone)
            )
        }.sortedWith(compareByDescending<StudentParentDto> { it.isPrimaryGuardian }.thenBy { it.name.lowercase() })
    }

    /** Approved parent count for a student (for the listing `parent_count`). */
    fun parentCountForStudent(schoolId: UUID, studentCode: String): Int =
        ParentChildLinksTable.selectAll().where {
            (ParentChildLinksTable.schoolId eq schoolId) and
                (ParentChildLinksTable.studentCode eq studentCode) and
                (ParentChildLinksTable.status eq "approved")
        }.count().toInt()

    /**
     * RA-SP: enforce the invariant "AT MOST ONE primary guardian per student".
     * Pass the link id that SHOULD be primary (or null to simply demote extras);
     * every other approved link for the same (school, student_code) is demoted.
     * Write operation — call from a write transaction after a link change.
     */
    fun enforceSinglePrimaryGuardian(
        schoolId: UUID,
        studentCode: String,
        primaryLinkId: UUID?
    ) {
        val now = Instant.now()
        ParentChildLinksTable.update({
            (ParentChildLinksTable.schoolId eq schoolId) and
                (ParentChildLinksTable.studentCode eq studentCode) and
                (ParentChildLinksTable.status eq "approved")
        }) {
            it[isPrimaryGuardian] = false
            it[actionedAt] = now
        }
        if (primaryLinkId != null) {
            ParentChildLinksTable.update({ ParentChildLinksTable.id eq primaryLinkId }) {
                it[isPrimaryGuardian] = true
                it[actionedAt] = now
            }
        }
    }

    // ───────────────────────── Attendance metric ──────────────────────────

    data class AttendanceSummary(
        val presentDays: Int,
        val absentDays: Int,
        val lateDays: Int,
        val percent: Float
    )

    /** Attendance summary for a student (person_id = student_code, type=student). */
    fun attendanceForStudent(schoolId: UUID, studentCode: String): AttendanceSummary {
        val statuses = AttendanceRecordsTable.selectAll().where {
            (AttendanceRecordsTable.schoolId eq schoolId) and
                (AttendanceRecordsTable.type eq "student") and
                (AttendanceRecordsTable.personId eq studentCode)
        }.map { it[AttendanceRecordsTable.status].lowercase() }
        val present = statuses.count { it == "present" }
        val late = statuses.count { it == "late" }
        val absent = statuses.count { it == "absent" }
        val total = statuses.size
        val percent = if (total > 0) ((present + late) * 100f) / total else 0f
        return AttendanceSummary(present, absent, late, percent)
    }

    // ───────────────────────── Academic score ─────────────────────────────

    /**
     * Average academic score for a student as a 0..100 percentage, derived from
     * published assessment marks (marks/maxMarks). Returns null when the student
     * has no graded marks yet so the UI can hide the metric honestly.
     */
    fun academicScoreForStudent(schoolId: UUID, studentCode: String): Float? {
        val ratios = mutableListOf<Float>()
        AssessmentsTable.selectAll().where {
            (AssessmentsTable.schoolId eq schoolId) and (AssessmentsTable.isActive eq true)
        }.forEach { a ->
            val maxMarks = a[AssessmentsTable.maxMarks]
            if (maxMarks <= 0) return@forEach
            val mark = AssessmentMarksTable.selectAll().where {
                (AssessmentMarksTable.assessmentId eq a[AssessmentsTable.id].value) and
                    (AssessmentMarksTable.studentId eq studentCode)
            }.firstOrNull()?.get(AssessmentMarksTable.marks)
            if (mark != null) ratios += (mark.toFloat() / maxMarks * 100f).coerceIn(0f, 100f)
        }
        return if (ratios.isEmpty()) null else ratios.average().toFloat()
    }

    // ───────────────────── Teacher workload auto-sync ─────────────────────

    /**
     * RA-SP: recompute and return the live student headcount for every teacher
     * who teaches the given (class, section). This is the workload auto-sync
     * entry point — call it right after a student is added to / moved out of a
     * class so any caller can refresh affected teacher metrics. Because counts
     * are DERIVED (never stored), simply reading them here guarantees they are
     * never stale; the map can also be logged/asserted by the caller.
     *
     * Key = teacher key (uuid string or "name:<name>"), value = distinct active
     * students that teacher teaches across ALL of their class+section assignments.
     */
    fun recalcTeacherStudentCountsForClass(
        schoolId: UUID,
        className: String,
        section: String
    ): Map<String, Int> {
        // Teachers touching this class+section.
        val teacherKeys = TeacherSubjectAssignmentsTable.selectAll().where {
            (TeacherSubjectAssignmentsTable.schoolId eq schoolId) and
                (TeacherSubjectAssignmentsTable.isActive eq true) and
                (TeacherSubjectAssignmentsTable.className eq className) and
                (TeacherSubjectAssignmentsTable.section eq section)
        }.map { teacherKey(it) }.distinct()

        return teacherKeys.associateWith { key ->
            // All class+section pairs this teacher is assigned to.
            val pairs = TeacherSubjectAssignmentsTable.selectAll().where {
                (TeacherSubjectAssignmentsTable.schoolId eq schoolId) and
                    (TeacherSubjectAssignmentsTable.isActive eq true)
            }.filter { teacherKey(it) == key }
                .map { it[TeacherSubjectAssignmentsTable.className] to it[TeacherSubjectAssignmentsTable.section] }
                .distinct()
            // Distinct student headcount across those pairs.
            pairs.sumOf { (cls, sec) ->
                StudentsTable.selectAll().where {
                    (StudentsTable.schoolId eq schoolId) and
                        (StudentsTable.isActive eq true) and
                        (StudentsTable.className eq cls) and
                        (StudentsTable.section eq sec)
                }.count().toInt()
            }
        }
    }

    /**
     * Convenience for a class MOVE: recompute teacher student counts for BOTH the
     * old and the new (class, section). Returns the union map so the caller can
     * confirm both ends were refreshed.
     */
    fun recalcTeacherStudentCountsForMove(
        schoolId: UUID,
        fromClassName: String,
        fromSection: String,
        toClassName: String,
        toSection: String
    ): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        out.putAll(recalcTeacherStudentCountsForClass(schoolId, fromClassName, fromSection))
        out.putAll(recalcTeacherStudentCountsForClass(schoolId, toClassName, toSection))
        return out
    }

    // ───────────── Teacher ↔ Student reconciliation (centralized) ──────────
    //
    // RA-LINK: the single, shared reconciliation surface for the
    // Student ↔ Teacher relationship. Because relationships are DERIVED from
    // the active (class, section, subject) assignment graph + the live roster,
    // "reconciling" is simply re-deriving the affected counts so every read
    // path (student profile teacher list, teacher workload, school dashboard)
    // reflects the change with no manual linking. Every write path (assignment
    // create/upsert/soft-delete, teacher create/deactivate, student
    // create/move/remove) funnels here so the logic lives in ONE place.

    /**
     * RA-LINK: recompute the live student headcount for a SINGLE teacher across
     * every class+section they are actively assigned to. This is the
     * teacher-side workload auto-sync entry point — call it right after a
     * teacher is created, their assignments change, or they are
     * activated/deactivated. Returns the distinct active-student total so the
     * caller can log/assert it. Because the value is DERIVED (never stored),
     * reading it here guarantees it is never stale.
     */
    fun recalcTeacherWorkload(schoolId: UUID, teacherId: UUID): Int {
        val pairs = TeacherSubjectAssignmentsTable.selectAll().where {
            (TeacherSubjectAssignmentsTable.schoolId eq schoolId) and
                (TeacherSubjectAssignmentsTable.isActive eq true) and
                (TeacherSubjectAssignmentsTable.teacherId eq teacherId)
        }.map { it[TeacherSubjectAssignmentsTable.className] to it[TeacherSubjectAssignmentsTable.section] }
            .distinct()
        return pairs.sumOf { (cls, sec) ->
            StudentsTable.selectAll().where {
                (StudentsTable.schoolId eq schoolId) and
                    (StudentsTable.isActive eq true) and
                    (StudentsTable.className eq cls) and
                    (StudentsTable.section eq sec)
            }.count().toInt()
        }
    }

    /**
     * RA-LINK: the canonical hook to call AFTER any single teacher-assignment
     * write (create / upsert / re-point / soft-delete). It re-derives every
     * affected teacher metric:
     *
     *   • student counts for all teachers touching the affected (class, section)
     *     — so students of that class instantly gain/lose this teacher; and
     *   • the workload of the specific teacher whose assignment changed (when
     *     known) — so a re-pointed teacher's totals refresh on BOTH ends.
     *
     * Returns the per-teacher student-count map for the affected class+section.
     * Idempotent and read-only with respect to the assignment graph (it only
     * recomputes derived numbers), so it is safe to call on every path.
     */
    fun recalcForAssignmentChange(
        schoolId: UUID,
        className: String,
        section: String,
        teacherId: UUID? = null
    ): Map<String, Int> {
        val counts = recalcTeacherStudentCountsForClass(schoolId, className, section)
        if (teacherId != null) recalcTeacherWorkload(schoolId, teacherId)
        return counts
    }

    /**
     * RA-LINK: SOFT-DELETE every active assignment of a teacher (Example 5 —
     * teacher deactivated / removed). Relationship history is preserved: rows
     * are flipped to `isActive = false` (reusing the project's soft-delete
     * convention) instead of being physically removed, and the derived student
     * teacher lists drop the teacher automatically.
     *
     * Returns the distinct set of (class, section) pairs that were touched so
     * the caller can recompute the remaining teachers' counts for those classes.
     * Write operation — call from a write transaction.
     */
    fun softDeactivateTeacherAssignments(
        schoolId: UUID,
        teacherId: UUID
    ): Set<Pair<String, String>> {
        val affected = TeacherSubjectAssignmentsTable.selectAll().where {
            (TeacherSubjectAssignmentsTable.schoolId eq schoolId) and
                (TeacherSubjectAssignmentsTable.teacherId eq teacherId) and
                (TeacherSubjectAssignmentsTable.isActive eq true)
        }.map { it[TeacherSubjectAssignmentsTable.className] to it[TeacherSubjectAssignmentsTable.section] }
            .toSet()

        if (affected.isNotEmpty()) {
            TeacherSubjectAssignmentsTable.update({
                (TeacherSubjectAssignmentsTable.schoolId eq schoolId) and
                    (TeacherSubjectAssignmentsTable.teacherId eq teacherId) and
                    (TeacherSubjectAssignmentsTable.isActive eq true)
            }) {
                it[isActive] = false
                it[updatedAt] = Instant.now()
            }
        }
        return affected
    }

    // ─────────────────────────── Insights ─────────────────────────────────

    /**
     * RA-SP: rule-based, backend-generated insight strings for a student. Each
     * one is derived from a real signal (attendance, parents, teachers, academic
     * score, recency) so the profile's Insights section is honest and never
     * fabricated. Returns up to 5, newest-most-relevant first.
     */
    fun insightsForStudent(
        attendancePercent: Float,
        classAverageAttendance: Float?,
        parentCount: Int,
        hasPrimaryGuardian: Boolean,
        teacherCount: Int,
        academicScore: Float?,
        daysSinceAdmission: Long?
    ): List<String> {
        val out = mutableListOf<String>()
        if (attendancePercent >= 95f) {
            out += "Excellent attendance at ${attendancePercent.toInt()}%."
        } else if (attendancePercent in 1f..74.9f) {
            out += "Attendance needs attention at ${attendancePercent.toInt()}%."
        }
        if (classAverageAttendance != null && attendancePercent > classAverageAttendance + 1f) {
            out += "Attendance above class average."
        }
        if (academicScore != null && academicScore >= 75f) {
            out += "Strong academic performance (${academicScore.toInt()}%)."
        }
        if (parentCount >= 2) {
            out += "Linked to $parentCount guardians."
        } else if (parentCount == 1 && hasPrimaryGuardian) {
            out += "Primary guardian on record."
        } else if (parentCount == 0) {
            out += "No parent linked yet."
        }
        if (teacherCount > 0) {
            out += "Connected to $teacherCount teacher${if (teacherCount == 1) "" else "s"}."
        }
        if (daysSinceAdmission != null && daysSinceAdmission in 0..30) {
            out += "Recently enrolled."
        }
        return out.take(5)
    }

    // ─────────────────────── Activity timeline ────────────────────────────

    /**
     * RA-SP: a student's recent activity feed assembled from real rows
     * (admission, attendance, graded marks, parent links), merged and sorted
     * newest-first then capped. Honest empty state when nothing is recorded yet.
     */
    fun activitiesForStudent(
        schoolId: UUID,
        studentCode: String,
        admissionDate: Instant?,
        limit: Int = 15
    ): List<StudentActivityDto> {
        val items = mutableListOf<Pair<Instant, StudentActivityDto>>()

        // Joined school (admission).
        admissionDate?.let { adm ->
            items += adm to StudentActivityDto(
                title = "Joined school",
                createdAt = adm.toString(),
                type = "admission"
            )
        }

        // Graded marks become "results updated" entries.
        AssessmentsTable.selectAll().where {
            (AssessmentsTable.schoolId eq schoolId) and (AssessmentsTable.isActive eq true)
        }.orderBy(AssessmentsTable.createdAt, SortOrder.DESC).limit(limit).forEach { a ->
            val graded = AssessmentMarksTable.selectAll().where {
                (AssessmentMarksTable.assessmentId eq a[AssessmentsTable.id].value) and
                    (AssessmentMarksTable.studentId eq studentCode)
            }.firstOrNull()
            if (graded?.get(AssessmentMarksTable.marks) != null) {
                val ts = graded[AssessmentMarksTable.updatedAt]
                items += ts to StudentActivityDto(
                    title = "Marks recorded: ${a[AssessmentsTable.name]} · ${a[AssessmentsTable.subject]}",
                    createdAt = ts.toString(),
                    type = "marks"
                )
            }
        }

        // Parent links (approved + pending) become "parent linked" entries.
        ParentChildLinksTable.selectAll().where {
            (ParentChildLinksTable.schoolId eq schoolId) and
                (ParentChildLinksTable.studentCode eq studentCode)
        }.forEach { link ->
            val ts = link[ParentChildLinksTable.actionedAt] ?: link[ParentChildLinksTable.requestedAt]
            val name = link[ParentChildLinksTable.childName]
            items += ts to StudentActivityDto(
                title = if (link[ParentChildLinksTable.status] == "approved")
                    "Parent linked${name?.let { "" } ?: ""}"
                else "Parent link requested",
                createdAt = ts.toString(),
                type = "parent_link"
            )
        }

        // Recent attendance marks (latest few) become "attendance marked" entries.
        AttendanceRecordsTable.selectAll().where {
            (AttendanceRecordsTable.schoolId eq schoolId) and
                (AttendanceRecordsTable.type eq "student") and
                (AttendanceRecordsTable.personId eq studentCode)
        }.orderBy(AttendanceRecordsTable.createdAt, SortOrder.DESC).limit(5).forEach { rec ->
            val ts = rec[AttendanceRecordsTable.createdAt]
            items += ts to StudentActivityDto(
                title = "Attendance marked: ${rec[AttendanceRecordsTable.status].replaceFirstChar { it.uppercase() }} (${rec[AttendanceRecordsTable.date]})",
                createdAt = ts.toString(),
                type = "attendance"
            )
        }

        return items.sortedByDescending { it.first }.take(limit).map { it.second }
    }
}
