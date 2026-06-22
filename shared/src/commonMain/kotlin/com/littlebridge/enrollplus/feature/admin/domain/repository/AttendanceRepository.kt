package com.littlebridge.enrollplus.feature.admin.domain.repository

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.admin.domain.model.AttendanceResponse

interface AttendanceRepository {
    suspend fun getDailyAttendance(
        token: String,
        type: String = "student",
        grade: String? = null,
        date: String? = null
    ): NetworkResult<ApiResponse<AttendanceResponse>>
}
