package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.example.PushTokenLifecycle
import com.example.data.local.AppDatabase
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

data class UserSession(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val isAuthenticated: Boolean = false
)

sealed class AuthState {
    data object Idle : AuthState()
    data object Loading : AuthState()
    data class Authenticated(val session: UserSession) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthRepository(private val applicationContext: Context) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _userSession = MutableStateFlow(UserSession())
    val userSession: StateFlow<UserSession> = _userSession.asStateFlow()

    init {
        checkCurrentUser()
    }

    private fun getFirebaseAuthSafe(): FirebaseAuth? {
        return try {
            if (com.google.firebase.FirebaseApp.getApps(applicationContext).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(applicationContext)
            }
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.w("AuthRepository", "Firebase is unavailable: ${e.localizedMessage}")
            null
        }
    }

    fun checkCurrentUser() {
        val currentUser = getFirebaseAuthSafe()?.currentUser
        if (currentUser == null) {
            _userSession.value = UserSession()
            _authState.value = AuthState.Idle
            return
        }

        val session = UserSession(
            uid = currentUser.uid,
            email = currentUser.email.orEmpty(),
            displayName = currentUser.displayName.orEmpty(),
            photoUrl = currentUser.photoUrl?.toString().orEmpty(),
            isAuthenticated = true
        )
        _userSession.value = session
        _authState.value = AuthState.Authenticated(session)
    }

    suspend fun signInWithGoogle(activity: Activity, webClientId: String): Result<UserSession> {
        if (webClientId.isBlank()) {
            val message = "Google Sign-In is not configured: google_web_client_id is required."
            _authState.value = AuthState.Error(message)
            return Result.failure(IllegalStateException(message))
        }

        _authState.value = AuthState.Loading
        return try {
            // This method is invoked by an explicit "Sign in with Google" button.
            // Google's Credential Manager guidance uses GetSignInWithGoogleOption for this flow;
            // GetGoogleIdOption is intended for the bottom-sheet account discovery flow and can
            // return NoCredentialException on explicit button sign-in even when Google accounts exist.
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val result: GetCredentialResponse = CredentialManager.create(activity).getCredential(
                request = request,
                context = activity
            )

            val credential = result.credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                throw IllegalStateException("Unsupported Google credential type")
            }

            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
            val auth = getFirebaseAuthSafe()
                ?: throw IllegalStateException("Firebase Authentication is not configured")
            val user = auth.signInWithCredential(firebaseCredential).await().user
                ?: throw IllegalStateException("Firebase returned no authenticated user")

            val session = UserSession(
                uid = user.uid,
                email = user.email.orEmpty(),
                displayName = user.displayName.orEmpty(),
                photoUrl = user.photoUrl?.toString().orEmpty(),
                isAuthenticated = true
            )
            _userSession.value = session
            _authState.value = AuthState.Authenticated(session)
            Result.success(session)
        } catch (e: GetCredentialException) {
            Log.e("AuthRepository", "Google Sign-In failed", e)
            val exceptionType = e::class.java.simpleName.ifBlank { "GetCredentialException" }
            val exceptionDetail = e.localizedMessage?.trim().takeUnless { it.isNullOrBlank() }
            val message = buildString {
                append("Google Sign-In failed [")
                append(exceptionType)
                append("]")
                if (exceptionDetail != null) {
                    append(": ")
                    append(exceptionDetail.take(240))
                }
            }
            _authState.value = AuthState.Error(message)
            Result.failure(e)
        } catch (e: CancellationException) {
            _authState.value = AuthState.Idle
            throw e
        } catch (e: Exception) {
            Log.e("AuthRepository", "Authentication failed", e)
            val message = e.localizedMessage ?: "Authentication failed"
            _authState.value = AuthState.Error(message)
            Result.failure(e)
        }
    }

    fun setAuthError(message: String) {
        _authState.value = AuthState.Error(message)
    }

    suspend fun purgeImportedFinancialDataLocally() {
        AppDatabase.getDatabase(applicationContext).invoiceDao().deleteAllInvoices()
    }

    suspend fun signOut() {
        _authState.value = AuthState.Loading
        try {
            PushTokenLifecycle.revokeCurrentDeviceBeforeSignOut().getOrThrow()
            purgeImportedFinancialDataLocally()
            getFirebaseAuthSafe()?.signOut()
            _userSession.value = UserSession()
            _authState.value = AuthState.Idle
        } catch (e: CancellationException) {
            _authState.value = AuthState.Idle
            throw e
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.localizedMessage ?: "Sign-out failed")
            throw e
        }
    }

    suspend fun completeAccountDeletionLocalCleanup() {
        PushTokenLifecycle.deleteLocalTokenAfterAccountDeletion()
            .onFailure { Log.w("AuthRepository", "Local FCM cleanup after account deletion failed", it) }

        purgeImportedFinancialDataLocally()
        getFirebaseAuthSafe()?.signOut()
        _userSession.value = UserSession()
        _authState.value = AuthState.Idle
    }
}
