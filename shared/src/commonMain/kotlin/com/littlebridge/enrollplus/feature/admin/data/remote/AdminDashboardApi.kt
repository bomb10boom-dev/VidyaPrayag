/*
 * File: AdminDashboardApi.kt
 * Module: feature.admin.data.remote
 *
 * Network client for the redesigned admin home dashboard.
 *
 * Server routes (all JWT — the bearer token is attached automatically by the
 * Ktor Auth plugin; the `token` arg only gates the call in the ViewModel):
 *   GET /api/admin/dashboard/summary
 *   GET /api/admin/dashboard/analytics
 *   GET /api/admin/dashboard/activity
 */
package com.littlebridge.enrollplus.feature.admin.data.remote

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.network.safeApiCall
import com.littlebridge.enrollplus.feature.admin.domain.model.AdminDashboardActivity
import com.littlebridge.enrollplus.feature.admin.domain.model.AdminDashboardAnalytics
import com.littlebridge.enrollplus.feature.admin.domain.model.AdminDashboardOverview
import com.littlebridge.enrollplus.feature.admin.domain.model.AdminDashboardSummary
import io.ktor.client.HttpClient
import io.ktor.client.request.get

class AdminDashboardApi(
    private val client: HttpClient,
    private val baseUrl: String
) {

    private fun getUrl(path: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cleanPath = if (path.startsWith("/")) path.substring(1) else path
        return "$base$cleanPath"
    }

    suspend fun getSummary(
        token: String
    ): NetworkResult<ApiResponse<AdminDashboardSummary>> = safeApiCall {
        client.get(getUrl("api/admin/dashboard/summary"))
    }

    suspend fun getAnalytics(
        token: String
    ): NetworkResult<ApiResponse<AdminDashboardAnalytics>> = safeApiCall {
        client.get(getUrl("api/admin/dashboard/analytics"))
    }

    suspend fun getActivity(
        token: String
    ): NetworkResult<ApiResponse<AdminDashboardActivity>> = safeApiCall {
        client.get(getUrl("api/admin/dashboard/activity"))
    }

    /**
     * Consolidated command-center payload powering the redesigned
     * SchoolHomeScreenV2 in ONE network call (School Pulse, KPIs, insights,
     * parent engagement, communication, events, teacher spotlight,
     * achievements, fee analytics, birthdays).
     */
    suspend fun getOverview(
        token: String
    ): NetworkResult<ApiResponse<AdminDashboardOverview>> = safeApiCall {
        client.get(getUrl("api/admin/dashboard/overview"))
    }
}
