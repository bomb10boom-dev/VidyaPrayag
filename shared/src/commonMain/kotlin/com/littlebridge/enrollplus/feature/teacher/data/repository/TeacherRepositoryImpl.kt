package com.littlebridge.enrollplus.feature.teacher.data.repository

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.teacher.data.remote.TeacherApi
import com.littlebridge.enrollplus.feature.teacher.domain.model.*
import com.littlebridge.enrollplus.feature.teacher.domain.repository.TeacherRepository

class TeacherRepositoryImpl(
    private val api: TeacherApi,
) : TeacherRepository {
    override suspend fun getHome(token: String): NetworkResult<TeacherHomeResponse> =
        api.getHome(token)

    override suspend fun getClasses(token: String): NetworkResult<TeacherClassesResponse> =
        api.getClasses(token)

    override suspend fun getAttendance(token: String, classId: String, date: String): NetworkResult<TeacherAttendanceResponse> =
        api.getAttendance(token, classId, date)

    override suspend fun getMarks(token: String, classId: String, examId: String): NetworkResult<TeacherMarksResponse> =
        api.getMarks(token, classId, examId)

    override suspend fun getSyllabus(token: String, classId: String, subject: String): NetworkResult<TeacherSyllabusResponse> =
        api.getSyllabus(token, classId, subject)

    override suspend fun getHomework(token: String): NetworkResult<TeacherHomeworkResponse> =
        api.getHomework(token)

    override suspend fun getProfile(token: String): NetworkResult<TeacherProfileResponse> =
        api.getProfile(token)

    override suspend fun getAssessments(token: String, classId: String): NetworkResult<TeacherAssessmentsResponse> =
        api.getAssessments(token, classId)

    override suspend fun submitAttendance(token: String, request: SubmitAttendanceRequest): NetworkResult<ApiResponse<Unit>> =
        api.submitAttendance(token, request)

    override suspend fun submitMarks(token: String, request: SubmitMarksRequest): NetworkResult<ApiResponse<Unit>> =
        api.submitMarks(token, request)

    override suspend fun updateSyllabus(token: String, request: UpdateSyllabusRequest): NetworkResult<ApiResponse<Unit>> =
        api.updateSyllabus(token, request)

    override suspend fun createHomework(token: String, request: CreateHomeworkRequest): NetworkResult<ApiResponse<Unit>> =
        api.createHomework(token, request)

    override suspend fun createAssessment(token: String, request: CreateAssessmentRequest): NetworkResult<ApiResponse<TeacherAssessmentDto>> =
        api.createAssessment(token, request)

    override suspend fun getLeaveRequests(token: String, status: String?): NetworkResult<TeacherLeaveListResponse> =
        api.getLeaveRequests(token, status)

    override suspend fun decideLeaveRequest(token: String, id: String, request: TeacherLeaveDecisionRequest): NetworkResult<ApiResponse<Unit>> =
        api.decideLeaveRequest(token, id, request)

    override suspend fun broadcastToClass(token: String, request: TeacherClassBroadcastRequest): NetworkResult<TeacherClassBroadcastResponse> =
        api.broadcastToClass(token, request)
}
