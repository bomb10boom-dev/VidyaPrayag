package com.littlebridge.enrollplus.feature.teacher.data.remote

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.network.safeApiCall
import com.littlebridge.enrollplus.feature.teacher.domain.model.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * TeacherApi — Ktor client for the (new) `api/v1/teacher/…` routes.
 *
 * Mirrors [com.littlebridge.enrollplus.feature.parent.data.remote.ParentApi]: every call is wrapped
 * in [safeApiCall], authenticated with a bearer token, and hits the school base URL. Read endpoints
 * return typed response envelopes; write endpoints return `ApiResponse<Unit>` (server replies
 * `{ success, message }`).
 */
class TeacherApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    private fun getUrl(path: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cleanPath = if (path.startsWith("/")) path.substring(1) else path
        return "$base$cleanPath"
    }

    // ── Reads ───────────────────────────────────────────────────────────────

    suspend fun getHome(token: String): NetworkResult<TeacherHomeResponse> = safeApiCall {
        client.get(getUrl("api/v1/teacher/home"))
    }

    suspend fun getClasses(token: String): NetworkResult<TeacherClassesResponse> = safeApiCall {
        client.get(getUrl("api/v1/teacher/classes"))
    }

    suspend fun getAttendance(
        token: String,
        classId: String,
        date: String,
    ): NetworkResult<TeacherAttendanceResponse> = safeApiCall {
        client.get(getUrl("api/v1/teacher/attendance")) {
            parameter("class_id", classId)
            parameter("date", date)
        }
    }

    suspend fun getMarks(
        token: String,
        classId: String,
        examId: String,
    ): NetworkResult<TeacherMarksResponse> = safeApiCall {
        client.get(getUrl("api/v1/teacher/marks")) {
            parameter("class_id", classId)
            parameter("exam_id", examId)
        }
    }

    suspend fun getSyllabus(
        token: String,
        classId: String,
        subject: String,
    ): NetworkResult<TeacherSyllabusResponse> = safeApiCall {
        client.get(getUrl("api/v1/teacher/syllabus")) {
            parameter("class_id", classId)
            parameter("subject", subject)
        }
    }

    suspend fun getHomework(token: String): NetworkResult<TeacherHomeworkResponse> = safeApiCall {
        client.get(getUrl("api/v1/teacher/homework"))
    }

    // RA-40: list the exams a teacher can mark for an owned class. The marks
    // plane requires a valid exam_id; this is where the exam selector sources it.
    suspend fun getAssessments(
        token: String,
        classId: String,
    ): NetworkResult<TeacherAssessmentsResponse> = safeApiCall {
        client.get(getUrl("api/v1/teacher/assessments")) {
            parameter("class_id", classId)
        }
    }

    suspend fun getProfile(token: String): NetworkResult<TeacherProfileResponse> = safeApiCall {
        client.get(getUrl("api/v1/teacher/profile"))
    }

    // ── Writes ──────────────────────────────────────────────────────────────

    suspend fun submitAttendance(
        token: String,
        request: SubmitAttendanceRequest,
    ): NetworkResult<ApiResponse<Unit>> = safeApiCall {
        client.post(getUrl("api/v1/teacher/attendance")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun submitMarks(
        token: String,
        request: SubmitMarksRequest,
    ): NetworkResult<ApiResponse<Unit>> = safeApiCall {
        client.post(getUrl("api/v1/teacher/marks")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun updateSyllabus(
        token: String,
        request: UpdateSyllabusRequest,
    ): NetworkResult<ApiResponse<Unit>> = safeApiCall {
        client.patch(getUrl("api/v1/teacher/syllabus")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun createHomework(
        token: String,
        request: CreateHomeworkRequest,
    ): NetworkResult<ApiResponse<Unit>> = safeApiCall {
        client.post(getUrl("api/v1/teacher/homework")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    // RA-40: create a new exam for an owned class. Server replies 201 with the
    // created assessment in `data`, so the UI can immediately select it.
    suspend fun createAssessment(
        token: String,
        request: CreateAssessmentRequest,
    ): NetworkResult<ApiResponse<TeacherAssessmentDto>> = safeApiCall {
        client.post(getUrl("api/v1/teacher/assessments")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    // ── RA-44: teacher leave workflow ─────────────────────────────────────────

    /** Leave requests routed to this teacher's classes (optionally status-filtered). */
    suspend fun getLeaveRequests(
        token: String,
        status: String? = null,
    ): NetworkResult<TeacherLeaveListResponse> = safeApiCall {
        client.get(getUrl("api/v1/teacher/leave-requests")) {
            if (status != null) parameter("status", status)
        }
    }

    /** Approve / reject a leave request the teacher owns. */
    suspend fun decideLeaveRequest(
        token: String,
        id: String,
        request: TeacherLeaveDecisionRequest,
    ): NetworkResult<ApiResponse<Unit>> = safeApiCall {
        client.patch(getUrl("api/v1/teacher/leave-requests/$id")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    /** RA-51: broadcast a message to every parent of an owned class. */
    suspend fun broadcastToClass(
        token: String,
        request: TeacherClassBroadcastRequest,
    ): NetworkResult<TeacherClassBroadcastResponse> = safeApiCall {
        client.post(getUrl("api/v1/teacher/messages/class")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }
}
