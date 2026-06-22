package com.littlebridge.enrollplus.feature.parent.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.parent.domain.model.*
import com.littlebridge.enrollplus.feature.parent.domain.repository.ParentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AchievementBadge(
    val title: String,
    val iconName: String,
    val isLocked: Boolean = false,
    /** Hex colour stops as emitted by the server (e.g. "#B6C7EB"). */
    val gradientColors: List<String> = emptyList()
)

data class AcademicCompetency(
    val title: String,
    val iconName: String,
    /** 0f..1f as emitted by the server. */
    val progress: Float
)

data class PlayIndicator(
    val title: String,
    val description: String,
    val imageUrl: String,
    val isMet: Boolean = true
)

data class TrackProgressState(
    val childName: String = "",
    // RA-S06: the logged-in parent's own display name (persisted at sign-in,
    // RA-S03), used for the account avatar in the portal header instead of the
    // hardcoded literal "Parent". Empty until resolved.
    val accountName: String = "",
    val overallProgress: Float = 0f,
    val currentLevel: Int = 0,
    val journeyDescription: String = "",
    val badges: List<AchievementBadge> = emptyList(),
    val academicCompetencies: List<AcademicCompetency> = emptyList(),
    // RA-PP-FIX: the holistic-growth EI block is a narrative `description` PLUS a
    // 0..1 metric map — not a flat Map<String,Float>. Modelled truthfully so the
    // Overview tab renders both, and parsing never crashes on the string field.
    val emotionalDescription: String = "",
    val emotionalIntelligence: Map<String, Float> = emptyMap(),
    val playIndicators: List<PlayIndicator> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class TrackProgressViewModel(
    private val repository: ParentRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {
    private val _state = MutableStateFlow(TrackProgressState())
    val state: StateFlow<TrackProgressState> = _state.asStateFlow()

    init {
        loadTrackProgress()
        observeAccountName()
    }

    // RA-S06: keep the parent's own display name in state so the header avatar
    // greets the real user. Read reactively so a profile refresh (which
    // re-persists the name via getUserDetails) updates the header live.
    private fun observeAccountName() {
        viewModelScope.launch {
            preferenceRepository.getUserName().collect { name ->
                _state.update { it.copy(accountName = name ?: "") }
            }
        }
    }

    private fun loadTrackProgress() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            preferenceRepository.getUserToken().collect { token ->
                if (token != null) {
                    when (val result = repository.getTrackProgress(token)) {
                        is NetworkResult.Success -> {
                            val data = result.data.data
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    // hero_section is the canonical source for the holistic
                                    // level/progress. The child NAME comes from the dashboard
                                    // (child_summary), not this endpoint — left blank here.
                                    overallProgress = data.heroSection.progressPercentage / 100f,
                                    currentLevel = parseLevel(data.heroSection.levelLabel),
                                    journeyDescription = data.heroSection.journeyDescription,
                                    badges = data.badges.map { b ->
                                        AchievementBadge(b.title, b.icon, b.isLocked, b.colors)
                                    },
                                    academicCompetencies = data.academicCore.competencies.map { c ->
                                        AcademicCompetency(c.title, c.icon, c.progress.toFloat())
                                    },
                                    emotionalDescription = data.emotionalIntelligence.description,
                                    emotionalIntelligence = data.emotionalIntelligence.metrics
                                        .mapValues { (_, v) -> v.toFloat() },
                                    playIndicators = data.playDiscovery.map { p ->
                                        PlayIndicator(
                                            title = p.title,
                                            description = p.description,
                                            imageUrl = p.image.orEmpty(),
                                            isMet = p.status.equals("MET", ignoreCase = true),
                                        )
                                    }
                                )
                            }
                        }
                        is NetworkResult.Error -> {
                            _state.update { it.copy(isLoading = false, error = result.message) }
                        }
                        is NetworkResult.ConnectionError -> {
                            _state.update { it.copy(isLoading = false, error = "Connection error") }
                        }
                    }
                }
            }
        }
    }
}

/** Extract the numeric level from a server label like "LEVEL 4 REACHED" → 4 (0 if absent). */
private fun parseLevel(label: String): Int =
    Regex("""\d+""").find(label)?.value?.toIntOrNull() ?: 0
