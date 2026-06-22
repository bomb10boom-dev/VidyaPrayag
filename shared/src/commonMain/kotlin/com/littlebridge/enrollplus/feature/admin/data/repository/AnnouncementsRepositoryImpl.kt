package com.littlebridge.enrollplus.feature.admin.data.repository

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.admin.data.remote.AnnouncementsApi
import com.littlebridge.enrollplus.feature.admin.domain.model.AnnouncementDto
import com.littlebridge.enrollplus.feature.admin.domain.model.AnnouncementListResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.CreateAnnouncementRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.SyncWhatsAppResponse
import com.littlebridge.enrollplus.feature.admin.domain.repository.AnnouncementsRepository

class AnnouncementsRepositoryImpl(
    private val api: AnnouncementsApi
) : AnnouncementsRepository {

    override suspend fun getAnnouncements(token: String): NetworkResult<ApiResponse<AnnouncementListResponse>> =
        api.getAnnouncements(token)

    override suspend fun searchAnnouncements(token: String, query: String): NetworkResult<ApiResponse<AnnouncementListResponse>> =
        api.searchAnnouncements(token, query)

    override suspend fun createAnnouncement(token: String, request: CreateAnnouncementRequest): NetworkResult<ApiResponse<AnnouncementDto>> =
        api.createAnnouncement(token, request)

    override suspend fun syncWhatsApp(token: String): NetworkResult<ApiResponse<SyncWhatsAppResponse>> =
        api.syncWhatsApp(token)
}
