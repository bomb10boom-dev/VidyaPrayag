package com.littlebridge.enrollplus.feature.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.admin.domain.model.AnnouncementDto
import com.littlebridge.enrollplus.feature.admin.domain.model.CreateAnnouncementRequest
import com.littlebridge.enrollplus.feature.admin.domain.repository.AnnouncementsRepository
import com.littlebridge.enrollplus.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class Announcement(
    val id: String,
    val title: String,
    val description: String,
    val category: String, // "Holidays", "PTM", "Events", "Update", "Reminder"
    val date: String,
    val imageUrl: String? = null,
    val isFeatured: Boolean = false,
    val participants: List<String> = emptyList()
)

data class SchoolAnnouncementsState(
    /** Filtered list shown in the UI (after category + search). */
    val announcements: List<Announcement> = emptyList(),
    /**
     * Unfiltered cache so we can re-derive [announcements] without re-hitting
     * the network when the user toggles a category chip.
     */
    val allAnnouncements: List<Announcement> = emptyList(),
    /** null = "All" (show everything). */
    val selectedCategory: String? = null,
    val isWhatsAppSyncEnabled: Boolean = true,
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class SchoolAnnouncementsViewModel(
    private val announcementsRepository: AnnouncementsRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SchoolAnnouncementsState())
    val state: StateFlow<SchoolAnnouncementsState> = _state.asStateFlow()

    init {
        loadAnnouncements()
    }

    fun loadAnnouncements() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)

            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                AppLogger.d("SchoolAnnouncementsVM", "No auth token; skipping load")
                _state.value = _state.value.copy(isLoading = false)
                return@launch
            }

            when (val result = announcementsRepository.getAnnouncements(token)) {
                is NetworkResult.Success -> {
                    val all = result.data.data?.announcements.orEmpty().map { it.toUiModel() }
                    _state.value = _state.value.copy(
                        allAnnouncements = all,
                        announcements = applyCategoryFilter(all, _state.value.selectedCategory),
                        isLoading = false
                    )
                }
                is NetworkResult.Error -> {
                    AppLogger.e("SchoolAnnouncementsVM", "getAnnouncements error: ${result.message}")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                is NetworkResult.ConnectionError -> {
                    AppLogger.e("SchoolAnnouncementsVM", "getAnnouncements connection error")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "Connection error. Check your internet."
                    )
                }
            }
        }
    }

    fun searchAnnouncements(query: String) {
        if (query.isBlank()) {
            loadAnnouncements()
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val token = preferenceRepository.getUserToken().first() ?: return@launch
            when (val result = announcementsRepository.searchAnnouncements(token, query)) {
                is NetworkResult.Success -> {
                    val all = result.data.data?.announcements.orEmpty().map { it.toUiModel() }
                    _state.value = _state.value.copy(
                        allAnnouncements = all,
                        announcements = applyCategoryFilter(all, _state.value.selectedCategory),
                        isLoading = false
                    )
                }
                is NetworkResult.Error -> {
                    _state.value = _state.value.copy(isLoading = false, errorMessage = result.message)
                }
                is NetworkResult.ConnectionError -> {
                    _state.value = _state.value.copy(isLoading = false, errorMessage = "Connection error.")
                }
            }
        }
    }

    /**
     * Switch the active category filter. Pass null (or "All") to clear the
     * filter. Operates on the cached [SchoolAnnouncementsState.allAnnouncements]
     * to avoid an extra network round-trip.
     */
    fun setCategoryFilter(category: String?) {
        val normalized = category?.takeIf { it.isNotBlank() && !it.equals("All", ignoreCase = true) }
        val filtered = applyCategoryFilter(_state.value.allAnnouncements, normalized)
        _state.value = _state.value.copy(
            selectedCategory = normalized,
            announcements = filtered
        )
    }

    private fun applyCategoryFilter(
        items: List<Announcement>,
        category: String?
    ): List<Announcement> {
        if (category.isNullOrBlank()) return items
        return items.filter { it.category.equals(category, ignoreCase = true) }
    }

    /**
     * Create a new announcement and refresh the list on success. The screen
     * passes [onCreated] to dismiss the dialog only after the server
     * round-trip succeeds — preventing a stale local copy from sticking
     * around if the request fails.
     */
    fun createAnnouncement(
        type: String,
        title: String,
        description: String,
        date: String,
        subTitle: String? = null,
        eventImage: String? = null,
        // RA-49: audience targeting. audienceType defaults to ALL_SCHOOL.
        // For CLASS/SECTION/SUBJECT/STUDENT, audienceValues holds the raw
        // user-entered class names / subjects / student codes (comma-separated
        // is split by the caller). For ALL_SCHOOL the list is ignored.
        audienceType: String = "ALL_SCHOOL",
        audienceValues: List<String> = emptyList(),
        // VP-CAL: mirror Holiday/PTM/Event posts into the Academic Calendar.
        // Default enabled; ignored by the server for Update/Reminder types.
        addToCalendar: Boolean = true,
        onCreated: (() -> Unit)? = null
    ) {
        if (title.isBlank() || description.isBlank() || date.isBlank()) {
            _state.value = _state.value.copy(errorMessage = "Title, description and date are required.")
            return
        }
        val normalizedAudience = audienceType.trim().uppercase().ifBlank { "ALL_SCHOOL" }
        val cleanValues = audienceValues.map { it.trim() }.filter { it.isNotBlank() }
        if (normalizedAudience != "ALL_SCHOOL" && cleanValues.isEmpty()) {
            _state.value = _state.value.copy(
                errorMessage = "Add at least one target for a $normalizedAudience announcement."
            )
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isCreating = true, errorMessage = null)
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(isCreating = false, errorMessage = "Not signed in")
                return@launch
            }
            val filter = buildAudienceFilter(normalizedAudience, cleanValues)
            val body = CreateAnnouncementRequest(
                type = type.ifBlank { "Update" },
                title = title.trim(),
                subTitle = subTitle?.trim()?.takeIf { it.isNotBlank() },
                description = description.trim(),
                eventImage = eventImage?.trim()?.takeIf { it.isNotBlank() },
                date = date.trim(),
                audienceType = normalizedAudience,
                audienceFilter = filter,
                addToCalendar = addToCalendar
            )
            when (val r = announcementsRepository.createAnnouncement(token, body)) {
                is NetworkResult.Success -> {
                    _state.value = _state.value.copy(
                        isCreating = false,
                        infoMessage = "Announcement created"
                    )
                    onCreated?.invoke()
                    loadAnnouncements()
                }
                is NetworkResult.Error -> {
                    AppLogger.e("SchoolAnnouncementsVM", "createAnnouncement error: ${r.message}")
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

    fun toggleWhatsAppSync(enabled: Boolean) {
        _state.value = _state.value.copy(isWhatsAppSyncEnabled = enabled)
        if (enabled) {
            viewModelScope.launch {
                val token = preferenceRepository.getUserToken().first() ?: return@launch
                announcementsRepository.syncWhatsApp(token)
            }
        }
    }

    /**
     * RA-49: turn a chosen audience scope + the admin's free-text targets into
     * the JSON `audience_filter` the server's [resolveRecipientPhones] /
     * [NotifyRecipients] expand. Key shapes mirror the server contract:
     *   CLASS / SECTION → {"class_names":[...]}
     *   SUBJECT         → {"subjects":[...]}
     *   STUDENT         → {"student_codes":[...]}
     *   CUSTOM          → {"phones":[...]}
     * ALL_SCHOOL needs no filter (null).
     */
    private fun buildAudienceFilter(audienceType: String, values: List<String>): JsonElement? {
        if (audienceType == "ALL_SCHOOL" || values.isEmpty()) return null
        val arr = JsonArray(values.map { JsonPrimitive(it) })
        val key = when (audienceType) {
            "CLASS", "SECTION" -> "class_names"
            "SUBJECT" -> "subjects"
            "STUDENT" -> "student_codes"
            "CUSTOM" -> "phones"
            else -> "class_names"
        }
        return buildJsonObject { put(key, arr) }
    }

    private fun AnnouncementDto.toUiModel() = Announcement(
        id = eventId,
        title = title,
        description = description,
        category = type,
        date = date,
        imageUrl = eventImage,
        isFeatured = false
    )
}
