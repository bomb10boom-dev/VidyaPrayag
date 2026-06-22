/*
 * File: SchoolAccess.kt
 * Module: core
 *
 * Purpose:
 *   Single source of truth for school-side authorization & scoping. Before this
 *   existed, every *Routing.kt file declared its own private `resolveSchoolId`
 *   and most mutations were id-only (a caller from school A could mutate a row
 *   belonging to school B if they knew its id).
 *
 *   This module centralizes:
 *     - principal parsing (UUID + role)
 *     - school resolution from app_users.school_id
 *     - a guard that requires an authenticated school-admin WITH a school
 *     - ownership checks so every mutation/read is scoped to the caller's school
 *
 * Roles allowed to operate on the school surface:
 *   "school_admin"  – the school owner created during onboarding
 *   "school_staff"  – delegated staff (future-proofing; treated as school role)
 *   "admin"         – platform super-admin (full access)
 *
 * Usage in a route handler:
 *   val ctx = call.requireSchoolContext() ?: return@get   // responds 401/403 itself
 *   // ctx.userId, ctx.schoolId, ctx.role are now trusted
 */
package com.littlebridge.enrollplus.core

import com.littlebridge.enrollplus.db.AppUsersTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import io.ktor.http.*
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

/** Roles permitted to access the school admin surface (reads + shared writes). */
val SCHOOL_ROLES = setOf("school_admin", "school_staff", "admin")

/**
 * RA-39: roles permitted to perform PRIVILEGED school writes — provisioning or
 * removing teachers, resetting credentials, deleting media, and publishing
 * school-wide announcements. `school_staff` is deliberately EXCLUDED: delegated
 * staff can operate the day-to-day surface (`SCHOOL_ROLES`) but must not be able
 * to manage accounts/credentials or broadcast on the school's behalf.
 */
val SCHOOL_ADMIN_ROLES = setOf("school_admin", "admin")

/** Resolved, trusted context for a school-side request. */
data class SchoolContext(
    val userId: UUID,
    val schoolId: UUID,
    val role: String
)

/** Parse the JWT subject into a UUID, or null if missing/malformed. */
fun ApplicationCall.principalUserUuid(): UUID? =
    principalUserId()?.let { runCatching { UUID.fromString(it) }.getOrNull() }

/**
 * Reads (school_id, role) for a user straight from app_users. We deliberately
 * read role from the DB (not the JWT claim) so a role change takes effect on the
 * next request without forcing a token refresh, and so a stale/forged claim
 * cannot widen access.
 */
suspend fun resolveSchoolAndRole(uid: UUID): Pair<UUID?, String?> = dbQuery {
    AppUsersTable.selectAll().where { AppUsersTable.id eq uid }
        .singleOrNull()
        ?.let { it[AppUsersTable.schoolId] to it[AppUsersTable.role] }
        ?: (null to null)
}

/** Just the school id for a user (kept for read-only call sites). */
suspend fun resolveSchoolIdForUser(uid: UUID): UUID? = dbQuery {
    AppUsersTable.selectAll().where { AppUsersTable.id eq uid }
        .singleOrNull()?.get(AppUsersTable.schoolId)
}

/**
 * The canonical guard for every school endpoint. Responds with the appropriate
 * error envelope and returns null when the caller is not authorized:
 *   401 – no/invalid token
 *   403 – authenticated but not a school role
 *   404 – school role but no school yet (must finish onboarding)
 *
 * On success returns a fully-trusted [SchoolContext].
 */
suspend fun ApplicationCall.requireSchoolContext(): SchoolContext? {
    val uid = principalUserUuid() ?: run {
        fail("Invalid token", HttpStatusCode.Unauthorized, "UNAUTHORIZED")
        return null
    }
    // Read the full row once so we can enforce is_active in the same place we
    // resolve school_id + role (RA-34: a deactivated user must pass no guard).
    val userRow = dbQuery {
        AppUsersTable.selectAll().where { AppUsersTable.id eq uid }.singleOrNull()
    }
    if (userRow != null && !userRow[AppUsersTable.isActive]) {
        fail("This account has been deactivated. Contact your administrator.", HttpStatusCode.Forbidden, "ACCOUNT_DEACTIVATED")
        return null
    }
    val schoolId = userRow?.get(AppUsersTable.schoolId)
    val role = userRow?.get(AppUsersTable.role)
    val effectiveRole = role ?: "parent"
    if (effectiveRole !in SCHOOL_ROLES) {
        fail("You do not have access to school resources", HttpStatusCode.Forbidden, "FORBIDDEN")
        return null
    }
    if (schoolId == null) {
        fail("Complete school onboarding first", HttpStatusCode.NotFound, "NO_SCHOOL")
        return null
    }
    return SchoolContext(uid, schoolId, effectiveRole)
}

/**
 * RA-39: stricter guard for PRIVILEGED school writes. Identical to
 * [requireSchoolContext] (same DB-read of role, same is_active / onboarding
 * checks) but additionally requires the resolved role to be in
 * [SCHOOL_ADMIN_ROLES] — so `school_staff` is rejected with 403 even though it
 * passes the general school guard. Role is read from the DB, so a forged/stale
 * JWT claim cannot widen access here either.
 *
 *   401 – no/invalid token
 *   403 – authenticated but not a school role, OR a school_staff lacking admin rights
 *   404 – school role but no school yet
 */
suspend fun ApplicationCall.requireSchoolAdmin(): SchoolContext? {
    val ctx = requireSchoolContext() ?: return null   // already responded on failure
    if (ctx.role !in SCHOOL_ADMIN_ROLES) {
        fail(
            "This action requires a school administrator. Ask your school admin to do this.",
            HttpStatusCode.Forbidden,
            "ADMIN_REQUIRED"
        )
        return null
    }
    return ctx
}
