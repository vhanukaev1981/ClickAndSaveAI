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
            // Sign-out is intentionally distinct from Gmail disconnect and account deletion.
            // Revoke this device's push registration while Auth is still valid.
            // Failure is a hard gate: the result must be consumed before Firebase Auth sign-out
            // so that a revocation failure aborts the flow rather than leaving a live token behind.
            PushTokenLifecycle.revokeCurrentDeviceBeforeSignOut()
                .getOrThrow()

            // Account-derived invoice data must be gone before the local authenticated session ends.
            // Failure is a hard gate so another account cannot inherit stale financial data.
            purgeImportedFinancialDataLocally()

            getFirebaseAuthSafe()?.signOut()
            _userSession.value = UserSession()
            _authState.value = AuthState.Idle
        } catch (e: CancellationException) {
            // Coroutine was cancelled — restore the previous state without treating it as an error,
            // and propagate cancellation so structured concurrency is not broken.
            _authState.value = AuthState.Idle
            throw e
        } catch (e: Exception) {
            // Surface the error so the caller/UI can retry rather than leaving the session
            // in an indeterminate state or silently completing a partial sign-out.
            _authState.value = AuthState.Error(e.localizedMessage ?: "Sign-out failed")
            throw e
        }
    }

    suspend fun completeAccountDeletionLocalCleanup() {
        // The server already removed every push registration under the deleted account. Local FCM
        // deletion is therefore secondary cleanup and must not resurrect or call the deleted user.
        PushTokenLifecycle.deleteLocalTokenAfterAccountDeletion()
            .onFailure { Log.w("AuthRepository", "Local FCM cleanup after account deletion failed", it) }

        purgeImportedFinancialDataLocally()
        getFirebaseAuthSafe()?.signOut()
        _userSession.value = UserSession()
        _authState.value = AuthState.Idle
    }
}
