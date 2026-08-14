package com.example

import java.io.File
import org.junit.Assert.assertFalse
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
    fun signOutIsFailClosedWhenPushRevocationFails() {
        val authRepository = File("src/main/java/com/example/data/repository/AuthRepository.kt").readText()
        val signOutSection = authRepository
            .substringAfter("suspend fun signOut()")
            .substringBefore("_userSession.value = UserSession()")

        val revokeIndex = signOutSection.indexOf("PushTokenLifecycle.revokeCurrentDeviceBeforeSignOut()")
        val hardGateIndex = signOutSection.indexOf("getOrThrow()")
        val firebaseSignOutIndex = signOutSection.indexOf("getFirebaseAuthSafe()?.signOut()")

        assertTrue("Revocation result must be consumed as a hard gate", hardGateIndex > revokeIndex)
        assertTrue("Firebase Auth sign-out must occur only after revocation succeeds", firebaseSignOutIndex > hardGateIndex)
        assertFalse(signOutSection.contains("Push revocation incomplete during sign-out"))
    }

    @Test
    fun gmailDisconnectAloneDoesNotRevokeAccountPushRegistration() {
        val gmailRepository = File("src/main/java/com/example/data/repository/GmailRepository.kt").readText()
        val disconnectSection = gmailRepository
            .substringAfter("suspend fun disconnectGmail()")
            .substringBeforeLast("}\n")

        assertFalse(disconnectSection.contains("PushTokenLifecycle"))
        assertFalse(disconnectSection.contains("unregisterPushToken"))
        assertFalse(disconnectSection.contains("deleteToken()"))
    }

    @Test
    fun pushRegistrationRequiresAuthenticatedFirebaseUser() {
        val service = File("src/main/java/com/example/ClickAndSaveMessagingService.kt").readText()
        val registrationSection = service
            .substringAfter("object PushRegistration")
            .substringBefore("class ClickAndSaveMessagingService")

        assertTrue(registrationSection.contains("if (FirebaseAuth.getInstance().currentUser == null) return"))
        assertTrue(registrationSection.contains("if (FirebaseAuth.getInstance().currentUser == null || token.isBlank()) return"))
    }
}
