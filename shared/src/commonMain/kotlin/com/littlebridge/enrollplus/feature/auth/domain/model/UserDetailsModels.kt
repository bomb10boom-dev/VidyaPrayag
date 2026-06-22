package com.littlebridge.enrollplus.feature.auth.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class UserDetailsResponse(
    val success: Boolean,
    val data: UserDetailsData
)

@Serializable
data class UserDetailsData(
    @SerialName("personal_details") val personalDetails: PersonalDetails,
    @SerialName("onboarding_details") val onboardingDetails: OnboardingDetails
)

@Serializable
data class PersonalDetails(
    val role: String,
    val id: String,
    val name: String,
    @SerialName("profile_pic") val profilePic: String? = null,
    val email: String? = null,
    val mobile: String? = null
)

@Serializable
data class OnboardingDetails(
    @SerialName("onboarding_status") val onboardingStatus: String,
    @SerialName("total_steps") val totalSteps: Int,
    @SerialName("list_of_steps") val listOfSteps: List<OnboardingStepData>,
    @SerialName("support_info") val supportInfo: SupportInfo? = null,
    @SerialName("tutorial_video_link") val tutorialVideoLink: String? = null,
    @SerialName("menu_features") val menuFeatures: List<FeatureFlag>,
    @SerialName("app_themes") val appThemes: List<AppThemeData>,
    @SerialName("tos_link") val tosLink: String,
    @SerialName("privacy_policy_link") val privacyPolicyLink: String
)

@Serializable
data class OnboardingStepData(
    val name: String,
    val description: String,
    val status: String,
    val icon: String,
    @SerialName("is_enabled") val isEnabled: Boolean,
    @SerialName("is_required") val isRequired: Boolean
)

@Serializable
data class SupportInfo(
    val name: String,
    val description: String,
    @SerialName("contact_number") val contactNumber: String,
    @SerialName("contact_email") val contactEmail: String,
    val icon: String
)

@Serializable
data class FeatureFlag(
    val name: String,
    @SerialName("is_enabled") val isEnabled: Boolean,
    @SerialName("is_live") val isLive: Boolean
)

@Serializable
data class AppThemeData(
    val name: String,
    @SerialName("is_enabled") val isEnabled: Boolean,
    @SerialName("is_live") val isLive: Boolean
)
