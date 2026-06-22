/*
 * File: LeaveRequestModels.kt
 * Module: feature.admin.domain.model
 *
 * DTOs for leave requests endpoints.
 * Matches server: feature.school.LeaveRequestsRouting.kt
 *
 * GET  /api/v1/school/leave-requests?type=student|teacher&status=Pending|Approved|Rejected
 * POST /api/v1/school/leave-requests
 * PATCH /api/v1/school/leave-requests/{id}/status
 */
package com.littlebridge.enrollplus.feature.admin.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LeaveRequestDto(
    val id: String,
    @SerialName("requester_name") val requesterName: String,
    @SerialName("requester_role") val requesterRole: String,
    @SerialName("date_from") val dateFrom: String,
    @SerialName("date_to") val dateTo: String,
    @SerialName("date_range") val dateRange: String,
    val reason: String,
    @SerialName("image_url") val imageUrl: String? = null,
    val status: String
)

@Serializable
data class LeaveRequestsResponse(
    val requests: List<LeaveRequestDto>,
    @SerialName("approval_rate") val approvalRate: Int,
    @SerialName("weekly_count") val weeklyCount: Int
)

@Serializable
data class CreateLeaveRequest(
    @SerialName("requester_name") val requesterName: String,
    @SerialName("requester_role") val requesterRole: String, // "student" | "teacher"
    @SerialName("date_from") val dateFrom: String,
    @SerialName("date_to") val dateTo: String,
    val reason: String,
    @SerialName("image_url") val imageUrl: String? = null
)

@Serializable
data class UpdateLeaveStatusRequest(
    val status: String  // "Pending" | "Approved" | "Rejected"
)
