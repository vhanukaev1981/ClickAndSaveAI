package com.example

import android.content.Context
import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.local.InvoiceItem
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class InvoiceDaoIntegrityTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun gmailUpsertRefreshesSourceFieldsAndPreservesEnrichment() = runTest {
        val dao = database.invoiceDao()
        val original = InvoiceItem(
            providerName = "ספק ישן",
            category = "תקשורת",
            monthlyCost = 100.0,
            recommendedAlternative = "מסלול מאומת",
            alternativeMonthlyCost = 75.0,
            potentialMonthlySavings = 25.0,
            status = "חיסכון אומת",
            isSwitchRequested = true,
            accountNumber = "LOCAL-ACCOUNT",
            billDate = "2026-07-01",
            sourceMessageId = "gmail-message-1",
            sourceType = "GMAIL_READONLY",
            verificationStatus = "VERIFIED"
        )
        dao.insertInvoice(original)

        dao.upsertGmailInvoice(
            InvoiceItem(
                providerName = "ספק מתוקן",
                category = "אינטרנט",
                monthlyCost = 89.90,
                billDate = "2026-08-01",
                sourceMessageId = "gmail-message-1",
                sourceType = "GMAIL_READONLY",
                verificationStatus = "UNVERIFIED_GMAIL_IMPORT"
            )
        )

        val updated = dao.findBySourceMessageId("gmail-message-1")
        assertNotNull(updated)
        assertEquals("ספק מתוקן", updated?.providerName)
        assertEquals("אינטרנט", updated?.category)
        assertEquals(89.90, updated?.monthlyCost ?: 0.0, 0.0)
        assertEquals("2026-08-01", updated?.billDate)
        assertEquals("מסלול מאומת", updated?.recommendedAlternative)
        assertEquals(75.0, updated?.alternativeMonthlyCost ?: 0.0, 0.0)
        assertEquals(25.0, updated?.potentialMonthlySavings ?: 0.0, 0.0)
        assertEquals("חיסכון אומת", updated?.status)
        assertEquals(true, updated?.isSwitchRequested)
        assertEquals("LOCAL-ACCOUNT", updated?.accountNumber)
    }

    @Test
    fun deletingGmailInvoicesDoesNotDeleteManualInvoices() = runTest {
        val dao = database.invoiceDao()
        dao.insertInvoice(
            InvoiceItem(
                providerName = "ידני",
                category = "חשמל",
                monthlyCost = 250.0,
                sourceType = "MANUAL"
            )
        )
        dao.insertInvoice(
            InvoiceItem(
                providerName = "Gmail",
                category = "אינטרנט",
                monthlyCost = 120.0,
                sourceMessageId = "gmail-message-2",
                sourceType = "GMAIL_READONLY"
            )
        )

        dao.deleteGmailInvoices()

        val remaining = dao.getAllInvoices()
        kotlinx.coroutines.flow.first(remaining).also { invoices ->
            assertEquals(1, invoices.size)
            assertEquals("ידני", invoices.single().providerName)
            assertEquals("MANUAL", invoices.single().sourceType)
        }
    }
}
