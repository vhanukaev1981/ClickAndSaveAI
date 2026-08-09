package com.example

import com.example.data.local.InvoiceItem
import com.example.data.repository.mergeObservedGmailInvoice
import com.example.data.repository.normalizeRemovedGmailSourceIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailInvoiceMergeTest {
    @Test
    fun parserUpgradeRefreshesObservedFieldsWithoutErasingLocalUserState() {
        val existing = InvoiceItem(
            id = 42,
            providerName = "ספק שזוהה מהודעת Gmail",
            category = "ביטוח",
            monthlyCost = 602.0,
            recommendedAlternative = "הצעה שנשמרה מקומית",
            alternativeMonthlyCost = 550.0,
            potentialMonthlySavings = 52.0,
            status = "בקשת חיסכון קודמת נמצאת בטיפול",
            isSwitchRequested = true,
            dateAdded = 123456789L,
            accountNumber = "local-only",
            billDate = "2026-07-01",
            sourceMessageId = "gmail-message-1",
            sourceType = "GMAIL_READONLY",
            verificationStatus = "UNVERIFIED_GMAIL_IMPORT"
        )
        val upgraded = InvoiceItem(
            providerName = "הראל",
            category = "ביטוח",
            monthlyCost = 599.0,
            billDate = "2026-08-01",
            sourceMessageId = "gmail-message-1",
            sourceType = "GMAIL_READONLY",
            verificationStatus = "UNVERIFIED_GMAIL_IMPORT"
        )

        val merged = mergeObservedGmailInvoice(existing, upgraded)

        assertEquals(42L, merged.id)
        assertEquals("הראל", merged.providerName)
        assertEquals(599.0, merged.monthlyCost, 0.0)
        assertEquals("2026-08-01", merged.billDate)
        assertEquals(123456789L, merged.dateAdded)
        assertEquals("הצעה שנשמרה מקומית", merged.recommendedAlternative)
        assertEquals(52.0, merged.potentialMonthlySavings, 0.0)
        assertEquals("בקשת חיסכון קודמת נמצאת בטיפול", merged.status)
        assertTrue(merged.isSwitchRequested)
        assertEquals("local-only", merged.accountNumber)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parserUpgradeCannotMergeDifferentGmailSources() {
        mergeObservedGmailInvoice(
            InvoiceItem(
                providerName = "A",
                category = "ביטוח",
                monthlyCost = 1.0,
                sourceMessageId = "source-a"
            ),
            InvoiceItem(
                providerName = "B",
                category = "ביטוח",
                monthlyCost = 2.0,
                sourceMessageId = "source-b"
            )
        )
    }

    @Test
    fun removedGmailSourcesAreNormalizedBeforeRoomCleanup() {
        assertEquals(
            listOf("message-1", "message-1:pdf:old"),
            normalizeRemovedGmailSourceIds(
                listOf(" message-1 ", "", "message-1", "message-1:pdf:old")
            )
        )
    }
}
