/*
 * File: AcademicYearRouting.kt
 * Module: feature.calendar
 *
 * Academic Year management — the real replacement for the Settings
 * "Academic Year (Coming Soon)" stub. Lets a school admin Create / Activate /
 * Archive / View-historical academic years. Exactly ONE year can be active at a
 * time (activating one archives nothing but de-activates the previously active
 * one).
 *
 * Endpoints (under /api/admin/academic-years, JWT-guarded, school-scoped):
 *   GET  /api/admin/academic-years            — list (active first, then by start desc)
 *   POST /api/admin/academic-years            — create (optionally activate)
 *   PUT  /api/admin/academic-years/{id}       — edit / activate / archive
 *
 * Authorization:
 *   GET uses requireSchoolContext(); POST/PUT use requireSchoolAdmin().
 */
package com.littlebridge.enrollplus.feature.calendar

import com.littlebridge.enrollplus.core.created
import com.littlebridge.enrollplus.core.fail
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.requireSchoolAdmin
import com.littlebridge.enrollplus.core.requireSchoolContext
import com.littlebridge.enrollplus.db.AcademicYearsTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// DTOs
// ─────────────────────────────────────────────────────────────────────────────

object AcademicYearStatus {
    const val DRAFT = "DRAFT"
    const val ACTIVE = "ACTIVE"
    const val ARCHIVED = "ARCHIVED"
    val ALL = setOf(DRAFT, ACTIVE, ARCHIVED)
}

@Serializable
data class AcademicYearDto(
    val id: String,
    val name: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    @SerialName("is_active") val isActive: Boolean,
    val status: String,
    @SerialName("academic_days") val academicDays: Int? = null,
    @SerialName("holiday_days") val holidayDays: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class AcademicYearsListResponse(val years: List<AcademicYearDto>, val total: Int)

@Serializable
data class CreateAcademicYearRequest(
    val name: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    @SerialName("academic_days") val academicDays: Int? = null,
    @SerialName("holiday_days") val holidayDays: Int? = null,
    val activate: Boolean = false
)

@Serializable
data class UpdateAcademicYearRequest(
    val name: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("academic_days") val academicDays: Int? = null,
    @SerialName("holiday_days") val holidayDays: Int? = null,
    // status transitions: send "ACTIVE" to activate (auto de-activates others),
    // "ARCHIVED" to archive. "DRAFT" keeps it editable.
    val status: String? = null
)

private fun org.jetbrains.exposed.sql.ResultRow.toAcademicYearDto(): AcademicYearDto =
    AcademicYearDto(
        id = this[AcademicYearsTable.id].value.toString(),
        name = this[AcademicYearsTable.name],
        startDate = this[AcademicYearsTable.startDate],
        endDate = this[AcademicYearsTable.endDate],
        isActive = this[AcademicYearsTable.isActive],
        status = this[AcademicYearsTable.status],
        academicDays = this[AcademicYearsTable.academicDays],
        holidayDays = this[AcademicYearsTable.holidayDays],
        createdAt = this[AcademicYearsTable.createdAt].toString(),
        updatedAt = this[AcademicYearsTable.updatedAt].toString()
    )

/** De-activate every other year for this school (used when activating one). */
private fun deactivateOthers(schoolId: UUID, keepId: UUID?) {
    AcademicYearsTable.update({
        (AcademicYearsTable.schoolId eq schoolId) and (AcademicYearsTable.isActive eq true)
    }) {
        it[isActive] = false
        // keep prior status unless it was ACTIVE → move it back to ARCHIVED
        it[status] = AcademicYearStatus.ARCHIVED
        it[updatedAt] = Instant.now()
    }
    // (keepId itself is set active by the caller after this runs)
}

// ─────────────────────────────────────────────────────────────────────────────
// Routing
// ─────────────────────────────────────────────────────────────────────────────

fun Route.academicYearRouting() {
    authenticate("jwt") {
        route("/api/admin/academic-years") {

            // ---- list (active first, then newest start) ----
            get {
                val ctx = call.requireSchoolContext() ?: return@get
                val list = dbQuery {
                    AcademicYearsTable.selectAll()
                        .where { AcademicYearsTable.schoolId eq ctx.schoolId }
                        .orderBy(
                            AcademicYearsTable.isActive to SortOrder.DESC,
                            AcademicYearsTable.startDate to SortOrder.DESC
                        )
                        .map { it.toAcademicYearDto() }
                }
                call.ok(AcademicYearsListResponse(list, list.size), message = "Academic years fetched")
            }

            // ---- create ----
            post {
                val ctx = call.requireSchoolAdmin() ?: return@post
                val req = call.receive<CreateAcademicYearRequest>()
                if (req.name.isBlank()) { call.fail("name is required"); return@post }
                if (parseIso(req.startDate) == null) { call.fail("start_date must be YYYY-MM-DD"); return@post }
                if (parseIso(req.endDate) == null) { call.fail("end_date must be YYYY-MM-DD"); return@post }
                if (parseIso(req.endDate)!!.isBefore(parseIso(req.startDate)!!)) {
                    call.fail("end_date cannot precede start_date"); return@post
                }

                val dto = dbQuery {
                    val now = Instant.now()
                    if (req.activate) deactivateOthers(ctx.schoolId, null)
                    val id = AcademicYearsTable.insertAndGetId {
                        it[schoolId] = ctx.schoolId
                        it[name] = req.name.trim()
                        it[startDate] = req.startDate
                        it[endDate] = req.endDate
                        it[isActive] = req.activate
                        it[status] = if (req.activate) AcademicYearStatus.ACTIVE else AcademicYearStatus.DRAFT
                        it[academicDays] = req.academicDays
                        it[holidayDays] = req.holidayDays
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                    AcademicYearsTable.selectAll()
                        .where { AcademicYearsTable.id eq id }
                        .first()
                        .toAcademicYearDto()
                }
                call.created(dto, message = "Academic year created")
            }

            // ---- update / activate / archive ----
            put("/{id}") {
                val ctx = call.requireSchoolAdmin() ?: return@put
                val yearId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (yearId == null) { call.fail("Invalid academic year id"); return@put }
                val req = call.receive<UpdateAcademicYearRequest>()
                val newStatus = req.status?.uppercase()
                if (newStatus != null && newStatus !in AcademicYearStatus.ALL) {
                    call.fail("Invalid status. Allowed: ${AcademicYearStatus.ALL.joinToString()}"); return@put
                }
                req.startDate?.let { if (parseIso(it) == null) { return@put call.fail("start_date must be YYYY-MM-DD") } }
                req.endDate?.let { if (parseIso(it) == null) { return@put call.fail("end_date must be YYYY-MM-DD") } }

                val dto = dbQuery {
                    val row = AcademicYearsTable.selectAll()
                        .where { (AcademicYearsTable.schoolId eq ctx.schoolId) and (AcademicYearsTable.id eq yearId) }
                        .firstOrNull() ?: return@dbQuery null

                    val now = Instant.now()
                    val activating = newStatus == AcademicYearStatus.ACTIVE
                    if (activating) deactivateOthers(ctx.schoolId, yearId)

                    AcademicYearsTable.update({
                        (AcademicYearsTable.schoolId eq ctx.schoolId) and (AcademicYearsTable.id eq yearId)
                    }) {
                        req.name?.let { v -> it[name] = v.trim() }
                        req.startDate?.let { v -> it[startDate] = v }
                        req.endDate?.let { v -> it[endDate] = v }
                        req.academicDays?.let { v -> it[academicDays] = v }
                        req.holidayDays?.let { v -> it[holidayDays] = v }
                        when (newStatus) {
                            AcademicYearStatus.ACTIVE -> { it[status] = AcademicYearStatus.ACTIVE; it[isActive] = true }
                            AcademicYearStatus.ARCHIVED -> { it[status] = AcademicYearStatus.ARCHIVED; it[isActive] = false }
                            AcademicYearStatus.DRAFT -> { it[status] = AcademicYearStatus.DRAFT; it[isActive] = false }
                            else -> {}
                        }
                        it[updatedAt] = now
                    }

                    AcademicYearsTable.selectAll()
                        .where { AcademicYearsTable.id eq yearId }
                        .first()
                        .toAcademicYearDto()
                }
                if (dto == null) { call.fail("Academic year not found", HttpStatusCode.NotFound); return@put }
                call.ok(dto, message = "Academic year updated")
            }
        }
    }
}
