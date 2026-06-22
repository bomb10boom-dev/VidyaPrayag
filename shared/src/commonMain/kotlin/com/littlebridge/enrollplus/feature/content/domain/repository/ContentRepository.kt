package com.littlebridge.enrollplus.feature.content.domain.repository

import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.content.domain.model.LandingData

interface ContentRepository {
    suspend fun getLandingContent(): NetworkResult<LandingData>
}
