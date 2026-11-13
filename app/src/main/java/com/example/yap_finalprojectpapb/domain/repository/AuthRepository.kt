package com.example.yap_finalprojectpapb.domain.repository

import android.content.Context
import com.example.yap_finalprojectpapb.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val isLoggedIn: Boolean
    val currentUid: String?
    fun observeUserProfile(): Flow<UserProfile?>
    suspend fun getUserProfile(): UserProfile?
    suspend fun loginWithEmail(email: String, password: String): UserProfile
    suspend fun registerWithEmail(
        firstName: String,
        lastName: String,
        email: String,
        password: String
    ): UserProfile
    suspend fun signInWithGoogle(context: Context): UserProfile
    suspend fun sendEmailOtp(email: String, otpCode: String)
    suspend fun sendEmailVerification()
    suspend fun sendPasswordReset(email: String)
    suspend fun updateName(newName: String): UserProfile
    suspend fun changeEmail(newEmail: String, currentPassword: String? = null)
    suspend fun changePassword(currentPassword: String, newPassword: String)
    suspend fun deleteAccount(password: String)
    fun logout()
}
