package com.littlebridge.enrollplus.feature.admin.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class OnboardingBasics(
    val schoolName: String = "",
    val boardAffiliation: String = "",
    val officialEmail: String = "",
    val contactNumber: String = "",
    val countryCode: String = "+91",
    val address: String = "Education Lane, Knowledge Hub, Sector 42, New Delhi - 110001",
    // Real geo captured via "Use current location" (report §11.2). Null until
    // the admin grants permission and we obtain a fix; persisted as
    // latitude/longitude + full_address on the school record.
    val latitude: Double? = null,
    val longitude: Double? = null,
    val city: String = "",
    val district: String = "",
    val state: String = "",
    val pincode: String = ""
)
