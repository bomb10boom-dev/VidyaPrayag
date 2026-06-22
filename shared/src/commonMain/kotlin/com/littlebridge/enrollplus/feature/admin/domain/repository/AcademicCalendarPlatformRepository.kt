/*
 * File: AcademicCalendarPlatformRepository.kt
 * Module: feature.admin.domain.repository
 *
 * Repository contract for the Academic Calendar platform (VP-CAL).
 */
package com.littlebridge.enrollplus.feature.admin.domain.repository

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.admin.domain.model.AcademicCalendarEventDto
import com.littlebridge.enrollplus.feature.admin.domain.model.CalendarDashboardDto
import com.littlebridge.enrollplus.feature.admin.domain.model.CalendarEventsListResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.CreateCalendarEventRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.DuplicateCalendarEventRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.UpdateCalendarEventRequest

interface AcademicCalendarPlatformRepository {
    suspend fun getDashboard(token: String): NetworkResult<ApiResponse<CalendarDashboardDto>>
    suspend fun getEvents(
        token: String,
        month: String? = null,
        status: String? = null,
        type: String? = null
    ): NetworkResult<ApiResponse<CalendarEventsListResponse>>
    suspend fun getEvent(token: String, eventId: String): NetworkResult<ApiResponse<AcademicCalendarEventDto>>
    suspend fun createEvent(token: String, request: CreateCalendarEventRequest): NetworkResult<ApiResponse<AcademicCalendarEventDto>>
    suspend fun updateEvent(token: String, eventId: String, request: UpdateCalendarEventRequest): NetworkResult<ApiResponse<AcademicCalendarEventDto>>
    suspend fun deleteEvent(token: String, eventId: String): NetworkResult<ApiResponse<Unit>>
    suspend fun duplicateEvent(token: String, eventId: String, request: DuplicateCalendarEventRequest): NetworkResult<ApiResponse<AcademicCalendarEventDto>>
}
