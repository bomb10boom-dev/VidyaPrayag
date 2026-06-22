/*
 * File: AdmissionModels.kt
 * Module: feature.admin.domain.model
 *
 * Domain models that mirror the server DTOs from
 *   server/.../feature/admissions/AdmissionRouting.kt
 *
 * Endpoints:
 *   GET   /api/v1/admissions/enquiries/summary       (JWT)
 *   GET   /api/v1/admissions/enquiries               (JWT, paginated)
 *   POST  /api/v1/admissions/enquiries               (JWT)
 *   PATCH /api/v1/admissions/enquiries/{id}/status   (JWT)
 *
 * The wire format is snake_case so we use @SerialName everywhere.
 */
package com.littlebridge.enrollplus.feature.admin.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One row in the admissions list / recent-enquiries widget.
 *
 * `status` is one of (server canonical):
 *   "new" | "followup" | "converted" | "rejected"
 */
@Serializable
data class Enquiry(
    val id: String? = null,
    @SerialName("student_name") val studentName: String,
    @SerialName("parent_name") val parentName: String,
    @SerialName("class") val className: String,
    val date: String,
    val status: String,
    @SerialName("profile_pic") val profilePic: String? = null
) {
    companion object {
        const val STATUS_NEW = "new"
        const val STATUS_FOLLOWUP = "followup"
        const val STATUS_CONVERTED = "converted"
        const val STATUS_REJECTED = "rejected"

        val ALLOWED_STATUSES = setOf(STATUS_NEW, STATUS_FOLLOWUP, STATUS_CONVERTED, STATUS_REJECTED)
    }
}

@Serializable
data class EnquirySummaryCount(
    val total: Int,
    val new: Int,
    @SerialName("follow_ups") val followUps: Int,
    val converted: Int
)

/**
 * Response payload for `GET /api/v1/admissions/enquiries/summary`.
 *
 * `efficiency` is a server-computed string like "85%". We keep the raw form so
 * the UI can decide how to render it (badge text, etc.).
 */
@Serializable
data class EnquirySummary(
    @SerialName("summary_count") val summaryCount: EnquirySummaryCount,
    @SerialName("recent_enquiries") val recentEnquiries: List<Enquiry>,
    val efficiency: String
)

@Serializable
data class EnquiryPagination(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("total_pages") val totalPages: Int,
    @SerialName("total_records") val totalRecords: Int
)

@Serializable
data class EnquiryListResponse(
    val enquiries: List<Enquiry>,
    val pagination: EnquiryPagination
)

/**
 * Request body for `POST /api/v1/admissions/enquiries`.
 *
 * `schoolId` is optional - the server falls back to the calling user's school
 * if it is not supplied.
 */
@Serializable
data class CreateEnquiryRequest(
    @SerialName("student_name") val studentName: String,
    @SerialName("parent_name") val parentName: String,
    @SerialName("parent_phone") val parentPhone: String? = null,
    @SerialName("parent_email") val parentEmail: String? = null,
    @SerialName("class") val className: String,
    val source: String? = null,
    val notes: String? = null,
    @SerialName("school_id") val schoolId: String? = null
)

/**
 * Request body for `PATCH /api/v1/admissions/enquiries/{id}/status`.
 *
 * Server validates that `status` is in [Enquiry.ALLOWED_STATUSES].
 */
@Serializable
data class UpdateEnquiryStatusRequest(
    val status: String
)
