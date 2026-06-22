/*
 * File: TeacherModels.kt
 * Module: feature.admin.domain.model
 *
 * DTOs for the school teacher-provisioning API (RA-22).
 * Matches server: feature.school.TeacherProvisioningRouting.kt
 *   GET    /api/v1/school/teachers
 *   POST   /api/v1/school/teachers
 *   DELETE /api/v1/school/teachers/{id}
 *   POST   /api/v1/school/teachers/{id}/reset-password  (RA-32)
 */
package com.littlebridge.enrollplus.feature.admin.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TeacherAccountDto(
    val id: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val role: String,
    @SerialName("school_id") val schoolId: String
)

@Serializable
data class TeacherListResponse(
    val teachers: List<TeacherAccountDto>
)

// =====================================================================
// Teacher CARD contract — client mirror of the redesigned teacher list
// (server: feature.school.TeacherProvisioningRouting.TeacherCardListResponse).
// camelCase wire names match the server exactly; every field carries a safe
// default so a partial/legacy payload can never crash deserialization.
// =====================================================================

@Serializable
data class TeacherCardProfileDto(
    val name: String = "",
    val avatarUrl: String? = null,
    val role: String = "",
    val status: String = "ACTIVE"                            // ACTIVE | INACTIVE
)

@Serializable
data class TeacherCardAcademicAssignmentDto(
    val grades: List<String> = emptyList(),
    val subjects: List<String> = emptyList()
)

@Serializable
data class TeacherCardWorkloadDto(
    val totalClasses: Int = 0,
    val totalStudents: Int = 0
)

@Serializable
data class TeacherCardActivityDto(
    val attendancePercentage: Int? = null,                   // null = no data
    val lastActiveAt: String? = null                         // ISO-8601 UTC, null = never
)

@Serializable
data class TeacherCardActionsDto(
    val canViewProfile: Boolean = true,
    val canAssignClass: Boolean = false,
    val canDeactivate: Boolean = false
)

@Serializable
data class TeacherCardDto(
    val id: String = "",
    val profile: TeacherCardProfileDto = TeacherCardProfileDto(),
    val academicAssignment: TeacherCardAcademicAssignmentDto = TeacherCardAcademicAssignmentDto(),
    val workload: TeacherCardWorkloadDto = TeacherCardWorkloadDto(),
    val activity: TeacherCardActivityDto = TeacherCardActivityDto(),
    val actions: TeacherCardActionsDto = TeacherCardActionsDto()
)

@Serializable
data class TeacherCardPaginationDto(
    val page: Int = 1,
    val pageSize: Int = 20,
    val totalRecords: Int = 0,
    val hasNext: Boolean = false
)

@Serializable
data class TeacherCardListResponse(
    val teachers: List<TeacherCardDto> = emptyList(),
    val pagination: TeacherCardPaginationDto = TeacherCardPaginationDto()
)

@Serializable
data class CreateTeacherRequest(
    val name: String,
    val identifier: String,                                  // email OR phone
    @SerialName("initial_password") val initialPassword: String? = null
)

/** RA-32: server returns the freshly-issued plaintext password exactly once. */
@Serializable
data class TeacherCredentialDto(
    val id: String,
    val name: String,
    val email: String,
    @SerialName("initial_password") val initialPassword: String
)
