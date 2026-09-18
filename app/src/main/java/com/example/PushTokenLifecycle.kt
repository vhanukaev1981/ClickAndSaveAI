package com.example

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Owns privacy-sensitive lifecycle handling for the current device's FCM registration.
 *
 * Registration remains handled by [PushRegistration]. Sign-out calls this object while the
 * Firebase user is still authenticated so the backend can delete the exact current token. The
 * local FCM token is then deleted as a second line of defense so a stale backend registration
 * cannot continue representing the signed-out device indefinitely.
 */
object PushTokenLifecycle {
    private const val TAG = "PushTokenLifecycle"
    private const val FCM_OPERATION_TIMEOUT_MS = 5_000L
    @Volatile private var registrationSuppressedForSignOut: Boolean = false

    fun beginSignOutRegistrationSuppression() {
        registrationSuppressedForSignOut = true
    }

    fun endSignOutRegistrationSuppression() {
        registrationSuppressedForSignOut = false
    }

    fun isRegistrationSuppressed(): Boolean = registrationSuppressedForSignOut

    suspend fun revokeCurrentDeviceBeforeSignOut(): Result<Unit> {
        val messaging = FirebaseMessaging.getInstance()
        val authenticated = FirebaseAuth.getInstance().currentUser != null

        var tokenResolutionFailure: Throwable? = null
        var backendRevoked = false
        var localDeleted = false
        val token = runCatching {
            withTimeout(FCM_OPERATION_TIMEOUT_MS) { messaging.token.await().trim() }
        }.onFailure { error ->
            tokenResolutionFailure = error
            Log.w(TAG, "Unable to resolve current FCM token before sign-out", error)
        }.getOrNull().orEmpty()

        if (authenticated && token.isNotEmpty()) {
            runCatching {
                withTimeout(FCM_OPERATION_TIMEOUT_MS) {
                    FirebaseFunctions.getInstance("europe-west1")
                        .getHttpsCallable("unregisterPushToken")
                        .call(mapOf("token" to token))
                        .await()
                }
            }.onSuccess {
                backendRevoked = true
            }.onFailure { error ->
                Log.w(TAG, "Backend FCM token revocation failed before sign-out", error)
            }
        }

        // Always try to delete the local token even when the authenticated backend revocation
        // fails. Firebase will mint a fresh token on a later authenticated session, while the
        // server delivery path already deletes registrations that FCM reports as invalid.
        val localDeletion = runCatching {
            withTimeout(FCM_OPERATION_TIMEOUT_MS) { messaging.deleteToken().await() }
        }.onSuccess {
            localDeleted = true
        }.onFailure { error ->
            Log.w(TAG, "Local FCM token deletion failed during sign-out", error)
        }

        if (backendRevoked || localDeleted) return Result.success(Unit)

        val failure = localDeletion.exceptionOrNull()
            ?: tokenResolutionFailure
            ?: IllegalStateException("Push token revocation failed on both backend and local paths")
        return Result.failure(failure)
    }

    suspend fun deleteLocalTokenAfterAccountDeletion(): Result<Unit> {
        return runCatching {
            withTimeout(FCM_OPERATION_TIMEOUT_MS) {
                FirebaseMessaging.getInstance().deleteToken().await()
            }
            Unit
        }.onFailure { error ->
            // The server-side account subtree has already been removed, so this token has no
            // remaining Click & Save registration. This cleanup prevents local token reuse only.
            Log.w(TAG, "Local FCM token cleanup after account deletion failed", error)
        }
    }
}
