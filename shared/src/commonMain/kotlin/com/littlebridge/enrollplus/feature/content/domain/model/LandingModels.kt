package com.littlebridge.enrollplus.feature.content.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class LandingSection(
    @SerialName("top_tagline") val topTagline: String,
    @SerialName("sub_tagline") val subTagline: String,
    @SerialName("list_of_features") val listOfFeatures: List<String>,
    @SerialName("list_of_sub_features") val listOfSubFeatures: List<String>
)

@Serializable
data class LandingItem(
    @SerialName("icon_url") val iconUrl: String,
    val heading: String,
    val description: String,
    @SerialName("is_live") val isLive: Boolean
)

@Serializable
data class LandingData(
    @SerialName("top_tagline") val topTagline: String,
    @SerialName("sub_tagline") val subTagline: String,
    @SerialName("parent_info") val parentInfo: LandingSection,
    @SerialName("school_info") val schoolInfo: LandingSection,
    @SerialName("list_of_offerings") val listOfOfferings: List<LandingItem>,
    @SerialName("list_of_portals") val listOfPortals: List<LandingItem>,
    @SerialName("login_modes") val loginModes: List<String>,
    @SerialName("tos_link") val tosLink: String,
    @SerialName("privacy_policy_link") val privacyPolicyLink: String
)

@Serializable
data class LandingResponse(
    val success: Boolean,
    val message: String,
    val data: LandingData
)
