package com.example

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PushSignOutSourceGuardTest {
    @Test
    fun authSignOutRevokesPushBeforeFirebaseAuthSignOut() {
        val authRepository = File("src/main/java/com/example/data/repository/AuthRepository.kt").readText()
        val signOutSection = authRepository
            .substringAfter("suspend fun signOut()")
            .substringBefore("_userSession.value = UserSession()")

        val revokeIndex = signOutSection.indexOf("PushTokenLifecycle.revokeCurrentDeviceBeforeSignOut()")
        val firebaseSignOutIndex = signOutSection.indexOf("getFirebaseAuthSafe()?.signOut()")

        assertTrue("Current-device push revocation must be wired into sign-out", revokeIndex >= 0)
        assertTrue("Push revocation must happen while Firebase Auth is still valid", firebaseSignOutIndex > revokeIndex)
    }

    @Test
    fun pushLifecycleUsesBackendRevocationLocalDeletionAndTimeouts() {
        val lifecycle = File("src/main/java/com/example/PushTokenLifecycle.kt").readText()

        assertTrue(lifecycle.contains("getHttpsCallable(\"unregisterPushToken\")"))
        assertTrue(lifecycle.contains("messaging.deleteToken().await()"))
        assertTrue(lifecycle.contains("withTimeout(FCM_OPERATION_TIMEOUT_MS)"))
        assertTrue(lifecycle.contains("FirebaseAuth.getInstance().currentUser != null"))
    }

    @Test
    fun signOutStillCompletesWhenPushRevocationFails() {
        val authRepository = File("src/main/java/com/example/data/repository/AuthRepository.kt").readText()
        val signOutSection = authRepository
            .substringAfter("suspend fun signOut()")
            .substringBefore("_userSession.value = UserSession()")

        assertTrue(signOutSection.contains(".onFailure { Log.w(\"AuthRepository\", \"Push revocation incomplete during sign-out\", it) }"))
        assertTrue(signOutSection.contains("getFirebaseAuthSafe()?.signOut()"))
    }
}
