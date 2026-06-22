package com.littlebridge.enrollplus.feature.admin.domain.repository

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.admin.domain.model.SchoolProfileDto
import com.littlebridge.enrollplus.feature.admin.domain.model.UpdateSchoolProfileRequest

/** RA-47: read + edit the institutional `schools` row (school-admin write). */
interface SchoolProfileRepository {
    suspend fun getProfile(token: String): NetworkResult<ApiResponse<SchoolProfileDto>>
    suspend fun updateProfile(
        token: String,
        request: UpdateSchoolProfileRequest
    ): NetworkResult<ApiResponse<SchoolProfileDto>>
}
