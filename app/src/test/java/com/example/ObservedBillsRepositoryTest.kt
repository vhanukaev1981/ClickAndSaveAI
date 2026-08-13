package com.example

import com.example.data.repository.BackendInvoice
import com.example.data.repository.computeStaleObservedSourceIds
import com.example.data.repository.toObservedInvoiceItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservedBillsRepositoryTest {
    @Test
    fun completeSnapshotRemovesOnlyMissingObservedGmailSources() {
        val stale = computeStaleObservedSourceIds(
            localSourceIds = listOf("a", " b ", "c", "c", ""),
            authoritativeSourceIds = listOf("b", "c"),
            sourceSetComplete = true
        )

        assertEquals(listOf("a"), stale)
    }

    @Test
    fun incompleteSnapshotNeverAuthorizesLocalDeletion() {
        val stale = computeStaleObservedSourceIds(
            localSourceIds = listOf("a", "b", "c"),
            authoritativeSourceIds = listOf("b"),
            sourceSetComplete = false
        )

        assertTrue(stale.isEmpty())
    }

    @Test
    fun backendObservedBillMapsToGmailOnlyLocalRowWithoutSavingsClaims() {
        val local = BackendInvoice(
            sourceMessageId = "message-123:pdf:0",
            providerName = "הראל",
            category = "insurance",
            monthlyCost = 602.0,
            receivedDate = "2026-08-08",
            verificationStatus = "UNVERIFIED_GMAIL_IMPORT"
        ).toObservedInvoiceItem()

        assertEquals("message-123:pdf:0", local.sourceMessageId)
        assertEquals("GMAIL_READONLY", local.sourceType)
        assertEquals("הראל", local.providerName)
        assertEquals("insurance", local.category)
        assertEquals(602.0, local.monthlyCost, 0.0)
        assertEquals("2026-08-08", local.billDate)
        assertEquals("UNVERIFIED_GMAIL_IMPORT", local.verificationStatus)
        assertEquals(0.0, local.potentialMonthlySavings, 0.0)
        assertEquals(0.0, local.alternativeMonthlyCost, 0.0)
        assertFalse(local.isSwitchRequested)
    }

    @Test
    fun authoritativeSourceNormalizationDoesNotDeleteEquivalentTrimmedIds() {
        val stale = computeStaleObservedSourceIds(
            localSourceIds = listOf(" message-a ", "message-b"),
            authoritativeSourceIds = listOf("message-a", "message-b", "message-b"),
            sourceSetComplete = true
        )

        assertTrue(stale.isEmpty())
    }
}
