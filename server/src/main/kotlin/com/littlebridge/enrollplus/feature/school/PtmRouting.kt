/*
 * File: PtmRouting.kt
 * Module: feature.school
 *
 * Endpoints (all JWT, school-admin scoped):
 *   GET   /api/v1/school/ptm
 *   POST  /api/v1/school/ptm
 *   PATCH /api/v1/school/ptm/{id}/metrics            (check-ins, invites, read receipts)
 *   PUT   /api/v1/school/ptm/{id}/class-progress     (upsert per-class met/total)
 *   POST  /api/v1/school/ptm/{id}/complete           (finalize -> turnout/total_met)
 *
 * Spec ref: school_api_spec.artifact.md §Module: PTM
 *
 * `GET /api/v1/school/ptm` returns three buckets:
 *   active_event   — next upcoming PTM (date >= today); falls back to the
 *                    most-recent past row if no future row exists.  Null if
 *                    the school has never scheduled a PTM.
 *   history        — past PTMs with turnout / total_met (capped at 10).
 *   class_progress — per-class met/total rollup for the active event,
 *                    computed against `ptm_class_progress`.
 *
 * `POST /api/v1/school/ptm` creates a new PTM with sane defaults (counters
 * start at zero) and returns it as the new active_event payload.
 */
package com.littlebridge.enrollplus.feature.school

import com.littlebridge.enrollplus.core.created
import com.littlebridge.enrollplus.core.fail
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.okMessage
import com.littlebridge.enrollplus.core.requireSchoolContext
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.PtmClassProgressTable
import com.littlebridge.enrollplus.db.PtmEventsTable
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// ---------------- DTOs ----------------

@Serializable
data class PtmActiveEventDto(
    val id: String,
    val title: String,
    val date: String,
    val slot: String,
    @SerialName("expected_parents") val expectedParents: Int,
    @SerialName("checked_in_parents") val checkedInParents: Int,
    @SerialName("invites_delivered") val invitesDelivered: Int,
    @SerialName("read_receipts") val readReceipts: Int
)

@Serializable
data class PtmHistoryDto(
    val id: String,
    val date: String,
    val title: String,
    val turnout: Int,
    @SerialName("total_met") val totalMet: Int
)

@Serializable
data class PtmClassProgressDto(
    val id: String,
    @SerialName("class_name") val className: String,
    @SerialName("teacher_name") val teacherName: String,
    @SerialName("met_count") val metCount: Int,
    @SerialName("total_count") val totalCount: Int,
    val progress: Double
)

@Serializable
data class PtmResponse(
    @SerialName("active_event") val activeEvent: PtmActiveEventDto?,
    val history: List<PtmHistoryDto>,
    @SerialName("class_progress") val classProgress: List<PtmClassProgressDto>
)

@Serializable
data class CreatePtmDto(
    val title: String,
    val date: String,                                        // YYYY-MM-DD
    val slot: String,                                        // e.g. "09:00 - 13:00"
    @SerialName("expected_parents") val expectedParents: Int = 0
)

/** Partial metric update — only non-null fields are written. */
@Serializable
data class UpdatePtmMetricsDto(
    @SerialName("checked_in_parents") val checkedInParents: Int? = null,
    @SerialName("invites_delivered") val invitesDelivered: Int? = null,
    @SerialName("read_receipts") val readReceipts: Int? = null
)

/** Upsert one class's met/total rollup for an event. */
@Serializable
data class UpsertClassProgressDto(
    @SerialName("class_name") val className: String,
    @SerialName("teacher_name") val teacherName: String = "",
    @SerialName("met_count") val metCount: Int,
    @SerialName("total_count") val totalCount: Int
)

// ---------------- helpers ----------------

/** Confirms [eventId] belongs to [schoolId]. Must run inside dbQuery {}. */
private fun ptmOwnedBySchool(eventId: UUID, schoolId: UUID): Boolean =
    PtmEventsTable.selectAll()
        .where { (PtmEventsTable.id eq eventId) and (PtmEventsTable.schoolId eq schoolId) }
        .count() > 0L

private fun org.jetbrains.exposed.sql.ResultRow.toActiveDto() = PtmActiveEventDto(
    id = this[PtmEventsTable.id].value.toString(),
    title = this[PtmEventsTable.title],
    date = this[PtmEventsTable.date],
    slot = this[PtmEventsTable.slot],
    expectedParents = this[PtmEventsTable.expectedParents],
    checkedInParents = this[PtmEventsTable.checkedInParents],
    invitesDelivered = this[PtmEventsTable.invitesDelivered],
    readReceipts = this[PtmEventsTable.readReceipts]
)

private fun org.jetbrains.exposed.sql.ResultRow.toHistoryDto() = PtmHistoryDto(
    id = this[PtmEventsTable.id].value.toString(),
    date = this[PtmEventsTable.date],
    title = this[PtmEventsTable.title],
    turnout = this[PtmEventsTable.turnout],
    totalMet = this[PtmEventsTable.totalMet]
)

private fun org.jetbrains.exposed.sql.ResultRow.toProgressDto(): PtmClassProgressDto {
    val met = this[PtmClassProgressTable.metCount]
    val total = this[PtmClassProgressTable.totalCount]
    return PtmClassProgressDto(
        id = this[PtmClassProgressTable.id].value.toString(),
        className = this[PtmClassProgressTable.className],
        teacherName = this[PtmClassProgressTable.teacherName],
        metCount = met,
        totalCount = total,
        progress = if (total == 0) 0.0 else met.toDouble() / total.toDouble()
    )
}

fun Route.ptmRouting() {
    authenticate("jwt") {
        route("/api/v1/school/ptm") {

            // -------- GET --------
            get {
                val ctx = call.requireSchoolContext() ?: return@get
                val schoolId = ctx.schoolId

                val payload = dbQuery {
                    val today = LocalDate.now().toString()

                    // Active event = next upcoming (date >= today, soonest first)
                    val upcoming = PtmEventsTable.selectAll()
                        .where { (PtmEventsTable.schoolId eq schoolId) and (PtmEventsTable.date greaterEq today) }
                        .orderBy(PtmEventsTable.date, SortOrder.ASC)
                        .limit(1)
                        .firstOrNull()
                    val past = if (upcoming == null) {
                        PtmEventsTable.selectAll()
                            .where { (PtmEventsTable.schoolId eq schoolId) and (PtmEventsTable.date less today) }
                            .orderBy(PtmEventsTable.date, SortOrder.DESC)
                            .limit(1)
                            .firstOrNull()
                    } else null
                    val active = upcoming ?: past

                    val historyRows = PtmEventsTable.selectAll()
                        .where { (PtmEventsTable.schoolId eq schoolId) and (PtmEventsTable.date less today) }
                        .orderBy(PtmEventsTable.date, SortOrder.DESC)
                        .limit(10)
                        .toList()

                    val classProgress = active?.let { ev ->
                        PtmClassProgressTable.selectAll()
                            .where { PtmClassProgressTable.ptmEventId eq ev[PtmEventsTable.id].value }
                            .orderBy(PtmClassProgressTable.className, SortOrder.ASC)
                            .map { it.toProgressDto() }
                    } ?: emptyList()

                    PtmResponse(
                        activeEvent = active?.toActiveDto(),
                        history = historyRows.map { it.toHistoryDto() },
                        classProgress = classProgress
                    )
                }
                call.ok(payload, message = "PTM data fetched successfully")
            }

            // -------- CREATE --------
            post {
                val ctx = call.requireSchoolContext() ?: return@post
                val uid = ctx.userId
                val req = call.receive<CreatePtmDto>()
                if (req.title.isBlank() || req.date.isBlank() || req.slot.isBlank()) {
                    call.fail("title, date, slot are required"); return@post
                }
                runCatching { LocalDate.parse(req.date) }.onFailure {
                    call.fail("date must be YYYY-MM-DD"); return@post
                }
                if (req.expectedParents < 0) { call.fail("expected_parents cannot be negative"); return@post }

                val schoolId = ctx.schoolId
                val newId = UUID.randomUUID()
                val now = Instant.now()
                dbQuery {
                    PtmEventsTable.insert {
                        it[PtmEventsTable.id] = newId
                        it[PtmEventsTable.schoolId] = schoolId
                        it[title] = req.title
                        it[date] = req.date
                        it[slot] = req.slot
                        it[expectedParents] = req.expectedParents
                        it[checkedInParents] = 0
                        it[invitesDelivered] = 0
                        it[readReceipts] = 0
                        it[turnout] = 0
                        it[totalMet] = 0
                        it[createdBy] = uid
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                }
                call.created(
                    PtmActiveEventDto(
                        id = newId.toString(),
                        title = req.title,
                        date = req.date,
                        slot = req.slot,
                        expectedParents = req.expectedParents,
                        checkedInParents = 0,
                        invitesDelivered = 0,
                        readReceipts = 0
                    ),
                    message = "PTM scheduled"
                )
            }

            // -------- UPDATE METRICS (check-ins / invites / read receipts) --------
            patch("/{id}/metrics") {
                val ctx = call.requireSchoolContext() ?: return@patch
                val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: run { call.fail("Invalid id"); return@patch }
                val req = call.receive<UpdatePtmMetricsDto>()
                listOfNotNull(req.checkedInParents, req.invitesDelivered, req.readReceipts)
                    .firstOrNull { it < 0 }?.let { call.fail("metric values cannot be negative"); return@patch }

                val n = dbQuery {
                    if (!ptmOwnedBySchool(id, ctx.schoolId)) return@dbQuery 0
                    PtmEventsTable.update({ PtmEventsTable.id eq id }) {
                        req.checkedInParents?.let { v -> it[checkedInParents] = v }
                        req.invitesDelivered?.let { v -> it[invitesDelivered] = v }
                        req.readReceipts?.let { v -> it[readReceipts] = v }
                        it[updatedAt] = Instant.now()
                    }
                }
                if (n == 0) call.fail("PTM event not found", HttpStatusCode.NotFound)
                else call.okMessage("PTM metrics updated")
            }

            // -------- UPSERT CLASS PROGRESS --------
            put("/{id}/class-progress") {
                val ctx = call.requireSchoolContext() ?: return@put
                val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: run { call.fail("Invalid id"); return@put }
                val req = call.receive<UpsertClassProgressDto>()
                if (req.className.isBlank()) { call.fail("class_name is required"); return@put }
                if (req.metCount < 0 || req.totalCount < 0) { call.fail("counts cannot be negative"); return@put }
                if (req.metCount > req.totalCount) { call.fail("met_count cannot exceed total_count"); return@put }

                val ok = dbQuery {
                    if (!ptmOwnedBySchool(id, ctx.schoolId)) return@dbQuery false
                    val now = Instant.now()
                    val existing = PtmClassProgressTable.selectAll()
                        .where {
                            (PtmClassProgressTable.ptmEventId eq id) and
                                (PtmClassProgressTable.className eq req.className)
                        }
                        .singleOrNull()
                    if (existing == null) {
                        PtmClassProgressTable.insert {
                            it[ptmEventId] = id
                            it[className] = req.className
                            it[teacherName] = req.teacherName
                            it[metCount] = req.metCount
                            it[totalCount] = req.totalCount
                            it[updatedAt] = now
                        }
                    } else {
                        PtmClassProgressTable.update({
                            (PtmClassProgressTable.ptmEventId eq id) and
                                (PtmClassProgressTable.className eq req.className)
                        }) {
                            if (req.teacherName.isNotBlank()) it[teacherName] = req.teacherName
                            it[metCount] = req.metCount
                            it[totalCount] = req.totalCount
                            it[updatedAt] = now
                        }
                    }
                    true
                }
                if (!ok) call.fail("PTM event not found", HttpStatusCode.NotFound)
                else call.okMessage("Class progress updated")
            }

            // -------- COMPLETE (finalize turnout / total_met) --------
            post("/{id}/complete") {
                val ctx = call.requireSchoolContext() ?: return@post
                val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: run { call.fail("Invalid id"); return@post }

                val n = dbQuery {
                    val ev = PtmEventsTable.selectAll()
                        .where { (PtmEventsTable.id eq id) and (PtmEventsTable.schoolId eq ctx.schoolId) }
                        .singleOrNull()
                        ?: return@dbQuery 0
                    // total_met = sum of met_count across class progress rows.
                    val totalMetSum = PtmClassProgressTable.selectAll()
                        .where { PtmClassProgressTable.ptmEventId eq id }
                        .sumOf { it[PtmClassProgressTable.metCount] }
                    val checkedIn = ev[PtmEventsTable.checkedInParents]
                    val expected = ev[PtmEventsTable.expectedParents]
                    val turnoutPct = if (expected <= 0) 0 else (checkedIn * 100) / expected
                    PtmEventsTable.update({ PtmEventsTable.id eq id }) {
                        it[turnout] = turnoutPct
                        it[totalMet] = totalMetSum
                        it[updatedAt] = Instant.now()
                    }
                }
                if (n == 0) call.fail("PTM event not found", HttpStatusCode.NotFound)
                else call.okMessage("PTM marked complete")
            }
        }
    }
}
