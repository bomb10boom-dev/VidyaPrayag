package com.littlebridge.enrollplus.feature.admin.domain.repository

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.admin.domain.model.AssignTeacherClassesRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.AssignmentOptionsDto
import com.littlebridge.enrollplus.feature.admin.domain.model.BulkAssignResponseDto
import com.littlebridge.enrollplus.feature.admin.domain.model.TeacherAssignmentOverviewDto

/** RA-TAM: Teacher Assignment Management reads & writes (single source of truth). */
interface TeacherAssignmentRepository {
    suspend fun getOverview(token: String, teacherId: String): NetworkResult<ApiResponse<TeacherAssignmentOverviewDto>>
    suspend fun getOptions(token: String): NetworkResult<ApiResponse<AssignmentOptionsDto>>
    suspend fun bulkAssign(token: String, teacherId: String, request: AssignTeacherClassesRequest): NetworkResult<ApiResponse<BulkAssignResponseDto>>
    suspend fun removeAssignment(token: String, teacherId: String, assignmentId: String): NetworkResult<ApiResponse<Unit>>
}
