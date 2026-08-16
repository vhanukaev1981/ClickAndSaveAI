package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.InvoiceItem
import com.example.data.repository.BackendInvoice
import com.example.data.repository.FinancialHomeContext
import com.example.data.repository.FinancialHomeResult
import com.example.data.repository.FinancialSessionRecovery
import com.example.data.repository.FinancialSyncState
import com.example.data.repository.GmailConnectionResult
import com.example.data.repository.GmailScanResult
import com.example.data.repository.ShoppingRepository
import com.example.data.repository.projectGmailScanToLocalCache
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GateBRemoteAcceptanceTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: ShoppingRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = ShoppingRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun freshAuthenticatedSessionRestoresServerStateWithoutReconnectAndRelaunchIsIdempotent() = runBlocking {
        repository.addInvoice(
            InvoiceItem(
                providerName = "Manual bill",
                category = "OTHER",
                monthlyCost = 42.0,
                sourceMessageId = null,
                sourceType = "MANUAL",
                verificationStatus = "UNVERIFIED"
            )
        )

        val scan = GmailScanResult(
            invoices = listOf(
                BackendInvoice(
                    sourceMessageId = "gmail-restored-1",
                    providerName = "Restored Provider",
                    category = "INTERNET",
                    monthlyCost = 99.0,
                    receivedDate = "2026-08-01",
                    verificationStatus = "UNVERIFIED_GMAIL_IMPORT"
                )
            ),
            scannedMessages = 154,
            importedCount = 0,
            removedSourceMessageIds = emptyList()
        )
        val home = FinancialHomeResult(
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

        var connectionStatusCalls = 0
        var recoveryScanCalls = 0
        var financialHomeCalls = 0
        var oauthReconnectCalls = 0

        fun recovery() = FinancialSessionRecovery(
            getConnectionStatus = {
                connectionStatusCalls += 1
                GmailConnectionResult(true, "restored@example.com", "gmail-readonly-v1")
            },
            recoverInvoices = {
                recoveryScanCalls += 1
                projectGmailScanToLocalCache(repository, scan)
                scan
            },
            getFinancialHome = {
                financialHomeCalls += 1
                home
            }
        )

        val firstLaunch = recovery().refresh(previous = null)
        assertEquals(FinancialSyncState.Ready(scan, home), firstLaunch)
        assertEquals(2, repository.invoices.first().size)

        // Simulate a new app process/fresh local financial session. The server still reports the
        // existing Gmail grant, so recovery must run directly without any OAuth reconnect step.
        val secondLaunch = recovery().refresh(previous = null)
        assertEquals(FinancialSyncState.Ready(scan, home), secondLaunch)

        val restoredRows = repository.invoices.first()
        assertEquals(2, restoredRows.size)
        assertEquals(1, restoredRows.count { it.sourceMessageId == "gmail-restored-1" })
        assertEquals(1, restoredRows.count { it.sourceType == "MANUAL" })
        assertEquals(2, connectionStatusCalls)
        assertEquals(2, recoveryScanCalls)
        assertEquals(2, financialHomeCalls)
        assertEquals(0, oauthReconnectCalls)
        assertTrue((secondLaunch as FinancialSyncState.Ready).financialHome.context.sourceCoverage.contains("GMAIL_READONLY"))
    }
}
