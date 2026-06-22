/*
 * File: ParentLeaveRouting.kt
 * Module: feature.parent
 *
 * RA-44 — the PARENT leg of the cross-role leave workflow.
 *
 *   POST /api/v1/parent/leave        — a parent applies for an owned child.
 *   GET  /api/v1/parent/leave        — the parent's own submitted requests.
 *
 * Apply resolves the {child_id} the parent owns (RA-56 ownership gate), derives
 * the school + class + section from the child/student linkage, routes the
 * request to the child's class teacher(s) (teacher_subject_assignments), inserts
 * a PENDING leave_requests row carrying parent_id / child_id / teacher_id, and
 * notifies the routed teacher(s). The request is then visible to that teacher
 * (TeacherLeaveRouting) and overridable by an admin (LeaveRequestsRouting).
 *
 * MULTI-TENANCY: the school_id written is the CHILD's school_id (derived from a
 * row already proven to belong to the calling parent) — never from the body.
 */
package com.littlebridge.enrollplus.feature.parent

import com.littlebridge.enrollplus.core.created
import com.littlebridge.enrollplus.core.fail
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.principalUserUuid
import com.littlebridge.enrollplus.db.ChildrenTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.LeaveRequestsTable
import com.littlebridge.enrollplus.db.StudentsTable
import com.littlebridge.enrollplus.db.TeacherSubjectAssignmentsTable
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
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// ── DTOs ─────────────────────────────────────────────────────────────────────

@Serializable
data class CreateParentLeaveDto(
    @SerialName("child_id") val childId: String,
    @SerialName("date_from") val dateFrom: String, // YYYY-MM-DD
    @SerialName("date_to") val dateTo: String,      // YYYY-MM-DD
    val reason: String,
    @SerialName("image_url") val imageUrl: String? = null,
)

@Serializable
data class ParentLeaveDto(
    val id: String,
    @SerialName("child_name") val childName: String,
    @SerialName("date_from") val dateFrom: String,
    @SerialName("date_to") val dateTo: String,
    val reason: String,
    @SerialName("image_url") val imageUrl: String? = null,
    val status: String,
)

@Serializable
data class ParentLeaveListResponse(val requests: List<ParentLeaveDto> = emptyList())

private fun org.jetbrains.exposed.sql.ResultRow.toParentLeaveDto() = ParentLeaveDto(
    id = this[LeaveRequestsTable.id].value.toString(),
    childName = this[LeaveRequestsTable.requesterName],
    dateFrom = this[LeaveRequestsTable.dateFrom],
    dateTo = this[LeaveRequestsTable.dateTo],
    reason = this[LeaveRequestsTable.reason],
    imageUrl = this[LeaveRequestsTable.imageUrl],
    status = this[LeaveRequestsTable.status],
)

/** Validates a YYYY-MM-DD pair: both parseable and from <= to. */
private fun validateParentLeaveDates(from: String, to: String): String? {
    val a = runCatching { LocalDate.parse(from) }.getOrNull()
        ?: return "date_from must be a valid YYYY-MM-DD date"
    val b = runCatching { LocalDate.parse(to) }.getOrNull()
        ?: return "date_to must be a valid YYYY-MM-DD date"
    if (b.isBefore(a)) return "date_to cannot be before date_from"
    return null
}

fun Route.parentLeaveRouting() {
    authenticate("jwt") {
        route("/api/v1/parent/leave") {

            // -------- LIST own requests --------
            get {
                val uid = call.principalUserUuid() ?: run {
                    call.fail("Invalid token", HttpStatusCode.Unauthorized, "UNAUTHORIZED"); return@get
                }
                val rows = dbQuery {
                    LeaveRequestsTable.selectAll()
                        .where { LeaveRequestsTable.parentId eq uid }
                        .orderBy(LeaveRequestsTable.createdAt, SortOrder.DESC)
                        .map { it.toParentLeaveDto() }
                }
                call.ok(ParentLeaveListResponse(requests = rows), message = "Leave requests loaded")
            }

            // -------- APPLY for an owned child --------
            post {
                val uid = call.principalUserUuid() ?: run {
                    call.fail("Invalid token", HttpStatusCode.Unauthorized, "UNAUTHORIZED"); return@post
                }
                val req = call.receive<CreateParentLeaveDto>()
                val childId = runCatching { UUID.fromString(req.childId) }.getOrNull() ?: run {
                    call.fail("A valid child_id is required", HttpStatusCode.BadRequest, "BAD_CHILD_ID"); return@post
                }
                validateParentLeaveDates(req.dateFrom, req.dateTo)?.let { call.fail(it); return@post }
                if (req.reason.isBlank()) { call.fail("reason is required"); return@post }

                // RA-56 ownership gate: the child must belong to the caller and be active.
                val child = dbQuery {
                    ChildrenTable.selectAll().where {
                        (ChildrenTable.id eq childId) and
                            (ChildrenTable.parentId eq uid) and
                            (ChildrenTable.isActive eq true)
                    }.singleOrNull()
                } ?: run {
                    call.fail("Child not found", HttpStatusCode.NotFound, "CHILD_NOT_FOUND"); return@post
                }

                val schoolId = child[ChildrenTable.schoolId] ?: run {
                    // Not yet linked to a school → there is no teacher to route to.
                    call.fail("This child is not linked to a school yet", HttpStatusCode.Conflict, "CHILD_UNLINKED")
                    return@post
                }
                val childName = child[ChildrenTable.childName]
                val studentCode = child[ChildrenTable.studentCode]

                // Authoritative class+section from the linked student row when present,
                // else the parent-typed grade as a best-effort fallback.
                val student = if (studentCode != null) dbQuery {
                    StudentsTable.selectAll().where { StudentsTable.studentCode eq studentCode }.singleOrNull()
                } else null
                val className = student?.get(StudentsTable.className) ?: child[ChildrenTable.currentGrade]
                val section = student?.get(StudentsTable.section) ?: "A"

                // Route to the child's class teacher(s): every active assignment for
                // (school, class, section). Distinct teacher ids; first one is stored
                // as the primary teacher_id for the teacher-side list filter.
                val teacherIds: List<UUID> = if (className != null) dbQuery {
                    TeacherSubjectAssignmentsTable.selectAll().where {
                        (TeacherSubjectAssignmentsTable.schoolId eq schoolId) and
                            (TeacherSubjectAssignmentsTable.className eq className) and
                            (TeacherSubjectAssignmentsTable.section eq section) and
                            (TeacherSubjectAssignmentsTable.isActive eq true)
                    }.mapNotNull { it[TeacherSubjectAssignmentsTable.teacherId] }.distinct()
                } else emptyList()
                val primaryTeacher = teacherIds.firstOrNull()

                val newId = UUID.randomUUID()
                val now = Instant.now()
                dbQuery {
                    LeaveRequestsTable.insert {
                        it[LeaveRequestsTable.id] = newId
                        it[LeaveRequestsTable.schoolId] = schoolId
                        it[requesterId] = uid
                        it[requesterName] = childName
                        it[requesterRole] = "student"
                        it[dateFrom] = req.dateFrom
                        it[dateTo] = req.dateTo
                        it[reason] = req.reason
                        it[imageUrl] = req.imageUrl
                        it[status] = "Pending"
                        it[LeaveRequestsTable.className] = className
                        it[LeaveRequestsTable.section] = section
                        it[teacherId] = primaryTeacher
                        it[LeaveRequestsTable.childId] = childId
                        it[parentId] = uid
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                }

                // RA-44 + RA-41: notify the routed class teacher(s). When no teacher is
                // assigned to the class yet, the request still lands in the admin queue.
                if (teacherIds.isNotEmpty()) {
                    Notify.toUsers(
                        userIds = teacherIds,
                        category = "leave",
                        title = "New leave request",
                        body = "$childName has a pending leave request for your review.",
                        schoolId = schoolId,
                        actorId = uid,
                        deepLink = "teacher/leave-requests",
                        refType = "leave_request",
                        refId = newId.toString(),
                    )
                }

                call.created(
                    ParentLeaveDto(
                        id = newId.toString(),
                        childName = childName,
                        dateFrom = req.dateFrom,
                        dateTo = req.dateTo,
                        reason = req.reason,
                        imageUrl = req.imageUrl,
                        status = "Pending",
                    ),
                    message = "Leave request submitted",
                )
            }
        }
    }
}
