package com.littlebridge.enrollplus.feature.teacher.domain.repository

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.teacher.domain.model.*

interface TeacherRepository {
    // Reads
    suspend fun getHome(token: String): NetworkResult<TeacherHomeResponse>
    suspend fun getClasses(token: String): NetworkResult<TeacherClassesResponse>
    suspend fun getAttendance(token: String, classId: String, date: String): NetworkResult<TeacherAttendanceResponse>
    suspend fun getMarks(token: String, classId: String, examId: String): NetworkResult<TeacherMarksResponse>
    suspend fun getSyllabus(token: String, classId: String, subject: String): NetworkResult<TeacherSyllabusResponse>
    suspend fun getHomework(token: String): NetworkResult<TeacherHomeworkResponse>
    suspend fun getProfile(token: String): NetworkResult<TeacherProfileResponse>
    suspend fun getAssessments(token: String, classId: String): NetworkResult<TeacherAssessmentsResponse>

    // Writes
    suspend fun submitAttendance(token: String, request: SubmitAttendanceRequest): NetworkResult<ApiResponse<Unit>>
    suspend fun submitMarks(token: String, request: SubmitMarksRequest): NetworkResult<ApiResponse<Unit>>
    suspend fun updateSyllabus(token: String, request: UpdateSyllabusRequest): NetworkResult<ApiResponse<Unit>>
    suspend fun createHomework(token: String, request: CreateHomeworkRequest): NetworkResult<ApiResponse<Unit>>
    suspend fun createAssessment(token: String, request: CreateAssessmentRequest): NetworkResult<ApiResponse<TeacherAssessmentDto>>

    // RA-44: teacher leave workflow.
    suspend fun getLeaveRequests(token: String, status: String? = null): NetworkResult<TeacherLeaveListResponse>
    suspend fun decideLeaveRequest(token: String, id: String, request: TeacherLeaveDecisionRequest): NetworkResult<ApiResponse<Unit>>

    // RA-51: message all parents of an owned class.
    suspend fun broadcastToClass(token: String, request: TeacherClassBroadcastRequest): NetworkResult<TeacherClassBroadcastResponse>
}
