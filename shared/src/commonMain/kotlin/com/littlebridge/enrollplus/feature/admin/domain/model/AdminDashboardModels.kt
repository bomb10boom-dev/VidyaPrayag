/*
 * File: AdminDashboardModels.kt
 * Module: feature.admin.domain.model
 *
 * DTOs for the redesigned admin home dashboard.
 * Matches server: feature.school.AdminDashboardRouting.kt
 *
 * GET /api/admin/dashboard/summary   → AdminDashboardSummary
 * GET /api/admin/dashboard/analytics → AdminDashboardAnalytics
 * GET /api/admin/dashboard/activity  → AdminDashboardActivity
 *
 * Field names use camelCase (the server emits camelCase JSON for these
 * endpoints — see the sample contract), so no @SerialName remapping is needed.
 * Every field carries a safe default so a partial / older server payload still
 * deserialises (the client never crashes on a missing key).
 */
package com.littlebridge.enrollplus.feature.admin.domain.model

import kotlinx.serialization.Serializable

// ---------------- summary ----------------

@Serializable
data class DashboardSchool(
    val id: String = "",
    val name: String = "",
    val logoUrl: String? = null,
    val academicYear: String = "",
    val currentTerm: String = ""
)

@Serializable
data class DashboardAdmin(
    val id: String = "",
    val name: String = "Admin",
    val avatarUrl: String? = null
)

@Serializable
data class DashboardTrend(
    val direction: String = "flat",   // up | down | flat
    val value: Double = 0.0
)

@Serializable
data class DashboardMetric(
    val key: String = "",
    val label: String = "",
    val value: Int = 0,
    val unit: String = "",
    val trend: DashboardTrend? = null
)

@Serializable
data class DashboardCampusHealth(
    val status: String = "HEALTHY",   // HEALTHY | WATCH | CRITICAL
    val message: String = "",
    val metrics: List<DashboardMetric> = emptyList()
)

@Serializable
data class DashboardCountTrend(
    val direction: String = "flat",
    val percentage: Int = 0
)

@Serializable
data class DashboardStudentsStat(
    val total: Int = 0,
    val active: Int = 0,
    val newAdmissions: Int = 0,
    val trend: DashboardCountTrend = DashboardCountTrend()
)

@Serializable
data class DashboardTeachersStat(
    val total: Int = 0,
    val active: Int = 0,
    val newJoined: Int = 0,
    val trend: DashboardCountTrend = DashboardCountTrend()
)

@Serializable
data class DashboardSimpleStat(
    val total: Int = 0,
    val active: Int = 0
)

@Serializable
data class DashboardStatistics(
    val students: DashboardStudentsStat = DashboardStudentsStat(),
    val teachers: DashboardTeachersStat = DashboardTeachersStat(),
    val classes: DashboardSimpleStat = DashboardSimpleStat(),
    val subjects: DashboardSimpleStat = DashboardSimpleStat()
)

@Serializable
data class DashboardDepartment(
    val name: String = "",
    val teacherCount: Int = 0
)

@Serializable
data class DashboardTeacherInsight(
    val totalTeachers: Int = 0,
    val assignedTeachers: Int = 0,
    val pendingAssignment: Int = 0,
    val assignmentCoverage: Int = 0,
    val departments: List<DashboardDepartment> = emptyList()
)

@Serializable
data class DashboardQuickAction(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val enabled: Boolean = true,
    val permission: String = ""
)

@Serializable
data class AdminDashboardSummary(
    val school: DashboardSchool = DashboardSchool(),
    val admin: DashboardAdmin = DashboardAdmin(),
    val campusHealth: DashboardCampusHealth = DashboardCampusHealth(),
    val statistics: DashboardStatistics = DashboardStatistics(),
    val teacherInsight: DashboardTeacherInsight = DashboardTeacherInsight(),
    val quickActions: List<DashboardQuickAction> = emptyList()
)

// ---------------- analytics ----------------

@Serializable
data class DashboardAttendanceTrend(
    val period: String = "monthly",
    val labels: List<String> = emptyList(),
    val values: List<Int> = emptyList()
)

@Serializable
data class DashboardStudentGrowth(
    val labels: List<String> = emptyList(),
    val values: List<Int> = emptyList()
)

@Serializable
data class DashboardTopClass(
    @kotlinx.serialization.SerialName("class") val className: String = "",
    val score: Int = 0
)

@Serializable
data class DashboardClassPerformance(
    val topClasses: List<DashboardTopClass> = emptyList()
)

@Serializable
data class DashboardAttendanceBreakdown(
    val present: Int = 0,
    val absent: Int = 0,
    val late: Int = 0
)

@Serializable
data class AdminDashboardAnalytics(
    val attendanceTrend: DashboardAttendanceTrend = DashboardAttendanceTrend(),
    val studentGrowth: DashboardStudentGrowth = DashboardStudentGrowth(),
    val classPerformance: DashboardClassPerformance = DashboardClassPerformance(),
    val attendanceBreakdown: DashboardAttendanceBreakdown = DashboardAttendanceBreakdown()
)

// ---------------- activity ----------------

@Serializable
data class DashboardAlert(
    val id: String = "",
    val type: String = "INFO",        // WARNING | INFO | CRITICAL
    val title: String = "",
    val description: String = "",
    val priority: String = "MEDIUM",  // HIGH | MEDIUM | LOW
    val action: String = "",
    val createdAt: String = ""
)

@Serializable
data class DashboardActivity(
    val id: String = "",
    val type: String = "",
    val title: String = "",
    val description: String = "",
    val performedBy: String = "",
    val time: String = "",
    val createdAt: String = ""
)

@Serializable
data class AdminDashboardActivity(
    val alerts: List<DashboardAlert> = emptyList(),
    val activities: List<DashboardActivity> = emptyList()
)
