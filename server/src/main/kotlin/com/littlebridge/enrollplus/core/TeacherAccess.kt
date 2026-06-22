/*
 * File: TeacherAccess.kt
 * Module: core
 *
 * Purpose:
 *   Single source of truth for TEACHER-side authorization & scoping, mirroring
 *   SchoolAccess.kt. Master rebuild doc gap G1 mandates: "every teacher endpoint
 *   filters by teacher_id ∈ JWT and the requested class_id must belong to that
 *   teacher's assignments; reject otherwise with 403".
 *
 *   This module centralizes:
 *     - principal parsing (UUID) reusing core/SecurityModule
 *     - school + role resolution straight from app_users (not the JWT claim, so a
 *       role change takes effect on the next request and a forged claim can't
 *       widen access)
 *     - a guard that requires an authenticated TEACHER with a school
 *     - an assignment-scope check so a teacher can only read/write the classes
 *       they are actually assigned to (teacher_subject_assignments)
 *
 * The `classId` exchanged with the client (TeacherClassDto.id) is the
 * teacher_subject_assignments row id. Every scoped endpoint resolves that id
 * back to its assignment, asserts it belongs to the caller's school AND is
 * assigned to the caller (by teacher_id OR teacher_name fallback), and only then
 * trusts its className / section / subject.
 *
 * Usage in a route handler:
 *   val ctx = call.requireTeacherContext() ?: return@get          // 401/403/404 itself
 *   val asg = call.requireOwnedAssignment(ctx, classId) ?: return@get  // 400/403/404 itself
 *   // asg.className, asg.section, asg.subject are now trusted
 */
package com.littlebridge.enrollplus.core

import com.littlebridge.enrollplus.db.AppUsersTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.TeacherSubjectAssignmentsTable
import io.ktor.http.*
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

/** Roles permitted to access the teacher surface. */
val TEACHER_ROLES = setOf("teacher", "school_admin", "admin")

/** Resolved, trusted context for a teacher-side request. */
data class TeacherContext(
    val userId: UUID,
    val schoolId: UUID,
    val role: String,
    val fullName: String,
)

/**
 * A teacher's class assignment, resolved + ownership-checked. The className /
 * section / subject here are trusted (came from a row already proven to belong
 * to the caller), so handlers can safely scope further queries by them.
 */
data class OwnedAssignment(
    val assignmentId: UUID,
    val schoolId: UUID,
    val className: String,
    val section: String,
    val subject: String,
    val teacherId: UUID?,
    val teacherName: String?,
)

/**
 * The canonical guard for every teacher endpoint. Responds with the appropriate
 * error envelope and returns null when the caller is not authorized:
 *   401 – no/invalid token
 *   403 – authenticated but not a teacher role
 *   404 – teacher role but no school yet
 *
 * On success returns a fully-trusted [TeacherContext].
 */
suspend fun ApplicationCall.requireTeacherContext(): TeacherContext? {
    val uid = principalUserUuid() ?: run {
        fail("Invalid token", HttpStatusCode.Unauthorized, "UNAUTHORIZED")
        return null
    }
    val row = dbQuery {
        AppUsersTable.selectAll().where { AppUsersTable.id eq uid }.singleOrNull()
    } ?: run {
        fail("User not found", HttpStatusCode.Unauthorized, "UNAUTHORIZED")
        return null
    }
    // RA-34: a deactivated account must pass no guard.
    if (!row[AppUsersTable.isActive]) {
        fail("This account has been deactivated. Contact your administrator.", HttpStatusCode.Forbidden, "ACCOUNT_DEACTIVATED")
        return null
    }
    val role = row[AppUsersTable.role]
    if (role !in TEACHER_ROLES) {
        fail("You do not have access to teacher resources", HttpStatusCode.Forbidden, "FORBIDDEN")
        return null
    }
    val schoolId = row[AppUsersTable.schoolId] ?: run {
        fail("Your account is not linked to a school yet", HttpStatusCode.NotFound, "NO_SCHOOL")
        return null
    }
    return TeacherContext(
        userId = uid,
        schoolId = schoolId,
        role = role,
        fullName = row[AppUsersTable.fullName],
    )
}

/**
 * All assignment rows owned by [ctx] (same school AND assigned to this teacher by
 * id, or by name as a pre-FK fallback). school_admin / admin see every active
 * assignment in their school so they can stand in for a teacher.
 */
suspend fun teacherAssignmentsFor(ctx: TeacherContext): List<OwnedAssignment> = dbQuery {
    val rows = TeacherSubjectAssignmentsTable.selectAll().where {
        (TeacherSubjectAssignmentsTable.schoolId eq ctx.schoolId) and
            (TeacherSubjectAssignmentsTable.isActive eq true)
    }.toList()

    val privileged = ctx.role == "school_admin" || ctx.role == "admin"
    rows.filter { r ->
        if (privileged) return@filter true
        val byId = r[TeacherSubjectAssignmentsTable.teacherId] == ctx.userId
        val byName = r[TeacherSubjectAssignmentsTable.teacherName]
            ?.equals(ctx.fullName, ignoreCase = true) == true
        byId || byName
    }.map { it.toOwnedAssignment() }
}

/**
 * Resolve a client-supplied class_id (assignment id) to a trusted
 * [OwnedAssignment], asserting ownership. Responds + returns null on:
 *   400 – missing/malformed id
 *   404 – not found in caller's school
 *   403 – exists but not assigned to caller
 */
suspend fun ApplicationCall.requireOwnedAssignment(
    ctx: TeacherContext,
    classId: String?,
): OwnedAssignment? {
    val id = classId?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: run {
        fail("A valid class_id is required", HttpStatusCode.BadRequest, "BAD_CLASS_ID")
        return null
    }
    val row = dbQuery {
        TeacherSubjectAssignmentsTable.selectAll().where {
            (TeacherSubjectAssignmentsTable.id eq id) and
                (TeacherSubjectAssignmentsTable.schoolId eq ctx.schoolId) and
                (TeacherSubjectAssignmentsTable.isActive eq true)
        }.singleOrNull()
    } ?: run {
        fail("Class not found in your school", HttpStatusCode.NotFound, "CLASS_NOT_FOUND")
        return null
    }

    val privileged = ctx.role == "school_admin" || ctx.role == "admin"
    val owns = privileged ||
        row[TeacherSubjectAssignmentsTable.teacherId] == ctx.userId ||
        row[TeacherSubjectAssignmentsTable.teacherName]?.equals(ctx.fullName, ignoreCase = true) == true
    if (!owns) {
        fail("You are not assigned to this class", HttpStatusCode.Forbidden, "NOT_ASSIGNED")
        return null
    }
    return row.toOwnedAssignment()
}

private fun org.jetbrains.exposed.sql.ResultRow.toOwnedAssignment() = OwnedAssignment(
    assignmentId = this[TeacherSubjectAssignmentsTable.id].value,
    schoolId = this[TeacherSubjectAssignmentsTable.schoolId],
    className = this[TeacherSubjectAssignmentsTable.className],
    section = this[TeacherSubjectAssignmentsTable.section],
    subject = this[TeacherSubjectAssignmentsTable.subject],
    teacherId = this[TeacherSubjectAssignmentsTable.teacherId],
    teacherName = this[TeacherSubjectAssignmentsTable.teacherName],
)
