/*
 * File: AnnouncementModels.kt
 * Module: feature.admin.domain.model
 *
 * DTOs for the school announcements API.
 * Matches server: feature.announcements.AnnouncementRouting.kt
 */
package com.littlebridge.enrollplus.feature.admin.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AnnouncementDto(
    val type: String,
    @SerialName("event_id") val eventId: String,
    val title: String,
    @SerialName("sub_title") val subTitle: String? = null,
    val description: String,
    @SerialName("event_image") val eventImage: String? = null,
    val date: String,
    // RA-49: audience segmentation echoed back by the server so the list/detail
    // can surface who a post was targeted at. Defaults keep older posts working.
    @SerialName("audience_type") val audienceType: String = "ALL_SCHOOL",
    @SerialName("audience_filter") val audienceFilter: JsonElement? = null
)

@Serializable
data class AnnouncementListResponse(
    val announcements: List<AnnouncementDto>
)

@Serializable
data class CreateAnnouncementRequest(
    val type: String,
    val title: String,
    @SerialName("sub_title") val subTitle: String? = null,
    val description: String,
    @SerialName("event_image") val eventImage: String? = null,
    val date: String,
    // RA-49: audience targeting from the UI. audienceType is one of
    // ALL_SCHOOL / CLASS / SECTION / SUBJECT / STUDENT / CUSTOM (server
    // validates). audienceFilter carries the scope JSON (e.g. class_names).
    // Both omitted → server defaults to ALL_SCHOOL (back-compat).
    @SerialName("audience_type") val audienceType: String? = null,
    @SerialName("audience_filter") val audienceFilter: JsonElement? = null,
    // VP-CAL: when the UI's "Add To Academic Calendar" toggle is enabled (the
    // default) and the type is Holiday/PTM/Event, the server also creates a
    // matching calendar event (source = ANNOUNCEMENT). No-op for Update/Reminder.
    @SerialName("add_to_calendar") val addToCalendar: Boolean = true
)

@Serializable
data class SyncWhatsAppResponse(
    @SerialName("job_id") val jobId: String,
    @SerialName("total_queued") val totalQueued: Int,
    @SerialName("estimated_time_minutes") val estimatedTimeMinutes: Int
)
