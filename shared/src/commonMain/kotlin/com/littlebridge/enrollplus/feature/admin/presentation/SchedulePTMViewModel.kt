package com.littlebridge.enrollplus.feature.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.admin.domain.model.CreatePtmRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.PtmActiveEventDto
import com.littlebridge.enrollplus.feature.admin.domain.model.PtmClassProgressDto
import com.littlebridge.enrollplus.feature.admin.domain.model.PtmHistoryDto
import com.littlebridge.enrollplus.feature.admin.domain.repository.PtmRepository
import com.littlebridge.enrollplus.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class PTMHistoryItem(
    val id: String,
    val date: String,
    val title: String,
    val turnout: Int,
    val totalMet: Int
)

data class ClassPTMProgress(
    val id: String,
    val className: String,
    val teacherName: String,
    val metCount: Int,
    val totalCount: Int,
    val progress: Float
)

data class SchedulePTMState(
    val activeEventTitle: String = "",
    val activeEventDate: String = "",
    val activeEventSlot: String = "",
    val expectedParents: Int = 0,
    val checkedInParents: Int = 0,
    val invitesDelivered: Int = 0,
    val readReceipts: Int = 0,
    val history: List<PTMHistoryItem> = emptyList(),
    val classProgress: List<ClassPTMProgress> = emptyList(),
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class SchedulePTMViewModel(
    private val ptmRepository: PtmRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SchedulePTMState())
    val state: StateFlow<SchedulePTMState> = _state.asStateFlow()

    init {
        loadPtm()
    }

    fun loadPtm() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)

            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                AppLogger.d("SchedulePTMVM", "No auth token; skipping load")
                _state.value = _state.value.copy(isLoading = false)
                return@launch
            }

            when (val result = ptmRepository.getPtm(token)) {
                is NetworkResult.Success -> {
                    val data = result.data.data
                    _state.value = _state.value.copy(
                        activeEventTitle = data?.activeEvent?.title ?: "",
                        activeEventDate = data?.activeEvent?.date ?: "",
                        activeEventSlot = data?.activeEvent?.slot ?: "",
                        expectedParents = data?.activeEvent?.expectedParents ?: 0,
                        checkedInParents = data?.activeEvent?.checkedInParents ?: 0,
                        invitesDelivered = data?.activeEvent?.invitesDelivered ?: 0,
                        readReceipts = data?.activeEvent?.readReceipts ?: 0,
                        history = data?.history?.map { it.toUiModel() } ?: emptyList(),
                        classProgress = data?.classProgress?.map { it.toUiModel() } ?: emptyList(),
                        isLoading = false
                    )
                }
                is NetworkResult.Error -> {
                    AppLogger.e("SchedulePTMVM", "getPtm error: ${result.message}")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                is NetworkResult.ConnectionError -> {
                    AppLogger.e("SchedulePTMVM", "getPtm connection error")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "Connection error. Check your internet."
                    )
                }
            }
        }
    }

    /**
     * Schedule a new PTM event. On success the screen dismisses its dialog
     * via [onCreated] and we re-pull `/api/v1/school/ptm` to refresh the
     * active-event banner + history.
     */
    fun createPtm(
        title: String,
        date: String,
        slot: String,
        onCreated: (() -> Unit)? = null
    ) {
        if (title.isBlank() || date.isBlank() || slot.isBlank()) {
            _state.value = _state.value.copy(errorMessage = "Title, date and slot are required.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isCreating = true, errorMessage = null)
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(isCreating = false, errorMessage = "Not signed in")
                return@launch
            }
            val body = CreatePtmRequest(title = title.trim(), date = date.trim(), slot = slot.trim())
            when (val r = ptmRepository.createPtm(token, body)) {
                is NetworkResult.Success -> {
                    _state.value = _state.value.copy(
                        isCreating = false,
                        infoMessage = "PTM scheduled"
                    )
                    onCreated?.invoke()
                    loadPtm()
                }
                is NetworkResult.Error -> {
                    AppLogger.e("SchedulePTMVM", "createPtm error: ${r.message}")
                    _state.value = _state.value.copy(isCreating = false, errorMessage = r.message)
                }
                is NetworkResult.ConnectionError -> {
                    _state.value = _state.value.copy(
                        isCreating = false,
                        errorMessage = "Connection error. Check your internet."
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(errorMessage = null, infoMessage = null)
    }

    private fun PtmHistoryDto.toUiModel() = PTMHistoryItem(
        id = id,
        date = date,
        title = title,
        turnout = turnout,
        totalMet = totalMet
    )

    private fun PtmClassProgressDto.toUiModel() = ClassPTMProgress(
        id = id,
        className = className,
        teacherName = teacherName,
        metCount = metCount,
        totalCount = totalCount,
        progress = progress
    )
}
