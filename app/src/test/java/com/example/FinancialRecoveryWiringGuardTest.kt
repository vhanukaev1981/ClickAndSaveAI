package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialRecoveryWiringGuardTest {
    @Test
    fun viewModelOwnsOneFinancialRecoveryPipelineForStartupManualAndPostOauth() {
        val viewModel = File("src/main/java/com/example/ui/MainViewModel.kt").readText()

        assertTrue(viewModel.contains("FinancialSessionRecovery"))
        assertTrue(viewModel.contains("FinancialSyncState"))
        assertTrue(viewModel.contains("FinancialRefreshReason"))
        assertTrue(viewModel.contains("private val _financialSyncState"))
        assertTrue(viewModel.contains("val financialSyncState"))
        assertTrue(viewModel.contains("fun refreshFinancialSession("))
        assertTrue(viewModel.contains("gmailRepository.refreshConnectionStatus()"))
        assertTrue(viewModel.contains("gmailRepository.scanInvoices()"))
        assertTrue(viewModel.contains("backendRepository.getFinancialHome()"))

        val authorizationBlock = viewModel
            .substringAfter("fun completeGmailAuthorization(serverAuthCode: String)")
            .substringBefore("fun reportGmailAuthorizationError")
        assertTrue(authorizationBlock.contains("FinancialRefreshReason.GMAIL_CONNECTED"))
        assertFalse(authorizationBlock.contains("gmailRepository.scanInvoices()"))

        val manualBlock = viewModel
            .substringAfter("fun triggerGmailSync()")
            .substringBefore("fun disconnectGmail()")
        assertTrue(manualBlock.contains("FinancialRefreshReason.MANUAL_SCAN"))
        assertFalse(manualBlock.contains("gmailRepository.scanInvoices()"))
    }

    @Test
    fun authenticatedStartupUsesViewModelRecoveryInsteadOfConnectionOnlyRefresh() {
        val activity = File("src/main/java/com/example/MainActivity.kt").readText()
        val startupBlock = activity
            .substringAfter("LaunchedEffect(session.uid)")
            .substringBefore("Scaffold(")

        assertTrue(startupBlock.contains("FinancialRefreshReason.STARTUP"))
        assertTrue(startupBlock.contains("viewModel.refreshFinancialSession"))
        assertFalse(startupBlock.contains("refreshConnectionStatusAndUpgradeIfNeeded"))
        assertFalse(startupBlock.contains("viewModel.gmailRepository"))
    }

    @Test
    fun signOutKeepsServerGmailConnectionAndReturnsFinancialSessionToUnauthenticatedState() {
        val viewModel = File("src/main/java/com/example/ui/MainViewModel.kt").readText()
        val signOutBlock = viewModel
            .substringAfter("fun signOut()")
            .substringBefore("val selectedTab")

        assertTrue(signOutBlock.contains("FinancialSyncState.Unauthenticated"))
        assertFalse(signOutBlock.contains("gmailRepository.disconnectGmail()"))
    }
}
