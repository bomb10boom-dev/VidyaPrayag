package com.littlebridge.enrollplus.feature.admin.domain.repository

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.admin.domain.model.CreateStaffRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.StaffDto
import com.littlebridge.enrollplus.feature.admin.domain.model.StaffListResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.UpdateStaffRequest

/** RA-S17: admin Non-teaching-staff roster + profile reads & writes. */
interface StaffRepository {
    suspend fun getStaff(token: String, query: String? = null, department: String? = null): NetworkResult<ApiResponse<StaffListResponse>>
    suspend fun createStaff(token: String, request: CreateStaffRequest): NetworkResult<ApiResponse<StaffDto>>
    suspend fun getStaffProfile(token: String, staffId: String): NetworkResult<ApiResponse<StaffDto>>
    suspend fun updateStaff(token: String, staffId: String, request: UpdateStaffRequest): NetworkResult<ApiResponse<StaffDto>>
    suspend fun deleteStaff(token: String, staffId: String): NetworkResult<ApiResponse<Unit>>
}
