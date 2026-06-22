/*
 * File: MediaRouting.kt
 * Module: feature.media
 *
 * REAL binary media upload for the school side. This is the foundation that
 * lets every school-side surface (logo, gallery, tour videos, institutional
 * profile pictures) move from "paste a URL" placeholders to genuine files
 * the school uploads from their device.
 *
 * Routes (all JWT + school role):
 *   POST   /api/v1/school/media/upload     — multipart binary → Supabase Storage,
 *                                            returns the public URL + records a
 *                                            row in school_media.
 *   DELETE /api/v1/school/media            — remove a media row + its storage
 *                                            object by url (body: { "url": ... }).
 *
 * Multipart contract for POST /upload:
 *   - form field "file"  : the binary part (required)
 *   - form field "kind"  : IMAGE | VIDEO | LOGO | PROFILE (optional, default IMAGE)
 *
 * Returns 503 (not a crash) if Supabase env vars aren't set yet, so the
 * server boots fine on Render before the user configures storage.
 *
 * Used by UI:
 *   - composeApp admin screens (gallery picker, branding/logo, profile pic).
 *     The client uploads bytes here, gets back a url, then includes that url
 *     in the existing PUT /user/profile/gallery | /tour-videos sync calls.
 */
package com.littlebridge.enrollplus.feature.media

import com.littlebridge.enrollplus.core.fail
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.okMessage
import com.littlebridge.enrollplus.core.requireSchoolAdmin
import com.littlebridge.enrollplus.core.requireSchoolContext
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.SchoolMediaTable
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant

// ---------- DTOs ----------

@Serializable
data class UploadResponse(
    val url: String,
    val kind: String,
    @SerialName("size_bytes") val sizeBytes: Long,
)

@Serializable
data class DeleteMediaRequest(
    val url: String,
)

// Hard cap so a malicious/buggy client can't OOM the 512 MB Render dyno.
private const val MAX_UPLOAD_BYTES = 25L * 1024 * 1024   // 25 MB

private val VALID_KINDS = setOf("IMAGE", "VIDEO", "LOGO", "PROFILE")

fun Application.mediaRouting() {
    routing {
        // Audit finding I (§6): without this authenticate("jwt") block the
        // route has no JWTPrincipal, so requireSchoolContext() always 401s and
        // the upload is dead. Wrapping it (mirroring ResultsRouting.kt:234)
        // makes the school-scoped upload/delete actually reachable.
        authenticate("jwt") {
            route("/api/v1/school/media") {

                // -------- POST /upload --------
                post("/upload") {
                val ctx = call.requireSchoolContext() ?: return@post

                if (!SupabaseStorage.isConfigured()) {
                    call.fail(
                        "Media storage is not configured on the server. " +
                            "Set SUPABASE_URL and SUPABASE_SERVICE_KEY env vars.",
                        status = HttpStatusCode.ServiceUnavailable,
                        errorCode = "STORAGE_NOT_CONFIGURED",
                    )
                    return@post
                }

                var kind = "IMAGE"
                var fileBytes: ByteArray? = null
                var contentType: String? = null

                val multipart = call.receiveMultipart()
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            if (part.name == "kind") {
                                val v = part.value.trim().uppercase()
                                if (v in VALID_KINDS) kind = v
                            }
                        }
                        is PartData.FileItem -> {
                            if (part.name == "file" && fileBytes == null) {
                                contentType = part.contentType?.toString()
                                    ?: part.originalFileName?.let { guessContentType(it) }
                                @Suppress("DEPRECATION")
                                fileBytes = part.streamProvider().use { it.readBytes() }
                            }
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                val bytes = fileBytes
                if (bytes == null || bytes.isEmpty()) {
                    call.fail("No file part found (expected multipart field 'file').")
                    return@post
                }
                if (bytes.size > MAX_UPLOAD_BYTES) {
                    call.fail(
                        "File too large (${bytes.size / (1024 * 1024)} MB). Max 25 MB.",
                        status = HttpStatusCode.PayloadTooLarge,
                    )
                    return@post
                }
                val ct = contentType
                if (ct == null || SupabaseStorage.extensionFor(ct, kind) == null) {
                    call.fail(
                        "Unsupported file type '${ct ?: "unknown"}' for kind $kind.",
                        status = HttpStatusCode.UnsupportedMediaType,
                    )
                    return@post
                }

                val result = SupabaseStorage.upload(ctx.schoolId, kind, bytes, ct)
                if (result == null) {
                    call.fail(
                        "Upload to storage failed. Please try again.",
                        status = HttpStatusCode.BadGateway,
                        errorCode = "STORAGE_UPLOAD_FAILED",
                    )
                    return@post
                }

                // Record IMAGE/VIDEO in school_media so storage metrics + gallery
                // reads stay consistent. LOGO/PROFILE are referenced directly on
                // the school row by the caller, so we don't double-count them.
                if (kind == "IMAGE" || kind == "VIDEO") {
                    dbQuery {
                        val nextPos = SchoolMediaTable.selectAll()
                            .where {
                                (SchoolMediaTable.schoolId eq ctx.schoolId) and
                                    (SchoolMediaTable.kind eq kind)
                            }
                            .count().toInt()
                        SchoolMediaTable.insert {
                            it[schoolId] = ctx.schoolId
                            it[SchoolMediaTable.kind] = kind
                            it[url] = result.url
                            it[position] = nextPos
                            it[sizeBytes] = result.sizeBytes
                            it[uploadedBy] = ctx.userId
                            it[createdAt] = Instant.now()
                        }
                    }
                }

                call.ok(
                    UploadResponse(url = result.url, kind = kind, sizeBytes = result.sizeBytes),
                    message = "Upload successful",
                )
            }

            // -------- DELETE / (by url) — privileged: RA-39 --------
            delete {
                val ctx = call.requireSchoolAdmin() ?: return@delete
                val req = call.receive<DeleteMediaRequest>()
                val url = req.url.trim()
                if (url.isEmpty()) {
                    call.fail("url is required")
                    return@delete
                }

                val removed = dbQuery {
                    SchoolMediaTable.deleteWhere {
                        (SchoolMediaTable.schoolId eq ctx.schoolId) and
                            (SchoolMediaTable.url eq url)
                    }
                }

                // Best-effort storage cleanup so we don't leak orphaned bytes.
                SupabaseStorage.objectPathFromPublicUrl(url)?.let {
                    SupabaseStorage.delete(it)
                }

                call.okMessage(
                    if (removed > 0) "Media removed" else "Media not found (already removed)",
                )
            }
            }
        }
    }
}

/** Fallback MIME guess from filename when the part has no content type. */
private fun guessContentType(fileName: String): String? =
    when (fileName.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "heic" -> "image/heic"
        "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        "webm" -> "video/webm"
        else -> null
    }
