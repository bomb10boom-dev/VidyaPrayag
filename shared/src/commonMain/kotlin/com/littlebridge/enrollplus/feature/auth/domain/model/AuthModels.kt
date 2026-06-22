package com.littlebridge.enrollplus.feature.auth.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
enum class AuthFlow {
    LOGIN_EMAIL,
    SIGNUP_EMAIL,
    LOGIN_PHONE,
    SIGNUP_PHONE
}

@Serializable
data class CheckUserRequest(
    val identifier: String
)

@Serializable
data class UserFlowResponse(
    @SerialName("is_new_user") val isNewUser: Boolean,
    @SerialName("auth_method_required") val authMethodRequired: String,
    val message: String
)

@Serializable
data class LoginRequest(
    val identifier: String,
    val password: String? = null,
    val otp: String? = null,
    val role: String // "ADMIN" | "TEACHER" | "PARENT"
)

@Serializable
data class SignupRequest(
    val name: String,
    val identifier: String,
    val password: String? = null,
    val otp: String? = null,
    val role: String
)

/**
 * Body for POST /api/v1/auth/register-school — the "Onboard your school"
 * self-registration. Creates a school_admin + an owning (pending) school in
 * one call and returns an [AuthResponse] (profile_completed = false), which
 * routes the new admin straight into the onboarding wizard.
 */
@Serializable
data class SchoolRegisterRequest(
    val name: String,                 // admin / principal contact name
    val identifier: String,           // email
    val password: String,
    @SerialName("school_name") val schoolName: String,
    val board: String? = null,
    @SerialName("school_type") val schoolType: String? = null,
    val city: String? = null,
    val state: String? = null,
    @SerialName("contact_phone") val contactPhone: String? = null,
)

@Serializable
data class AuthResponse(
    val token: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val role: String,
    @SerialName("profile_completed") val profileCompleted: Boolean,
    // RA-54: true when a provisioned teacher must change their generated
    // initial password on first login. Drives the TeacherFirstLogin gate.
    @SerialName("must_change_password") val mustChangePassword: Boolean = false
)

@Serializable
data class ChangePasswordRequest(
    @SerialName("old_password") val oldPassword: String? = null,
    @SerialName("new_password") val newPassword: String
)

@Serializable
data class ErrorResponse(
    val message: String
)

@Serializable
data class OtpRequest(
    val identifier: String,
    val purpose: String? = null
)

@Serializable
data class OtpResponse(
    val message: String
)

@Serializable
data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String
)

@Serializable
data class LogoutRequest(
    @SerialName("refresh_token") val refreshToken: String? = null
)
