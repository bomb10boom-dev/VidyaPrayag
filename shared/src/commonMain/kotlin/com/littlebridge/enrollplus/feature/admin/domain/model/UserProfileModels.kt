/*
 * File: UserProfileModels.kt
 * Module: feature.admin.domain.model
 *
 * DTOs for the school / institutional-profile endpoints.
 * Matches server: feature.user.UserProfileRouting.kt
 *
 *   GET  /api/v1/user/profile                → UserProfileResponse
 *   PUT  /api/v1/user/profile/philosophy     ← PhilosophyDetails
 *   PUT  /api/v1/user/profile/tour-videos    ← TourVideosRequest
 *   PUT  /api/v1/user/profile/gallery        ← GalleryRequest  → GalleryUpdateResponse
 *
 * NOTE: keep field names in lock-step with the Ktor side (snake_case via
 * @SerialName) — this is the source of truth for the wire format.
 */
package com.littlebridge.enrollplus.feature.admin.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PhilosophyDetailsDto(
    @SerialName("core_mission") val coreMission: String? = null,
    @SerialName("learning_model") val learningModel: String? = null,
    @SerialName("primary_language") val primaryLanguage: String? = null
)

@Serializable
data class GalleryBlockDto(
    val images: List<String> = emptyList(),
    @SerialName("total_storage") val totalStorage: String = "10 GB",
    @SerialName("storage_used") val storageUsed: String = "0 B"
)

@Serializable
data class UserProfileResponse(
    @SerialName("public_profile") val publicProfile: Boolean = true,
    @SerialName("philosophy_details") val philosophyDetails: PhilosophyDetailsDto = PhilosophyDetailsDto(),
    @SerialName("video_tour_data") val videoTourData: List<String> = emptyList(),
    val gallery: GalleryBlockDto = GalleryBlockDto()
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

/**
 * Response of POST /api/v1/school/media/upload — the REAL upload endpoint
 * that turns "paste a URL" placeholders into genuine files in Supabase
 * Storage. `url` is the public URL the client then stores in gallery /
 * branding / profile flows.
 */
@Serializable
data class MediaUploadResponse(
    val url: String,
    val kind: String,
    @SerialName("size_bytes") val sizeBytes: Long = 0L
)

/** A picked file ready to upload: raw bytes + mime + suggested name. */
data class PickedMedia(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PickedMedia) return false
        return fileName == other.fileName &&
            mimeType == other.mimeType &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + mimeType.hashCode()
        return result
    }
}
