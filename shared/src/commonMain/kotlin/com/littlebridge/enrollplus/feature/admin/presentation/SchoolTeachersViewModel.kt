package com.littlebridge.enrollplus.feature.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.admin.domain.model.CreateTeacherRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.TeacherCardDto
import com.littlebridge.enrollplus.feature.admin.domain.model.TeacherCredentialDto
import com.littlebridge.enrollplus.feature.admin.domain.repository.TeachersRepository
import com.littlebridge.enrollplus.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


data class SchoolTeachersState(
    val teachers: List<TeacherCardDto> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isMutating: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    // Pagination: current page, whether the server has another page, and the
    // total roster size (for a "showing X of N" hint).
    val page: Int = 1,
    val hasNext: Boolean = false,
    val totalRecords: Int = 0,
    // RA-32: a freshly-issued credential, shown ONCE in a dialog after a
    // password reset so the admin can hand it over. Cleared via
    // [dismissIssuedCredential] when the admin closes the dialog.
    val issuedCredential: TeacherCredentialDto? = null,
)

private const val TEACHERS_PAGE_SIZE = 20

/**
 * RA-22: backs the teacher roster on the People tab — list active teachers in
 * the admin's school, add a teacher (writes app_users), and remove (soft-delete)
 * a teacher. Every list mutation re-fetches from the server so the UI never
 * shows a stale local copy after a failed write.
 */
class SchoolTeachersViewModel(
    private val repository: TeachersRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SchoolTeachersState())
    val state: StateFlow<SchoolTeachersState> = _state.asStateFlow()

    init {
        load()
    }

    /** (Re)load the FIRST page, replacing the roster. Used on init and after
     *  every mutation so the UI never shows a stale local copy. */
    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(isLoading = false, errorMessage = "Not signed in")
                return@launch
            }
            when (val result = repository.getTeachers(token, page = 1, pageSize = TEACHERS_PAGE_SIZE)) {
                is NetworkResult.Success -> {
                    val body = result.data.data
                    _state.value = _state.value.copy(
                        teachers = body?.teachers.orEmpty(),
                        isLoading = false,
                        page = body?.pagination?.page ?: 1,
                        hasNext = body?.pagination?.hasNext ?: false,
                        totalRecords = body?.pagination?.totalRecords ?: 0
                    )
                }
                is NetworkResult.Error -> {
                    AppLogger.e("SchoolTeachersVM", "getTeachers error: ${result.message}")
                    _state.value = _state.value.copy(isLoading = false, errorMessage = result.message)
                }
                is NetworkResult.ConnectionError -> {
                    _state.value = _state.value.copy(isLoading = false, errorMessage = "Connection error. Check your internet.")
                }
            }
        }
    }

    /** Append the NEXT page to the current roster (infinite scroll / "load
     *  more"). No-op when already loading or there is no further page. */
    fun loadMore() {
        val current = _state.value
        if (current.isLoading || current.isLoadingMore || !current.hasNext) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingMore = true, errorMessage = null)
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(isLoadingMore = false, errorMessage = "Not signed in")
                return@launch
            }
            val nextPage = current.page + 1
            when (val result = repository.getTeachers(token, page = nextPage, pageSize = TEACHERS_PAGE_SIZE)) {
                is NetworkResult.Success -> {
                    val body = result.data.data
                    // De-dupe by id in case a mutation shifted the page window.
                    val merged = (current.teachers + body?.teachers.orEmpty())
                        .distinctBy { it.id }
                    _state.value = _state.value.copy(
                        teachers = merged,
                        isLoadingMore = false,
                        page = body?.pagination?.page ?: nextPage,
                        hasNext = body?.pagination?.hasNext ?: false,
                        totalRecords = body?.pagination?.totalRecords ?: current.totalRecords
                    )
                }
                is NetworkResult.Error -> {
                    AppLogger.e("SchoolTeachersVM", "getTeachers(loadMore) error: ${result.message}")
                    _state.value = _state.value.copy(isLoadingMore = false, errorMessage = result.message)
                }
                is NetworkResult.ConnectionError -> {
                    _state.value = _state.value.copy(isLoadingMore = false, errorMessage = "Connection error. Check your internet.")
                }
            }
        }
    }

    /**
     * Add a teacher by email (with an initial password) or phone (OTP login).
     * [onAdded] fires only after the server round-trip succeeds, so the screen
     * dismisses its dialog only on success.
     */
    fun addTeacher(
        name: String,
        identifier: String,
        initialPassword: String?,
        onAdded: (() -> Unit)? = null
    ) {
        if (name.isBlank() || identifier.isBlank()) {
            _state.value = _state.value.copy(errorMessage = "Name and email/phone are required.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isMutating = true, errorMessage = null)
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(isMutating = false, errorMessage = "Not signed in")
                return@launch
            }
            val body = CreateTeacherRequest(
                name = name.trim(),
                identifier = identifier.trim(),
                initialPassword = initialPassword?.trim()?.takeIf { it.isNotBlank() }
            )
            when (val r = repository.createTeacher(token, body)) {
                is NetworkResult.Success -> {
                    _state.value = _state.value.copy(isMutating = false, infoMessage = "Teacher added")
                    onAdded?.invoke()
                    load()
                }
                is NetworkResult.Error -> {
                    _state.value = _state.value.copy(isMutating = false, errorMessage = r.message)
                }
                is NetworkResult.ConnectionError -> {
                    _state.value = _state.value.copy(isMutating = false, errorMessage = "Connection error. Check your internet.")
                }
            }
        }
    }

    /** Remove (soft-delete) a teacher, then refresh the roster. */
    fun removeTeacher(teacherId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isMutating = true, errorMessage = null)
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(isMutating = false, errorMessage = "Not signed in")
                return@launch
            }
            when (val r = repository.deleteTeacher(token, teacherId)) {
                is NetworkResult.Success -> {
                    _state.value = _state.value.copy(isMutating = false, infoMessage = "Teacher removed")
                    load()
                }
                is NetworkResult.Error -> {
                    _state.value = _state.value.copy(isMutating = false, errorMessage = r.message)
                }
                is NetworkResult.ConnectionError -> {
                    _state.value = _state.value.copy(isMutating = false, errorMessage = "Connection error. Check your internet.")
                }
            }
        }
    }

    /**
     * RA-32: reissue a teacher's initial password. The server generates a new
     * secure password, persists only its hash, revokes the teacher's live
     * sessions, and returns the plaintext ONCE — surfaced via
     * [SchoolTeachersState.issuedCredential] for the admin to hand over.
     */
    fun resetPassword(teacherId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isMutating = true, errorMessage = null)
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(isMutating = false, errorMessage = "Not signed in")
                return@launch
            }
            when (val r = repository.resetTeacherPassword(token, teacherId)) {
                is NetworkResult.Success -> {
                    val cred = r.data.data
                    if (cred != null) {
                        _state.value = _state.value.copy(
                            isMutating = false,
                            infoMessage = "New password issued",
                            issuedCredential = cred
                        )
                    } else {
                        _state.value = _state.value.copy(
                            isMutating = false,
                            infoMessage = "New password issued"
                        )
                    }
                }
                is NetworkResult.Error -> {
                    _state.value = _state.value.copy(isMutating = false, errorMessage = r.message)
                }
                is NetworkResult.ConnectionError -> {
                    _state.value = _state.value.copy(isMutating = false, errorMessage = "Connection error. Check your internet.")
                }
            }
        }
    }

    /** RA-32: dismiss the one-time credential dialog. */
    fun dismissIssuedCredential() {
        _state.value = _state.value.copy(issuedCredential = null)
    }

    fun clearMessages() {
        _state.value = _state.value.copy(errorMessage = null, infoMessage = null)
    }
}
