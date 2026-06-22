/*
 * File: AttendanceModels.kt
 * Module: feature.admin.domain.model
 *
 * DTOs for daily attendance endpoint.
 * Matches server: feature.school.SchoolRouting.kt  GET /api/v1/school/attendance/daily
 */
package com.littlebridge.enrollplus.feature.admin.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AttendanceEntryDto(
    @SerialName("profile_pic") val profilePic: String? = null,
    val name: String,
    val id: String,
    val status: String   // "present" | "absent" | "late" | "half_day"
)

@Serializable
data class AttendanceResponse(
    val type: String,
    val grade: String? = null,
    @SerialName("present_count") val presentCount: Int,
    @SerialName("absent_count") val absentCount: Int,
    @SerialName("total_count") val totalCount: Int,
    @SerialName("attendance_percentage") val attendancePercentage: String,
    @SerialName("attendance_list") val attendanceList: List<AttendanceEntryDto>
)
