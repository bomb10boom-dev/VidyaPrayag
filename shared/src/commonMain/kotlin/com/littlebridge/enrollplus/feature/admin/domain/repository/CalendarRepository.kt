package com.littlebridge.enrollplus.feature.admin.domain.repository

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.admin.domain.model.CalendarResponse

interface CalendarRepository {
    suspend fun getCalendar(token: String, date: String? = null, viewType: String = "month"): NetworkResult<ApiResponse<CalendarResponse>>
}
