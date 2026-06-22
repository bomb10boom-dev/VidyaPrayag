package com.littlebridge.enrollplus.feature.admin.domain.repository

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.admin.domain.model.CreateLeaveRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.LeaveRequestDto
import com.littlebridge.enrollplus.feature.admin.domain.model.LeaveRequestsResponse

interface LeaveRequestsRepository {
    suspend fun getLeaveRequests(token: String, type: String = "student", status: String? = null): NetworkResult<ApiResponse<LeaveRequestsResponse>>
    suspend fun createLeaveRequest(token: String, request: CreateLeaveRequest): NetworkResult<ApiResponse<LeaveRequestDto>>
    suspend fun updateLeaveStatus(token: String, id: String, status: String): NetworkResult<ApiResponse<LeaveRequestDto>>
}
