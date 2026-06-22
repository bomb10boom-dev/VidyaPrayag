package com.littlebridge.enrollplus.feature.admin.data.repository

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.admin.data.remote.AttendanceApi
import com.littlebridge.enrollplus.feature.admin.domain.model.AttendanceResponse
import com.littlebridge.enrollplus.feature.admin.domain.repository.AttendanceRepository

class AttendanceRepositoryImpl(
    private val api: AttendanceApi
) : AttendanceRepository {

    override suspend fun getDailyAttendance(
        token: String,
        type: String,
        grade: String?,
        date: String?
    ): NetworkResult<ApiResponse<AttendanceResponse>> = api.getDailyAttendance(token, type, grade, date)
}
