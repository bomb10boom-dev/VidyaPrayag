/*
 * File: ParentLinkRouting.kt
 * Module: feature.parent
 *
 * Endpoints (Module: Link Your Child wizard — report §5.3, SWEEP-A):
 *   GET  /api/v1/parent/schools/search?q=     (JWT — step 2: match a school by name)
 *   POST /api/v1/parent/link-child            (JWT — step 3: link a child by roll/admission no.)
 *
 * Replaces the MockV2-driven ParentLinkChildScreenV2 wizard with real data:
 *   - step 2 searches active schools by name substring (SchoolsTable);
 *   - step 3 resolves the school's canonical student (StudentsTable) by
 *     (school_id, roll_number) and links the parent to that child by creating
 *     / updating a ChildrenTable row carrying the resolved student_code.
 *
 * Spec ref: parent_api_spec.artifact.md §Module: Child Onboarding (linking).
 */
package com.littlebridge.enrollplus.feature.parent

import com.littlebridge.enrollplus.core.created
import com.littlebridge.enrollplus.core.fail
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.principalUserId
import com.littlebridge.enrollplus.core.requireSchoolAdmin
import com.littlebridge.enrollplus.db.AppUsersTable
import com.littlebridge.enrollplus.db.ChildrenTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.ParentChildLinksTable
import com.littlebridge.enrollplus.db.SchoolsTable
import com.littlebridge.enrollplus.db.StudentsTable
import com.littlebridge.enrollplus.feature.notifications.Notify
import com.littlebridge.enrollplus.feature.notifications.NotifyRecipients
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

// ---------- DTOs ----------

@Serializable
data class SchoolMatchDto(
    val id: String,
    val name: String,
    val board: String,
    val city: String,
    @SerialName("logo_url") val logoUrl: String? = null
)

@Serializable
data class SchoolSearchResponse(
    val schools: List<SchoolMatchDto>
)

@Serializable
data class LinkChildRequest(
    @SerialName("school_id") val schoolId: String,
    @SerialName("roll_number") val rollNumber: String,
    // Optional — parent's preferred contact name/language captured in step 1.
    @SerialName("parent_name") val parentName: String? = null
)

@Serializable
data class LinkedChildDto(
    @SerialName("child_id") val childId: String,
    @SerialName("child_name") val childName: String,
    @SerialName("class_name") val className: String,
    val roll: String,
    @SerialName("school_name") val schoolName: String,
    @SerialName("profile_photo_url") val profilePhotoUrl: String? = null,
    // RA-48: linking is now request→approve. "pending" until a school admin approves;
    // "approved" carries a real child_id. Defaulted so older clients still parse.
    val status: String = "approved",
)

// ---- RA-48 admin-side DTOs (school admin vetting the link queue) ----

@Serializable
data class LinkRequestDto(
    val id: String,
    @SerialName("parent_id") val parentId: String,
    @SerialName("parent_name") val parentName: String? = null,
    @SerialName("parent_phone") val parentPhone: String? = null,
    @SerialName("student_code") val studentCode: String? = null,
    @SerialName("roll_number") val rollNumber: String? = null,
    @SerialName("child_name") val childName: String? = null,
    @SerialName("class_name") val className: String? = null,
    val status: String,
    @SerialName("requested_at") val requestedAt: String,
)

@Serializable
data class LinkRequestListResponse(
    val requests: List<LinkRequestDto>
)

// ---------- routing ----------

fun Route.parentLinkRouting() {
    authenticate("jwt") {
        route("/api/v1/parent") {

            // ---- GET /schools/search?q= ----
            // Step 2 of the link wizard: match active schools by name substring.
            get("/schools/search") {
                call.principalUserId() ?: run {
                    call.fail("Invalid token", HttpStatusCode.Unauthorized); return@get
                }
                val query = call.request.queryParameters["q"]?.trim().orEmpty()
                val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 10).coerceIn(1, 50)

                val matches = dbQuery {
                    SchoolsTable.selectAll()
                        .where {
                            val active = SchoolsTable.isActive eq true
                            if (query.isBlank()) {
                                active
                            } else {
                                active and (SchoolsTable.name.lowerCase() like "%${query.lowercase()}%")
                            }
                        }
                        .orderBy(SchoolsTable.name, SortOrder.ASC)
                        .limit(limit)
                        .map { row ->
                            SchoolMatchDto(
                                id = row[SchoolsTable.id].value.toString(),
                                name = row[SchoolsTable.name],
                                board = row[SchoolsTable.board],
                                city = row[SchoolsTable.city],
                                logoUrl = row[SchoolsTable.logoUrl]
                            )
                        }
                }
                call.ok(SchoolSearchResponse(schools = matches), message = "Found ${matches.size} school(s)")
            }

            // ---- POST /link-child ----
            // RA-48: step 3 now creates a PENDING parent_child_links request that a
            // school admin must approve — a parent can no longer self-link to any
            // roll number. RA-03: a per-parent+school probe throttle limits roll-
            // number guessing (max pending requests per school per parent).
            post("/link-child") {
                val uid = call.principalUserId()?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: run {
                    call.fail("Invalid token", HttpStatusCode.Unauthorized); return@post
                }
                val req = runCatching { call.receive<LinkChildRequest>() }.getOrNull() ?: run {
                    call.fail("Invalid request body"); return@post
                }
                val schoolUuid = runCatching { UUID.fromString(req.schoolId) }.getOrNull() ?: run {
                    call.fail("Invalid school_id"); return@post
                }
                if (req.rollNumber.isBlank()) {
                    call.fail("roll_number is required"); return@post
                }

                val result = dbQuery {
                    // 1) Verify school exists & is active.
                    val schoolRow = SchoolsTable.selectAll()
                        .where { (SchoolsTable.id eq schoolUuid) and (SchoolsTable.isActive eq true) }
                        .singleOrNull() ?: return@dbQuery LinkResult.SchoolNotFound
                    val schoolNameVal = schoolRow[SchoolsTable.name]

                    // RA-03 throttle: cap outstanding pending requests this parent has
                    // against this school, so they cannot brute-force roll numbers.
                    val pendingCount = ParentChildLinksTable.selectAll().where {
                        (ParentChildLinksTable.parentId eq uid) and
                            (ParentChildLinksTable.schoolId eq schoolUuid) and
                            (ParentChildLinksTable.status eq "pending")
                    }.count()
                    if (pendingCount >= MAX_PENDING_LINKS_PER_SCHOOL) {
                        return@dbQuery LinkResult.Throttled
                    }

                    // 2) Resolve the canonical student by (school, roll/admission no.).
                    // FIX (link-child 404): the previous implementation did an exact
                    // text `rollNumber eq input` + `singleOrNull()`. That failed for
                    // EVERY input because (a) rolls are stored as plain digits ('1')
                    // so '001'/'01' never matched, (b) the admission/student code
                    // (e.g. 'S1-G3A-001') was never checked, and (c) `singleOrNull()`
                    // silently returns null when SEVERAL classes share the same roll
                    // number (roll '1' exists in 3-A, 3-B, 5-A, 8-A…) — so even the
                    // correct plain roll resolved to "not found".
                    //
                    // Resolution order now:
                    //   1. exact admission/student code match (unique — always wins);
                    //   2. roll-number match with numeric normalisation ('001' ≡ '1');
                    //      if it is ambiguous across classes we tell the parent to use
                    //      the full admission number instead of lying with a 404.
                    // School rosters are small (≤ a few hundred rows), so the scoped
                    // fetch + in-memory normalisation mirrors the SchoolStudentsRouting
                    // search style and stays Postgres/SQLite-portable.
                    val rollInput = req.rollNumber.trim()
                    val roster = StudentsTable.selectAll()
                        .where {
                            (StudentsTable.schoolId eq schoolUuid) and
                                (StudentsTable.isActive eq true)
                        }
                        .toList()

                    fun normaliseRoll(v: String): String {
                        val t = v.trim()
                        // strip leading zeros for purely numeric rolls: '001' → '1'
                        return if (t.all { it.isDigit() } && t.isNotEmpty()) {
                            t.trimStart('0').ifEmpty { "0" }
                        } else t.lowercase()
                    }

                    val byCode = roster.filter {
                        it[StudentsTable.studentCode].equals(rollInput, ignoreCase = true)
                    }
                    val matches = if (byCode.isNotEmpty()) byCode else {
                        val wanted = normaliseRoll(rollInput)
                        roster.filter { normaliseRoll(it[StudentsTable.rollNumber]) == wanted }
                    }

                    val studentRow = when {
                        matches.isEmpty() -> return@dbQuery LinkResult.StudentNotFound
                        matches.size > 1 -> return@dbQuery LinkResult.AmbiguousRoll(matches.size)
                        else -> matches.single()
                    }

                    val studentCodeVal = studentRow[StudentsTable.studentCode]
                    val childNameVal = studentRow[StudentsTable.fullName]
                    val classNameVal = studentRow[StudentsTable.className]
                    val photoVal = studentRow[StudentsTable.profilePhotoUrl]
                    val now = Instant.now()

                    // If a link (pending or approved) already exists, don't duplicate.
                    val existingLink = ParentChildLinksTable.selectAll().where {
                        (ParentChildLinksTable.parentId eq uid) and
                            (ParentChildLinksTable.schoolId eq schoolUuid) and
                            (ParentChildLinksTable.studentCode eq studentCodeVal) and
                            ((ParentChildLinksTable.status eq "pending") or (ParentChildLinksTable.status eq "approved"))
                    }.singleOrNull()

                    val linkId: UUID = if (existingLink != null) {
                        existingLink[ParentChildLinksTable.id].value
                    } else {
                        val newId = UUID.randomUUID()
                        ParentChildLinksTable.insert {
                            it[id] = newId
                            it[parentId] = uid
                            it[schoolId] = schoolUuid
                            // Store the school's CANONICAL roll (not the raw parent
                            // input like '001' / 'S1-G3A-001') so the admin queue
                            // displays exactly what the roster shows.
                            it[rollNumber] = studentRow[StudentsTable.rollNumber]
                            it[studentCode] = studentCodeVal
                            it[childName] = childNameVal
                            it[status] = "pending"
                            it[requestedAt] = now
                        }
                        newId
                    }

                    LinkResult.Pending(
                        linkId = linkId,
                        child = LinkedChildDto(
                            childId = "",                 // no child row until approved
                            childName = childNameVal,
                            className = classNameVal,
                            roll = studentRow[StudentsTable.rollNumber],
                            schoolName = schoolNameVal,
                            profilePhotoUrl = photoVal,
                            status = "pending",
                        ),
                    )
                }

                when (result) {
                    is LinkResult.SchoolNotFound -> call.fail("School not found", HttpStatusCode.NotFound)
                    is LinkResult.StudentNotFound ->
                        call.fail(
                            "No student found with that roll/admission number at this school. " +
                                "Try the full admission number printed on the school ID (e.g. S1-G3A-001).",
                            HttpStatusCode.NotFound
                        )
                    is LinkResult.AmbiguousRoll ->
                        call.fail(
                            "${result.count} students at this school share that roll number across different classes. " +
                                "Please enter the full admission number instead (e.g. S1-G3A-001).",
                            HttpStatusCode.Conflict,
                            "ROLL_AMBIGUOUS"
                        )
                    is LinkResult.Throttled ->
                        call.fail("You have too many pending link requests for this school. Please wait for them to be reviewed.", HttpStatusCode.TooManyRequests, "LINK_THROTTLED")
                    is LinkResult.Pending -> {
                        // RA-48 + RA-41: notify the school admins that a parent wants to link.
                        val admins = NotifyRecipients.adminsInSchool(schoolUuid)
                        if (admins.isNotEmpty()) {
                            Notify.toUsers(
                                userIds = admins,
                                category = "link_request",
                                title = "New child-link request",
                                body = "A parent requested to link to roll ${req.rollNumber}.",
                                schoolId = schoolUuid,
                                actorId = uid,
                                deepLink = "admin/link-requests",
                                refType = "link_request",
                                refId = result.linkId.toString(),
                            )
                        }
                        call.created(result.child, message = "Link request submitted for approval")
                    }
                }
            }
        }

        // ============================================================
        // RA-48: school-admin link-request queue. A school admin vets the
        // pending parent→child link requests for THEIR school only (the
        // requireSchoolAdmin guard reads school_id from app_users, never the
        // body), then approves (materialises the children row + grants the
        // parent read access) or rejects (no link). Either decision notifies
        // the requesting parent.
        // ============================================================
        route("/api/v1/school") {

            // ---- GET /link-requests?status=pending ----
            // List the link requests for the admin's school. Defaults to pending.
            get("/link-requests") {
                val ctx = call.requireSchoolAdmin() ?: return@get
                val statusFilter = call.request.queryParameters["status"]?.trim()?.lowercase()
                    ?.takeIf { it in setOf("pending", "approved", "rejected") }
                    ?: "pending"

                val rows = dbQuery {
                    // Join the requesting parent for display (name/phone).
                    ParentChildLinksTable.selectAll()
                        .where {
                            (ParentChildLinksTable.schoolId eq ctx.schoolId) and
                                (ParentChildLinksTable.status eq statusFilter)
                        }
                        .orderBy(ParentChildLinksTable.requestedAt, SortOrder.DESC)
                        .map { link ->
                            val parentUuid = link[ParentChildLinksTable.parentId]
                            val parentRow = AppUsersTable.selectAll()
                                .where { AppUsersTable.id eq parentUuid }
                                .singleOrNull()
                            // Enrich with the canonical class from students by code.
                            val code = link[ParentChildLinksTable.studentCode]
                            val classNameVal = code?.let { c ->
                                StudentsTable.selectAll()
                                    .where { (StudentsTable.schoolId eq ctx.schoolId) and (StudentsTable.studentCode eq c) }
                                    .singleOrNull()?.get(StudentsTable.className)
                            }
                            LinkRequestDto(
                                id = link[ParentChildLinksTable.id].value.toString(),
                                parentId = parentUuid.toString(),
                                parentName = parentRow?.get(AppUsersTable.fullName),
                                parentPhone = parentRow?.get(AppUsersTable.phone),
                                studentCode = code,
                                rollNumber = link[ParentChildLinksTable.rollNumber],
                                childName = link[ParentChildLinksTable.childName],
                                className = classNameVal,
                                status = link[ParentChildLinksTable.status],
                                requestedAt = link[ParentChildLinksTable.requestedAt].toString(),
                            )
                        }
                }
                call.ok(LinkRequestListResponse(requests = rows), message = "${rows.size} ${statusFilter} request(s)")
            }

            // ---- POST /link-requests/{id}/approve ----
            // Materialise the children row from the pending link's student_code,
            // mark the link approved, and notify the parent. Idempotent: if the
            // children row already exists for this (parent, student_code) it is
            // reused instead of duplicated.
            post("/link-requests/{id}/approve") {
                val ctx = call.requireSchoolAdmin() ?: return@post
                val linkId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: run {
                    call.fail("Invalid request id"); return@post
                }

                val outcome = dbQuery {
                    val link = ParentChildLinksTable.selectAll()
                        .where {
                            (ParentChildLinksTable.id eq linkId) and
                                (ParentChildLinksTable.schoolId eq ctx.schoolId)
                        }
                        .singleOrNull() ?: return@dbQuery DecisionResult.NotFound
                    if (link[ParentChildLinksTable.status] != "pending") {
                        return@dbQuery DecisionResult.AlreadyDecided(link[ParentChildLinksTable.status])
                    }

                    val parentUuid = link[ParentChildLinksTable.parentId]
                    val code = link[ParentChildLinksTable.studentCode]
                        ?: return@dbQuery DecisionResult.NotFound

                    // Re-resolve the canonical student so we copy the latest truth
                    // (name/class/photo) into the parent-facing children row.
                    val studentRow = StudentsTable.selectAll()
                        .where {
                            (StudentsTable.schoolId eq ctx.schoolId) and
                                (StudentsTable.studentCode eq code) and
                                (StudentsTable.isActive eq true)
                        }
                        .singleOrNull() ?: return@dbQuery DecisionResult.NotFound

                    val childNameVal = studentRow[StudentsTable.fullName]
                    val classNameVal = studentRow[StudentsTable.className]
                    val photoVal = studentRow[StudentsTable.profilePhotoUrl]
                    val now = Instant.now()

                    // Idempotent upsert of the children row for (parent, code).
                    val existingChild = ChildrenTable.selectAll()
                        .where {
                            (ChildrenTable.parentId eq parentUuid) and
                                (ChildrenTable.schoolId eq ctx.schoolId) and
                                (ChildrenTable.studentCode eq code)
                        }
                        .singleOrNull()

                    val childUuid: UUID = if (existingChild != null) {
                        val cid = existingChild[ChildrenTable.id].value
                        ChildrenTable.update({ ChildrenTable.id eq cid }) {
                            it[childName] = childNameVal
                            it[currentGrade] = classNameVal
                            it[profilePic] = photoVal
                            it[isActive] = true
                            it[updatedAt] = now
                        }
                        cid
                    } else {
                        val newId = UUID.randomUUID()
                        ChildrenTable.insert {
                            it[id] = newId
                            it[parentId] = parentUuid
                            it[schoolId] = ctx.schoolId
                            it[studentCode] = code
                            it[childName] = childNameVal
                            it[currentGrade] = classNameVal
                            it[profilePic] = photoVal
                            it[isActive] = true
                            it[createdAt] = now
                            it[updatedAt] = now
                        }
                        newId
                    }

                    // Flip the link → approved + audit who/when.
                    ParentChildLinksTable.update({ ParentChildLinksTable.id eq linkId }) {
                        it[status] = "approved"
                        it[childId] = childUuid
                        it[actionedBy] = ctx.userId
                        it[actionedAt] = now
                    }

                    // RA-SP: parent linking rule — a student may have MULTIPLE
                    // parents but AT MOST ONE primary guardian. Enforce it via the
                    // centralized aggregation service: if no approved link for this
                    // student is primary yet, promote the one we just approved;
                    // otherwise leave the existing primary intact.
                    val hasPrimary = ParentChildLinksTable.selectAll().where {
                        (ParentChildLinksTable.schoolId eq ctx.schoolId) and
                            (ParentChildLinksTable.studentCode eq code) and
                            (ParentChildLinksTable.status eq "approved") and
                            (ParentChildLinksTable.isPrimaryGuardian eq true)
                    }.any()
                    com.littlebridge.enrollplus.feature.school.StudentAggregationService
                        .enforceSinglePrimaryGuardian(
                            schoolId = ctx.schoolId,
                            studentCode = code,
                            primaryLinkId = if (hasPrimary) {
                                ParentChildLinksTable.selectAll().where {
                                    (ParentChildLinksTable.schoolId eq ctx.schoolId) and
                                        (ParentChildLinksTable.studentCode eq code) and
                                        (ParentChildLinksTable.status eq "approved") and
                                        (ParentChildLinksTable.isPrimaryGuardian eq true)
                                }.first()[ParentChildLinksTable.id].value
                            } else linkId
                        )

                    // A parent who just got their first child approved has completed
                    // their onboarding — flip profile_completed so the app routes to
                    // the dashboard instead of the link wizard.
                    AppUsersTable.update({ AppUsersTable.id eq parentUuid }) {
                        it[profileCompleted] = true
                        it[updatedAt] = now
                    }

                    DecisionResult.Approved(parentUuid, childUuid, childNameVal)
                }

                when (outcome) {
                    is DecisionResult.NotFound -> call.fail("Link request not found", HttpStatusCode.NotFound)
                    is DecisionResult.AlreadyDecided ->
                        call.fail("This request was already ${outcome.status}", HttpStatusCode.Conflict, "ALREADY_DECIDED")
                    is DecisionResult.Approved -> {
                        Notify.toUser(
                            userId = outcome.parentId,
                            category = "link_request",
                            title = "Child link approved",
                            body = "${outcome.childName} has been linked to your account.",
                            schoolId = ctx.schoolId,
                            actorId = ctx.userId,
                            deepLink = "parent/dashboard",
                            refType = "link_request",
                            refId = linkId.toString(),
                        )
                        call.ok(mapOf("child_id" to outcome.childId.toString(), "status" to "approved"), message = "Link approved")
                    }
                    else -> call.fail("Unexpected outcome", HttpStatusCode.InternalServerError)
                }
            }

            // ---- POST /link-requests/{id}/reject ----
            // Mark the link rejected (no children row created) and notify the parent.
            post("/link-requests/{id}/reject") {
                val ctx = call.requireSchoolAdmin() ?: return@post
                val linkId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: run {
                    call.fail("Invalid request id"); return@post
                }

                val outcome = dbQuery {
                    val link = ParentChildLinksTable.selectAll()
                        .where {
                            (ParentChildLinksTable.id eq linkId) and
                                (ParentChildLinksTable.schoolId eq ctx.schoolId)
                        }
                        .singleOrNull() ?: return@dbQuery DecisionResult.NotFound
                    if (link[ParentChildLinksTable.status] != "pending") {
                        return@dbQuery DecisionResult.AlreadyDecided(link[ParentChildLinksTable.status])
                    }
                    val parentUuid = link[ParentChildLinksTable.parentId]
                    val childNameVal = link[ParentChildLinksTable.childName]
                    val now = Instant.now()
                    ParentChildLinksTable.update({ ParentChildLinksTable.id eq linkId }) {
                        it[status] = "rejected"
                        it[actionedBy] = ctx.userId
                        it[actionedAt] = now
                    }
                    DecisionResult.Rejected(parentUuid, childNameVal)
                }

                when (outcome) {
                    is DecisionResult.NotFound -> call.fail("Link request not found", HttpStatusCode.NotFound)
                    is DecisionResult.AlreadyDecided ->
                        call.fail("This request was already ${outcome.status}", HttpStatusCode.Conflict, "ALREADY_DECIDED")
                    is DecisionResult.Rejected -> {
                        Notify.toUser(
                            userId = outcome.parentId,
                            category = "link_request",
                            title = "Child link request declined",
                            body = "Your request to link ${outcome.childName ?: "a child"} was not approved. Please verify the roll number with the school.",
                            schoolId = ctx.schoolId,
                            actorId = ctx.userId,
                            deepLink = "parent/link-child",
                            refType = "link_request",
                            refId = linkId.toString(),
                        )
                        call.ok(mapOf("status" to "rejected"), message = "Link request rejected")
                    }
                    else -> call.fail("Unexpected outcome", HttpStatusCode.InternalServerError)
                }
            }
        }
    }
}

/** RA-03: max outstanding pending link requests a parent may have per school. */
private const val MAX_PENDING_LINKS_PER_SCHOOL = 3

/** RA-48: result of an admin approve/reject so the suspend respond runs outside dbQuery. */
private sealed interface DecisionResult {
    data object NotFound : DecisionResult
    data class AlreadyDecided(val status: String) : DecisionResult
    data class Approved(val parentId: UUID, val childId: UUID, val childName: String) : DecisionResult
    data class Rejected(val parentId: UUID, val childName: String?) : DecisionResult
}

/** Internal result of the link transaction so the suspend respond happens outside dbQuery. */
private sealed interface LinkResult {
    data object SchoolNotFound : LinkResult
    data object StudentNotFound : LinkResult
    /** Several classes share this roll number — the parent must use the unique admission code. */
    data class AmbiguousRoll(val count: Int) : LinkResult
    data object Throttled : LinkResult
    data class Pending(val linkId: UUID, val child: LinkedChildDto) : LinkResult
}
