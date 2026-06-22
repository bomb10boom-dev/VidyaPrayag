/*
 * File: TeacherProvisioningRouting.kt
 * Module: feature.school
 *
 * Fixes audit finding C (§3.1): there was previously NO code path anywhere that
 * created a `teacher` app_users row, so login could never find a teacher and
 * the entire /api/v1/teacher/... surface + teacher portal were dead in
 * production. This adds a school-admin-only endpoint that provisions a teacher
 * account scoped to the admin's own school.
 *
 * Endpoints (JWT + school-scoped via requireSchoolContext):
 *   POST   /api/v1/school/teachers                     create a teacher app_users row
 *   GET    /api/v1/school/teachers                     list active teachers in the admin's school
 *   DELETE /api/v1/school/teachers/{id}                deactivate (soft-delete) a teacher (RA-22)
 *   POST   /api/v1/school/teachers/{id}/reset-password reissue an initial password (RA-32)
 *
 * A teacher created here can then log in via:
 *   - email + password   (if an email + initial_password were supplied), OR
 *   - phone + OTP         (if a phone identifier was supplied; OTP via /send-otp)
 */
package com.littlebridge.enrollplus.feature.school

import com.littlebridge.enrollplus.core.SCHOOL_ADMIN_ROLES
import com.littlebridge.enrollplus.core.created
import com.littlebridge.enrollplus.core.fail
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.okMessage
import com.littlebridge.enrollplus.core.requireSchoolAdmin
import com.littlebridge.enrollplus.core.requireSchoolContext
import com.littlebridge.enrollplus.db.AppUsersTable
import com.littlebridge.enrollplus.db.AttendanceRecordsTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.DeviceTokensTable
import com.littlebridge.enrollplus.db.FacultyTable
import com.littlebridge.enrollplus.db.NotificationsTable
import com.littlebridge.enrollplus.db.StudentsTable
import com.littlebridge.enrollplus.db.TeacherSubjectAssignmentsTable
import com.littlebridge.enrollplus.db.UserSessionsTable
import com.littlebridge.enrollplus.feature.auth.hashPassword
import com.littlebridge.enrollplus.feature.auth.normaliseIdentifier
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Serializable
data class CreateTeacherDto(
    val name: String,
    val identifier: String,                               // email OR phone
    @SerialName("initial_password") val initialPassword: String? = null
)

@Serializable
data class TeacherAccountDto(
    val id: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val role: String,
    @SerialName("school_id") val schoolId: String
)

@Serializable
data class TeacherListResponse(val teachers: List<TeacherAccountDto>)

// =====================================================================
// Teacher CARD contract — drives the redesigned School-Admin teacher list
// (SchoolPeopleScreenV2 → Teachers sub-tab). Every card is a self-contained
// admin summary so the list needs no follow-up per-row fetches.
// GET /api/v1/school/teachers  →  TeacherCardListResponse
// =====================================================================

@Serializable
data class TeacherCardProfileDto(
    val name: String,
    val avatarUrl: String? = null,
    val role: String,
    val status: String                                    // ACTIVE | INACTIVE
)

@Serializable
data class TeacherCardAcademicAssignmentDto(
    val grades: List<String> = emptyList(),
    val subjects: List<String> = emptyList()
)

@Serializable
data class TeacherCardWorkloadDto(
    val totalClasses: Int = 0,
    val totalStudents: Int = 0
)

@Serializable
data class TeacherCardActivityDto(
    val attendancePercentage: Int? = null,                // null when no data
    val lastActiveAt: String? = null                      // ISO-8601 UTC, or null = never
)

@Serializable
data class TeacherCardActionsDto(
    val canViewProfile: Boolean = true,
    val canAssignClass: Boolean = false,
    val canDeactivate: Boolean = false
)

@Serializable
data class TeacherCardDto(
    val id: String,
    val profile: TeacherCardProfileDto,
    val academicAssignment: TeacherCardAcademicAssignmentDto,
    val workload: TeacherCardWorkloadDto,
    val activity: TeacherCardActivityDto,
    val actions: TeacherCardActionsDto
)

@Serializable
data class TeacherCardPaginationDto(
    val page: Int,
    val pageSize: Int,
    val totalRecords: Int,
    val hasNext: Boolean
)

@Serializable
data class TeacherCardListResponse(
    val teachers: List<TeacherCardDto>,
    val pagination: TeacherCardPaginationDto
)

/**
 * RA-32: response for a credential reset. Carries the freshly-generated
 * plaintext password EXACTLY ONCE so the admin can hand it to the teacher;
 * it is never stored in plaintext server-side (only the hash is persisted).
 */
@Serializable
data class TeacherCredentialDto(
    val id: String,
    val name: String,
    val email: String,
    @SerialName("initial_password") val initialPassword: String
)

private fun isEmail(id: String) = id.contains("@")

/**
 * RA-32: generate a human-readable but high-entropy initial password
 * (no ambiguous chars like 0/O/1/l/I) using [SecureRandom]. ~12 chars from a
 * 56-symbol alphabet ≈ 69 bits of entropy — strong enough for a one-time
 * credential the teacher is expected to change after first login.
 */
private val resetPwRng = SecureRandom()
private fun generateInitialPassword(length: Int = 12): String {
    val alphabet = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789@#%"
    return buildString(length) {
        repeat(length) { append(alphabet[resetPwRng.nextInt(alphabet.length)]) }
    }
}

/**
 * BUGFIX (admin-created teachers were invisible in Supabase `faculty`):
 * onboarding mirrors every provisioned teacher into the `faculty` roster via the
 * SAME `external_id = "U-<userId>"` convention (OnboardingRouting.ensureFacultyRow),
 * but this admin-facing POST /api/v1/school/teachers endpoint only ever wrote an
 * `app_users` row. As a result:
 *   - the teacher never appeared in the `faculty` table the admin was looking at, and
 *   - SchoolAnalyticsRouting (which joins attendance to faculty on external_id) could
 *     never surface that teacher's accountability/efficiency metrics.
 *
 * This helper makes the admin create path mirror onboarding exactly. It is
 * idempotent on external_id, so re-provisioning is safe. MUST run inside dbQuery {}.
 */
private fun ensureFacultyRow(schoolId: UUID, userId: UUID, name: String) {
    val externalId = "U-$userId"
    val exists = FacultyTable.selectAll()
        .where { FacultyTable.externalId eq externalId }
        .firstOrNull()
    if (exists != null) return
    FacultyTable.insert {
        it[FacultyTable.schoolId] = schoolId
        it[FacultyTable.externalId] = externalId
        it[FacultyTable.userId] = userId
        it[FacultyTable.name] = name.trim()
        it[isActive] = true
        it[createdAt] = Instant.now()
    }
}

fun Route.teacherProvisioningRouting() {
    authenticate("jwt") {
        route("/api/v1/school/teachers") {

            // ---- create a teacher account (privileged: RA-39) ----
            post {
                val ctx = call.requireSchoolAdmin() ?: return@post
                val req = runCatching { call.receive<CreateTeacherDto>() }.getOrNull()
                    ?: run { call.fail("Invalid body"); return@post }

                val id = normaliseIdentifier(req.identifier)
                if (req.name.isBlank() || id.isBlank()) {
                    call.fail("name and identifier are required", HttpStatusCode.BadRequest)
                    return@post
                }
                if (isEmail(id) && req.initialPassword.isNullOrBlank()) {
                    call.fail(
                        "initial_password is required when provisioning a teacher by email",
                        HttpStatusCode.BadRequest
                    )
                    return@post
                }

                val existing = dbQuery {
                    AppUsersTable.selectAll()
                        .where { (AppUsersTable.phone eq id) or (AppUsersTable.email eq id) }
                        .firstOrNull()
                }
                if (existing != null) {
                    call.fail("An account with this identifier already exists", HttpStatusCode.Conflict, "USER_EXISTS")
                    return@post
                }

                val newId = UUID.randomUUID()
                val now = Instant.now()
                dbQuery {
                    AppUsersTable.insert {
                        it[AppUsersTable.id] = newId
                        it[fullName] = req.name.trim()
                        it[role] = "teacher"
                        it[schoolId] = ctx.schoolId
                        if (isEmail(id)) {
                            it[email] = id
                            it[passwordHash] = hashPassword(req.initialPassword!!)
                            it[isEmailVerified] = true
                        } else {
                            it[phone] = id
                            it[isPhoneVerified] = true
                        }
                        it[profileCompleted] = false
                        // RA-54: provisioned teachers must change their generated
                        // initial password on first login. This flag is the
                        // server-side gate signal NavGraphV2 reads; the
                        // POST /auth/change-password endpoint clears it.
                        it[mustChangePassword] = true
                        it[isActive] = true
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                    // BUGFIX: mirror the new teacher into the `faculty` roster so it
                    // shows up in Supabase and is visible to SchoolAnalyticsRouting
                    // (which joins attendance → faculty on external_id = "U-<userId>").
                    ensureFacultyRow(ctx.schoolId, newId, req.name)

                    // RA-LINK: a teacher was created → reconcile their workload
                    // through the centralized service so they are immediately linked
                    // to all students in any class+section they already cover. A
                    // freshly-provisioned teacher has no assignments yet (those are
                    // created via the assignment routing, which triggers its own
                    // reconciliation), so this is typically 0 — but funnelling every
                    // teacher-create through the SAME reconciler guarantees the link
                    // graph is never left stale and needs no manual linking.
                    StudentAggregationService.recalcTeacherWorkload(ctx.schoolId, newId)
                }

                call.created(
                    TeacherAccountDto(
                        id = newId.toString(),
                        name = req.name.trim(),
                        email = if (isEmail(id)) id else null,
                        phone = if (!isEmail(id)) id else null,
                        role = "teacher",
                        schoolId = ctx.schoolId.toString()
                    ),
                    message = "Teacher account created"
                )
            }

            // ---- list teachers in the admin's school as summary CARDS ----
            //
            // Drives the redesigned School-Admin teacher list. Returns EVERY
            // teacher in the school — including inactive accounts and teachers
            // with no class/subject assignments yet — so the admin sees the full
            // roster (an empty card still shows "No grades/subjects assigned").
            //
            // Performance (no N+1): exactly FIVE queries total regardless of page
            // size — (1) the paginated teacher page, (2) its total count, then a
            // single batched query each for (3) all assignments owned by the
            // page's teachers, (4) the faculty bridge rows (userId → externalId)
            // for those teachers, and (5) the 30-day faculty attendance window.
            // Student workload is derived from a single grouped students query.
            // Everything else is aggregated in-memory.
            //
            // Faculty-attendance bridge: faculty attendance is keyed on
            // FacultyTable.externalId, but teachers are app_users rows. We join
            // via FacultyTable.userId == app_users.id; when no faculty row links
            // a teacher we simply report attendancePercentage = null (the card
            // renders "—" rather than fabricating a number).
            get {
                val ctx = call.requireSchoolContext() ?: return@get
                val isAdmin = ctx.role in SCHOOL_ADMIN_ROLES

                val page = (call.request.queryParameters["page"]?.toIntOrNull() ?: 1)
                    .coerceAtLeast(1)
                val pageSize = (call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20)
                    .coerceIn(1, 100)
                val offset = (page - 1).toLong() * pageSize

                val response = dbQuery {
                    // (2) total roster size for pagination metadata.
                    val totalRecords = AppUsersTable.selectAll()
                        .where {
                            (AppUsersTable.schoolId eq ctx.schoolId) and
                                (AppUsersTable.role eq "teacher")
                        }
                        .count()

                    // (1) one page of teachers — NO isActive filter so inactive
                    // accounts are still listed (card shows an INACTIVE badge).
                    val teacherRows = AppUsersTable.selectAll()
                        .where {
                            (AppUsersTable.schoolId eq ctx.schoolId) and
                                (AppUsersTable.role eq "teacher")
                        }
                        .orderBy(AppUsersTable.fullName, SortOrder.ASC)
                        .limit(pageSize, offset)
                        .toList()

                    val teacherIds = teacherRows.map { it[AppUsersTable.id].value }

                    if (teacherIds.isEmpty()) {
                        return@dbQuery TeacherCardListResponse(
                            teachers = emptyList(),
                            pagination = TeacherCardPaginationDto(
                                page = page,
                                pageSize = pageSize,
                                totalRecords = totalRecords.toInt(),
                                hasNext = false
                            )
                        )
                    }

                    // (3) all assignments for the page's teachers in ONE query.
                    // The codebase avoids `inList`; OR-reduce the teacher ids.
                    val assignmentPredicate = teacherIds
                        .map { tid -> TeacherSubjectAssignmentsTable.teacherId eq tid }
                        .reduce { acc, next -> acc or next }
                    val assignmentRows = TeacherSubjectAssignmentsTable.selectAll()
                        .where {
                            (TeacherSubjectAssignmentsTable.schoolId eq ctx.schoolId) and
                                (TeacherSubjectAssignmentsTable.isActive eq true) and
                                assignmentPredicate
                        }
                        .toList()
                    val assignmentsByTeacher = assignmentRows.groupBy {
                        it[TeacherSubjectAssignmentsTable.teacherId]
                    }

                    // (4) faculty bridge rows (userId → externalId / department)
                    // for the page's teachers in ONE query, so we can locate
                    // faculty attendance and label the role more specifically.
                    val facultyPredicate = teacherIds
                        .map { tid -> FacultyTable.userId eq tid }
                        .reduce { acc, next -> acc or next }
                    val facultyByUserId: Map<UUID, Pair<String, String?>> =
                        FacultyTable.selectAll()
                            .where {
                                (FacultyTable.schoolId eq ctx.schoolId) and facultyPredicate
                            }
                            .toList()
                            .mapNotNull { row ->
                                row[FacultyTable.userId]?.let { uid ->
                                    uid to (row[FacultyTable.externalId] to row[FacultyTable.department])
                                }
                            }
                            .toMap()
                    val externalIdByUserId: Map<UUID, String> =
                        facultyByUserId.mapValues { it.value.first }

                    // (5) 30-day faculty attendance window, grouped by personId
                    // (= FacultyTable.externalId). Only fetched when at least one
                    // teacher on this page actually bridges to a faculty row.
                    val cutoff = LocalDate.now().minusDays(30)
                    val attendanceByExternalId: Map<String, List<String>> =
                        if (externalIdByUserId.isEmpty()) {
                            emptyMap()
                        } else {
                            AttendanceRecordsTable.selectAll()
                                .where {
                                    (AttendanceRecordsTable.schoolId eq ctx.schoolId) and
                                        (AttendanceRecordsTable.type eq "faculty")
                                }
                                .toList()
                                .filter {
                                    runCatching {
                                        LocalDate.parse(it[AttendanceRecordsTable.date]).isAfter(cutoff)
                                    }.getOrDefault(false)
                                }
                                .groupBy(
                                    { it[AttendanceRecordsTable.personId] },
                                    { it[AttendanceRecordsTable.status] }
                                )
                        }

                    // Student workload source: active students grouped by their
                    // (className|section) bucket, counted ONCE for the whole
                    // school. totalStudents per teacher = sum over their distinct
                    // assigned classes (so co-teaching never double-counts within
                    // a teacher, and an unassigned teacher gets 0).
                    val studentCountByClass: Map<Pair<String, String>, Int> =
                        StudentsTable.selectAll()
                            .where {
                                (StudentsTable.schoolId eq ctx.schoolId) and
                                    (StudentsTable.isActive eq true)
                            }
                            .toList()
                            .groupingBy {
                                it[StudentsTable.className] to it[StudentsTable.section]
                            }
                            .eachCount()

                    val cards = teacherRows.map { row ->
                        val teacherId = row[AppUsersTable.id].value
                        val assignments = assignmentsByTeacher[teacherId].orEmpty()

                        // grades = distinct class names; subjects = distinct subjects.
                        val grades = assignments
                            .map { it[TeacherSubjectAssignmentsTable.className] }
                            .filter { it.isNotBlank() }
                            .distinct()
                        val subjects = assignments
                            .map { it[TeacherSubjectAssignmentsTable.subject] }
                            .filter { it.isNotBlank() }
                            .distinct()

                        // distinct (className, section) classes this teacher owns.
                        val distinctClasses = assignments
                            .map {
                                it[TeacherSubjectAssignmentsTable.className] to
                                    it[TeacherSubjectAssignmentsTable.section]
                            }
                            .distinct()
                        val totalClasses = distinctClasses.size
                        val totalStudents = distinctClasses
                            .sumOf { studentCountByClass[it] ?: 0 }

                        // attendance via the faculty bridge — null when unlinked
                        // or no records in the window (card shows "—", no guess).
                        val attendancePercentage = externalIdByUserId[teacherId]
                            ?.let { ext -> attendanceByExternalId[ext] }
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { statuses ->
                                val present = statuses.count { it.equals("PRESENT", true) }
                                kotlin.math.round(present * 100.0 / statuses.size).toInt()
                            }

                        val isActive = row[AppUsersTable.isActive]
                        // Role label, most-specific first: a bridged faculty
                        // department (e.g. "Mathematics Teacher"), else the
                        // teacher's primary assigned subject, else plain "Teacher".
                        val department = facultyByUserId[teacherId]?.second?.takeIf { it.isNotBlank() }
                        val roleLabel = when {
                            department != null -> "$department Teacher"
                            subjects.isNotEmpty() -> "${subjects.first()} Teacher"
                            else -> "Teacher"
                        }

                        TeacherCardDto(
                            id = teacherId.toString(),
                            profile = TeacherCardProfileDto(
                                name = row[AppUsersTable.fullName],
                                avatarUrl = row[AppUsersTable.profilePicUrl],
                                role = roleLabel,
                                status = if (isActive) "ACTIVE" else "INACTIVE"
                            ),
                            academicAssignment = TeacherCardAcademicAssignmentDto(
                                grades = grades,
                                subjects = subjects
                            ),
                            workload = TeacherCardWorkloadDto(
                                totalClasses = totalClasses,
                                totalStudents = totalStudents
                            ),
                            activity = TeacherCardActivityDto(
                                attendancePercentage = attendancePercentage,
                                lastActiveAt = row[AppUsersTable.lastLoginAt]?.toString()
                            ),
                            actions = TeacherCardActionsDto(
                                canViewProfile = true,
                                // Only privileged school admins may assign classes
                                // or deactivate; the UI hides these for everyone
                                // else (and never hardcodes them).
                                canAssignClass = isAdmin && isActive,
                                canDeactivate = isAdmin && isActive
                            )
                        )
                    }

                    TeacherCardListResponse(
                        teachers = cards,
                        pagination = TeacherCardPaginationDto(
                            page = page,
                            pageSize = pageSize,
                            totalRecords = totalRecords.toInt(),
                            hasNext = offset + cards.size < totalRecords
                        )
                    )
                }

                call.ok(response, message = "Teachers fetched successfully")
            }

            // ---- HARD-delete a teacher (RA-22) ----
            // FIX (admin remove leaves Supabase rows behind): previously this
            // only flipped is_active=false, so the app_users row (and the
            // teacher's sessions/tokens/assignments) stayed in Supabase forever.
            // Admin removal now performs a real DELETE inside one transaction:
            //   - sessions + device tokens + class/subject assignments are
            //     purged here (no DB-level FK exists for them);
            //   - authored content (assessments / homework / syllabus / marks)
            //     is PRESERVED — the DB FKs are ON DELETE SET NULL, so school
            //     academic history survives with the author cleared;
            //   - teacher_periods rows cascade away via their FK.
            // IDOR-safe: every statement is constrained to ctx.schoolId, so an
            // admin can only delete teachers belonging to their OWN school.
            delete("/{id}") {
                val ctx = call.requireSchoolAdmin() ?: return@delete   // privileged: RA-39
                val teacherId = call.parameters["id"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: run { call.fail("A valid teacher id is required", HttpStatusCode.BadRequest, "BAD_TEACHER_ID"); return@delete }

                val now = Instant.now()
                val deleted = dbQuery {
                    // Confirm the teacher exists in THIS school first.
                    val row = AppUsersTable.selectAll()
                        .where {
                            (AppUsersTable.id eq teacherId) and
                                (AppUsersTable.schoolId eq ctx.schoolId) and
                                (AppUsersTable.role eq "teacher")
                        }
                        .firstOrNull() ?: return@dbQuery false

                    // Kill the teacher's live sessions FIRST (immediate lockout even
                    // if a later statement fails), then purge auth artefacts.
                    UserSessionsTable.update({ UserSessionsTable.userId eq teacherId }) {
                        it[revokedAt] = now
                    }
                    UserSessionsTable.deleteWhere { UserSessionsTable.userId eq teacherId }
                    DeviceTokensTable.deleteWhere { DeviceTokensTable.userId eq teacherId }
                    // The teacher's personal notification inbox is meaningless once
                    // the account is gone — purge it so no orphan rows linger.
                    NotificationsTable.deleteWhere { NotificationsTable.userId eq teacherId }

                    // RA-LINK: the teacher's class/subject assignments are the
                    // relationship records that derive Student ↔ Teacher links.
                    // Per the soft-delete requirement (Example 5) we do NOT hard
                    // delete them — they are flipped to is_active=false via the
                    // centralized reconciler so relationship HISTORY remains intact
                    // while the teacher disappears from every live derived view.
                    // The reconciler returns the (class, section) pairs it touched.
                    val affected = StudentAggregationService.softDeactivateTeacherAssignments(
                        ctx.schoolId, teacherId
                    )

                    // BUGFIX: remove the mirrored `faculty` row too. It is keyed by
                    // external_id = "U-<userId>" (set on create / onboarding); without
                    // this the deleted teacher would linger forever in the faculty
                    // roster and keep showing up in analytics. School-scoped for IDOR.
                    FacultyTable.deleteWhere {
                        (FacultyTable.schoolId eq ctx.schoolId) and
                            (FacultyTable.externalId eq "U-$teacherId")
                    }

                    // Finally the account row itself. Tables with real FKs to
                    // app_users (assessments / homework / syllabus_units → SET NULL,
                    // teacher_periods → CASCADE) are handled by Postgres.
                    AppUsersTable.deleteWhere {
                        (AppUsersTable.id eq teacherId) and
                            (AppUsersTable.schoolId eq ctx.schoolId) and
                            (AppUsersTable.role eq "teacher")
                    }

                    // RA-LINK: re-derive student counts for every class the removed
                    // teacher used to cover, so the REMAINING teachers of those
                    // classes keep accurate workload metrics with no stale data.
                    affected.forEach { (cls, sec) ->
                        StudentAggregationService.recalcTeacherStudentCountsForClass(
                            ctx.schoolId, cls, sec
                        )
                    }
                    true
                }

                if (!deleted) {
                    call.fail("Teacher not found in your school", HttpStatusCode.NotFound, "TEACHER_NOT_FOUND")
                    return@delete
                }
                call.okMessage("Teacher removed")
            }

            // ---- reissue a teacher's initial password (RA-32) ----
            // Recovers the "lost credential" case: once the admin dismisses the
            // create-teacher dialog the initial password is gone (only its hash
            // is stored). This generates a NEW secure password, persists only the
            // hash, revokes the teacher's live sessions, and returns the plaintext
            // ONCE so the admin can hand it over.
            //
            // IDOR-safe: every lookup/update is constrained to ctx.schoolId, so an
            // admin can only reset teachers in their OWN school. Email teachers
            // only — phone teachers authenticate via OTP (no password to reset).
            post("/{id}/reset-password") {
                val ctx = call.requireSchoolAdmin() ?: return@post   // privileged: RA-39
                val teacherId = call.parameters["id"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: run { call.fail("A valid teacher id is required", HttpStatusCode.BadRequest, "BAD_TEACHER_ID"); return@post }

                val now = Instant.now()
                val newPassword = generateInitialPassword()

                val result = dbQuery {
                    val row = AppUsersTable.selectAll()
                        .where {
                            (AppUsersTable.id eq teacherId) and
                                (AppUsersTable.schoolId eq ctx.schoolId) and
                                (AppUsersTable.role eq "teacher") and
                                (AppUsersTable.isActive eq true)
                        }
                        .firstOrNull() ?: return@dbQuery null

                    val email = row[AppUsersTable.email]
                    if (email.isNullOrBlank()) {
                        // Phone-only teacher: there is no password to reset.
                        return@dbQuery TeacherCredentialDto("", row[AppUsersTable.fullName], "", "")
                    }

                    AppUsersTable.update({ AppUsersTable.id eq teacherId }) {
                        it[passwordHash] = hashPassword(newPassword)
                        it[isEmailVerified] = true
                        it[updatedAt] = now
                    }
                    // Revoke live sessions so the old credential can't keep a foothold.
                    UserSessionsTable.update({ UserSessionsTable.userId eq teacherId }) {
                        it[revokedAt] = now
                    }

                    TeacherCredentialDto(
                        id = teacherId.toString(),
                        name = row[AppUsersTable.fullName],
                        email = email,
                        initialPassword = newPassword
                    )
                }

                when {
                    result == null ->
                        call.fail("Teacher not found in your school", HttpStatusCode.NotFound, "TEACHER_NOT_FOUND")
                    result.id.isBlank() ->
                        call.fail(
                            "This teacher signs in with phone + OTP, so there is no password to reset. Ask them to log in with their phone number.",
                            HttpStatusCode.Conflict,
                            "TEACHER_USES_OTP"
                        )
                    else ->
                        call.ok(result, message = "New initial password issued")
                }
            }
        }
    }
}
