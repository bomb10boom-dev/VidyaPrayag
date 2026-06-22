package com.littlebridge.enrollplus.feature.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.admin.data.remote.MediaApi
import com.littlebridge.enrollplus.feature.admin.domain.model.GalleryRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.PhilosophyDetailsDto
import com.littlebridge.enrollplus.feature.admin.domain.model.PickedMedia
import com.littlebridge.enrollplus.feature.admin.domain.model.TourVideosRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.VisibilityRequest
import com.littlebridge.enrollplus.feature.admin.domain.repository.UserProfileRepository
import com.littlebridge.enrollplus.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class GalleryImage(
    val id: String,
    val url: String
)

data class InstitutionalProfileState(
    // Header / decorative — these still come from /user/details elsewhere
    // (SchoolDashboard already wires that); kept as defaults to avoid breaking
    // existing screen bindings.
    val schoolName: String = "",
    val licenseType: String = "",
    val location: String = "",
    val profileImageUrl: String = "",

    // Wired to /api/v1/user/profile
    val isPublic: Boolean = true,
    val missionStatement: String = "",
    val learningModel: String = "",
    val primaryLanguage: String = "",
    val activeTourName: String = "",
    val galleryImages: List<GalleryImage> = emptyList(),
    val storageUsage: Float = 0f,        // 0..1 — fraction used, derived from used/total bytes
    val storageUsedHuman: String = "0 B",
    val totalStorageHuman: String = "10 GB",

    // UI-only — `profile_completion` isn't a server field yet; we compute it
    // client-side from how many text fields are filled.
    val profileCompletion: Int = 0,

    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    // True while a binary photo/video is uploading to Supabase Storage so the
    // gallery/tour buttons can show a spinner instead of the old URL dialog.
    val isUploading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class InstitutionalProfileViewModel(
    private val userProfileRepository: UserProfileRepository,
    private val preferenceRepository: PreferenceRepository,
    private val mediaApi: MediaApi
) : ViewModel() {

    private val _state = MutableStateFlow(InstitutionalProfileState())
    val state: StateFlow<InstitutionalProfileState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(isLoading = false); return@launch
            }
            when (val result = userProfileRepository.getProfile(token)) {
                is NetworkResult.Success -> {
                    val data = result.data.data
                    val phil = data?.philosophyDetails
                    val gal  = data?.gallery
                    val images = gal?.images.orEmpty().mapIndexed { idx, url ->
                        GalleryImage(id = (idx + 1).toString(), url = url)
                    }
                    val mission = phil?.coreMission ?: ""
                    val model   = phil?.learningModel ?: ""
                    val lang    = phil?.primaryLanguage ?: ""
                    val firstVideo = data?.videoTourData?.firstOrNull().orEmpty()

                    _state.value = _state.value.copy(
                        isLoading         = false,
                        isPublic          = data?.publicProfile ?: true,
                        missionStatement  = mission,
                        learningModel     = model,
                        primaryLanguage   = lang,
                        activeTourName    = firstVideo,
                        galleryImages     = images,
                        storageUsedHuman  = gal?.storageUsed ?: "0 B",
                        totalStorageHuman = gal?.totalStorage ?: "10 GB",
                        storageUsage      = computeStorageFraction(gal?.storageUsed, gal?.totalStorage),
                        profileCompletion = computeCompletion(mission, model, lang, images.size, firstVideo)
                    )
                }
                is NetworkResult.Error -> {
                    AppLogger.e("InstitutionalProfileVM", "getProfile error: ${result.message}")
                    _state.value = _state.value.copy(isLoading = false, errorMessage = result.message)
                }
                is NetworkResult.ConnectionError -> {
                    AppLogger.e("InstitutionalProfileVM", "getProfile connection error")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "Connection error. Check your internet."
                    )
                }
            }
        }
    }

    // -------- Local edits --------

    /**
     * Persist the public/private visibility toggle. We update the UI
     * optimistically, call the dedicated /visibility endpoint, and roll back
     * if the server rejects the change so the switch never lies about state.
     */
    fun togglePublic(value: Boolean) {
        val previous = _state.value.isPublic
        _state.value = _state.value.copy(isPublic = value, errorMessage = null)
        viewModelScope.launch {
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(isPublic = previous)
                return@launch
            }
            when (val r = userProfileRepository.updateVisibility(token, VisibilityRequest(value))) {
                is NetworkResult.Success -> {
                    val confirmed = r.data.data?.publicProfile ?: value
                    _state.value = _state.value.copy(
                        isPublic = confirmed,
                        infoMessage = if (confirmed) "Profile is now public" else "Profile is now private"
                    )
                }
                is NetworkResult.Error -> {
                    AppLogger.e("InstitutionalProfileVM", "updateVisibility error: ${r.message}")
                    _state.value = _state.value.copy(isPublic = previous, errorMessage = r.message)
                }
                is NetworkResult.ConnectionError -> {
                    _state.value = _state.value.copy(
                        isPublic = previous,
                        errorMessage = "Connection error. Check your internet."
                    )
                }
            }
        }
    }

    fun updateMission(text: String) {
        _state.value = _state.value.copy(missionStatement = text)
    }

    fun updateLearningModel(model: String) {
        _state.value = _state.value.copy(learningModel = model)
    }

    fun updateLanguage(lang: String) {
        _state.value = _state.value.copy(primaryLanguage = lang)
    }

    // -------- Save endpoints --------

    fun saveProfile() {
        viewModelScope.launch {
            val token = preferenceRepository.getUserToken().first() ?: return@launch
            val s = _state.value
            _state.value = s.copy(isSaving = true, errorMessage = null)
            val body = PhilosophyDetailsDto(
                coreMission     = s.missionStatement,
                learningModel   = s.learningModel,
                primaryLanguage = s.primaryLanguage
            )
            when (val r = userProfileRepository.updatePhilosophy(token, body)) {
                is NetworkResult.Success -> {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        infoMessage = "Profile updated",
                        profileCompletion = computeCompletion(
                            s.missionStatement, s.learningModel, s.primaryLanguage,
                            s.galleryImages.size, s.activeTourName
                        )
                    )
                }
                is NetworkResult.Error -> {
                    AppLogger.e("InstitutionalProfileVM", "updatePhilosophy error: ${r.message}")
                    _state.value = _state.value.copy(isSaving = false, errorMessage = r.message)
                }
                is NetworkResult.ConnectionError -> {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        errorMessage = "Connection error. Check your internet."
                    )
                }
            }
        }
    }

    fun saveTourVideos(videos: List<String>) {
        viewModelScope.launch {
            val token = preferenceRepository.getUserToken().first() ?: return@launch
            _state.value = _state.value.copy(isSaving = true, errorMessage = null)
            when (val r = userProfileRepository.updateTourVideos(token, TourVideosRequest(videos))) {
                is NetworkResult.Success -> {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        activeTourName = videos.firstOrNull().orEmpty(),
                        infoMessage = "Tour videos updated"
                    )
                }
                is NetworkResult.Error -> {
                    AppLogger.e("InstitutionalProfileVM", "updateTourVideos error: ${r.message}")
                    _state.value = _state.value.copy(isSaving = false, errorMessage = r.message)
                }
                is NetworkResult.ConnectionError -> {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        errorMessage = "Connection error. Check your internet."
                    )
                }
            }
        }
    }

    /**
     * Upload a picked image as a REAL binary to Supabase Storage, then append
     * the returned public URL to the gallery and persist it. This replaces the
     * old "paste a URL" dialog — the admin now picks a file from their device.
     */
    fun uploadGalleryPhoto(picked: PickedMedia) {
        if (_state.value.isUploading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isUploading = true, errorMessage = null)
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(
                    isUploading = false,
                    errorMessage = "You are not signed in. Please log in again."
                )
                return@launch
            }
            when (
                val r = mediaApi.uploadMedia(
                    token = token,
                    bytes = picked.bytes,
                    fileName = picked.fileName,
                    mimeType = picked.mimeType,
                    kind = "IMAGE"
                )
            ) {
                is NetworkResult.Success -> {
                    val url = r.data.data?.url
                    _state.value = _state.value.copy(isUploading = false)
                    if (url.isNullOrBlank()) {
                        _state.value = _state.value.copy(errorMessage = "Upload succeeded but no URL was returned.")
                    } else {
                        // Persist the new gallery list (existing + uploaded url).
                        saveGallery(_state.value.galleryImages.map { it.url } + url)
                    }
                }
                is NetworkResult.Error -> {
                    _state.value = _state.value.copy(
                        isUploading = false,
                        errorMessage = if (r.code == 503) {
                            "Media storage isn't configured on the server yet. " +
                                "Set SUPABASE_URL and SUPABASE_SERVICE_KEY, then try again."
                        } else r.message
                    )
                }
                is NetworkResult.ConnectionError -> {
                    _state.value = _state.value.copy(
                        isUploading = false,
                        errorMessage = "No internet connection. Please try again."
                    )
                }
            }
        }
    }

    /**
     * Upload a picked video as a REAL binary to Supabase Storage, then set it
     * as the active virtual tour. Replaces the old "paste a video URL" dialog.
     */
    fun uploadTourVideo(picked: PickedMedia) {
        if (_state.value.isUploading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isUploading = true, errorMessage = null)
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(
                    isUploading = false,
                    errorMessage = "You are not signed in. Please log in again."
                )
                return@launch
            }
            when (
                val r = mediaApi.uploadMedia(
                    token = token,
                    bytes = picked.bytes,
                    fileName = picked.fileName,
                    mimeType = picked.mimeType,
                    kind = "VIDEO"
                )
            ) {
                is NetworkResult.Success -> {
                    val url = r.data.data?.url
                    _state.value = _state.value.copy(isUploading = false)
                    if (url.isNullOrBlank()) {
                        _state.value = _state.value.copy(errorMessage = "Upload succeeded but no URL was returned.")
                    } else {
                        saveTourVideos(listOf(url))
                    }
                }
                is NetworkResult.Error -> {
                    _state.value = _state.value.copy(
                        isUploading = false,
                        errorMessage = if (r.code == 503) {
                            "Media storage isn't configured on the server yet. " +
                                "Set SUPABASE_URL and SUPABASE_SERVICE_KEY, then try again."
                        } else r.message
                    )
                }
                is NetworkResult.ConnectionError -> {
                    _state.value = _state.value.copy(
                        isUploading = false,
                        errorMessage = "No internet connection. Please try again."
                    )
                }
            }
        }
    }

    fun saveGallery(images: List<String>) {
        viewModelScope.launch {
            val token = preferenceRepository.getUserToken().first() ?: return@launch
            _state.value = _state.value.copy(isSaving = true, errorMessage = null)
            when (val r = userProfileRepository.updateGallery(token, GalleryRequest(images))) {
                is NetworkResult.Success -> {
                    val data = r.data.data
                    val newImages = images.mapIndexed { idx, url -> GalleryImage((idx + 1).toString(), url) }
                    _state.value = _state.value.copy(
                        isSaving = false,
                        galleryImages = newImages,
                        storageUsedHuman = data?.storageUsed ?: _state.value.storageUsedHuman,
                        totalStorageHuman = data?.totalStorage ?: _state.value.totalStorageHuman,
                        storageUsage = computeStorageFraction(data?.storageUsed, data?.totalStorage),
                        infoMessage = "Gallery updated"
                    )
                }
                is NetworkResult.Error -> {
                    AppLogger.e("InstitutionalProfileVM", "updateGallery error: ${r.message}")
                    _state.value = _state.value.copy(isSaving = false, errorMessage = r.message)
                }
                is NetworkResult.ConnectionError -> {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        errorMessage = "Connection error. Check your internet."
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(errorMessage = null, infoMessage = null)
    }

    // -------- Helpers --------

    /**
     * Parse "1.4 MB" / "300 KB" / "0 B" etc. into bytes. Returns null on
     * unrecognised input — caller falls back to 0.
     */
    private fun parseHumanBytes(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        val trimmed = s.trim()
        val unit = trimmed.takeLastWhile { !it.isDigit() && it != '.' && it != ' ' }.trim().uppercase()
        val number = trimmed.dropLast(unit.length).trim().toDoubleOrNull() ?: return null
        val mult: Double = when (unit) {
            "GB" -> 1024.0 * 1024 * 1024
            "MB" -> 1024.0 * 1024
            "KB" -> 1024.0
            "B", "" -> 1.0
            else -> return null
        }
        return (number * mult).toLong()
    }

    private fun computeStorageFraction(used: String?, total: String?): Float {
        val u = parseHumanBytes(used) ?: return 0f
        val t = parseHumanBytes(total) ?: return 0f
        if (t <= 0) return 0f
        return (u.toFloat() / t.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Cheap completion heuristic for the profile-health card. Five "slots":
     * mission, model, language, ≥1 gallery image, ≥1 tour video. Each is 20%.
     */
    private fun computeCompletion(
        mission: String,
        model: String,
        language: String,
        galleryCount: Int,
        firstTourVideo: String
    ): Int {
        var pct = 0
        if (mission.isNotBlank()) pct += 20
        if (model.isNotBlank()) pct += 20
        if (language.isNotBlank()) pct += 20
        if (galleryCount > 0) pct += 20
        if (firstTourVideo.isNotBlank()) pct += 20
        return pct
    }
}
