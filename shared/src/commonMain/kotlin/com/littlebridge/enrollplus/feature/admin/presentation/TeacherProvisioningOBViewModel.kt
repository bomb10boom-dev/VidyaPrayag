/*
 * File: TeacherProvisioningOBViewModel.kt
 * Module: feature.admin.presentation
 *
 * Onboarding-time teacher provisioning. The school-onboarding wizard's Teachers
 * step previously only collected display NAMES used for the (cosmetic) subject
 * assignment matrix — it never created real `app_users` rows, so a teacher
 * "added" during onboarding could NOT log in. This view-model closes that gap by
 * calling the existing, privileged provisioning endpoint
 *   POST /api/v1/school/teachers   (feature.school.TeacherProvisioningRouting)
 * once per teacher, creating a real `role=teacher` account scoped to the
 * onboarding admin's school. Teachers added with an email get a generated
 * initial password (returned to the admin to hand over); the account is created
 * with must_change_password=true so the teacher resets it on first login.
 *
 * No hardcoding: every account created here comes from data the admin typed.
 */
package com.littlebridge.enrollplus.feature.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.admin.domain.model.CreateTeacherRequest
import com.littlebridge.enrollplus.feature.admin.domain.repository.TeachersRepository
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** One teacher to provision during onboarding. */
data class OnboardingTeacherInput(
    val name: String,
    val identifier: String,            // email OR phone the teacher will sign in with
    val initialPassword: String? = null // required by the server for email teachers
)

/** Result of a single provisioning attempt — surfaced so the admin can hand over creds. */
data class ProvisionedTeacher(
    val name: String,
    val identifier: String,
    val initialPassword: String?,      // non-null only for freshly created email accounts
    val created: Boolean,              // false when it already existed / failed
    val message: String? = null
)

data class TeacherProvisioningState(
    val isSubmitting: Boolean = false,
    val results: List<ProvisionedTeacher> = emptyList(),
    val errorMessage: String? = null,
)

class TeacherProvisioningOBViewModel(
    private val teachersRepository: TeachersRepository,
    private val preferenceRepository: PreferenceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TeacherProvisioningState())
    val state: StateFlow<TeacherProvisioningState> = _state.asStateFlow()

    /**
     * Generate a readable, high-entropy initial password (no ambiguous chars).
     * Mirrors the server's RA-32 generator so the credential we show the admin
     * matches what was actually persisted (we send it; the server hashes it).
     */
    fun generateInitialPassword(length: Int = 10): String {
        val alphabet = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789@#%"
        return buildString(length) {
            repeat(length) { append(alphabet[kotlin.random.Random.nextInt(alphabet.length)]) }
        }
    }

    /**
     * Provision every [teachers] entry as a real account. Best-effort per teacher:
     * a single failure (e.g. duplicate email) does NOT abort the rest — it is
     * recorded in [TeacherProvisioningState.results] so the wizard can continue.
     * Calls [onDone] once all attempts finish (always, even if some failed) so the
     * wizard never blocks onboarding on optional staff setup.
     */
    fun provisionAll(teachers: List<OnboardingTeacherInput>, onDone: () -> Unit) {
        if (_state.value.isSubmitting) return
        if (teachers.isEmpty()) { onDone(); return }

        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, errorMessage = null)

            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    errorMessage = "You are not signed in. Please log in again.",
                )
                // Don't hard-block the wizard — staff can be added later from the dashboard.
                onDone()
                return@launch
            }

            val out = mutableListOf<ProvisionedTeacher>()
            for (t in teachers) {
                val id = t.identifier.trim()
                if (t.name.isBlank() || id.isBlank()) {
                    // Skip incomplete rows silently — name-only entries are still used
                    // for the cosmetic assignment matrix but cannot create a login.
                    continue
                }
                val isEmail = id.contains("@")
                val pw = if (isEmail) (t.initialPassword?.takeIf { it.length >= 8 } ?: generateInitialPassword()) else null

                when (val r = teachersRepository.createTeacher(
                    token,
                    CreateTeacherRequest(name = t.name.trim(), identifier = id, initialPassword = pw)
                )) {
                    is NetworkResult.Success -> out.add(
                        ProvisionedTeacher(t.name.trim(), id, pw, created = true, message = "Account created")
                    )
                    is NetworkResult.Error -> {
                        AppLogger.e("TeacherProvisionOB", "createTeacher failed for $id: ${r.message}")
                        out.add(ProvisionedTeacher(t.name.trim(), id, null, created = false, message = r.message))
                    }
                    is NetworkResult.ConnectionError ->
                        out.add(ProvisionedTeacher(t.name.trim(), id, null, created = false, message = "Connection error"))
                }
            }

            _state.value = _state.value.copy(isSubmitting = false, results = out)
            onDone()
        }
    }
}
