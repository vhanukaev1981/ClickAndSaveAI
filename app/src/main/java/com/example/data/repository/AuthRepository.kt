package com.example.data.repository

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
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
    val idToken: String = "",
    val gmailOAuthAccessToken: String? = null,
    val isAuthenticated: Boolean = false
)

sealed class AuthState {
    data object Idle : AuthState()
    data object Loading : AuthState()
    data class Authenticated(val session: UserSession) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthRepository(private val context: Context) {

    private val credentialManager by lazy { CredentialManager.create(context) }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _userSession = MutableStateFlow(UserSession())
    val userSession: StateFlow<UserSession> = _userSession.asStateFlow()

    init {
        checkCurrentUser()
    }

    private fun getFirebaseAuthSafe(): FirebaseAuth? {
        return try {
            if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(context)
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

    suspend fun signInWithGoogle(webClientId: String): Result<UserSession> {
        if (webClientId.isBlank()) {
            val message = "Google Sign-In is not configured: a Web Client ID is required."
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

            val result: GetCredentialResponse = credentialManager.getCredential(
                request = request,
                context = context
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
                idToken = googleCredential.idToken,
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

    fun updateGmailOAuthAccessToken(token: String) {
        if (token.isBlank()) {
            _authState.value = AuthState.Error("A non-empty Gmail OAuth access token is required.")
            return
        }
        val updated = _userSession.value.copy(gmailOAuthAccessToken = token)
        _userSession.value = updated
        _authState.value = AuthState.Authenticated(updated)
    }

    fun signOut() {
        runCatching { getFirebaseAuthSafe()?.signOut() }
            .onFailure { Log.e("AuthRepository", "Sign-out failed", it) }
        _userSession.value = UserSession()
        _authState.value = AuthState.Idle
    }
}
