/*
 * File: NonTeachingStaffRouting.kt
 * Module: feature.school
 *
 * RA-S17: the Admin People tab demanded a third vertical — "Non-teaching staff"
 * (office, accounts, library, support, security, transport, …) — that did NOT
 * exist anywhere in the codebase (no table, no route, no model, no UI). This
 * builds the server end of that vertical end-to-end, mirroring the student
 * roster pattern (school-scoped, soft-delete, admin-gated writes).
 *
 * Non-teaching staff are roster records, NOT app_users — they do not log in.
 *
 * Endpoints (JWT + school-scoped; school_id resolved from JWT, never the body):
 *   GET    /api/v1/school/staff            list active staff (optional ?q=&department=)
 *   POST   /api/v1/school/staff            add a staff member (school-admin)
 *   GET    /api/v1/school/staff/{id}       single staff profile
 *   PATCH  /api/v1/school/staff/{id}       edit a staff member (school-admin)
 *   DELETE /api/v1/school/staff/{id}       soft-delete a staff member (school-admin)
 *
 * Every read/write is constrained to ctx.schoolId (IDOR-safe). Deletion is a
 * soft-delete (is_active=false) performed from the staff profile behind a
 * confirm dialog on the client (RA-S17 directive — no direct list-row delete).
 */
package com.littlebridge.enrollplus.feature.school

import com.littlebridge.enrollplus.core.created
import com.littlebridge.enrollplus.core.fail
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.okMessage
import com.littlebridge.enrollplus.core.requireSchoolAdmin
import com.littlebridge.enrollplus.core.requireSchoolContext
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.NonTeachingStaffTable
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

// ───────────────────────────── DTOs ─────────────────────────────

@Serializable
data class StaffDto(
    val id: String,
    @SerialName("full_name") val fullName: String,
    val role: String,
    val department: String? = null,
    val phone: String? = null,
    val email: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null
)

@Serializable
data class StaffListResponse(val staff: List<StaffDto>)

@Serializable
data class CreateStaffRequest(
    @SerialName("full_name") val fullName: String,
    val role: String,
    val department: String? = null,
    val phone: String? = null,
    val email: String? = null
)

@Serializable
data class UpdateStaffRequest(
    @SerialName("full_name") val fullName: String? = null,
    val role: String? = null,
    val department: String? = null,
    val phone: String? = null,
    val email: String? = null
)

// ─────────────────────────── helpers ────────────────────────────

private fun staffRowToDto(row: ResultRow): StaffDto =
    StaffDto(
        id = row[NonTeachingStaffTable.id].value.toString(),
        fullName = row[NonTeachingStaffTable.fullName],
        role = row[NonTeachingStaffTable.role],
        department = row[NonTeachingStaffTable.department],
        phone = row[NonTeachingStaffTable.phone],
        email = row[NonTeachingStaffTable.email],
        photoUrl = row[NonTeachingStaffTable.photoUrl]
    )

// ─────────────────────────── routing ────────────────────────────

fun Route.nonTeachingStaffRouting() {
    authenticate("jwt") {
        route("/api/v1/school/staff") {

            // ---- roster: active non-teaching staff in the caller's school ----
            // RA-S17: optional `q` (name/role/department search) and `department`
            // filter, applied in-memory after the scoped fetch so the SQL stays
            // Postgres-portable.
            get {
                val ctx = call.requireSchoolContext() ?: return@get
                val q = call.request.queryParameters["q"]?.trim()?.takeIf { it.isNotBlank() }?.lowercase()
                val dept = call.request.queryParameters["department"]?.trim()?.takeIf { it.isNotBlank() }
                val staff = dbQuery {
                    NonTeachingStaffTable.selectAll()
                        .where {
                            (NonTeachingStaffTable.schoolId eq ctx.schoolId) and
                                (NonTeachingStaffTable.isActive eq true)
                        }
                        .orderBy(NonTeachingStaffTable.fullName to SortOrder.ASC)
                        .map(::staffRowToDto)
                }.filter { s ->
                    (dept == null || s.department.equals(dept, ignoreCase = true)) &&
                        (q == null ||
                            s.fullName.lowercase().contains(q) ||
                            s.role.lowercase().contains(q) ||
                            (s.department?.lowercase()?.contains(q) == true))
                }
                call.ok(StaffListResponse(staff), message = "Staff fetched")
            }

            // ---- add a staff member (school-admin) ----
            post {
                val ctx = call.requireSchoolAdmin() ?: return@post
                val req = runCatching { call.receive<CreateStaffRequest>() }.getOrNull()
                    ?: run { call.fail("Invalid body"); return@post }
                if (req.fullName.isBlank() || req.role.isBlank()) {
                    call.fail("Name and role are required.")
                    return@post
                }
                val now = Instant.now()
                val dto = dbQuery {
                    val newId = NonTeachingStaffTable.insert {
                        it[schoolId] = ctx.schoolId
                        it[fullName] = req.fullName.trim()
                        it[role] = req.role.trim()
                        it[department] = req.department?.takeIf { d -> d.isNotBlank() }?.trim()
                        it[phone] = req.phone?.takeIf { p -> p.isNotBlank() }?.trim()
                        it[email] = req.email?.takeIf { e -> e.isNotBlank() }?.trim()
                        it[isActive] = true
                        it[createdAt] = now
                        it[updatedAt] = now
                    } get NonTeachingStaffTable.id
                    NonTeachingStaffTable.selectAll().where { NonTeachingStaffTable.id eq newId }.first().let(::staffRowToDto)
                }
                call.created(dto, message = "Staff member added")
            }

            // ---- single staff profile ----
            get("{id}") {
                val ctx = call.requireSchoolContext() ?: return@get
                val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: run { call.fail("Invalid staff id"); return@get }
                val dto = dbQuery {
                    NonTeachingStaffTable.selectAll()
                        .where {
                            (NonTeachingStaffTable.id eq id) and
                                (NonTeachingStaffTable.schoolId eq ctx.schoolId) and
                                (NonTeachingStaffTable.isActive eq true)
                        }
                        .firstOrNull()?.let(::staffRowToDto)
                }
                if (dto == null) {
                    call.fail("Staff member not found in your school", HttpStatusCode.NotFound, "STAFF_NOT_FOUND")
                    return@get
                }
                call.ok(dto, message = "Staff profile fetched")
            }

            // ---- edit a staff member (school-admin) ----
            patch("{id}") {
                val ctx = call.requireSchoolAdmin() ?: return@patch
                val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: run { call.fail("Invalid staff id"); return@patch }
                val req = runCatching { call.receive<UpdateStaffRequest>() }.getOrNull()
                    ?: run { call.fail("Invalid body"); return@patch }
                val now = Instant.now()
                val dto = dbQuery {
                    val exists = NonTeachingStaffTable.selectAll()
                        .where {
                            (NonTeachingStaffTable.id eq id) and
                                (NonTeachingStaffTable.schoolId eq ctx.schoolId) and
                                (NonTeachingStaffTable.isActive eq true)
                        }
                        .firstOrNull() ?: return@dbQuery null
                    NonTeachingStaffTable.update({
                        (NonTeachingStaffTable.id eq id) and (NonTeachingStaffTable.schoolId eq ctx.schoolId)
                    }) {
                        req.fullName?.takeIf { v -> v.isNotBlank() }?.let { v -> it[fullName] = v.trim() }
                        req.role?.takeIf { v -> v.isNotBlank() }?.let { v -> it[role] = v.trim() }
                        if (req.department != null) it[department] = req.department.takeIf { d -> d.isNotBlank() }?.trim()
                        if (req.phone != null) it[phone] = req.phone.takeIf { p -> p.isNotBlank() }?.trim()
                        if (req.email != null) it[email] = req.email.takeIf { e -> e.isNotBlank() }?.trim()
                        it[updatedAt] = now
                    }
                    NonTeachingStaffTable.selectAll().where { NonTeachingStaffTable.id eq id }.first().let(::staffRowToDto)
                }
                if (dto == null) {
                    call.fail("Staff member not found in your school", HttpStatusCode.NotFound, "STAFF_NOT_FOUND")
                    return@patch
                }
                call.ok(dto, message = "Staff member updated")
            }

            // ---- soft-delete a staff member (school-admin) ----
            delete("{id}") {
                val ctx = call.requireSchoolAdmin() ?: return@delete
                val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: run { call.fail("Invalid staff id"); return@delete }
                val n = dbQuery {
                    NonTeachingStaffTable.update({
                        (NonTeachingStaffTable.id eq id) and (NonTeachingStaffTable.schoolId eq ctx.schoolId)
                    }) {
                        it[isActive] = false
                        it[updatedAt] = Instant.now()
                    }
                }
                if (n == 0) {
                    call.fail("Staff member not found in your school", HttpStatusCode.NotFound, "STAFF_NOT_FOUND")
                    return@delete
                }
                call.okMessage("Staff member removed")
            }
        }
    }
}
