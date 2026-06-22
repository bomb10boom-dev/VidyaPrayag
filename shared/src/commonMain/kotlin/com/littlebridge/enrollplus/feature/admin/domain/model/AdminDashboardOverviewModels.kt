/*
 * File: AdminDashboardOverviewModels.kt
 * Module: feature.admin.domain.model
 *
 * DTOs for the redesigned, analytics-driven admin "command center".
 * Matches server: feature.school.AdminDashboardOverviewRouting.kt
 *
 * GET /api/admin/dashboard/overview → AdminDashboardOverview
 *
 * Field names are camelCase (the server emits camelCase JSON for this endpoint).
 * Every field carries a safe default so a partial / older payload still
 * deserialises and the client never crashes on a missing key. Modules that have
 * no backing data report `available = false` so the UI can hide them gracefully
 * (NEVER fabricate data).
 */
package com.littlebridge.enrollplus.feature.admin.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class OverviewHeader(
    val schoolId: String = "",
    val schoolName: String = "Your School",
    val logoUrl: String? = null,
    val academicYear: String = "",
    val currentTerm: String = "",
    val adminName: String = "Admin",
    val adminAvatarUrl: String? = null,
    val greeting: String = "Welcome",
    val lastUpdated: String = ""
)

@Serializable
data class OverviewPulseCategory(
    val key: String = "",
    val label: String = "",
    val score: Int = 0,
    val weight: Int = 0,
    val available: Boolean = false
)

@Serializable
data class OverviewSchoolPulse(
    val score: Int = 0,
    val status: String = "WATCH",   // EXCELLENT | HEALTHY | WATCH | CRITICAL
    val message: String = "",
    val categories: List<OverviewPulseCategory> = emptyList()
)

@Serializable
data class OverviewKpi(
    val key: String = "",
    val label: String = "",
    val value: Int = 0,
    val unit: String = "",
    val deltaDirection: String = "flat",  // up | down | flat
    val deltaValue: Double = 0.0,
    val deltaLabel: String = "",
    val available: Boolean = true
)

@Serializable
data class OverviewInsight(
    val id: String = "",
    val type: String = "INFO",            // ALERT | INFO | ACHIEVEMENT | REMINDER
    val severity: String = "LOW",         // HIGH | MEDIUM | LOW
    val title: String = "",
    val description: String = "",
    val action: String = ""
)

@Serializable
data class OverviewLeaderClass(
    val className: String = "",
    val score: Int = 0,
    val direction: String = "flat"
)

@Serializable
data class OverviewParentEngagement(
    val available: Boolean = false,
    val activeParentsPct: Int = 0,
    val activeParents: Int = 0,
    val totalParents: Int = 0,
    val mostEngagedClass: String = "",
    val leaderboard: List<OverviewLeaderClass> = emptyList()
)

@Serializable
data class OverviewCommunication(
    val unreadMessages: Int = 0,
    val pendingQueries: Int = 0,
    val announcements: Int = 0,
    val noticeAcknowledgements: Int = 0
)

@Serializable
data class OverviewEvent(
    val id: String = "",
    val title: String = "",
    val date: String = "",
    val daysAway: Int = 0,
    val type: String = "CALENDAR",        // CALENDAR | PTM | HOLIDAY
    val isHoliday: Boolean = false
)

@Serializable
data class OverviewEventDashboard(
    val available: Boolean = false,
    val upcoming: List<OverviewEvent> = emptyList(),
    val recentlyCompleted: List<OverviewEvent> = emptyList()
)

@Serializable
data class OverviewTeacherSpotlight(
    val available: Boolean = false,
    val teacherId: String? = null,
    val name: String = "",
    val department: String = "",
    val avatarUrl: String? = null,
    val score: Int = 0,
    val highlight: String = "",
    val subjectsTaught: Int = 0
)

@Serializable
data class OverviewAchievement(
    val id: String = "",
    val studentName: String = "",
    val title: String = "",
    val category: String = "ACADEMIC",    // ACADEMIC | SPORTS | COMPETITION
    val detail: String = "",
    val imageUrl: String? = null
)

@Serializable
data class OverviewAchievementShowcase(
    val available: Boolean = false,
    val items: List<OverviewAchievement> = emptyList()
)

@Serializable
data class OverviewFeePoint(
    val label: String = "",
    val value: Int = 0
)

@Serializable
data class OverviewFeeAnalytics(
    val available: Boolean = false,
    val totalCollected: Double = 0.0,
    val pending: Double = 0.0,
    val collectionRate: Int = 0,
    val currency: String = "INR",
    val trend: List<OverviewFeePoint> = emptyList()
)

@Serializable
data class OverviewBirthday(
    val name: String = "",
    val role: String = "STUDENT",         // STUDENT | TEACHER
    val date: String = "",
    val isToday: Boolean = false,
    val daysAway: Int = 0,
    val avatarUrl: String? = null
)

@Serializable
data class OverviewBirthdays(
    val available: Boolean = false,
    val today: List<OverviewBirthday> = emptyList(),
    val upcoming: List<OverviewBirthday> = emptyList()
)

@Serializable
data class OverviewModules(
    val transport: Boolean = false,
    val houses: Boolean = false
)

@Serializable
data class AdminDashboardOverview(
    val header: OverviewHeader = OverviewHeader(),
    val schoolPulse: OverviewSchoolPulse = OverviewSchoolPulse(),
    val kpis: List<OverviewKpi> = emptyList(),
    val insights: List<OverviewInsight> = emptyList(),
    val parentEngagement: OverviewParentEngagement = OverviewParentEngagement(),
    val communication: OverviewCommunication = OverviewCommunication(),
    val events: OverviewEventDashboard = OverviewEventDashboard(),
    val teacherSpotlight: OverviewTeacherSpotlight = OverviewTeacherSpotlight(),
    val achievements: OverviewAchievementShowcase = OverviewAchievementShowcase(),
    val feeAnalytics: OverviewFeeAnalytics = OverviewFeeAnalytics(),
    val birthdays: OverviewBirthdays = OverviewBirthdays(),
    val modules: OverviewModules = OverviewModules()
)
