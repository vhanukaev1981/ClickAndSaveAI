package com.example

import com.example.data.repository.BackendInvoice
import com.example.data.repository.FinancialHomeContext
import com.example.data.repository.FinancialHomeResult
import com.example.data.repository.FinancialSessionRecovery
import com.example.data.repository.FinancialSyncState
import com.example.data.repository.GmailConnectionResult
import com.example.data.repository.GmailScanResult
import com.example.data.repository.financialHomeOrNull
import com.example.data.repository.latestScanOrNull
import com.example.data.repository.observedRecurringMonthlySpendOrNull
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialRecoveryPipelineTest {
    private val scan = GmailScanResult(
        invoices = listOf(
            BackendInvoice(
                sourceMessageId = "gmail-1",
                providerName = "Provider",
                category = "INTERNET",
                monthlyCost = 99.0,
                receivedDate = "2026-08-01",
                verificationStatus = "UNVERIFIED_GMAIL_IMPORT"
            )
        ),
        scannedMessages = 12,
        importedCount = 0,
        removedSourceMessageIds = emptyList()
    )

    private val home = FinancialHomeResult(
        context = FinancialHomeContext(
            observedRecurringMonthlySpend = 99.0,
            recurringServiceCount = 1,
            isCompleteHouseholdSpend = false,
            sourceCoverage = listOf("GMAIL_READONLY"),
            recurringServices = emptyList(),
            categories = emptyList()
        ),
        insights = emptyList(),
        opportunities = emptyList()
    )

    @Test
    fun connectedRecoveryCallsServerInRequiredOrderAndReachesReady() = runBlocking {
        val order = mutableListOf<String>()
        val states = mutableListOf<FinancialSyncState>()
        val recovery = FinancialSessionRecovery(
            getConnectionStatus = {
                order += "connection"
                GmailConnectionResult(true, "user@example.com", "gmail-readonly-v1")
            },
            recoverInvoices = {
                order += "scan"
                scan
            },
            getFinancialHome = {
                order += "home"
                home
            }
        )

        val result = recovery.refresh(previous = FinancialSyncState.CheckingConnection) { states += it }

        assertEquals(listOf("connection", "scan", "home"), order)
        assertEquals(FinancialSyncState.CheckingConnection, states.first())
        assertTrue(states.contains(FinancialSyncState.Recovering))
        assertEquals(FinancialSyncState.Ready(scan, home), result)
        assertEquals(result, states.last())
    }

    @Test
    fun disconnectedAccountStopsBeforeScanAndFinancialHome() = runBlocking {
        val order = mutableListOf<String>()
        val recovery = FinancialSessionRecovery(
            getConnectionStatus = {
                order += "connection"
                GmailConnectionResult(false, "", "")
            },
            recoverInvoices = {
                order += "scan"
                scan
            },
            getFinancialHome = {
                order += "home"
                home
            }
        )

        val result = recovery.refresh(previous = null)

        assertEquals(listOf("connection"), order)
        assertEquals(FinancialSyncState.Disconnected, result)
    }

    @Test
    fun financialHomeFailureAfterBillRecoveryRetainsBillsButNotInventedTotals() = runBlocking {
        val recovery = FinancialSessionRecovery(
            getConnectionStatus = {
                GmailConnectionResult(true, "user@example.com", "gmail-readonly-v1")
            },
            recoverInvoices = { scan },
            getFinancialHome = { throw IllegalStateException("home unavailable") }
        )

        val result = recovery.refresh(previous = null)

        assertTrue(result is FinancialSyncState.Partial)
        assertEquals(scan, result.latestScanOrNull)
        assertNull(result.financialHomeOrNull)
        assertNull(result.observedRecurringMonthlySpendOrNull)
        assertTrue((result as FinancialSyncState.Partial).reason.isNotBlank())
    }

    @Test
    fun transientScanFailurePreservesLastVerifiedSnapshotWithoutReconnect() = runBlocking {
        var connectionCalls = 0
        var scanCalls = 0
        var homeCalls = 0
        val previous = FinancialSyncState.Ready(scan, home)
        val recovery = FinancialSessionRecovery(
            getConnectionStatus = {
                connectionCalls += 1
                GmailConnectionResult(true, "user@example.com", "gmail-readonly-v1")
            },
            recoverInvoices = {
                scanCalls += 1
                throw IllegalStateException("temporary scan failure")
            },
            getFinancialHome = {
                homeCalls += 1
                home
            }
        )

        val result = recovery.refresh(previous = previous)

        assertEquals(1, connectionCalls)
        assertEquals(1, scanCalls)
        assertEquals(0, homeCalls)
        assertTrue(result is FinancialSyncState.Partial)
        assertEquals(scan, result.latestScanOrNull)
        assertEquals(home, result.financialHomeOrNull)
        assertEquals(99.0, result.observedRecurringMonthlySpendOrNull!!, 0.0)
    }

    @Test
    fun firstRecoveryScanFailureIsFailedButNeverAuthRequiredOrKnownZero() = runBlocking {
        val recovery = FinancialSessionRecovery(
            getConnectionStatus = {
                GmailConnectionResult(true, "user@example.com", "gmail-readonly-v1")
            },
            recoverInvoices = { throw IllegalStateException("temporary scan failure") },
            getFinancialHome = { home }
        )

        val result = recovery.refresh(previous = null)

        assertTrue(result is FinancialSyncState.Failed)
        assertFalse((result as FinancialSyncState.Failed).isAuthRequired)
        assertNull(result.observedRecurringMonthlySpendOrNull)
    }

    @Test
    fun connectionFailureReportsStableSafeStageCodeWithoutRawExceptionText() = runBlocking {
        val sensitive = "token=secret-user-value"
        val recovery = FinancialSessionRecovery(
            getConnectionStatus = { throw IllegalStateException(sensitive) },
            recoverInvoices = { scan },
            getFinancialHome = { home }
        )

        val result = recovery.refresh(previous = null)

        assertTrue(result is FinancialSyncState.Failed)
        val reason = (result as FinancialSyncState.Failed).reason
        assertTrue(reason.contains("GMAIL_CONNECTION_STATUS_FAILED"))
        assertFalse(reason.contains(sensitive))
    }

    @Test
    fun scanFailureReportsStableSafeStageCodeWithoutRawExceptionText() = runBlocking {
        val sensitive = "credential=user-secret"
        val recovery = FinancialSessionRecovery(
            getConnectionStatus = { GmailConnectionResult(true, "user@example.com", "gmail-readonly-v1") },
            recoverInvoices = { throw IllegalStateException(sensitive) },
            getFinancialHome = { home }
        )

        val result = recovery.refresh(previous = null)

        assertTrue(result is FinancialSyncState.Failed)
        val reason = (result as FinancialSyncState.Failed).reason
        assertTrue(reason.contains("GMAIL_SCAN_FAILED"))
        assertFalse(reason.contains(sensitive))
    }
}
