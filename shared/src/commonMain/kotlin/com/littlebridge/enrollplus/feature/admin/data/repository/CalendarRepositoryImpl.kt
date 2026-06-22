package com.littlebridge.enrollplus.feature.admin.data.repository

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.admin.data.remote.CalendarApi
import com.littlebridge.enrollplus.feature.admin.domain.model.CalendarResponse
import com.littlebridge.enrollplus.feature.admin.domain.repository.CalendarRepository

class CalendarRepositoryImpl(
    private val api: CalendarApi
) : CalendarRepository {

    override suspend fun getCalendar(
        token: String,
        date: String?,
        viewType: String
    ): NetworkResult<ApiResponse<CalendarResponse>> = api.getCalendar(token, date, viewType)
}
