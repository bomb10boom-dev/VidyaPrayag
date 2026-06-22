package com.littlebridge.enrollplus.feature.schools.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class School(
    val id: String,
    val name: String,
    val location: String,
    val board: String,
    val description: String,
    val imageUrl: String,
    val sriScore: Double = 0.0,
    val feesRange: String = "",
    val isVerified: Boolean = false,
    val tags: List<String> = emptyList()
)
