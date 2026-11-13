package com.example.yap_finalprojectpapb.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yap_finalprojectpapb.data.repository.AuthRepositoryImpl
import com.example.yap_finalprojectpapb.domain.model.UserProfile
import com.example.yap_finalprojectpapb.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoggedIn: Boolean = false,
    val userProfile: UserProfile? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    // Explicit no-arg constructor required by ViewModelProvider.NewInstanceFactory
    constructor() : this(AuthRepositoryImpl())

    private val _uiState = MutableStateFlow(
        AuthUiState(isLoggedIn = authRepository.isLoggedIn)
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.observeUserProfile().collectLatest { profile ->
                _uiState.update {
                    it.copy(
                        isLoggedIn = profile != null || authRepository.isLoggedIn,
                        userProfile = profile
                    )
                }
            }
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun registerWithEmail(
        email: String,
        password: String,
        onSuccess: () -> Unit
    ) {
        if (_uiState.value.isLoading) return
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || password.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Email dan password wajib diisi.") }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            try {
                val profile = authRepository.registerWithEmail(
                    firstName = "User",
                    lastName = "",
                    email = trimmedEmail,
                    password = password
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        userProfile = profile,
                        errorMessage = null
                    )
                }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Gagal membuat akun."
                    )
                }
            }
        }
    }

    fun loginWithEmail(
        email: String,
        password: String,
        onSuccess: () -> Unit
    ) {
        if (_uiState.value.isLoading) return
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || password.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Email dan password wajib diisi.") }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            try {
                val profile = authRepository.loginWithEmail(trimmedEmail, password)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        userProfile = profile,
                        errorMessage = null
                    )
                }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Gagal melakukan login."
                    )
                }
            }
        }
    }

    fun signInWithGoogle(
        context: Context,
        onSuccess: () -> Unit
    ) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            try {
                val profile = authRepository.signInWithGoogle(context)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        userProfile = profile,
                        errorMessage = null
                    )
                }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Gagal masuk dengan Google."
                    )
                }
            }
        }
    }

    fun sendPasswordReset(
        email: String,
        onSuccess: () -> Unit
    ) {
        if (_uiState.value.isLoading) return
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || !trimmedEmail.contains("@")) {
            _uiState.update { it.copy(errorMessage = "Masukkan alamat email yang valid.") }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            try {
                authRepository.sendPasswordReset(trimmedEmail)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = "Tautan reset password telah dikirim ke $trimmedEmail."
                    )
                }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Gagal mengirim email reset password."
                    )
                }
            }
        }
    }

    fun updateName(
        newName: String,
        onSuccess: () -> Unit
    ) {
        if (_uiState.value.isLoading) return
        val trimmed = newName.trim()
        if (trimmed.length < 2) {
            _uiState.update { it.copy(errorMessage = "Nama lengkap minimal 2 karakter.") }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            try {
                val updated = authRepository.updateName(trimmed)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        userProfile = updated,
                        successMessage = "Nama berhasil diperbarui."
                    )
                }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Gagal memperbarui nama."
                    )
                }
            }
        }
    }

    fun changeEmail(
        newEmail: String,
        currentPassword: String? = null,
        onSuccess: () -> Unit
    ) {
        if (_uiState.value.isLoading) return
        val trimmed = newEmail.trim()
        if (trimmed.isEmpty() || !trimmed.contains("@")) {
            _uiState.update { it.copy(errorMessage = "Masukkan alamat email baru yang valid.") }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            try {
                authRepository.changeEmail(trimmed, currentPassword)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = "Email verifikasi telah dikirim ke $trimmed."
                    )
                }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Gagal mengubah email."
                    )
                }
            }
        }
    }

    fun changePassword(
        currentPassword: String,
        newPassword: String,
        confirmNewPassword: String,
        onSuccess: () -> Unit
    ) {
        if (_uiState.value.isLoading) return
        when {
            currentPassword.isEmpty() -> {
                _uiState.update { it.copy(errorMessage = "Password saat ini wajib diisi.") }
                return
            }
            newPassword.length < 8 -> {
                _uiState.update { it.copy(errorMessage = "Password baru minimal 8 karakter.") }
                return
            }
            newPassword != confirmNewPassword -> {
                _uiState.update { it.copy(errorMessage = "Konfirmasi password baru tidak cocok.") }
                return
            }
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            try {
                authRepository.changePassword(currentPassword, newPassword)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = "Password berhasil diperbarui."
                    )
                }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Gagal memperbarui password."
                    )
                }
            }
        }
    }

    fun deleteAccount(
        password: String,
        onSuccess: () -> Unit
    ) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            try {
                authRepository.deleteAccount(password)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoggedIn = false,
                        userProfile = null
                    )
                }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Gagal menghapus akun."
                    )
                }
            }
        }
    }

    fun logout(onSuccess: () -> Unit = {}) {
        authRepository.logout()
        _uiState.update {
            AuthUiState(isLoggedIn = false, userProfile = null)
        }
        onSuccess()
    }
}
