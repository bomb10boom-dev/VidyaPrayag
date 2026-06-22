/*
 * File: TeacherLeaveRouting.kt
 * Module: feature.teacher
 *
 * RA-44 — the TEACHER leg of the cross-role leave workflow.
 *
 *   GET   /api/v1/teacher/leave-requests?status=Pending|Approved|Rejected
 *   PATCH /api/v1/teacher/leave-requests/{id}   { "status": "Approved"|"Rejected" }
 *
 * A teacher sees leave requests routed to THEIR classes only — either directly
 * addressed (leave_requests.teacher_id == me) or for a (class, section) the
 * teacher is assigned to (teacher_subject_assignments). school_admin / admin
 * see every request in their school (they stand in for any teacher). Deciding
 * a request notifies the applicant parent. An admin can still override later
 * via the school leave-requests endpoint (LeaveRequestsRouting).
 *
 * MULTI-TENANCY: every query is scoped to ctx.schoolId (derived from the JWT in
 * requireTeacherContext) and a teacher can only act on requests inside the set
 * of classes they own — never another teacher's or another school's.
 */
package com.littlebridge.enrollplus.feature.teacher

import com.littlebridge.enrollplus.core.fail
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.okMessage
import com.littlebridge.enrollplus.core.requireTeacherContext
import com.littlebridge.enrollplus.core.teacherAssignmentsFor
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.LeaveRequestsTable
import com.littlebridge.enrollplus.feature.notifications.Notify
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

// ── DTOs ─────────────────────────────────────────────────────────────────────

@Serializable
data class TeacherLeaveDto(
    val id: String,
    @SerialName("student_name") val studentName: String,
    @SerialName("class_name") val className: String? = null,
    val section: String? = null,
    @SerialName("date_from") val dateFrom: String,
    @SerialName("date_to") val dateTo: String,
    val reason: String,
    @SerialName("image_url") val imageUrl: String? = null,
    val status: String,
)

@Serializable
data class TeacherLeaveListResponse(
    @SerialName("pending_count") val pendingCount: Int,
    val requests: List<TeacherLeaveDto> = emptyList(),
)

@Serializable
data class TeacherLeaveDecisionDto(val status: String)

private fun org.jetbrains.exposed.sql.ResultRow.toTeacherLeaveDto() = TeacherLeaveDto(
    id = this[LeaveRequestsTable.id].value.toString(),
    studentName = this[LeaveRequestsTable.requesterName],
    className = this[LeaveRequestsTable.className],
    section = this[LeaveRequestsTable.section],
    dateFrom = this[LeaveRequestsTable.dateFrom],
    dateTo = this[LeaveRequestsTable.dateTo],
    reason = this[LeaveRequestsTable.reason],
    imageUrl = this[LeaveRequestsTable.imageUrl],
    status = this[LeaveRequestsTable.status],
)

fun Route.teacherLeaveRouting() {
    authenticate("jwt") {
        route("/api/v1/teacher/leave-requests") {

            // -------- LIST (routed to my classes) --------
            get {
                val ctx = call.requireTeacherContext() ?: return@get
                val statusParam = call.request.queryParameters["status"]?.trim()

                // The (class, section) pairs this teacher owns — used to match
                // requests that were routed by class rather than a specific id.
                val owned = teacherAssignmentsFor(ctx)
                val ownedPairs = owned.map { it.className to it.section }.toSet()
                val privileged = ctx.role == "school_admin" || ctx.role == "admin"

                val all = dbQuery {
                    LeaveRequestsTable.selectAll()
                        .where {
                            (LeaveRequestsTable.schoolId eq ctx.schoolId) and
                                (LeaveRequestsTable.requesterRole eq "student")
                        }
                        .orderBy(LeaveRequestsTable.createdAt, SortOrder.DESC)
                        .toList()
                }

                val mine = all.filter { row ->
                    if (privileged) return@filter true
                    val direct = row[LeaveRequestsTable.teacherId] == ctx.userId
                    val byClass = (row[LeaveRequestsTable.className] to row[LeaveRequestsTable.section]) in ownedPairs
                    direct || byClass
                }

                val filtered = mine.filter { row ->
                    statusParam == null || row[LeaveRequestsTable.status].equals(statusParam, true)
                }
                val pendingCount = mine.count { it[LeaveRequestsTable.status].equals("Pending", true) }

                call.ok(
                    TeacherLeaveListResponse(
                        pendingCount = pendingCount,
                        requests = filtered.map { it.toTeacherLeaveDto() },
                    ),
                    message = "Leave requests loaded",
                )
            }

            // -------- DECIDE (approve / reject) --------
            patch("/{id}") {
                val ctx = call.requireTeacherContext() ?: return@patch
                val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: run {
                    call.fail("Invalid id", HttpStatusCode.BadRequest, "BAD_ID"); return@patch
                }
                val body = call.receive<TeacherLeaveDecisionDto>()
                val normalized = when (body.status.trim().lowercase()) {
                    "approved" -> "Approved"
                    "rejected" -> "Rejected"
                    else -> { call.fail("status must be Approved|Rejected"); return@patch }
                }

                val owned = teacherAssignmentsFor(ctx)
                val ownedPairs = owned.map { it.className to it.section }.toSet()
                val privileged = ctx.role == "school_admin" || ctx.role == "admin"

                // Read the row (school-scoped) and verify it is routed to this teacher
                // before mutating — a teacher may only decide requests for their classes.
                val decided = dbQuery {
                    val row = LeaveRequestsTable.selectAll().where {
                        (LeaveRequestsTable.id eq id) and (LeaveRequestsTable.schoolId eq ctx.schoolId)
                    }.singleOrNull() ?: return@dbQuery LeaveDecideResult.NotFound

                    val direct = row[LeaveRequestsTable.teacherId] == ctx.userId
                    val byClass = (row[LeaveRequestsTable.className] to row[LeaveRequestsTable.section]) in ownedPairs
                    if (!privileged && !direct && !byClass) return@dbQuery LeaveDecideResult.Forbidden

                    LeaveRequestsTable.update({
                        (LeaveRequestsTable.id eq id) and (LeaveRequestsTable.schoolId eq ctx.schoolId)
                    }) {
                        it[status] = normalized
                        it[actionedBy] = ctx.userId
                        it[actionedAt] = Instant.now()
                        it[updatedAt] = Instant.now()
                    }
                    LeaveDecideResult.Ok(
                        parentId = row[LeaveRequestsTable.parentId],
                        requesterName = row[LeaveRequestsTable.requesterName],
                    )
                }

                when (decided) {
                    LeaveDecideResult.NotFound ->
                        call.fail("Leave request not found", HttpStatusCode.NotFound, "NOT_FOUND")
                    LeaveDecideResult.Forbidden ->
                        call.fail("This leave request is not assigned to your classes", HttpStatusCode.Forbidden, "FORBIDDEN")
                    is LeaveDecideResult.Ok -> {
                        // RA-44 + RA-41: tell the applicant parent the outcome.
                        val verb = if (normalized == "Approved") "approved" else "rejected"
                        decided.parentId?.let { pid ->
                            Notify.toUser(
                                userId = pid,
                                category = "leave",
                                title = "Leave request $verb",
                                body = "${decided.requesterName}'s leave request was $verb by ${ctx.fullName}.",
                                schoolId = ctx.schoolId,
                                actorId = ctx.userId,
                                deepLink = "parent/leave",
                                refType = "leave_request",
                                refId = id.toString(),
                            )
                        }
                        call.okMessage("Leave request $verb")
                    }
                }
            }
        }
    }
}

/** Result of a teacher leave decision, so notify happens outside the txn. */
private sealed interface LeaveDecideResult {
    data object NotFound : LeaveDecideResult
    data object Forbidden : LeaveDecideResult
    data class Ok(val parentId: UUID?, val requesterName: String) : LeaveDecideResult
}
