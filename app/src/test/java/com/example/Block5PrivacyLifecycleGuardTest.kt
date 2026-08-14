package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Block5PrivacyLifecycleGuardTest {
    @Test
    fun signOutPurgesAccountDerivedInvoicesBeforeFirebaseSignOut() {
        val source = File("src/main/java/com/example/data/repository/AuthRepository.kt").readText()
        val section = source.substringAfter("suspend fun signOut()").substringBefore("_userSession.value")
        val purgeHelper = source
            .substringAfter("suspend fun purgeImportedFinancialDataLocally()")
            .substringBefore("suspend fun signOut()")

        val pushIndex = section.indexOf("revokeCurrentDeviceBeforeSignOut")
        val purgeIndex = section.indexOf("purgeImportedFinancialDataLocally()")
        val authIndex = section.indexOf("getFirebaseAuthSafe()?.signOut()")

        assertTrue(pushIndex >= 0)
        assertTrue(purgeIndex > pushIndex)
        assertTrue(authIndex > purgeIndex)
        assertTrue(purgeHelper.contains("deleteAllInvoices()"))
        assertFalse(section.substring(0, authIndex).contains(".onFailure"))
    }

    @Test
    fun privacyRepositoryExposesThreeDistinctPrivacyOperations() {
        val source = File("src/main/java/com/example/data/repository/PrivacyRepository.kt").readText()
        assertTrue(source.contains("getHttpsCallable(\"disconnectGmail\")"))
        assertTrue(source.contains("getHttpsCallable(\"deleteImportedFinancialData\")"))
        assertTrue(source.contains("getHttpsCallable(\"deleteAccount\")"))
        assertTrue(source.contains("DELETE_IMPORTED_FINANCIAL_DATA"))
        assertTrue(source.contains("DELETE_ACCOUNT"))
    }

    @Test
    fun gmailRepositoryUsesOneAuthoritativeDisconnectCallable() {
        val source = File("src/main/java/com/example/data/repository/GmailRepository.kt").readText()
        val section = source.substringAfter("suspend fun disconnectGmail()").substringBeforeLast("}\n")
        assertTrue(section.contains("backendRepository.disconnectGmailAuthoritatively()"))
        assertFalse(section.contains("stopGmailWatch"))
    }

    @Test
    fun profileKeepsDisconnectDeleteDataDeleteAccountSeparate() {
        val source = File("src/main/java/com/example/ui/screens/ProfileScreen.kt").readText()
        assertTrue(source.contains("onDisconnectGmail"))
        assertTrue(source.contains("onDeleteImportedData"))
        assertTrue(source.contains("onDeleteAccount"))
        assertTrue(source.contains("נתק Gmail"))
        assertTrue(source.contains("מחק נתונים מיובאים"))
        assertTrue(source.contains("מחק חשבון"))
    }
}
