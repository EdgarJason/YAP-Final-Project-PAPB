package com.example.yap_finalprojectpapb.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yap_finalprojectpapb.data.repository.AuthRepositoryImpl
import com.example.yap_finalprojectpapb.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State penampung data formulir pendaftaran akun YAP.
 * Pada alur terbaru: Form Register hanya membutuhkan Email, Password, & Confirm Password.
 * Pengisian nama akan dilakukan setelah pendaftaran berhasil pada layar NameInputScreen.
 */
data class RegisterUiState(
    val email: String = "",
    val emailError: String? = null,
    val password: String = "",
    val passwordError: String? = null,
    val confirmPassword: String = "",
    val confirmPasswordError: String? = null,
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val combinedFullName: String = "",
    val isLoading: Boolean = false,
    val isSubmitSuccess: Boolean = false,
    val submitMessage: String? = null,
    val authError: String? = null
)

class RegisterViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    constructor() : this(AuthRepositoryImpl())

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    // Regex email standar
    private val emailPattern = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun onEmailChange(newValue: String) {
        _uiState.update { current ->
            current.copy(
                email = newValue,
                emailError = if (current.emailError != null) validateEmail(newValue) else null,
                isSubmitSuccess = false,
                authError = null
            )
        }
    }

    fun onPasswordChange(newValue: String) {
        _uiState.update { current ->
            val newConfirmError = if (current.confirmPassword.isNotEmpty() && current.confirmPasswordError != null) {
                validateConfirmPassword(current.confirmPassword, newValue)
            } else current.confirmPasswordError

            current.copy(
                password = newValue,
                passwordError = if (current.passwordError != null) validatePassword(newValue) else null,
                confirmPasswordError = newConfirmError,
                isSubmitSuccess = false,
                authError = null
            )
        }
    }

    fun onConfirmPasswordChange(newValue: String) {
        _uiState.update { current ->
            current.copy(
                confirmPassword = newValue,
                confirmPasswordError = if (current.confirmPasswordError != null) {
                    validateConfirmPassword(newValue, current.password)
                } else null,
                isSubmitSuccess = false,
                authError = null
            )
        }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun toggleConfirmPasswordVisibility() {
        _uiState.update { it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible) }
    }

    private fun validateEmail(value: String): String? {
        val trimmed = value.trim()
        return when {
            trimmed.isEmpty() -> "Email wajib diisi"
            !emailPattern.matches(trimmed) -> "Format email tidak valid (contoh: nama@mail.com)"
            else -> null
        }
    }

    private fun validatePassword(value: String): String? {
        return when {
            value.isEmpty() -> "Kata sandi wajib diisi"
            value.length < 8 -> "Kata sandi minimal 8 karakter"
            else -> null
        }
    }

    private fun validateConfirmPassword(confirm: String, pass: String): String? {
        return when {
            confirm.isEmpty() -> "Konfirmasi kata sandi wajib diisi"
            confirm != pass -> "Konfirmasi kata sandi tidak sama dengan kata sandi"
            else -> null
        }
    }

    fun validateAndSubmit(): Boolean {
        val current = _uiState.value

        val emailErr = validateEmail(current.email)
        val passwordErr = validatePassword(current.password)
        val confirmErr = validateConfirmPassword(current.confirmPassword, current.password)

        val hasError = listOf(emailErr, passwordErr, confirmErr).any { it != null }

        if (hasError) {
            _uiState.update {
                it.copy(
                    emailError = emailErr,
                    passwordError = passwordErr,
                    confirmPasswordError = confirmErr,
                    isSubmitSuccess = false,
                    submitMessage = null,
                    authError = null
                )
            }
            return false
        }

        _uiState.update {
            it.copy(
                emailError = null,
                passwordError = null,
                confirmPasswordError = null,
                isSubmitSuccess = true,
                submitMessage = "Registrasi berhasil",
                authError = null
            )
        }

        return true
    }

    fun submitRegistration(onSuccess: (String, String) -> Unit) {
        if (_uiState.value.isLoading) return
        if (!validateAndSubmit()) return

        val current = _uiState.value
        val targetEmail = current.email.trim()
        val targetPassword = current.password

        _uiState.update { it.copy(isLoading = false, isSubmitSuccess = true, authError = null) }
        onSuccess(targetEmail, targetPassword)
    }

    fun signInWithGoogle(context: Context, onSuccess: (String) -> Unit) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, authError = null) }
        viewModelScope.launch {
            try {
                val profile = authRepository.signInWithGoogle(context)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        combinedFullName = profile.fullName,
                        email = profile.email,
                        isSubmitSuccess = true,
                        authError = null
                    )
                }
                onSuccess(profile.fullName)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        authError = e.message ?: "Gagal masuk dengan Google."
                    )
                }
            }
        }
    }

    fun resetForm() {
        _uiState.value = RegisterUiState()
    }
}
