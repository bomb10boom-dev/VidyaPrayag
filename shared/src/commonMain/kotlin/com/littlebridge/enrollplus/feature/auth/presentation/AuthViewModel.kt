package com.littlebridge.enrollplus.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.auth.domain.model.*
import com.littlebridge.enrollplus.feature.auth.domain.repository.AuthRepository
import com.littlebridge.enrollplus.util.AppLogger
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class AuthStep {
    data object Identifier : AuthStep()
    data object LoginPassword : AuthStep()
    data object SignupDetails : AuthStep()
    data object Otp : AuthStep()
}

data class AuthUiState(
    val step: AuthStep = AuthStep.Identifier,
    val flow: AuthFlow? = null,
    val identifier: String = "",
    val name: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val otp: String = "",
    val role: String = "PARENT", // "ADMIN" | "TEACHER" | "PARENT"
    val isLoading: Boolean = false,
    val error: String? = null,
    val isAuthSuccessful: Boolean = false,
    // ── "Onboard your school" self-registration ─────────────────────────────
    // When true, the Admin auth screen shows the school-registration form
    // instead of the "contact your administrator" notice.
    val isRegisterSchool: Boolean = false,
    val schoolName: String = "",
    val board: String = "CBSE",      // CBSE | ICSE | UP State | Other
    val schoolType: String = "Private Unaided",
    val city: String = "",
)

class AuthViewModel(
    private val repository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state = _state.asStateFlow()

    fun onIdentifierChanged(value: String) = _state.update { it.copy(identifier = value, error = null) }
    fun onNameChanged(value: String) = _state.update { it.copy(name = value) }
    fun onPasswordChanged(value: String) = _state.update { it.copy(password = value) }
    fun onConfirmPasswordChanged(value: String) = _state.update { it.copy(confirmPassword = value) }
    fun onOtpChanged(value: String) = _state.update { it.copy(otp = value) }
    fun onRoleChanged(value: String) = _state.update { it.copy(role = value) }

    // ── School self-registration field mutations ────────────────────────────
    fun onSchoolNameChanged(value: String) = _state.update { it.copy(schoolName = value, error = null) }
    fun onBoardChanged(value: String) = _state.update { it.copy(board = value) }
    fun onSchoolTypeChanged(value: String) = _state.update { it.copy(schoolType = value) }
    fun onCityChanged(value: String) = _state.update { it.copy(city = value) }

    /** Reveal the school-registration form (from the staff "no account" notice). */
    fun startRegisterSchool() = _state.update {
        it.copy(isRegisterSchool = true, error = null, name = "", password = "", confirmPassword = "")
    }

    /**
     * Jump STRAIGHT into the school-registration form from the very first
     * (Identifier) step — this powers the always-visible "Haven't registered?
     * Onboard with us now" link on the School Administration login page, so a
     * brand-new admin never has to type a throwaway email and hit a dead-end to
     * discover onboarding. Moves the step machine to [AuthStep.SignupDetails] and
     * flips the form into register mode in one shot.
     */
    fun startRegisterSchoolDirect() = _state.update {
        it.copy(
            step = AuthStep.SignupDetails,
            isRegisterSchool = true,
            error = null,
            name = "",
            password = "",
            confirmPassword = "",
        )
    }

    /** Return from the registration form back to the notice / sign-in. */
    fun cancelRegisterSchool() = _state.update {
        // If we entered the form directly from the Identifier step (no flow was
        // ever resolved), go back to Identifier; otherwise stay on the notice.
        if (it.flow == null) it.copy(isRegisterSchool = false, step = AuthStep.Identifier, error = null)
        else it.copy(isRegisterSchool = false, error = null)
    }

    /**
     * "Onboard your school" — validates the form and calls the public
     * /auth/register-school endpoint. On success the session is persisted with
     * profile_completed=false so the post-auth gate routes into the wizard.
     */
    fun registerSchool() {
        val s = _state.value
        when {
            s.name.isBlank() -> { _state.update { it.copy(error = "Please enter your name") }; return }
            s.identifier.isBlank() || !s.identifier.contains("@") ->
                { _state.update { it.copy(error = "Please enter a valid email") }; return }
            s.schoolName.isBlank() -> { _state.update { it.copy(error = "Please enter your school's name") }; return }
            s.password.length < 8 -> { _state.update { it.copy(error = "Password must be at least 8 characters") }; return }
            s.confirmPassword.isNotEmpty() && s.password != s.confirmPassword ->
                { _state.update { it.copy(error = "Passwords do not match") }; return }
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = repository.registerSchool(
                SchoolRegisterRequest(
                    name = s.name.trim(),
                    identifier = s.identifier.trim(),
                    password = s.password,
                    schoolName = s.schoolName.trim(),
                    board = s.board,
                    schoolType = s.schoolType,
                    city = s.city.trim().ifBlank { null },
                )
            )
            when (result) {
                is NetworkResult.Success -> _state.update { it.copy(isLoading = false, isAuthSuccessful = true) }
                is NetworkResult.Error -> {
                    AppLogger.e("AuthViewModel", "registerSchool error: ${result.message}")
                    _state.update { it.copy(isLoading = false, error = result.message) }
                }
                is NetworkResult.ConnectionError ->
                    _state.update { it.copy(isLoading = false, error = "Connection error. Please try again.") }
            }
        }
    }

    fun onContinue() {
        val currentState = _state.value
        if (currentState.identifier.isBlank()) {
            _state.update { it.copy(error = "Please enter email or phone") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.checkUser(currentState.identifier)) {
                is NetworkResult.Success -> {
                    val flow = result.data
                    AppLogger.d("AuthViewModel", "Check user success. Flow determined: $flow")
                    _state.update { 
                        it.copy(
                            isLoading = false,
                            flow = flow,
                            step = when (flow) {
                                AuthFlow.LOGIN_EMAIL -> AuthStep.LoginPassword
                                AuthFlow.SIGNUP_EMAIL -> AuthStep.SignupDetails
                                AuthFlow.LOGIN_PHONE, AuthFlow.SIGNUP_PHONE -> AuthStep.Otp
                            }
                        )
                    }
                    if (flow == AuthFlow.LOGIN_PHONE || flow == AuthFlow.SIGNUP_PHONE) {
                        repository.sendOtp(currentState.identifier, if (flow == AuthFlow.LOGIN_PHONE) "login" else "signup")
                    }
                }
                is NetworkResult.Error -> {
                    AppLogger.e("AuthViewModel", "Check user error: ${result.message}")
                    _state.update { it.copy(isLoading = false, error = result.message) }
                }
                is NetworkResult.ConnectionError -> {
                    AppLogger.e("AuthViewModel", "Check user: Connection Error")
                    _state.update { it.copy(isLoading = false, error = "Connection error. Please try again.") }
                }
            }
        }
    }

    fun onSubmit() {
        val currentState = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            
            val result = when (currentState.step) {
                AuthStep.LoginPassword -> {
                    repository.login(LoginRequest(
                        identifier = currentState.identifier,
                        password = currentState.password,
                        role = currentState.role
                    ))
                }
                AuthStep.SignupDetails -> {
                    if (currentState.name.isBlank()) {
                        _state.update { it.copy(isLoading = false, error = "Please enter your name") }
                        return@launch
                    }
                    if (currentState.password.isBlank()) {
                        _state.update { it.copy(isLoading = false, error = "Please create a password") }
                        return@launch
                    }
                    // Only enforce the confirm-password match when the UI actually collected a
                    // confirmation value. Otherwise confirmPassword is "" and the check would
                    // always fail even though the user only ever saw a single password field.
                    if (currentState.confirmPassword.isNotEmpty() &&
                        currentState.password != currentState.confirmPassword
                    ) {
                        _state.update { it.copy(isLoading = false, error = "Passwords do not match") }
                        return@launch
                    }
                    repository.signup(SignupRequest(
                        name = currentState.name,
                        identifier = currentState.identifier,
                        password = currentState.password,
                        role = currentState.role
                    ))
                }
                AuthStep.Otp -> {
                    if (currentState.flow == AuthFlow.SIGNUP_PHONE && currentState.name.isBlank()) {
                        _state.update { it.copy(isLoading = false, error = "Please enter your name") }
                        return@launch
                    }
                    
                    if (currentState.flow == AuthFlow.SIGNUP_PHONE) {
                        repository.signup(SignupRequest(
                            name = currentState.name,
                            identifier = currentState.identifier,
                            otp = currentState.otp,
                            role = currentState.role
                        ))
                    } else {
                        repository.login(LoginRequest(
                            identifier = currentState.identifier,
                            otp = currentState.otp,
                            role = currentState.role
                        ))
                    }
                }
                else -> return@launch
            }

            when (result) {
                is NetworkResult.Success -> {
                    _state.update { it.copy(isLoading = false, isAuthSuccessful = true) }
                }
                is NetworkResult.Error -> {
                    AppLogger.e("AuthViewModel", "Submit error: ${result.message}")
                    _state.update { it.copy(isLoading = false, error = result.message) }
                }
                is NetworkResult.ConnectionError -> {
                    AppLogger.e("AuthViewModel", "Submit: Connection Error")
                    _state.update { it.copy(isLoading = false, error = "Connection error. Please try again.") }
                }
            }
        }
    }
    
    fun goBack() {
        _state.update { it.copy(step = AuthStep.Identifier, error = null) }
    }

    /**
     * Clears every field of the auth form back to a pristine [AuthUiState].
     *
     * The [AuthViewModel] outlives a single sign-in attempt (it is bound to the unauth
     * ViewModelStoreOwner, so the same instance is reused after a logout → landing → login
     * round-trip). Without an explicit reset, the previously typed identifier/password —
     * and even a stale `isAuthSuccessful` flag — leak into the next login attempt. Each auth
     * screen calls this on mount so re-entering sign-in always starts from a blank form.
     */
    fun reset() {
        _state.value = AuthUiState()
    }
}
