/*
 * File: LinkRequestModels.kt
 * Module: feature.admin.domain.model
 *
 * RA-48: client DTOs for the school-admin link-request queue. These mirror the
 * server wire shapes in ParentLinkRouting.kt (LinkRequestDto / LinkRequestList-
 * Response). A school admin lists pending parent→child link requests for THEIR
 * school and approves/rejects them; the server scopes everything to the admin's
 * own school_id (read from the JWT, never the body).
 *
 * Server routes:
 *   GET  /api/v1/school/link-requests?status=pending|approved|rejected
 *   POST /api/v1/school/link-requests/{id}/approve
 *   POST /api/v1/school/link-requests/{id}/reject
 */
package com.littlebridge.enrollplus.feature.admin.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LinkRequestDto(
    val id: String,
    @SerialName("parent_id") val parentId: String,
    @SerialName("parent_name") val parentName: String? = null,
    @SerialName("parent_phone") val parentPhone: String? = null,
    @SerialName("student_code") val studentCode: String? = null,
    @SerialName("roll_number") val rollNumber: String? = null,
    @SerialName("child_name") val childName: String? = null,
    @SerialName("class_name") val className: String? = null,
    val status: String,
    @SerialName("requested_at") val requestedAt: String,
)

@Serializable
data class LinkRequestsResponse(
    val requests: List<LinkRequestDto> = emptyList()
)

/** Wire shape for the approve response body (`{ child_id, status }`). */
@Serializable
data class LinkDecisionResult(
    @SerialName("child_id") val childId: String? = null,
    val status: String = "",
)
