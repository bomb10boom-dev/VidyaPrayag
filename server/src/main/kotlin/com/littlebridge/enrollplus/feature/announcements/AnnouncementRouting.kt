/*
 * File: AnnouncementRouting.kt
 * Module: feature.announcements
 *
 * Endpoints:
 *   GET  /api/v1/school/announcements?user_id=…
 *   GET  /api/v1/school/announcements/search?query=…&user_id=…
 *   POST /api/v1/school/announcements/sync-whatsapp
 *   POST /api/v1/school/announcements          -- NEW (create one)
 *
 * Spec ref: vidya_prayag_api_spec2.artifact.md §Screen: School Dashboard (Announcement Tab)
 *
 * Authorization & school-resolution rule:
 *   Every endpoint is guarded by call.requireSchoolContext(): the caller must
 *   hold a school role and have a school. Reads/writes are scoped to that
 *   resolved school_id. A ?school_id= / body school_id override is honoured
 *   ONLY for the platform "admin" role; for everyone else it is ignored so a
 *   school_admin can never read or mutate another school's announcements.
 *
 * sync-whatsapp behaviour:
 *   - Only announcements that actually belong to the caller's school are
 *     considered; any foreign event_ids in the request are rejected.
 *   - Marks the synced announcements as synced_to_wa = true (scoped by school).
 *   - Inserts one real row per (announcement × parent_phone) into whatsapp_logs
 *     with status = QUEUED — total_queued is the true number of messages that
 *     will be dispatched (recipients = active parent users with a phone).
 *   - Returns the job_id and an ETA derived from the real queued count. The
 *     actual provider dispatch is performed by the async WhatsApp worker that
 *     drains whatsapp_logs rows in QUEUED status.
 */
package com.littlebridge.enrollplus.feature.announcements

import com.littlebridge.enrollplus.core.accepted
import com.littlebridge.enrollplus.core.created
import com.littlebridge.enrollplus.feature.notifications.Notify
import com.littlebridge.enrollplus.feature.notifications.NotifyRecipients
import com.littlebridge.enrollplus.core.fail
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.requireSchoolAdmin
import com.littlebridge.enrollplus.core.requireSchoolContext
import com.littlebridge.enrollplus.db.AnnouncementsTable
import com.littlebridge.enrollplus.db.AppUsersTable
import com.littlebridge.enrollplus.db.ChildrenTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.TeacherSubjectAssignmentsTable
import com.littlebridge.enrollplus.db.WhatsappLogsTable
import com.littlebridge.enrollplus.feature.calendar.syncAnnouncementToCalendar
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
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

@Serializable
data class AnnouncementDto(
    val type: String,
    @SerialName("event_id") val eventId: String,
    val title: String,
    @SerialName("sub_title") val subTitle: String? = null,
    val description: String,
    @SerialName("event_image") val eventImage: String? = null,
    val date: String,
    @SerialName("audience_type") val audienceType: String = "ALL_SCHOOL",
    @SerialName("audience_filter") val audienceFilter: JsonElement? = null
)

@Serializable
data class AnnouncementsListResponse(val announcements: List<AnnouncementDto>)

@Serializable
data class CreateAnnouncementDto(
    val type: String,
    val title: String,
    @SerialName("sub_title") val subTitle: String? = null,
    val description: String,
    @SerialName("event_image") val eventImage: String? = null,
    val date: String,                              // YYYY-MM-DD
    @SerialName("school_id") val schoolId: String? = null,
    // Broadcast segmentation. Defaults to ALL_SCHOOL when omitted so existing
    // clients keep working. audienceFilter is the scope JSON (see Tables.kt).
    @SerialName("audience_type") val audienceType: String? = null,
    @SerialName("audience_filter") val audienceFilter: JsonElement? = null,
    // VP-CAL integration: when a Holiday/PTM/Event announcement is created with
    // this flag enabled (the default), the platform also mirrors it into a
    // calendar event tagged source = ANNOUNCEMENT so the admin never has to
    // create the same thing twice. Plain "Update"/"Reminder" types ignore this.
    @SerialName("add_to_calendar") val addToCalendar: Boolean = true
)

/** Audience scopes a broadcast can target. */
private val VALID_AUDIENCE_TYPES = setOf(
    "ALL_SCHOOL", "CLASS", "SECTION", "SUBJECT", "STUDENT", "CUSTOM"
)

@Serializable
data class SyncWhatsAppRequest(
    @SerialName("school_id") val schoolId: String? = null,
    @SerialName("announcement_ids") val announcementIds: List<String>? = null
)

@Serializable
data class SyncWhatsAppResponse(
    @SerialName("job_id") val jobId: String,
    @SerialName("total_queued") val totalQueued: Int,
    @SerialName("estimated_time_minutes") val estimatedTimeMinutes: Int
)

// ------- helpers -------

/**
 * The effective school for the request. By default it is the caller's own
 * school. Only the platform "admin" role may target another school via an
 * override (query param or body); for any other role the override is ignored.
 */
private fun effectiveSchoolId(ctxSchoolId: UUID, ctxRole: String, override: String?): UUID {
    if (ctxRole != "admin") return ctxSchoolId
    return override?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: ctxSchoolId
}

// ------- routing -------

fun Route.announcementRouting() {
    authenticate("jwt") {
        route("/api/v1/school/announcements") {

            // ---- list ----
            get {
                val ctx = call.requireSchoolContext() ?: return@get
                val schoolId = effectiveSchoolId(
                    ctx.schoolId, ctx.role, call.request.queryParameters["school_id"]
                )
                val list = dbQuery {
                    AnnouncementsTable.selectAll()
                        .where { AnnouncementsTable.schoolId eq schoolId }
                        .orderBy(AnnouncementsTable.date, SortOrder.DESC)
                        .map { it.toDto() }
                }
                call.ok(AnnouncementsListResponse(list), message = "Announcements fetched successfully")
            }

            // ---- search ----
            get("/search") {
                val q = call.request.queryParameters["query"]?.lowercase().orEmpty()
                if (q.isBlank()) { call.fail("query is required"); return@get }
                val ctx = call.requireSchoolContext() ?: return@get
                val schoolId = effectiveSchoolId(
                    ctx.schoolId, ctx.role, call.request.queryParameters["school_id"]
                )
                val pattern = "%$q%"
                val list = dbQuery {
                    AnnouncementsTable.selectAll()
                        .where {
                            (AnnouncementsTable.schoolId eq schoolId) and
                                ((AnnouncementsTable.title.lowerCase() like pattern) or
                                    (AnnouncementsTable.description.lowerCase() like pattern))
                        }
                        .orderBy(AnnouncementsTable.date, SortOrder.DESC)
                        .map { it.toDto() }
                }
                call.ok(AnnouncementsListResponse(list), message = "Search results fetched")
            }

            // ---- create (privileged: RA-39 — staff cannot broadcast) ----
            post {
                val ctx = call.requireSchoolAdmin() ?: return@post
                val uid = ctx.userId
                val req = call.receive<CreateAnnouncementDto>()
                val schoolId = effectiveSchoolId(ctx.schoolId, ctx.role, req.schoolId)

                // Validate audience type (defaults to ALL_SCHOOL).
                val audienceType = (req.audienceType ?: "ALL_SCHOOL").uppercase()
                if (audienceType !in VALID_AUDIENCE_TYPES) {
                    call.fail(
                        "Invalid audience_type. Allowed: ${VALID_AUDIENCE_TYPES.joinToString()}",
                        HttpStatusCode.BadRequest
                    )
                    return@post
                }
                // Non-ALL_SCHOOL broadcasts must carry a scope filter.
                if (audienceType != "ALL_SCHOOL" && req.audienceFilter == null) {
                    call.fail(
                        "audience_filter is required when audience_type is $audienceType",
                        HttpStatusCode.BadRequest
                    )
                    return@post
                }
                val filterText = req.audienceFilter?.toString()

                val now = Instant.now()
                val eventId = "EVT_" + UUID.randomUUID().toString().take(8).uppercase()
                dbQuery {
                    AnnouncementsTable.insert {
                        it[AnnouncementsTable.schoolId] = schoolId
                        it[AnnouncementsTable.eventId] = eventId
                        it[type] = req.type
                        it[title] = req.title
                        it[subTitle] = req.subTitle
                        it[description] = req.description
                        it[eventImage] = req.eventImage
                        it[date] = req.date
                        it[AnnouncementsTable.audienceType] = audienceType
                        it[audienceFilter] = filterText
                        it[authorRole] = ctx.role
                        it[syncedToWa] = false
                        it[createdBy] = uid
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                }
                // RA-41 + RA-49: an announcement reaches parents + teachers
                // in-app, not just the WhatsApp sync. The IN-APP fan-out now
                // honours the audience scope (no blasting the whole school when
                // a post targets one class/subject/student). Parents are resolved
                // precisely from the audience_filter; teachers receive the post
                // only when it is school-wide (a class/student-scoped parent
                // notice isn't relevant to every teacher).
                val audienceParents = NotifyRecipients.parentsForAudience(
                    schoolId = schoolId,
                    audienceType = audienceType,
                    classNames = audienceStrList(req.audienceFilter, "class_names")
                        + audienceStr(req.audienceFilter, "class_name"),
                    subjects = audienceStrList(req.audienceFilter, "subjects")
                        + audienceStr(req.audienceFilter, "subject"),
                    studentCodes = audienceStrList(req.audienceFilter, "student_codes")
                        + audienceStr(req.audienceFilter, "student_code"),
                )
                val recipients = if (audienceType == "ALL_SCHOOL") {
                    (audienceParents + NotifyRecipients.teachersInSchool(schoolId)).distinct()
                } else {
                    audienceParents.distinct()
                }
                if (recipients.isNotEmpty()) {
                    Notify.toUsers(
                        userIds = recipients,
                        category = "announcement",
                        title = req.title,
                        body = req.subTitle ?: req.description.take(140),
                        schoolId = schoolId,
                        actorId = uid,
                        deepLink = "announcements/$eventId",
                        refType = "announcement",
                        refId = eventId,
                    )
                }
                // VP-CAL: mirror Holiday/PTM/Event announcements into the Academic
                // Calendar when "Add To Academic Calendar" is enabled. Idempotent
                // and a no-op for Update/Reminder types. Best-effort: a calendar
                // sync failure must never fail the announcement creation.
                if (req.addToCalendar) {
                    runCatching {
                        syncAnnouncementToCalendar(
                            schoolId = schoolId,
                            announcementEventId = eventId,
                            announcementType = req.type,
                            title = req.title,
                            description = req.description,
                            date = req.date,
                            eventImage = req.eventImage,
                            createdBy = uid
                        )
                    }
                }

                call.created(
                    AnnouncementDto(
                        req.type, eventId, req.title, req.subTitle, req.description,
                        req.eventImage, req.date, audienceType, req.audienceFilter
                    ),
                    message = "Announcement created"
                )
            }

            // ---- sync-whatsapp (privileged: RA-39 — staff cannot broadcast) ----
            post("/sync-whatsapp") {
                val ctx = call.requireSchoolAdmin() ?: return@post
                val req = call.receive<SyncWhatsAppRequest>()
                val schoolId = effectiveSchoolId(ctx.schoolId, ctx.role, req.schoolId)

                val jobId = "SYNC_WA_${UUID.randomUUID().toString().take(8).uppercase()}"
                val now = Instant.now()

                // Determine which announcements to sync, ALWAYS constrained to the
                // resolved school so foreign event_ids can never be queued.
                val result = dbQuery {
                    val schoolEventIds = AnnouncementsTable.selectAll()
                        .where { AnnouncementsTable.schoolId eq schoolId }
                        .map { it[AnnouncementsTable.eventId] }
                        .toSet()

                    val requested = req.announcementIds
                    val toSync: List<String> = if (requested.isNullOrEmpty()) {
                        // Default: every not-yet-synced announcement in this school.
                        AnnouncementsTable.selectAll()
                            .where {
                                (AnnouncementsTable.schoolId eq schoolId) and
                                    (AnnouncementsTable.syncedToWa eq false)
                            }
                            .map { it[AnnouncementsTable.eventId] }
                    } else {
                        // Keep only the requested IDs that truly belong to this school.
                        requested.filter { it in schoolEventIds }
                    }

                    // Surface foreign / unknown IDs as an error rather than silently
                    // ignoring them, so callers know their request was rejected.
                    val foreign = requested?.filterNot { it in schoolEventIds }.orEmpty()

                    Triple(toSync, foreign, schoolEventIds)
                }

                val (toSync, foreign, _) = result
                if (foreign.isNotEmpty()) {
                    call.fail(
                        "These announcement_ids do not belong to your school: ${foreign.joinToString()}",
                        HttpStatusCode.Forbidden
                    )
                    return@post
                }
                if (toSync.isEmpty()) {
                    call.accepted(
                        SyncWhatsAppResponse(jobId, 0, 0),
                        message = "Nothing to sync — all selected announcements are already up to date"
                    )
                    return@post
                }

                val queued = dbQuery {
                    var inserted = 0
                    toSync.forEach { aid ->
                        // Resolve THIS announcement's audience and expand it to the
                        // exact set of parent phone numbers that should receive it.
                        val row = AnnouncementsTable.selectAll()
                            .where {
                                (AnnouncementsTable.schoolId eq schoolId) and
                                    (AnnouncementsTable.eventId eq aid)
                            }
                            .firstOrNull()

                        val audienceType = row?.get(AnnouncementsTable.audienceType) ?: "ALL_SCHOOL"
                        val audienceFilter = row?.get(AnnouncementsTable.audienceFilter)
                        val authorRole = row?.get(AnnouncementsTable.authorRole) ?: "school_admin"
                        val authorId = row?.get(AnnouncementsTable.createdBy)

                        val phones = resolveRecipientPhones(
                            schoolId = schoolId,
                            audienceType = audienceType,
                            audienceFilter = audienceFilter,
                            authorRole = authorRole,
                            authorId = authorId
                        )

                        phones.forEach { phone ->
                            WhatsappLogsTable.insert {
                                it[WhatsappLogsTable.schoolId] = schoolId
                                it[announcementId] = aid
                                it[WhatsappLogsTable.jobId] = jobId
                                it[WhatsappLogsTable.phone] = phone
                                it[status] = "QUEUED"
                                it[createdAt] = now
                            }
                            inserted++
                        }
                        // Scope the flip by BOTH school and event id (defensive).
                        AnnouncementsTable.update({
                            (AnnouncementsTable.schoolId eq schoolId) and
                                (AnnouncementsTable.eventId eq aid)
                        }) {
                            it[syncedToWa] = true
                            it[updatedAt] = now
                        }
                    }
                    inserted
                }

                // ETA assumes ~30 messages/min throughput from the async worker.
                val eta = if (queued == 0) 0 else (queued / 30 + 1).coerceAtMost(60)
                call.accepted(
                    SyncWhatsAppResponse(jobId, queued, eta),
                    message = if (queued == 0)
                        "No parent recipients with a phone number were found for this school"
                    else
                        "Sync queued: $queued message(s) will be dispatched"
                )
            }
        }
    }
}

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * RA-49 helpers: pull a string list / single string out of the audience_filter
 * JSON the client sends so the IN-APP notification fan-out can resolve the same
 * recipients the WhatsApp expansion does. Tolerant of missing keys / shapes.
 */
private fun audienceStrList(filter: JsonElement?, key: String): List<String> {
    val obj = (filter as? JsonObject) ?: return emptyList()
    return obj[key]?.let {
        runCatching { it.jsonArray.mapNotNull { e -> e.jsonPrimitive.contentOrNull } }.getOrNull()
    } ?: emptyList()
}

private fun audienceStr(filter: JsonElement?, key: String): List<String> {
    val obj = (filter as? JsonObject) ?: return emptyList()
    return obj[key]?.jsonPrimitive?.contentOrNull?.let { listOf(it) } ?: emptyList()
}

private fun org.jetbrains.exposed.sql.ResultRow.toDto(): AnnouncementDto {
    val filterText = this[AnnouncementsTable.audienceFilter]
    val filter = filterText?.let { runCatching { lenientJson.parseToJsonElement(it) }.getOrNull() }
    return AnnouncementDto(
        type = this[AnnouncementsTable.type],
        eventId = this[AnnouncementsTable.eventId],
        title = this[AnnouncementsTable.title],
        subTitle = this[AnnouncementsTable.subTitle],
        description = this[AnnouncementsTable.description],
        eventImage = this[AnnouncementsTable.eventImage],
        date = this[AnnouncementsTable.date],
        audienceType = this[AnnouncementsTable.audienceType],
        audienceFilter = filter
    )
}

/**
 * Expand a broadcast's audience into the concrete set of parent phone numbers.
 *
 * MUST be called inside an active Exposed transaction (dbQuery).
 *
 * Rules:
 *  - ALL_SCHOOL  -> every active parent in the school with a phone.
 *  - CLASS       -> parents whose child's current_grade matches class_name.
 *  - SECTION     -> CLASS scope further intersected with section (best-effort:
 *                   children grade match; section data lives on students, so
 *                   when unavailable we fall back to class grade match).
 *  - SUBJECT     -> parents of children in any class where this subject is
 *                   taught (via teacher_subject_assignments).
 *  - STUDENT     -> parents of the children linked (children.student_code) to
 *                   the listed students.student_code values; falls back to grade
 *                   match only when no codes are supplied.
 *  - CUSTOM      -> explicit phone list in the filter, intersected with school.
 *
 * Teacher-authored broadcasts (authorRole startsWith "teacher") are constrained
 * to the classes/subjects that teacher is assigned to teach.
 */
private fun resolveRecipientPhones(
    schoolId: UUID,
    audienceType: String,
    audienceFilter: String?,
    authorRole: String,
    authorId: UUID?
): List<String> {
    fun allSchoolParentPhones(): List<String> =
        AppUsersTable.selectAll()
            .where { (AppUsersTable.schoolId eq schoolId) and (AppUsersTable.role eq "parent") and (AppUsersTable.isActive eq true) }
            .mapNotNull { it[AppUsersTable.phone] }
            .filter { it.isNotBlank() }
            .distinct()

    fun phonesForParents(parentIds: Collection<UUID>): List<String> {
        if (parentIds.isEmpty()) return emptyList()
        // Use the project's portable OR-reduce instead of `inList` (kept
        // consistent with ParentRouting's Exposed-version-safe approach).
        val idFilter = parentIds.distinct()
            .map { pid -> AppUsersTable.id eq pid }
            .reduce { acc, op -> acc or op }
        return AppUsersTable.selectAll()
            .where { idFilter and (AppUsersTable.isActive eq true) }
            .mapNotNull { it[AppUsersTable.phone] }
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun parentIdsForGrades(grades: Collection<String>): Set<UUID> {
        if (grades.isEmpty()) return emptySet()
        val wanted = grades.map { it.trim().lowercase() }.toSet()
        return ChildrenTable.selectAll()
            .where { (ChildrenTable.schoolId eq schoolId) and (ChildrenTable.isActive eq true) }
            .filter { (it[ChildrenTable.currentGrade]?.trim()?.lowercase()) in wanted }
            .map { it[ChildrenTable.parentId] }
            .toSet()
    }

    // Parents of the children linked to the given students.student_code values
    // (via the children.student_code link added in migration 002).
    fun parentIdsForStudentCodes(codes: Collection<String>): Set<UUID> {
        val wanted = codes.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        if (wanted.isEmpty()) return emptySet()
        return ChildrenTable.selectAll()
            .where { (ChildrenTable.schoolId eq schoolId) and (ChildrenTable.isActive eq true) }
            .filter { it[ChildrenTable.studentCode]?.trim() in wanted }
            .map { it[ChildrenTable.parentId] }
            .toSet()
    }

    val filter: JsonObject? = audienceFilter
        ?.let { runCatching { lenientJson.parseToJsonElement(it) as? JsonObject }.getOrNull() }

    fun str(key: String): String? = filter?.get(key)?.jsonPrimitive?.contentOrNull
    fun strList(key: String): List<String> =
        filter?.get(key)?.let { runCatching { it.jsonArray.mapNotNull { e -> e.jsonPrimitive.contentOrNull } }.getOrNull() }
            ?: emptyList()

    // Classes/subjects this teacher is allowed to broadcast to.
    val teacherClasses: Set<String> by lazy {
        if (authorId == null) emptySet()
        else TeacherSubjectAssignmentsTable.selectAll()
            .where { (TeacherSubjectAssignmentsTable.schoolId eq schoolId) and (TeacherSubjectAssignmentsTable.teacherId eq authorId) and (TeacherSubjectAssignmentsTable.isActive eq true) }
            .map { it[TeacherSubjectAssignmentsTable.className].trim().lowercase() }
            .toSet()
    }
    val isTeacher = authorRole.startsWith("teacher", ignoreCase = true)

    fun constrainToTeacher(grades: Collection<String>): Set<String> =
        if (!isTeacher) grades.toSet()
        else grades.filter { it.trim().lowercase() in teacherClasses }.toSet()

    return when (audienceType.uppercase()) {
        "ALL_SCHOOL" -> {
            // A teacher cannot blast the whole school; restrict to their classes.
            if (isTeacher) phonesForParents(parentIdsForGrades(teacherClasses))
            else allSchoolParentPhones()
        }

        "CLASS", "SECTION" -> {
            val classes = buildList {
                str("class_name")?.let { add(it) }
                addAll(strList("class_names"))
            }
            phonesForParents(parentIdsForGrades(constrainToTeacher(classes)))
        }

        "SUBJECT" -> {
            val subjects = buildList {
                str("subject")?.let { add(it) }
                addAll(strList("subjects"))
            }.map { it.trim().lowercase() }.toSet()
            if (subjects.isEmpty()) return emptyList()
            val classesTeachingSubject = TeacherSubjectAssignmentsTable.selectAll()
                .where { (TeacherSubjectAssignmentsTable.schoolId eq schoolId) and (TeacherSubjectAssignmentsTable.isActive eq true) }
                .filter { it[TeacherSubjectAssignmentsTable.subject].trim().lowercase() in subjects }
                .map { it[TeacherSubjectAssignmentsTable.className] }
            phonesForParents(parentIdsForGrades(constrainToTeacher(classesTeachingSubject)))
        }

        "CUSTOM" -> {
            val phones = strList("phones").map { it.trim() }.filter { it.isNotBlank() }.toSet()
            if (phones.isEmpty()) return emptyList()
            allSchoolParentPhones().filter { it in phones }
        }

        "STUDENT" -> {
            // Target exact students via children.student_code (migration 002).
            // Accepts {"student_codes":[...]} or {"student_code":"..."}. Falls
            // back to grade match only when no codes are supplied.
            val codes = strList("student_codes") + listOfNotNull(str("student_code"))
            if (codes.isNotEmpty()) {
                val parentIds = parentIdsForStudentCodes(codes)
                // Teacher scope guard: keep only students whose grade the teacher
                // teaches (looked up from the matched children rows).
                if (isTeacher) {
                    val allowedParents = ChildrenTable.selectAll()
                        .where { (ChildrenTable.schoolId eq schoolId) and (ChildrenTable.isActive eq true) }
                        .filter {
                            it[ChildrenTable.parentId] in parentIds &&
                                (it[ChildrenTable.currentGrade]?.trim()?.lowercase() in teacherClasses)
                        }
                        .map { it[ChildrenTable.parentId] }
                        .toSet()
                    phonesForParents(allowedParents)
                } else {
                    phonesForParents(parentIds)
                }
            } else {
                val grades = strList("grades") + listOfNotNull(str("grade"))
                phonesForParents(parentIdsForGrades(constrainToTeacher(grades)))
            }
        }

        else -> emptyList()
    }
}
