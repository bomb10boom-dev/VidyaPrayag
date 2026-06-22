package com.littlebridge.enrollplus.feature.parent.data.repository

import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.parent.data.remote.ParentApi
import com.littlebridge.enrollplus.feature.parent.domain.model.*
import com.littlebridge.enrollplus.feature.parent.domain.repository.ParentRepository

class ParentRepositoryImpl(
    private val api: ParentApi
) : ParentRepository {
    override suspend fun getDashboard(token: String): NetworkResult<ParentDashboardResponse> {
        return api.getDashboard(token)
    }

    override suspend fun getTrackProgress(token: String): NetworkResult<TrackProgressResponse> {
        return api.getTrackProgress(token)
    }

    override suspend fun getFees(token: String, childId: String?): NetworkResult<FeeResponse> {
        return api.getFees(token, childId)
    }

    override suspend fun getScholarships(token: String): NetworkResult<ScholarshipsResponse> {
        return api.getScholarships(token)
    }

    override suspend fun getAnnouncements(token: String): NetworkResult<ParentAnnouncementsResponse> {
        return api.getAnnouncements(token)
    }

    override suspend fun getNotifications(token: String): NetworkResult<ParentNotificationsResponse> {
        return api.getNotifications(token)
    }

    override suspend fun markNotificationRead(token: String, id: String): NetworkResult<Unit> {
        return api.markNotificationRead(token, id)
    }

    override suspend fun markAllNotificationsRead(token: String): NetworkResult<Unit> {
        return api.markAllNotificationsRead(token)
    }

    override suspend fun getChildAttendance(token: String, childId: String): NetworkResult<ParentAttendanceResponse> {
        return api.getChildAttendance(token, childId)
    }

    override suspend fun getChildMarks(token: String, childId: String): NetworkResult<ParentMarksResponse> {
        return api.getChildMarks(token, childId)
    }

    override suspend fun getChildSyllabus(token: String, childId: String): NetworkResult<ParentSyllabusResponse> {
        return api.getChildSyllabus(token, childId)
    }

    override suspend fun getChildTimetable(token: String, childId: String): NetworkResult<ParentTimetableResponse> {
        return api.getChildTimetable(token, childId)
    }

    override suspend fun searchSchools(token: String, query: String): NetworkResult<SchoolSearchResponse> {
        return api.searchSchools(token, query)
    }

    override suspend fun linkChild(token: String, request: LinkChildRequest): NetworkResult<LinkChildResponse> {
        return api.linkChild(token, request)
    }

    override suspend fun getLeaveRequests(token: String): NetworkResult<ParentLeaveListResponse> {
        return api.getLeaveRequests(token)
    }

    override suspend fun applyLeave(token: String, request: CreateParentLeaveRequest): NetworkResult<ParentLeaveCreateResponse> {
        return api.applyLeave(token, request)
    }

    override suspend fun getMessageThreads(token: String): NetworkResult<ParentMessageThreadsResponse> {
        return api.getMessageThreads(token)
    }

    override suspend fun getThreadMessages(token: String, threadId: String): NetworkResult<ParentThreadMessagesResponse> {
        return api.getThreadMessages(token, threadId)
    }

    override suspend fun sendMessage(token: String, request: ParentSendMessageRequest): NetworkResult<ParentSendMessageResponse> {
        return api.sendMessage(token, request)
    }

    override suspend fun getMessageRecipients(token: String): NetworkResult<ParentRecipientsResponse> {
        return api.getMessageRecipients(token)
    }
}
