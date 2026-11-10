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
 * Data disimpan di dalam ViewModel sehingga bertahan (survive) saat orientasi layar
 * berubah (Portrait <-> Landscape) sesuai ketentuan Tugas 2 PAPB.
 */
data class RegisterUiState(
    val firstName: String = "",
    val firstNameError: String? = null,
    val lastName: String = "",
    val lastNameError: String? = null,
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
    // Regex huruf & spasi saja untuk nama
    private val namePattern = Regex("^[a-zA-Z\\s]+$")

    fun onFirstNameChange(newValue: String) {
        _uiState.update { current ->
            current.copy(
                firstName = newValue,
                firstNameError = if (current.firstNameError != null) validateFirstName(newValue) else null,
                isSubmitSuccess = false,
                authError = null
            )
        }
    }

    fun onLastNameChange(newValue: String) {
        _uiState.update { current ->
            current.copy(
                lastName = newValue,
                lastNameError = if (current.lastNameError != null) validateLastName(newValue) else null,
                isSubmitSuccess = false,
                authError = null
            )
        }
    }

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

    private fun validateFirstName(value: String): String? {
        val trimmed = value.trim()
        return when {
            trimmed.isEmpty() -> "Nama depan wajib diisi"
            trimmed.length < 2 -> "Nama depan minimal 2 karakter"
            !namePattern.matches(trimmed) -> "Nama depan hanya boleh berisi huruf"
            else -> null
        }
    }

    private fun validateLastName(value: String): String? {
        val trimmed = value.trim()
        return when {
            trimmed.isEmpty() -> "Nama belakang wajib diisi"
            !namePattern.matches(trimmed) -> "Nama belakang hanya boleh berisi huruf"
            else -> null
        }
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

    /**
     * Memvalidasi seluruh elemen input dan menggabungkan data (Nama Depan + Nama Belakang).
     * Mengembalikan true jika seluruh input valid secara lokal.
     */
    fun validateAndSubmit(): Boolean {
        val current = _uiState.value

        val firstNameErr = validateFirstName(current.firstName)
        val lastNameErr = validateLastName(current.lastName)
        val emailErr = validateEmail(current.email)
        val passwordErr = validatePassword(current.password)
        val confirmErr = validateConfirmPassword(current.confirmPassword, current.password)

        val hasError = listOf(firstNameErr, lastNameErr, emailErr, passwordErr, confirmErr).any { it != null }

        if (hasError) {
            _uiState.update {
                it.copy(
                    firstNameError = firstNameErr,
                    lastNameError = lastNameErr,
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

        val fullName = "${current.firstName.trim()} ${current.lastName.trim()}"

        _uiState.update {
            it.copy(
                firstNameError = null,
                lastNameError = null,
                emailError = null,
                passwordError = null,
                confirmPasswordError = null,
                combinedFullName = fullName,
                isSubmitSuccess = true,
                submitMessage = "Akun berhasil dibuat untuk $fullName",
                authError = null
            )
        }

        return true
    }

    fun submitRegistration(onSuccess: (String) -> Unit) {
        if (_uiState.value.isLoading) return
        if (!validateAndSubmit()) return

        val current = _uiState.value
        val fullName = current.combinedFullName
        _uiState.update { it.copy(isLoading = true, authError = null) }

        viewModelScope.launch {
            try {
                authRepository.registerWithEmail(
                    firstName = current.firstName.trim(),
                    lastName = current.lastName.trim(),
                    email = current.email.trim(),
                    password = current.password
                )
                _uiState.update { it.copy(isLoading = false, isSubmitSuccess = true, authError = null) }
                onSuccess(fullName)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isSubmitSuccess = false,
                        authError = e.message ?: "Gagal mendaftarkan akun ke Firebase."
                    )
                }
            }
        }
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
