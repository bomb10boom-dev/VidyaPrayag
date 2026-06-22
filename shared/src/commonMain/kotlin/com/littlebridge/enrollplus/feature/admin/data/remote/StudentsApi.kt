/*
 * File: StudentsApi.kt
 * Module: feature.admin.data.remote
 *
 * RA-45: network client for the admin student roster + student/teacher profile
 * endpoints. Bearer token is attached by the shared HttpClient Auth plugin.
 *
 * Server routes (feature.school.SchoolStudentsRouting.kt):
 *   GET    /api/v1/school/students
 *   POST   /api/v1/school/students
 *   DELETE /api/v1/school/students/{id}
 *   GET    /api/v1/school/students/{id}
 *   GET    /api/v1/school/teachers/{id}
 */
package com.littlebridge.enrollplus.feature.admin.data.remote

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.network.safeApiCall
import com.littlebridge.enrollplus.feature.admin.domain.model.BulkImportStudentsRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.BulkImportStudentsResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.CreateStudentRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.StudentDto
import com.littlebridge.enrollplus.feature.admin.domain.model.StudentListResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.StudentProfileDto
import com.littlebridge.enrollplus.feature.admin.domain.model.TeacherProfileDto
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class StudentsApi(
    private val client: HttpClient,
    private val baseUrl: String
) {

    private fun getUrl(path: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cleanPath = if (path.startsWith("/")) path.substring(1) else path
        return "$base$cleanPath"
    }

    suspend fun getStudents(
        token: String
    ): NetworkResult<ApiResponse<StudentListResponse>> = safeApiCall {
        client.get(getUrl("api/v1/school/students"))
    }

    suspend fun createStudent(
        token: String,
        request: CreateStudentRequest
    ): NetworkResult<ApiResponse<StudentDto>> = safeApiCall {
        client.post(getUrl("api/v1/school/students")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun importStudents(
        token: String,
        request: BulkImportStudentsRequest
    ): NetworkResult<ApiResponse<BulkImportStudentsResponse>> = safeApiCall {
        client.post(getUrl("api/v1/school/students/import")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun deleteStudent(
        token: String,
        studentId: String
    ): NetworkResult<ApiResponse<Unit>> = safeApiCall {
        client.delete(getUrl("api/v1/school/students/$studentId"))
    }

    suspend fun getStudentProfile(
        token: String,
        studentId: String
    ): NetworkResult<ApiResponse<StudentProfileDto>> = safeApiCall {
        client.get(getUrl("api/v1/school/students/$studentId"))
    }

    suspend fun getTeacherProfile(
        token: String,
        teacherId: String
    ): NetworkResult<ApiResponse<TeacherProfileDto>> = safeApiCall {
        client.get(getUrl("api/v1/school/teachers/$teacherId"))
    }
}
