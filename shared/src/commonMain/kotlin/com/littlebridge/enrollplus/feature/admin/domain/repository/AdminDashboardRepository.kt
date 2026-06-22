package com.littlebridge.enrollplus.feature.admin.domain.repository

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.admin.domain.model.AdminDashboardActivity
import com.littlebridge.enrollplus.feature.admin.domain.model.AdminDashboardAnalytics
import com.littlebridge.enrollplus.feature.admin.domain.model.AdminDashboardOverview
import com.littlebridge.enrollplus.feature.admin.domain.model.AdminDashboardSummary

/**
 * Reads for the redesigned admin home (SchoolHomeScreenV2). Backed by
 * GET /api/admin/dashboard/{summary,analytics,activity,overview}.
 *
 * `getOverview` is the consolidated command-center payload (preferred for the
 * redesigned home); the others remain for backwards compatibility.
 */
interface AdminDashboardRepository {
    suspend fun getSummary(token: String): NetworkResult<ApiResponse<AdminDashboardSummary>>
    suspend fun getAnalytics(token: String): NetworkResult<ApiResponse<AdminDashboardAnalytics>>
    suspend fun getActivity(token: String): NetworkResult<ApiResponse<AdminDashboardActivity>>
    suspend fun getOverview(token: String): NetworkResult<ApiResponse<AdminDashboardOverview>>
}
