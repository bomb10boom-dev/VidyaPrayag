/*
 * File: MediaApi.kt
 * Module: feature.admin.data.remote
 *
 * Client for the REAL binary-media upload endpoint. This is what lets the
 * school-side surfaces (logo, cover photo, gallery, tour videos, profile
 * pictures) upload genuine files instead of pasting placeholder URLs.
 *
 * Server routes:
 *   POST   /api/v1/school/media/upload   (multipart: file + kind)  → MediaUploadResponse
 *   DELETE /api/v1/school/media          (json: { url })
 *
 * Flow:
 *   1) Platform image picker returns raw bytes (PickedMedia).
 *   2) uploadMedia() POSTs them here → Supabase Storage → public url.
 *   3) Caller stores that url via the existing gallery / branding flows.
 */
package com.littlebridge.enrollplus.feature.admin.data.remote

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.network.safeApiCall
import com.littlebridge.enrollplus.feature.admin.domain.model.MediaUploadResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
private data class DeleteMediaBody(val url: String)

class MediaApi(
    private val client: HttpClient,
    private val baseUrl: String
) {
    private fun getUrl(path: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cleanPath = if (path.startsWith("/")) path.substring(1) else path
        return "$base$cleanPath"
    }

    /**
     * Upload binary bytes to Supabase Storage via the server.
     *
     * @param kind one of IMAGE | VIDEO | LOGO | PROFILE
     */
    suspend fun uploadMedia(
        token: String,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        kind: String
    ): NetworkResult<ApiResponse<MediaUploadResponse>> = safeApiCall {
        client.post(getUrl("api/v1/school/media/upload")) {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("kind", kind)
                        append(
                            "file",
                            bytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, mimeType)
                                append(
                                    HttpHeaders.ContentDisposition,
                                    "filename=\"$fileName\""
                                )
                            }
                        )
                    }
                )
            )
        }
    }

    /** Remove a previously-uploaded media object by its public url. */
    suspend fun deleteMedia(
        token: String,
        url: String
    ): NetworkResult<ApiResponse<Unit>> = safeApiCall {
        client.delete(getUrl("api/v1/school/media")) {
            contentType(ContentType.Application.Json)
            setBody(DeleteMediaBody(url))
        }
    }
}
