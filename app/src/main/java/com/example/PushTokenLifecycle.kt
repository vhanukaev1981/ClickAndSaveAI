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

    suspend fun revokeCurrentDeviceBeforeSignOut(): Result<Unit> {
        val messaging = FirebaseMessaging.getInstance()
        val authenticated = FirebaseAuth.getInstance().currentUser != null

        var firstFailure: Throwable? = null
        val token = runCatching {
            withTimeout(FCM_OPERATION_TIMEOUT_MS) { messaging.token.await().trim() }
        }.onFailure { error ->
            firstFailure = error
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
            }.onFailure { error ->
                if (firstFailure == null) firstFailure = error
                Log.w(TAG, "Backend FCM token revocation failed before sign-out", error)
            }
        }

        // Always try to delete the local token even when the authenticated backend revocation
        // fails. Firebase will mint a fresh token on a later authenticated session, while the
        // server delivery path already deletes registrations that FCM reports as invalid.
        runCatching {
            withTimeout(FCM_OPERATION_TIMEOUT_MS) { messaging.deleteToken().await() }
        }.onFailure { error ->
            if (firstFailure == null) firstFailure = error
            Log.w(TAG, "Local FCM token deletion failed during sign-out", error)
        }

        val failure = firstFailure
        return if (failure != null) Result.failure(failure) else Result.success(Unit)
    }
}
