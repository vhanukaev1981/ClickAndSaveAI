package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservedBillsResumeSourceGuardTest {
    @Test
    fun resumeUsesOnlyThrottledAuthoritativeSnapshot() {
        val mainActivity = File("src/main/java/com/example/MainActivity.kt").readText()
        val onResume = mainActivity
            .substringAfter("override fun onResume()")
            .substringBefore("override fun onNewIntent")

        assertTrue(onResume.contains("shouldRefreshObservedBillsOnResume("))
        assertTrue(onResume.contains("refreshObservedBillsSnapshotIfConnected()"))
        assertFalse(onResume.contains("scanInvoices("))
        assertFalse(onResume.contains("refreshConnectionStatusAndUpgradeIfNeeded("))
    }

    @Test
    fun repositorySnapshotRefreshDoesNotChangeConnectionStateOnFailure() {
        val repository = File("src/main/java/com/example/data/repository/GmailRepository.kt").readText()
        val snapshotMethod = repository
            .substringAfter("suspend fun refreshObservedBillsSnapshotIfConnected()")
            .substringBefore("suspend fun refreshConnectionStatusAndUpgradeIfNeeded()")

        assertTrue(snapshotMethod.contains("observedBillsRepository.refreshObservedBills()"))
        assertTrue(snapshotMethod.contains("if (!_isConnected.value) return Result.success(null)"))
        assertFalse(snapshotMethod.contains("_isConnected.value = false"))
        assertFalse(snapshotMethod.contains("scanInvoices("))
    }
}
