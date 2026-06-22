package com.littlebridge.enrollplus.feature.admin.domain.repository

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.admin.domain.model.CreateTeacherRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.TeacherAccountDto
import com.littlebridge.enrollplus.feature.admin.domain.model.TeacherCardListResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.TeacherCredentialDto

/** RA-22: school teacher roster (list / add / delete). RA-32: credential reset. */
interface TeachersRepository {
    suspend fun getTeachers(
        token: String,
        page: Int = 1,
        pageSize: Int = 20
    ): NetworkResult<ApiResponse<TeacherCardListResponse>>
    suspend fun createTeacher(token: String, request: CreateTeacherRequest): NetworkResult<ApiResponse<TeacherAccountDto>>
    suspend fun deleteTeacher(token: String, teacherId: String): NetworkResult<ApiResponse<Unit>>
    suspend fun resetTeacherPassword(token: String, teacherId: String): NetworkResult<ApiResponse<TeacherCredentialDto>>
}
