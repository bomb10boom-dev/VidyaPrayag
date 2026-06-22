/*
 * File: UserProfileRouting.kt
 * Module: feature.user
 *
 * Endpoints implemented:
 *   GET /api/v1/user/profile?user_id=…             (JWT, school role)
 *   PUT /api/v1/user/profile/philosophy            (JWT, school role)
 *   PUT /api/v1/user/profile/tour-videos           (JWT, school role)
 *   PUT /api/v1/user/profile/gallery               (JWT, school role)
 *   PUT /api/v1/user/profile/visibility            (JWT, school role)
 *
 * Authorization:
 *   Every endpoint is guarded by call.requireSchoolContext(): the caller must
 *   hold a school role (school_admin/school_staff/admin) AND have a school. The
 *   resolved school_id is the ONLY school any of these reads/writes touch, so a
 *   caller can never read or mutate another school's profile/media.
 *
 * Spec ref: vidya_prayag_api_spec2.artifact.md §Screen: User Profile / School Profile
 *
 * Storage strategy (aligned to Supabase schema v2.1):
 *   - philosophy            → 1 row in school_philosophy keyed by school_id (UUID)
 *   - videos + gallery imgs → many rows in school_media (kind = "VIDEO" | "IMAGE")
 *   - storage info          → 1 row in storage_metrics keyed by school_id (UUID)
 *
 *   For PUT /tour-videos and PUT /gallery, the request body holds the FULL
 *   desired list. We DELETE-then-INSERT all rows of the matching kind to keep
 *   the implementation simple and correct (idempotent sync). The spec calls
 *   this "Delete / Insert (Sync list)".
 *
 * Storage stats:
 *   bytes_used is computed as SUM(school_media.size_bytes) WHERE kind='IMAGE'.
 *   storage_used (human-readable) is rendered from bytes_used via formatBytes().
 *   Since binary upload isn't wired in yet, individual rows currently have
 *   size_bytes = 0; an MVP estimate of 200 KB per image is applied so the UI
 *   shows movement until real uploads land.
 *
 * Used by UI:
 *   - composeApp/.../ui/screens/admin/InstitutionalProfileScreen.kt
 */
package com.littlebridge.enrollplus.feature.user

import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.okMessage
import com.littlebridge.enrollplus.core.requireSchoolContext
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.SchoolMediaTable
import com.littlebridge.enrollplus.db.SchoolPhilosophyTable
import com.littlebridge.enrollplus.db.StorageMetricsTable
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

// ---------- DTOs ----------

@Serializable
data class PhilosophyDetails(
    @SerialName("core_mission") val coreMission: String? = null,
    @SerialName("learning_model") val learningModel: String? = null,
    @SerialName("primary_language") val primaryLanguage: String? = null
)

@Serializable
data class GalleryBlock(
    val images: List<String>,
    @SerialName("total_storage") val totalStorage: String,
    @SerialName("storage_used") val storageUsed: String
)

@Serializable
data class UserProfileResponse(
    @SerialName("public_profile") val publicProfile: Boolean,
    @SerialName("philosophy_details") val philosophyDetails: PhilosophyDetails,
    @SerialName("video_tour_data") val videoTourData: List<String>,
    val gallery: GalleryBlock
)

@Serializable
data class TourVideosRequest(
    @SerialName("video_tour_data") val videoTourData: List<String>
)

@Serializable
data class GalleryRequest(
    val images: List<String>
)

@Serializable
data class GalleryUpdateResponse(
    @SerialName("storage_used") val storageUsed: String,
    @SerialName("total_storage") val totalStorage: String
)

@Serializable
data class VisibilityRequest(
    @SerialName("public_profile") val publicProfile: Boolean
)

@Serializable
data class VisibilityResponse(
    @SerialName("public_profile") val publicProfile: Boolean
)

// ---------- helpers ----------

/** Format a byte count as a human-readable string (GB / MB / KB / B). */
private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val gb = 1024.0 * 1024 * 1024
    val mb = 1024.0 * 1024
    val kb = 1024.0
    return when {
        bytes >= gb -> String.format("%.1f GB", bytes / gb)
        bytes >= mb -> String.format("%.1f MB", bytes / mb)
        bytes >= kb -> String.format("%.1f KB", bytes / kb)
        else -> "$bytes B"
    }
}

/** MVP estimate: 200 KB per image until binary uploads land. */
private const val MVP_BYTES_PER_IMAGE: Long = 200L * 1024L

private fun ensureStorageRow(schoolId: UUID) {
    val exists = StorageMetricsTable.selectAll()
        .where { StorageMetricsTable.schoolId eq schoolId }
        .count() > 0L
    if (!exists) {
        StorageMetricsTable.insert {
            it[StorageMetricsTable.schoolId] = schoolId
            it[totalStorage] = "10 GB"
            it[storageUsed] = "0 B"
            it[bytesUsed] = 0L
            it[updatedAt] = Instant.now()
        }
    }
}

private fun ensurePhilosophyRow(schoolId: UUID) {
    val exists = SchoolPhilosophyTable.selectAll()
        .where { SchoolPhilosophyTable.schoolId eq schoolId }
        .count() > 0L
    if (!exists) {
        SchoolPhilosophyTable.insert {
            it[SchoolPhilosophyTable.schoolId] = schoolId
            it[publicProfile] = true
            it[updatedAt] = Instant.now()
        }
    }
}

/**
 * Recompute storage_metrics.bytes_used / storage_used from the actual rows
 * in school_media (sum of size_bytes for IMAGE kind). Always called after
 * a /gallery sync so the UI reflects truth.
 *
 * We compute the sum client-side (one query, all rows mapped) instead of
 * via a SQL aggregate to keep the Exposed call surface minimal and
 * unambiguously portable across SQLite (local dev) and Postgres (prod).
 * Row counts are small (≤ a few hundred per school).
 */
private fun recomputeStorageUsage(schoolId: UUID): Pair<Long, String> {
    val imageRows = SchoolMediaTable.selectAll()
        .where { (SchoolMediaTable.schoolId eq schoolId) and (SchoolMediaTable.kind eq "IMAGE") }
        .toList()
    val realBytes = imageRows.sumOf { it[SchoolMediaTable.sizeBytes] }
    val imageCount = imageRows.size.toLong()

    // If no real bytes are recorded (size_bytes=0 across rows), fall back to
    // the MVP estimate of 200KB per image. This avoids "0 B" while we wait
    // for real file uploads to be wired in.
    val effectiveBytes = if (realBytes > 0) realBytes else imageCount * MVP_BYTES_PER_IMAGE
    val human = formatBytes(effectiveBytes)

    ensureStorageRow(schoolId)
    StorageMetricsTable.update({ StorageMetricsTable.schoolId eq schoolId }) {
        it[bytesUsed] = effectiveBytes
        it[storageUsed] = human
        it[updatedAt] = Instant.now()
    }
    return effectiveBytes to human
}

// ---------- Routing ----------

fun Route.userProfileRouting() {
    authenticate("jwt") {
        route("/api/v1/user/profile") {

            // -------- GET /profile --------
            get {
                val ctx = call.requireSchoolContext() ?: return@get
                val schoolId = ctx.schoolId
                val data = dbQuery {
                    val phil = SchoolPhilosophyTable.selectAll()
                        .where { SchoolPhilosophyTable.schoolId eq schoolId }
                        .firstOrNull()
                    val mediaRows = SchoolMediaTable.selectAll()
                        .where { SchoolMediaTable.schoolId eq schoolId }
                        .orderBy(SchoolMediaTable.position)
                        .toList()
                    val videos = mediaRows.filter { it[SchoolMediaTable.kind] == "VIDEO" }
                        .map { it[SchoolMediaTable.url] }
                    val images = mediaRows.filter { it[SchoolMediaTable.kind] == "IMAGE" }
                        .map { it[SchoolMediaTable.url] }
                    val storage = StorageMetricsTable.selectAll()
                        .where { StorageMetricsTable.schoolId eq schoolId }
                        .firstOrNull()

                    UserProfileResponse(
                        publicProfile = phil?.get(SchoolPhilosophyTable.publicProfile) ?: true,
                        philosophyDetails = PhilosophyDetails(
                            coreMission = phil?.get(SchoolPhilosophyTable.coreMission),
                            learningModel = phil?.get(SchoolPhilosophyTable.learningModel),
                            primaryLanguage = phil?.get(SchoolPhilosophyTable.primaryLanguage)
                        ),
                        videoTourData = videos,
                        gallery = GalleryBlock(
                            images = images,
                            totalStorage = storage?.get(StorageMetricsTable.totalStorage) ?: "10 GB",
                            storageUsed = storage?.get(StorageMetricsTable.storageUsed) ?: "0 B"
                        )
                    )
                }
                call.ok(data, message = "Profile fetched successfully")
            }

            // -------- PUT /philosophy --------
            put("/philosophy") {
                val ctx = call.requireSchoolContext() ?: return@put
                val schoolId = ctx.schoolId
                val req = call.receive<PhilosophyDetails>()
                dbQuery {
                    ensurePhilosophyRow(schoolId)
                    SchoolPhilosophyTable.update({ SchoolPhilosophyTable.schoolId eq schoolId }) {
                        req.coreMission?.let { v -> it[coreMission] = v }
                        req.learningModel?.let { v -> it[learningModel] = v }
                        req.primaryLanguage?.let { v -> it[primaryLanguage] = v }
                        it[updatedAt] = Instant.now()
                    }
                }
                call.okMessage("Philosophy updated successfully")
            }

            // -------- PUT /tour-videos --------
            put("/tour-videos") {
                val ctx = call.requireSchoolContext() ?: return@put
                val schoolId = ctx.schoolId
                val uid = ctx.userId
                val req = call.receive<TourVideosRequest>()
                dbQuery {
                    SchoolMediaTable.deleteWhere {
                        (SchoolMediaTable.schoolId eq schoolId) and (SchoolMediaTable.kind eq "VIDEO")
                    }
                    req.videoTourData.forEachIndexed { idx, url ->
                        SchoolMediaTable.insert {
                            it[SchoolMediaTable.schoolId] = schoolId
                            it[kind] = "VIDEO"
                            it[SchoolMediaTable.url] = url
                            it[position] = idx
                            it[sizeBytes] = 0L
                            it[uploadedBy] = uid
                            it[createdAt] = Instant.now()
                        }
                    }
                }
                call.okMessage("Tour videos updated successfully")
            }

            // -------- PUT /gallery --------
            put("/gallery") {
                val ctx = call.requireSchoolContext() ?: return@put
                val schoolId = ctx.schoolId
                val uid = ctx.userId
                val req = call.receive<GalleryRequest>()
                val data = dbQuery {
                    SchoolMediaTable.deleteWhere {
                        (SchoolMediaTable.schoolId eq schoolId) and (SchoolMediaTable.kind eq "IMAGE")
                    }
                    req.images.forEachIndexed { idx, url ->
                        SchoolMediaTable.insert {
                            it[SchoolMediaTable.schoolId] = schoolId
                            it[kind] = "IMAGE"
                            it[SchoolMediaTable.url] = url
                            it[position] = idx
                            // size_bytes will be populated once binary upload
                            // is wired in. For now we leave it 0 and rely on
                            // recomputeStorageUsage()'s MVP fallback.
                            it[sizeBytes] = 0L
                            it[uploadedBy] = uid
                            it[createdAt] = Instant.now()
                        }
                    }
                    recomputeStorageUsage(schoolId)
                    val row = StorageMetricsTable.selectAll()
                        .where { StorageMetricsTable.schoolId eq schoolId }
                        .single()
                    GalleryUpdateResponse(
                        storageUsed = row[StorageMetricsTable.storageUsed],
                        totalStorage = row[StorageMetricsTable.totalStorage]
                    )
                }
                call.ok(data, message = "Gallery updated successfully")
            }

            // -------- PUT /visibility --------
            // Persists the public/private toggle to school_philosophy.public_profile.
            put("/visibility") {
                val ctx = call.requireSchoolContext() ?: return@put
                val schoolId = ctx.schoolId
                val req = call.receive<VisibilityRequest>()
                dbQuery {
                    ensurePhilosophyRow(schoolId)
                    SchoolPhilosophyTable.update({ SchoolPhilosophyTable.schoolId eq schoolId }) {
                        it[publicProfile] = req.publicProfile
                        it[updatedAt] = Instant.now()
                    }
                }
                call.ok(
                    VisibilityResponse(publicProfile = req.publicProfile),
                    message = if (req.publicProfile) "Profile is now public" else "Profile is now private"
                )
            }
        }
    }
}
