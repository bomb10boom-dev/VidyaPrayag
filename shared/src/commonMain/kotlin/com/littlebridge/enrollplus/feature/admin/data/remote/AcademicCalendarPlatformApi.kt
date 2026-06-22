/*
 * File: AcademicCalendarPlatformApi.kt
 * Module: feature.admin.data.remote
 *
 * Network client for the Academic Calendar platform (VP-CAL).
 *
 * Server routes (feature.calendar.AcademicCalendarRouting):
 *   GET    /api/admin/calendar/dashboard
 *   GET    /api/admin/calendar/events?month=&status=&type=
 *   POST   /api/admin/calendar/events
 *   GET    /api/admin/calendar/events/{eventId}
 *   PUT    /api/admin/calendar/events/{eventId}
 *   DELETE /api/admin/calendar/events/{eventId}
 *   POST   /api/admin/calendar/events/{eventId}/duplicate
 *
 * Named "…PlatformApi" to avoid clashing with the legacy CalendarApi (month-grid).
 */
package com.littlebridge.enrollplus.feature.admin.data.remote

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.network.safeApiCall
import com.littlebridge.enrollplus.feature.admin.domain.model.AcademicCalendarEventDto
import com.littlebridge.enrollplus.feature.admin.domain.model.CalendarDashboardDto
import com.littlebridge.enrollplus.feature.admin.domain.model.CalendarEventsListResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.CreateCalendarEventRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.DuplicateCalendarEventRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.UpdateCalendarEventRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class AcademicCalendarPlatformApi(
    private val client: HttpClient,
    private val baseUrl: String
) {
    private fun url(path: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val clean = if (path.startsWith("/")) path.substring(1) else path
        return "$base$clean"
    }

    suspend fun getDashboard(
        token: String
    ): NetworkResult<ApiResponse<CalendarDashboardDto>> = safeApiCall {
        client.get(url("api/admin/calendar/dashboard"))
    }

    suspend fun getEvents(
        token: String,
        month: String? = null,
        status: String? = null,
        type: String? = null
    ): NetworkResult<ApiResponse<CalendarEventsListResponse>> = safeApiCall {
        client.get(url("api/admin/calendar/events")) {
            month?.let { parameter("month", it) }
            status?.let { parameter("status", it) }
            type?.let { parameter("type", it) }
        }
    }

    suspend fun getEvent(
        token: String,
        eventId: String
    ): NetworkResult<ApiResponse<AcademicCalendarEventDto>> = safeApiCall {
        client.get(url("api/admin/calendar/events/$eventId"))
    }

    suspend fun createEvent(
        token: String,
        request: CreateCalendarEventRequest
    ): NetworkResult<ApiResponse<AcademicCalendarEventDto>> = safeApiCall {
        client.post(url("api/admin/calendar/events")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun updateEvent(
        token: String,
        eventId: String,
        request: UpdateCalendarEventRequest
    ): NetworkResult<ApiResponse<AcademicCalendarEventDto>> = safeApiCall {
        client.put(url("api/admin/calendar/events/$eventId")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun deleteEvent(
        token: String,
        eventId: String
    ): NetworkResult<ApiResponse<Unit>> = safeApiCall {
        client.delete(url("api/admin/calendar/events/$eventId"))
    }

    suspend fun duplicateEvent(
        token: String,
        eventId: String,
        request: DuplicateCalendarEventRequest
    ): NetworkResult<ApiResponse<AcademicCalendarEventDto>> = safeApiCall {
        client.post(url("api/admin/calendar/events/$eventId/duplicate")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }
}
