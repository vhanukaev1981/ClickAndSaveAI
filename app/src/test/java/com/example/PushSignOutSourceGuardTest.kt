package com.example

// Block 3 P0 contract: sign-out attempts push revocation first but never strands the user signed in.
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
    fun signOutFailsClosedOnlyWhenBothPushRevocationPathsFail() {
        val authRepository = File("src/main/java/com/example/data/repository/AuthRepository.kt").readText()
        val signOutSection = authRepository
            .substringAfter("suspend fun signOut()")
            .substringBefore("_userSession.value = UserSession()")

        val revokeIndex = signOutSection.indexOf("PushTokenLifecycle.revokeCurrentDeviceBeforeSignOut()")
        val hardGateIndex = signOutSection.indexOf("getOrThrow()")
        val firebaseSignOutIndex = signOutSection.indexOf("getFirebaseAuthSafe()?.signOut()")

        assertTrue("Push revocation must still be attempted before auth sign-out", revokeIndex >= 0)
        assertTrue("Only a double-path revocation failure may block auth sign-out", hardGateIndex > revokeIndex)
        assertTrue("Firebase Auth sign-out must happen after the guarded revocation result", firebaseSignOutIndex > hardGateIndex)
        assertTrue(signOutSection.contains("beginSignOutRegistrationSuppression"))
        assertTrue(authRepository.contains("endSignOutRegistrationSuppression"))
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
    fun pushRegistrationRequiresAuthenticatedFirebaseUserAndNoSignOutSuppression() {
        val service = File("src/main/java/com/example/ClickAndSaveMessagingService.kt").readText()
        val registrationSection = service
            .substringAfter("object PushRegistration")
            .substringBefore("class ClickAndSaveMessagingService")

        assertTrue(registrationSection.contains("PushTokenLifecycle.isRegistrationSuppressed()"))
        assertTrue(registrationSection.contains("if (FirebaseAuth.getInstance().currentUser == null) return"))
        assertTrue(registrationSection.contains("if (FirebaseAuth.getInstance().currentUser == null || token.isBlank()) return"))
    }
}
