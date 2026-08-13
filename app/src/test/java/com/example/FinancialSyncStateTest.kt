package com.example

import com.example.data.repository.BackendInvoice
import com.example.data.repository.FinancialHomeContext
import com.example.data.repository.FinancialHomeResult
import com.example.data.repository.FinancialSyncState
import com.example.data.repository.GmailScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FinancialSyncStateTest {
    private val scan = GmailScanResult(
        invoices = listOf(
            BackendInvoice(
                sourceMessageId = "gmail:message-1",
                providerName = "Provider",
                category = "INTERNET",
                monthlyCost = 99.0,
                receivedDate = "2026-08-01",
                verificationStatus = "OBSERVED"
            )
        ),
        scannedMessages = 10,
        importedCount = 1,
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
    fun recoveringDoesNotExposeFinancialValuesAsKnownZero() {
        val state: FinancialSyncState = FinancialSyncState.Recovering

        assertNull(state.financialHomeOrNull)
        assertNull(state.observedRecurringMonthlySpendOrNull)
        assertNull(state.recurringServiceCountOrNull)
    }

    @Test
    fun partialWithoutVerifiedFinancialHomeKeepsFinancialValuesUnknown() {
        val state: FinancialSyncState = FinancialSyncState.Partial(
            latestScan = scan,
            financialHome = null,
            reason = "Financial Home temporarily unavailable"
        )

        assertEquals(scan, state.latestScanOrNull)
        assertNull(state.financialHomeOrNull)
        assertNull(state.observedRecurringMonthlySpendOrNull)
        assertNull(state.recurringServiceCountOrNull)
    }

    @Test
    fun readyExposesOnlyTheAuthoritativeFinancialHomeValues() {
        val state: FinancialSyncState = FinancialSyncState.Ready(
            latestScan = scan,
            financialHome = home
        )

        assertEquals(scan, state.latestScanOrNull)
        assertEquals(home, state.financialHomeOrNull)
        assertEquals(99.0, state.observedRecurringMonthlySpendOrNull!!, 0.0)
        assertEquals(1, state.recurringServiceCountOrNull)
    }
}
