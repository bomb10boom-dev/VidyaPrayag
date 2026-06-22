package com.littlebridge.enrollplus.feature.admin.domain.repository

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.admin.domain.model.BulkImportStudentsRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.BulkImportStudentsResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.CreateStudentRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.StudentDto
import com.littlebridge.enrollplus.feature.admin.domain.model.StudentListResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.StudentProfileDto
import com.littlebridge.enrollplus.feature.admin.domain.model.TeacherProfileDto

/** RA-45: admin student roster + student/teacher profile reads & student writes. */
interface StudentsRepository {
    suspend fun getStudents(token: String): NetworkResult<ApiResponse<StudentListResponse>>
    suspend fun createStudent(token: String, request: CreateStudentRequest): NetworkResult<ApiResponse<StudentDto>>
    suspend fun importStudents(token: String, request: BulkImportStudentsRequest): NetworkResult<ApiResponse<BulkImportStudentsResponse>>
    suspend fun deleteStudent(token: String, studentId: String): NetworkResult<ApiResponse<Unit>>
    suspend fun getStudentProfile(token: String, studentId: String): NetworkResult<ApiResponse<StudentProfileDto>>
    suspend fun getTeacherProfile(token: String, teacherId: String): NetworkResult<ApiResponse<TeacherProfileDto>>
}
