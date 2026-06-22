package com.littlebridge.enrollplus.feature.user

import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.principalUserUuid
import com.littlebridge.enrollplus.db.AnnouncementsTable
import com.littlebridge.enrollplus.db.AppConfigTable
import com.littlebridge.enrollplus.db.ChildrenTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.FeeRecordsTable
import com.littlebridge.enrollplus.db.ScholarshipApplicationsTable
import com.littlebridge.enrollplus.db.ScholarshipsTable
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

@Serializable
data class AchievementBadgeDto(
    val title: String,
    @SerialName("icon_name") val iconName: String,
    @SerialName("is_locked") val isLocked: Boolean,
    @SerialName("gradient_colors") val gradientColors: List<Long>
)

@Serializable
data class AcademicCompetencyDto(
    val title: String,
    @SerialName("icon_name") val iconName: String,
    val progress: Float
)

@Serializable
data class PlayIndicatorDto(
    val title: String,
    val description: String,
    @SerialName("image_url") val imageUrl: String,
    @SerialName("is_met") val isMet: Boolean
)

@Serializable
data class TrackProgressDataDto(
    @SerialName("child_name") val childName: String,
    @SerialName("overall_progress") val overallProgress: Float,
    @SerialName("current_level") val currentLevel: Int,
    @SerialName("journey_description") val journeyDescription: String,
    val badges: List<AchievementBadgeDto>,
    @SerialName("academic_competencies") val academicCompetencies: List<AcademicCompetencyDto>,
    @SerialName("emotional_intelligence") val emotionalIntelligence: Map<String, Float>,
    @SerialName("play_indicators") val playIndicators: List<PlayIndicatorDto>
)

@Serializable
data class TrackProgressResponse(
    val success: Boolean,
    val message: String,
    val data: TrackProgressDataDto
)

// --- Fees ---
// (audit §8.2) The /fees endpoint is owned by feature/parent/ParentFeesRouting.kt,
// which serves the client-correct `FeeData` shape (finding D). The previously-dead
// `FeeDataDto`/`FeeAnnouncementDto` that used to live here were never registered by
// any route in this file, so they were removed to kill the duplicate/dead contract.

// --- Scholarships ---
@Serializable
data class ScholarshipDto(
    val id: String,
    val title: String,
    val description: String,
    val amount: String,
    @SerialName("time_left") val timeLeft: String,
    val category: String,
    @SerialName("is_critical") val isCritical: Boolean = false
)

@Serializable
data class ScholarshipApplicationDto(
    val id: String,
    val institution: String,
    val program: String,
    val status: String,
    @SerialName("icon_name") val iconName: String
)

@Serializable
data class ScholarshipsDataDto(
    val scholarships: List<ScholarshipDto>,
    val applications: List<ScholarshipApplicationDto>,
    @SerialName("profile_strength") val profileStrength: Int,
    @SerialName("streak_days") val streakDays: Int,
    @SerialName("current_level") val currentLevel: Int
)

// --- Announcements ---
@Serializable
data class ParentAnnouncementDto(
    val id: String,
    val title: String,
    val description: String,
    val date: String,
    val category: String,
    @SerialName("is_featured") val isFeatured: Boolean = false,
    @SerialName("image_url") val imageUrl: String? = null
)

@Serializable
data class ParentAnnouncementsDataDto(
    val announcements: List<ParentAnnouncementDto>,
    @SerialName("is_whatsapp_sync_enabled") val isWhatsAppSyncEnabled: Boolean
)

// --- Notifications (report §5.3 — MockV2 eradication, SWEEP-A) ---
// A unified inbox feed for the parent. Aggregates two real signals:
//   1. School announcements (AnnouncementsTable) scoped to the parent's
//      children's schools — same source as /announcements.
//   2. Fee reminders (FeeRecordsTable) for DUE/OVERDUE line items belonging
//      to this parent — surfaced as actionable notifications.
// The client maps `category` → the existing VNotification tinting:
//   "fees" | "academic" | "attendance" | "announcement".
@Serializable
data class ParentNotificationDto(
    val id: String,
    val category: String,
    val title: String,
    val body: String,
    val time: String,
    val unread: Boolean = true
)

@Serializable
data class ParentNotificationsDataDto(
    val notifications: List<ParentNotificationDto>,
    @SerialName("unread_count") val unreadCount: Int
)

fun Route.parentRouting() {
    authenticate("jwt") {
        route("/api/v1/parent") {
            // -------- SCHOLARSHIPS (audit §4.2/§5.2 — now DB-backed) --------
            // Reads real opportunity rows from ScholarshipsTable and the
            // parent's own applications from ScholarshipApplicationsTable
            // (scoped by parent uid). Replaces the hardcoded "$45,000 STEM"
            // fiction. When operators haven't curated any rows yet the lists
            // are honestly empty (never fabricated).
            get("/scholarships") {
                val uid = call.principalUserUuid() ?: run {
                    call.respond(HttpStatusCode.Unauthorized); return@get
                }

                val data = dbQuery {
                    val scholarships = ScholarshipsTable.selectAll()
                        .where { ScholarshipsTable.isActive eq true }
                        .orderBy(ScholarshipsTable.position, SortOrder.ASC)
                        .map { row ->
                            ScholarshipDto(
                                id = row[ScholarshipsTable.id].value.toString(),
                                title = row[ScholarshipsTable.title],
                                description = row[ScholarshipsTable.description],
                                amount = row[ScholarshipsTable.amount],
                                timeLeft = row[ScholarshipsTable.timeLeft],
                                category = row[ScholarshipsTable.category],
                                isCritical = row[ScholarshipsTable.isCritical]
                            )
                        }

                    val applications = ScholarshipApplicationsTable.selectAll()
                        .where { ScholarshipApplicationsTable.parentId eq uid }
                        .orderBy(ScholarshipApplicationsTable.position, SortOrder.ASC)
                        .map { row ->
                            ScholarshipApplicationDto(
                                id = row[ScholarshipApplicationsTable.id].value.toString(),
                                institution = row[ScholarshipApplicationsTable.institution],
                                program = row[ScholarshipApplicationsTable.program],
                                status = row[ScholarshipApplicationsTable.status],
                                iconName = row[ScholarshipApplicationsTable.iconName]
                            )
                        }

                    // Gamification fields derived from real signals (no fixed fiction):
                    //  - profile_strength scales with how complete the parent's
                    //    application footprint is (0 apps → 40, capped at 100).
                    //  - current_level = highest child level the parent has.
                    val childLevels = ChildrenTable.selectAll()
                        .where { (ChildrenTable.parentId eq uid) and (ChildrenTable.isActive eq true) }
                        .map { it[ChildrenTable.currentLevel] }
                    val currentLevel = childLevels.maxOrNull() ?: 1
                    val profileStrength = (40 + applications.size * 15).coerceAtMost(100)

                    ScholarshipsDataDto(
                        scholarships = scholarships,
                        applications = applications,
                        profileStrength = profileStrength,
                        streakDays = 0,
                        currentLevel = currentLevel
                    )
                }
                call.ok(data, message = "Scholarships data fetched")
            }

            // -------- ANNOUNCEMENTS (parent-school harmony, report §9.1) --------
            // Parents now read the SAME announcements rows the school side
            // creates (AnnouncementsTable), scoped to the schools their children
            // are enrolled in. This replaces the old static/mock list so a
            // school announcement actually reaches the parent.
            get("/announcements") {
                val uid = call.principalUserUuid() ?: run {
                    call.respond(HttpStatusCode.Unauthorized); return@get
                }

                val data = dbQuery {
                    // 1) Resolve the schools this parent's active children belong to.
                    val schoolIds = ChildrenTable.selectAll()
                        .where {
                            (ChildrenTable.parentId eq uid) and (ChildrenTable.isActive eq true)
                        }
                        .mapNotNull { it[ChildrenTable.schoolId] }
                        .distinct()

                    // 2) Pull announcements for those schools, newest first.
                    val rows = if (schoolIds.isEmpty()) {
                        emptyList()
                    } else {
                        val schoolFilter = schoolIds
                            .map { schoolId -> AnnouncementsTable.schoolId eq schoolId }
                            .reduce { acc, op -> acc or op }

                        AnnouncementsTable.selectAll()
                            .where { schoolFilter }
                            .orderBy(AnnouncementsTable.createdAt, SortOrder.DESC)
                            .map { row ->
                                ParentAnnouncementDto(
                                    id = row[AnnouncementsTable.id].value.toString(),
                                    title = row[AnnouncementsTable.title],
                                    description = row[AnnouncementsTable.description],
                                    date = row[AnnouncementsTable.date],
                                    category = row[AnnouncementsTable.type],
                                    // "Special" announcements are surfaced as featured.
                                    isFeatured = row[AnnouncementsTable.type]
                                        .equals("Special", ignoreCase = true),
                                    imageUrl = row[AnnouncementsTable.eventImage]
                                )
                            }
                    }

                    // 3) WhatsApp sync flag from the same app_config the school uses.
                    val flagsRaw = AppConfigTable.selectAll()
                        .where { AppConfigTable.key eq "flags" }
                        .singleOrNull()?.get(AppConfigTable.value)
                    val waEnabled = runCatching {
                        flagsRaw
                            ?.let { lenientJson.parseToJsonElement(it).jsonObject }
                            ?.get("is_whatsapp_sync_enabled")
                            ?.jsonPrimitive?.content
                            ?.toBooleanStrictOrNull()
                    }.getOrNull() ?: false

                    ParentAnnouncementsDataDto(
                        announcements = rows,
                        isWhatsAppSyncEnabled = waEnabled
                    )
                }
                call.ok(data, message = "Announcements data fetched")
            }

            // -------- NOTIFICATIONS (report §5.3, SWEEP-A) --------
            // Real unified inbox replacing MockV2.notifications. Combines the
            // school announcements this parent can see with fee reminders for
            // any DUE/OVERDUE line items they owe, newest first.
            get("/notifications") {
                val uid = call.principalUserUuid() ?: run {
                    call.respond(HttpStatusCode.Unauthorized); return@get
                }

                val data = dbQuery {
                    val items = mutableListOf<Pair<java.time.Instant, ParentNotificationDto>>()

                    // 1) School announcements scoped to the parent's children.
                    val schoolIds = ChildrenTable.selectAll()
                        .where {
                            (ChildrenTable.parentId eq uid) and (ChildrenTable.isActive eq true)
                        }
                        .mapNotNull { it[ChildrenTable.schoolId] }
                        .distinct()

                    if (schoolIds.isNotEmpty()) {
                        val schoolFilter = schoolIds
                            .map { schoolId -> AnnouncementsTable.schoolId eq schoolId }
                            .reduce { acc, op -> acc or op }

                        AnnouncementsTable.selectAll()
                            .where { schoolFilter }
                            .orderBy(AnnouncementsTable.createdAt, SortOrder.DESC)
                            .forEach { row ->
                                val createdAt = row[AnnouncementsTable.createdAt]
                                items += createdAt to ParentNotificationDto(
                                    id = "ann_" + row[AnnouncementsTable.id].value.toString(),
                                    category = "announcement",
                                    title = row[AnnouncementsTable.title],
                                    body = row[AnnouncementsTable.description],
                                    time = row[AnnouncementsTable.date],
                                    unread = true,
                                )
                            }
                    }

                    // 2) Fee reminders — only outstanding (DUE/OVERDUE) items.
                    FeeRecordsTable.selectAll()
                        .where {
                            (FeeRecordsTable.parentId eq uid) and
                                ((FeeRecordsTable.status eq "DUE") or (FeeRecordsTable.status eq "OVERDUE"))
                        }
                        .orderBy(FeeRecordsTable.updatedAt, SortOrder.DESC)
                        .forEach { row ->
                            val overdue = row[FeeRecordsTable.status].equals("OVERDUE", ignoreCase = true)
                            val currency = row[FeeRecordsTable.currency]
                            val amount = row[FeeRecordsTable.amount]
                            val due = row[FeeRecordsTable.dueDate]
                            items += row[FeeRecordsTable.updatedAt] to ParentNotificationDto(
                                id = "fee_" + row[FeeRecordsTable.id].value.toString(),
                                category = "fees",
                                title = if (overdue) {
                                    "Overdue: ${row[FeeRecordsTable.title]}"
                                } else {
                                    "Fee due: ${row[FeeRecordsTable.title]}"
                                },
                                body = buildString {
                                    append("$currency ")
                                    append(if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString())
                                    if (!due.isNullOrBlank()) append(" • due $due")
                                },
                                time = due ?: "",
                                unread = true,
                            )
                        }

                    val ordered = items
                        .sortedByDescending { it.first }
                        .map { it.second }

                    ParentNotificationsDataDto(
                        notifications = ordered,
                        unreadCount = ordered.count { it.unread },
                    )
                }
                call.ok(data, message = "Notifications fetched")
            }
        }
    }
}
