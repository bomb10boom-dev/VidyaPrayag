/*
 * File: TeacherAssignmentApi.kt
 * Module: feature.admin.data.remote
 *
 * RA-TAM: network client for the Teacher Assignment Management surface. Bearer
 * token is attached by the shared HttpClient Auth plugin.
 *
 * Server routes (feature.school.TeacherAssignmentRouting.kt):
 *   GET    /api/v1/school/teacher-assignments/overview/{teacherId}
 *   GET    /api/v1/school/teacher-assignments/options
 *   POST   /api/v1/school/teacher-assignments/bulk/{teacherId}
 *   DELETE /api/v1/school/teacher-assignments/{teacherId}/items/{assignmentId}
 */
package com.littlebridge.enrollplus.feature.admin.data.remote

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.network.safeApiCall
import com.littlebridge.enrollplus.feature.admin.domain.model.AssignTeacherClassesRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.AssignmentOptionsDto
import com.littlebridge.enrollplus.feature.admin.domain.model.BulkAssignResponseDto
import com.littlebridge.enrollplus.feature.admin.domain.model.TeacherAssignmentOverviewDto
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class TeacherAssignmentApi(
    private val client: HttpClient,
    private val baseUrl: String
) {

    private fun getUrl(path: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cleanPath = if (path.startsWith("/")) path.substring(1) else path
        return "$base$cleanPath"
    }

    /** Overview: summary + current assignments + insights + distribution. */
    suspend fun getOverview(
        token: String,
        teacherId: String
    ): NetworkResult<ApiResponse<TeacherAssignmentOverviewDto>> = safeApiCall {
        client.get(getUrl("api/v1/school/teacher-assignments/overview/$teacherId"))
    }

    /** Selector options: classes (with sections) + subjects. */
    suspend fun getOptions(
        token: String
    ): NetworkResult<ApiResponse<AssignmentOptionsDto>> = safeApiCall {
        client.get(getUrl("api/v1/school/teacher-assignments/options"))
    }

    /** Bulk assign: one subject → many class+section targets, one save. */
    suspend fun bulkAssign(
        token: String,
        teacherId: String,
        request: AssignTeacherClassesRequest
    ): NetworkResult<ApiResponse<BulkAssignResponseDto>> = safeApiCall {
        client.post(getUrl("api/v1/school/teacher-assignments/bulk/$teacherId")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    /** Remove a single assignment scoped to the teacher. */
    suspend fun removeAssignment(
        token: String,
        teacherId: String,
        assignmentId: String
    ): NetworkResult<ApiResponse<Unit>> = safeApiCall {
        client.delete(getUrl("api/v1/school/teacher-assignments/$teacherId/items/$assignmentId"))
    }
}
