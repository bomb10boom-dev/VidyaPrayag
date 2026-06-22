/*
 * File: SchoolProfileModels.kt
 * Module: feature.admin.domain.model
 *
 * RA-47: DTOs for the institutional-profile (schools row) read/edit API.
 * Matches server: feature.school.SchoolProfileRouting.kt
 *   GET /api/v1/school/profile
 *   PUT /api/v1/school/profile
 *
 * Field names mirror the server @SerialName so the same JSON decodes on both
 * sides. Update fields are nullable → only non-null fields are sent/written
 * (PATCH semantics).
 */
package com.littlebridge.enrollplus.feature.admin.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SchoolProfileDto(
    val id: String,
    val name: String,
    val board: String,
    val medium: String,
    @SerialName("school_gender") val schoolGender: String,
    @SerialName("contact_phone") val contactPhone: String? = null,
    @SerialName("contact_email") val contactEmail: String? = null,
    @SerialName("principal_name") val principalName: String? = null,
    @SerialName("principal_phone") val principalPhone: String? = null,
    @SerialName("principal_email") val principalEmail: String? = null,
    @SerialName("full_address") val fullAddress: String? = null,
    val city: String,
    val district: String,
    val state: String,
    val pincode: String? = null,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("brand_color") val brandColor: String
)

@Serializable
data class UpdateSchoolProfileRequest(
    val name: String? = null,
    val board: String? = null,
    val medium: String? = null,
    @SerialName("school_gender") val schoolGender: String? = null,
    @SerialName("contact_phone") val contactPhone: String? = null,
    @SerialName("contact_email") val contactEmail: String? = null,
    @SerialName("principal_name") val principalName: String? = null,
    @SerialName("principal_phone") val principalPhone: String? = null,
    @SerialName("principal_email") val principalEmail: String? = null,
    @SerialName("full_address") val fullAddress: String? = null,
    val city: String? = null,
    val district: String? = null,
    val state: String? = null,
    val pincode: String? = null,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("brand_color") val brandColor: String? = null
)
