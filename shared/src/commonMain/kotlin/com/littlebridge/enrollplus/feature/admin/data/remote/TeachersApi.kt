/*
 * File: TeachersApi.kt
 * Module: feature.admin.data.remote
 *
 * Network client for school teacher-provisioning endpoints (RA-22). These
 * endpoints existed on the server but had NO client, so the admin app could
 * not list, add, or remove teachers. This wires the full roster surface.
 *
 * Server routes (feature.school.TeacherProvisioningRouting.kt):
 *   GET    /api/v1/school/teachers
 *   POST   /api/v1/school/teachers
 *   DELETE /api/v1/school/teachers/{id}
 *   POST   /api/v1/school/teachers/{id}/reset-password  (RA-32)
 */
package com.littlebridge.enrollplus.feature.admin.data.remote

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.network.safeApiCall
import com.littlebridge.enrollplus.feature.admin.domain.model.CreateTeacherRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.TeacherAccountDto
import com.littlebridge.enrollplus.feature.admin.domain.model.TeacherCardListResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.TeacherCredentialDto
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class TeachersApi(
    private val client: HttpClient,
    private val baseUrl: String
) {

    private fun getUrl(path: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cleanPath = if (path.startsWith("/")) path.substring(1) else path
        return "$base$cleanPath"
    }

    /**
     * Fetch one page of teacher summary CARDS (server returns every teacher in
     * the school — incl. inactive / unassigned — paginated). page/pageSize map
     * straight onto the server query params.
     */
    suspend fun getTeachers(
        token: String,
        page: Int = 1,
        pageSize: Int = 20
    ): NetworkResult<ApiResponse<TeacherCardListResponse>> = safeApiCall {
        client.get(getUrl("api/v1/school/teachers")) {
            parameter("page", page)
            parameter("pageSize", pageSize)
        }
    }

    suspend fun createTeacher(
        token: String,
        request: CreateTeacherRequest
    ): NetworkResult<ApiResponse<TeacherAccountDto>> = safeApiCall {
        client.post(getUrl("api/v1/school/teachers")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun deleteTeacher(
        token: String,
        teacherId: String
    ): NetworkResult<ApiResponse<Unit>> = safeApiCall {
        client.delete(getUrl("api/v1/school/teachers/$teacherId"))
    }

    /** RA-32: reissue an initial password; server returns the plaintext once. */
    suspend fun resetTeacherPassword(
        token: String,
        teacherId: String
    ): NetworkResult<ApiResponse<TeacherCredentialDto>> = safeApiCall {
        client.post(getUrl("api/v1/school/teachers/$teacherId/reset-password"))
    }
}
