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
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
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
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
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
            val message = "Google Sign-In was cancelled or failed."
            _authState.value = AuthState.Error(message)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Authentication failed", e)
            val message = e.localizedMessage ?: "Authentication failed"
            _authState.value = AuthState.Error(message)
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        // Revoke this device while Firebase Auth is still valid. A transient revocation failure
        // must never trap the user in a signed-in session; PushTokenLifecycle also attempts to
        // delete the local FCM token so a later login receives a fresh registration.
        PushTokenLifecycle.revokeCurrentDeviceBeforeSignOut()
            .onFailure { Log.w("AuthRepository", "Push revocation incomplete during sign-out", it) }

        runCatching { getFirebaseAuthSafe()?.signOut() }
            .onFailure { Log.e("AuthRepository", "Sign-out failed", it) }

        // Invoice data can originate from the signed-in Gmail account. Purge it before another
        // account can be used on the same device. Android backup is disabled, so this also keeps
        // account-derived invoice metadata from lingering locally after sign-out.
        runCatching {
            AppDatabase.getDatabase(applicationContext).invoiceDao().deleteAllInvoices()
        }.onFailure {
            Log.e("AuthRepository", "Local invoice purge on sign-out failed", it)
        }

        _userSession.value = UserSession()
        _authState.value = AuthState.Idle
    }
}
