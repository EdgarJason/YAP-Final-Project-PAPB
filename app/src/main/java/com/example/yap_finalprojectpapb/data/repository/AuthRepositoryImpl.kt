package com.example.yap_finalprojectpapb.data.repository

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.yap_finalprojectpapb.domain.model.UserProfile
import com.example.yap_finalprojectpapb.domain.repository.AuthRepository
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class AuthRepositoryImpl(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : AuthRepository {

    companion object {
        private const val TAG = "AuthRepositoryImpl"
        const val WEB_CLIENT_ID = "967814301003-hjnv6oncdqhcq3ugg5p5m73cjsc0bbdb.apps.googleusercontent.com"
        private const val USERS_COLLECTION = "users"
    }

    override val isLoggedIn: Boolean
        get() = auth.currentUser != null

    override val currentUid: String?
        get() = auth.currentUser?.uid

    override fun observeUserProfile(): Flow<UserProfile?> = callbackFlow {
        var firestoreListener: ListenerRegistration? = null

        val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            firestoreListener?.remove()
            val user = firebaseAuth.currentUser
            if (user == null) {
                trySend(null)
            } else {
                trySend(user.toFallbackProfile())
                firestoreListener = firestore.collection(USERS_COLLECTION)
                    .document(user.uid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            trySend(user.toFallbackProfile())
                            return@addSnapshotListener
                        }
                        if (snapshot != null && snapshot.exists()) {
                            val profile = snapshot.toUserProfile(user)
                            trySend(profile)
                        } else {
                            trySend(user.toFallbackProfile())
                        }
                    }
            }
        }

        auth.addAuthStateListener(authListener)
        awaitClose {
            firestoreListener?.remove()
            auth.removeAuthStateListener(authListener)
        }
    }

    override suspend fun getUserProfile(): UserProfile? {
        val user = auth.currentUser ?: return null
        val snapshot = withTimeoutOrNull(4_000L) {
            runCatching {
                firestore.collection(USERS_COLLECTION).document(user.uid).get().await()
            }.getOrNull()
        }
        return if (snapshot != null && snapshot.exists()) {
            snapshot.toUserProfile(user)
        } else {
            user.toFallbackProfile()
        }
    }

    override suspend fun loginWithEmail(email: String, password: String): UserProfile {
        val trimmedEmail = email.trim()
        require(trimmedEmail.isNotEmpty()) { "Email wajib diisi." }
        require(password.isNotEmpty()) { "Password wajib diisi." }

        return try {
            val result = auth.signInWithEmailAndPassword(trimmedEmail, password).await()
            val user = result.user ?: error("Gagal memuat sesi pengguna.")
            ensureFirestoreProfile(user)
        } catch (e: Exception) {
            throw IllegalStateException(mapFirebaseError(e), e)
        }
    }

    override suspend fun registerWithEmail(
        firstName: String,
        lastName: String,
        email: String,
        password: String
    ): UserProfile {
        val trimmedFirst = firstName.trim()
        val trimmedLast = lastName.trim()
        val trimmedEmail = email.trim()
        val fullName = "$trimmedFirst $trimmedLast".trim()

        return try {
            val result = auth.createUserWithEmailAndPassword(trimmedEmail, password).await()
            val user = result.user ?: error("Gagal membuat akun pengguna.")

            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(fullName)
                .build()
            runCatching { user.updateProfile(profileUpdates).await() }

            val now = System.currentTimeMillis()
            val profile = UserProfile(
                uid = user.uid,
                firstName = trimmedFirst,
                lastName = trimmedLast,
                fullName = fullName,
                email = trimmedEmail,
                photoUrl = user.photoUrl?.toString().orEmpty(),
                createdAt = now,
                updatedAt = now
            )
            saveProfileToFirestore(profile)
            profile
        } catch (e: Exception) {
            throw IllegalStateException(mapFirebaseError(e), e)
        }
    }

    override suspend fun signInWithGoogle(context: Context): UserProfile {
        return try {
            val credentialManager = CredentialManager.create(context)
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = auth.signInWithCredential(firebaseCredential).await()
                val user = authResult.user ?: error("Gagal masuk dengan akun Google.")
                ensureFirestoreProfile(user)
            } else {
                signInWithFirebaseGoogleWebFlow(context)
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "CredentialManager cancelled/rejected: ${e.message}", e)
            val msg = e.message.orEmpty()
            if (msg.contains("16") || msg.contains("reauth", ignoreCase = true) || msg.contains("28444") || msg.contains("Developer console", ignoreCase = true)) {
                throw IllegalStateException(
                    "Penyedia Google belum aktif di Firebase Console (Authentication > Sign-in method) atau SHA-1 belum ditambahkan di Project Settings.",
                    e
                )
            } else if (msg.contains("cancel", ignoreCase = true) && !msg.contains("[")) {
                throw IllegalStateException("Login Google dibatalkan.", e)
            } else {
                throw IllegalStateException("Gagal masuk dengan Google: $msg", e)
            }
        } catch (e: GetCredentialException) {
            Log.w(TAG, "CredentialManager failed: ${e.message}, falling back to Firebase OAuthProvider", e)
            signInWithFirebaseGoogleWebFlow(context)
        } catch (e: Exception) {
            Log.e(TAG, "Google sign-in error: ${e.message}", e)
            throw IllegalStateException(mapFirebaseError(e), e)
        }
    }

    private suspend fun signInWithFirebaseGoogleWebFlow(context: Context): UserProfile {
        val activity = context.findActivity()
            ?: throw IllegalStateException("Tidak dapat membuka halaman login Google.")
        return try {
            val provider = OAuthProvider.newBuilder("google.com")
                .addCustomParameter("prompt", "select_account")
                .setScopes(listOf("email", "profile"))
                .build()

            val authResult = auth.startActivityForSignInWithProvider(activity, provider).await()
            val user = authResult.user ?: error("Gagal masuk dengan akun Google.")
            ensureFirestoreProfile(user)
        } catch (e: Exception) {
            Log.e(TAG, "Firebase OAuthProvider google.com error: ${e.message}", e)
            throw IllegalStateException(mapFirebaseError(e), e)
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    override suspend fun sendPasswordReset(email: String) {
        val trimmedEmail = email.trim()
        require(trimmedEmail.isNotEmpty()) { "Email wajib diisi." }
        try {
            auth.sendPasswordResetEmail(trimmedEmail).await()
        } catch (e: Exception) {
            throw IllegalStateException(mapFirebaseError(e), e)
        }
    }

    override suspend fun updateName(newName: String): UserProfile {
        val trimmed = newName.trim()
        require(trimmed.length >= 2) { "Nama minimal 2 karakter." }
        val user = auth.currentUser ?: error("Sesi login tidak ditemukan.")

        try {
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(trimmed)
                .build()
            user.updateProfile(profileUpdates).await()

            val parts = trimmed.split(" ", limit = 2)
            val firstName = parts.firstOrNull().orEmpty()
            val lastName = if (parts.size > 1) parts[1] else ""
            val now = System.currentTimeMillis()

            withTimeoutOrNull(4_000L) {
                runCatching {
                    firestore.collection(USERS_COLLECTION).document(user.uid)
                        .set(
                            mapOf(
                                "uid" to user.uid,
                                "firstName" to firstName,
                                "lastName" to lastName,
                                "fullName" to trimmed,
                                "email" to user.email.orEmpty(),
                                "updatedAt" to now
                            ),
                            SetOptions.merge()
                        ).await()
                }
            }
            return getUserProfile() ?: user.toFallbackProfile().copy(fullName = trimmed)
        } catch (e: Exception) {
            throw IllegalStateException(mapFirebaseError(e), e)
        }
    }

    override suspend fun changeEmail(newEmail: String, currentPassword: String?) {
        val trimmedEmail = newEmail.trim()
        require(trimmedEmail.isNotEmpty() && trimmedEmail.contains("@")) {
            "Masukkan alamat email baru yang valid."
        }
        val user = auth.currentUser ?: error("Sesi login tidak ditemukan.")

        try {
            if (!currentPassword.isNullOrBlank() && !user.email.isNullOrBlank()) {
                val credential = EmailAuthProvider.getCredential(user.email!!, currentPassword)
                user.reauthenticate(credential).await()
            }
            user.verifyBeforeUpdateEmail(trimmedEmail).await()
            withTimeoutOrNull(4_000L) {
                runCatching {
                    firestore.collection(USERS_COLLECTION).document(user.uid)
                        .set(
                            mapOf(
                                "pendingEmail" to trimmedEmail,
                                "updatedAt" to System.currentTimeMillis()
                            ),
                            SetOptions.merge()
                        ).await()
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException(mapFirebaseError(e), e)
        }
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String) {
        require(currentPassword.isNotEmpty()) { "Password saat ini wajib diisi." }
        require(newPassword.length >= 8) { "Password baru minimal 8 karakter." }
        val user = auth.currentUser ?: error("Sesi login tidak ditemukan.")
        val email = user.email ?: error("Akun ini tidak menggunakan login email/password.")

        try {
            val credential = EmailAuthProvider.getCredential(email, currentPassword)
            user.reauthenticate(credential).await()
            user.updatePassword(newPassword).await()
        } catch (e: Exception) {
            throw IllegalStateException(mapFirebaseError(e), e)
        }
    }

    override suspend fun deleteAccount(password: String) {
        val user = auth.currentUser ?: error("Sesi login tidak ditemukan.")
        val email = user.email
        val usesPasswordProvider = user.providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID }

        if (usesPasswordProvider) {
            require(password.isNotEmpty()) { "Masukkan password untuk konfirmasi penghapusan akun." }
        }

        try {
            if (usesPasswordProvider && !email.isNullOrBlank()) {
                val credential = EmailAuthProvider.getCredential(email, password)
                user.reauthenticate(credential).await()
            }
            val uid = user.uid
            withTimeoutOrNull(4_000L) {
                runCatching {
                    firestore.collection(USERS_COLLECTION).document(uid).delete().await()
                }
            }
            user.delete().await()
        } catch (e: Exception) {
            throw IllegalStateException(mapFirebaseError(e), e)
        }
    }

    override fun logout() {
        auth.signOut()
    }

    private suspend fun ensureFirestoreProfile(user: FirebaseUser): UserProfile {
        val docRef = firestore.collection(USERS_COLLECTION).document(user.uid)
        val snapshot = withTimeoutOrNull(4_000L) {
            runCatching { docRef.get().await() }.getOrNull()
        }
        if (snapshot != null && snapshot.exists()) {
            return snapshot.toUserProfile(user)
        }
        val fallback = user.toFallbackProfile()
        saveProfileToFirestore(fallback)
        return fallback
    }

    private suspend fun saveProfileToFirestore(profile: UserProfile) {
        withTimeoutOrNull(4_000L) {
            runCatching {
                firestore.collection(USERS_COLLECTION)
                    .document(profile.uid)
                    .set(
                        mapOf(
                            "uid" to profile.uid,
                            "firstName" to profile.firstName,
                            "lastName" to profile.lastName,
                            "fullName" to profile.fullName,
                            "email" to profile.email,
                            "photoUrl" to profile.photoUrl,
                            "createdAt" to profile.createdAt,
                            "updatedAt" to profile.updatedAt
                        ),
                        SetOptions.merge()
                    )
                    .await()
            }
        }
    }

    private fun FirebaseUser.toFallbackProfile(): UserProfile {
        val display = displayName?.trim().orEmpty()
        val fallbackName = if (display.isNotEmpty()) {
            display
        } else {
            email?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: "Pengguna YAP"
        }
        val parts = fallbackName.split(" ", limit = 2)
        val hasPass = providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID }
        return UserProfile(
            uid = uid,
            firstName = parts.firstOrNull().orEmpty(),
            lastName = if (parts.size > 1) parts[1] else "",
            fullName = fallbackName,
            email = email.orEmpty(),
            photoUrl = photoUrl?.toString().orEmpty(),
            hasPassword = hasPass
        )
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toUserProfile(user: FirebaseUser): UserProfile {
        val fallback = user.toFallbackProfile()
        val firstName = getString("firstName")?.takeIf { it.isNotBlank() } ?: fallback.firstName
        val lastName = getString("lastName") ?: fallback.lastName
        val fullName = getString("fullName")?.takeIf { it.isNotBlank() }
            ?: "$firstName $lastName".trim().ifEmpty { fallback.fullName }
        val email = getString("email")?.takeIf { it.isNotBlank() } ?: fallback.email
        val photoUrl = getString("photoUrl")?.takeIf { it.isNotBlank() } ?: fallback.photoUrl
        val createdAt = getLong("createdAt") ?: fallback.createdAt
        val updatedAt = getLong("updatedAt") ?: fallback.updatedAt
        return UserProfile(
            uid = user.uid,
            firstName = firstName,
            lastName = lastName,
            fullName = fullName,
            email = email,
            photoUrl = photoUrl,
            hasPassword = fallback.hasPassword,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun mapFirebaseError(error: Exception): String {
        val msg = error.localizedMessage.orEmpty()
        return when {
            msg.contains("operation is not allowed", ignoreCase = true) || msg.contains("sign-in provider is disabled", ignoreCase = true) ->
                "Penyedia login ini belum diaktifkan di Firebase Console. Buka Authentication > Sign-in method lalu aktifkan Email/Password & Google."
            error is FirebaseAuthInvalidUserException -> "Akun dengan email tersebut tidak ditemukan."
            error is FirebaseAuthWeakPasswordException -> "Password terlalu lemah (minimal 8 karakter)."
            error is FirebaseAuthInvalidCredentialsException -> "Email atau password yang dimasukkan salah."
            error is FirebaseAuthUserCollisionException -> "Email ini sudah terdaftar. Silakan gunakan fitur Log In."
            error is FirebaseAuthRecentLoginRequiredException -> "Sesi keamanan kedaluwarsa. Silakan logout dan login kembali terlebih dahulu."
            msg.contains("canceled", ignoreCase = true) || msg.contains("cancelled", ignoreCase = true) -> "Login Google dibatalkan oleh pengguna."
            msg.isNotBlank() -> msg
            else -> "Terjadi kesalahan saat menghubungi layanan autentikasi Firebase."
        }
    }
}
