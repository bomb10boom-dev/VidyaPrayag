/*
 * File: StaffModels.kt
 * Module: feature.admin.domain.model
 *
 * RA-S17: DTOs for the admin Non-teaching-staff vertical. Mirror server:
 * feature.school.NonTeachingStaffRouting.kt
 *   GET    /api/v1/school/staff           (?q=&department=)
 *   POST   /api/v1/school/staff
 *   GET    /api/v1/school/staff/{id}
 *   PATCH  /api/v1/school/staff/{id}
 *   DELETE /api/v1/school/staff/{id}
 *
 * @SerialName mirrors the server so the same JSON decodes on both sides.
 */
package com.littlebridge.enrollplus.feature.admin.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StaffDto(
    val id: String,
    @SerialName("full_name") val fullName: String,
    val role: String,
    val department: String? = null,
    val phone: String? = null,
    val email: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null
)

@Serializable
data class StaffListResponse(val staff: List<StaffDto>)

@Serializable
data class CreateStaffRequest(
    @SerialName("full_name") val fullName: String,
    val role: String,
    val department: String? = null,
    val phone: String? = null,
    val email: String? = null
)

@Serializable
data class UpdateStaffRequest(
    @SerialName("full_name") val fullName: String? = null,
    val role: String? = null,
    val department: String? = null,
    val phone: String? = null,
    val email: String? = null
)
