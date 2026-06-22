/*
 * File: SupabaseStorage.kt
 * Module: feature.media
 *
 * Thin, dependency-free wrapper around the Supabase Storage REST API.
 * This is what turns the school-side media flows from "paste a URL"
 * placeholders into REAL binary uploads owned by the school.
 *
 * Why a REST wrapper and not the official supabase-kt SDK?
 * --------------------------------------------------------
 *   - The official SDK pulls in a large coroutine/serialization tree and
 *     its own Ktor client config that fights with ours. On the Render free
 *     tier (512 MB) every extra MB of fat-jar + heap matters.
 *   - Storage's upload/delete surface is two HTTP calls. Wrapping them by
 *     hand keeps the jar lean and the behaviour 100 % predictable across
 *     Render / Android-Studio-run / local.
 *
 * Configuration (all read from env — never hard-coded):
 *   SUPABASE_URL          e.g. https://abcdxyz.supabase.co   (required)
 *   SUPABASE_SERVICE_KEY  service_role key (required — bypasses RLS so the
 *                         server can write on behalf of any school)
 *   SUPABASE_BUCKET       storage bucket name (default: "school-media")
 *
 * If SUPABASE_URL or SUPABASE_SERVICE_KEY are missing, isConfigured() is
 * false and the routing layer returns a clear 503 instead of crashing — so
 * the server still boots on Render even before the user sets the vars.
 *
 * Object path convention (multi-tenant safe):
 *   {schoolId}/{kind}/{uuid}.{ext}
 *   e.g. 3f...e1/gallery/9a...22.jpg
 * Path always starts with the school's UUID so one school can never
 * overwrite another's objects.
 *
 * Public URL returned:
 *   {SUPABASE_URL}/storage/v1/object/public/{bucket}/{objectPath}
 * (assumes the bucket is marked PUBLIC in the Supabase dashboard — see the
 * manual setup guide. For private buckets you'd issue signed URLs instead.)
 */
package com.littlebridge.enrollplus.feature.media

import com.littlebridge.enrollplus.feature.auth.delivery.OtpEnv
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.util.UUID

/** Result of a successful upload: the public URL + the storage object path. */
data class UploadResult(
    val url: String,
    val objectPath: String,
    val sizeBytes: Long,
)

object SupabaseStorage {

    // -- env (resolved lazily so a missing var doesn't break class-load) --
    private val baseUrl: String? get() = OtpEnv.get("SUPABASE_URL")?.trimEnd('/')
    private val serviceKey: String? get() = OtpEnv.get("SUPABASE_SERVICE_KEY")
    private val bucket: String get() = OtpEnv.get("SUPABASE_BUCKET", "school-media")

    /** True only when the minimum env is present, so callers can 503 cleanly. */
    fun isConfigured(): Boolean = baseUrl != null && serviceKey != null

    /** Dedicated client — bigger timeouts than OTP (files are larger). */
    private val client: HttpClient by lazy {
        HttpClient(CIO) {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 60_000   // a 5 MB gallery image on a slow link
                socketTimeoutMillis = 60_000
            }
        }
    }

    /** Allowed content types → file extension. Anything else is rejected. */
    private val IMAGE_TYPES = mapOf(
        "image/jpeg" to "jpg",
        "image/jpg" to "jpg",
        "image/png" to "png",
        "image/webp" to "webp",
        "image/gif" to "gif",
        "image/heic" to "heic",
    )
    private val VIDEO_TYPES = mapOf(
        "video/mp4" to "mp4",
        "video/quicktime" to "mov",
        "video/webm" to "webm",
    )

    fun extensionFor(contentType: String, kind: String): String? {
        val ct = contentType.substringBefore(';').trim().lowercase()
        return when (kind.uppercase()) {
            "VIDEO" -> VIDEO_TYPES[ct]
            else -> IMAGE_TYPES[ct]
        }
    }

    /**
     * Upload raw bytes to Supabase Storage.
     *
     * @param schoolId   tenant — first path segment (isolation)
     * @param kind       IMAGE | VIDEO | LOGO | PROFILE — second path segment
     * @param bytes      file content
     * @param contentType MIME type from the multipart part
     * @return UploadResult on success, or null on any failure (caller maps to 502)
     */
    suspend fun upload(
        schoolId: UUID,
        kind: String,
        bytes: ByteArray,
        contentType: String,
    ): UploadResult? {
        val root = baseUrl ?: return null
        val key = serviceKey ?: return null
        val ext = extensionFor(contentType, kind) ?: "bin"
        val folder = kind.lowercase()
        val objectPath = "$schoolId/$folder/${UUID.randomUUID()}.$ext"
        val endpoint = "$root/storage/v1/object/$bucket/$objectPath"

        return try {
            val resp: HttpResponse = client.post(endpoint) {
                header(HttpHeaders.Authorization, "Bearer $key")
                // x-upsert=false: never silently overwrite (paths are uuid-unique anyway)
                header("x-upsert", "false")
                contentType(ContentType.parse(contentType.substringBefore(';').trim()))
                setBody(bytes)
            }
            if (resp.status.isSuccess()) {
                UploadResult(
                    url = "$root/storage/v1/object/public/$bucket/$objectPath",
                    objectPath = objectPath,
                    sizeBytes = bytes.size.toLong(),
                )
            } else {
                // Surface the gateway message to server logs (never the key).
                System.err.println(
                    "[SupabaseStorage] upload failed ${resp.status.value}: ${resp.bodyAsText().take(300)}"
                )
                null
            }
        } catch (e: Exception) {
            System.err.println("[SupabaseStorage] upload exception: ${e.message}")
            null
        }
    }

    /**
     * Delete an object by its storage path (best-effort — used when a school
     * removes a gallery image so we don't leak orphaned bytes).
     * Returns true on success or if storage isn't configured (no-op).
     */
    suspend fun delete(objectPath: String): Boolean {
        val root = baseUrl ?: return true
        val key = serviceKey ?: return true
        return try {
            val resp = client.delete("$root/storage/v1/object/$bucket/$objectPath") {
                header(HttpHeaders.Authorization, "Bearer $key")
            }
            resp.status.isSuccess()
        } catch (e: Exception) {
            System.err.println("[SupabaseStorage] delete exception: ${e.message}")
            false
        }
    }

    /**
     * Given one of OUR public URLs, recover the object path so we can delete it.
     * Returns null if the URL isn't a Supabase public URL for our bucket.
     */
    fun objectPathFromPublicUrl(url: String): String? {
        val root = baseUrl ?: return null
        val prefix = "$root/storage/v1/object/public/$bucket/"
        return if (url.startsWith(prefix)) url.removePrefix(prefix) else null
    }
}
