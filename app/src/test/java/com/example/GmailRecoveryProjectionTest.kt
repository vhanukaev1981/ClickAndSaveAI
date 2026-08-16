package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.InvoiceItem
import com.example.data.repository.BackendInvoice
import com.example.data.repository.GmailScanResult
import com.example.data.repository.ShoppingRepository
import com.example.data.repository.projectGmailScanToLocalCache
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GmailRecoveryProjectionTest {
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
    fun freshCacheRecoversServerInvoicesAndSecondRecoveryDoesNotDuplicate() = runBlocking {
        val scan = GmailScanResult(
            invoices = listOf(
                invoice("gmail-1", "Provider A", 90.0),
                invoice("gmail-2", "Provider B", 120.0),
                invoice("gmail-3", "Provider C", 150.0)
            ),
            scannedMessages = 3,
            importedCount = 0,
            removedSourceMessageIds = emptyList()
        )

        projectGmailScanToLocalCache(repository, scan)
        assertEquals(3, repository.invoices.first().size)

        projectGmailScanToLocalCache(repository, scan)
        assertEquals(3, repository.invoices.first().size)
    }

    @Test
    fun repeatedRecoveryUpdatesSameSourceInPlace() = runBlocking {
        projectGmailScanToLocalCache(
            repository,
            GmailScanResult(
                invoices = listOf(invoice("gmail-1", "Provider A", 90.0)),
                scannedMessages = 1,
                importedCount = 0,
                removedSourceMessageIds = emptyList()
            )
        )

        projectGmailScanToLocalCache(
            repository,
            GmailScanResult(
                invoices = listOf(invoice("gmail-1", "Provider A", 109.0)),
                scannedMessages = 1,
                importedCount = 0,
                removedSourceMessageIds = emptyList()
            )
        )

        val invoices = repository.invoices.first()
        assertEquals(1, invoices.size)
        assertEquals(109.0, invoices.single().monthlyCost, 0.0)
    }

    @Test
    fun explicitServerRemovalDeletesOnlyObservedGmailRowAndPreservesManualBill() = runBlocking {
        repository.addInvoice(
            InvoiceItem(
                providerName = "Manual bill",
                category = "OTHER",
                monthlyCost = 50.0,
                sourceMessageId = null,
                sourceType = "MANUAL",
                verificationStatus = "UNVERIFIED"
            )
        )
        projectGmailScanToLocalCache(
            repository,
            GmailScanResult(
                invoices = listOf(invoice("gmail-stale", "Provider A", 90.0)),
                scannedMessages = 1,
                importedCount = 0,
                removedSourceMessageIds = emptyList()
            )
        )

        projectGmailScanToLocalCache(
            repository,
            GmailScanResult(
                invoices = emptyList(),
                scannedMessages = 1,
                importedCount = 0,
                removedSourceMessageIds = listOf("gmail-stale")
            )
        )

        val invoices = repository.invoices.first()
        assertEquals(1, invoices.size)
        assertEquals("MANUAL", invoices.single().sourceType)
        assertNull(invoices.single().sourceMessageId)
    }

    private fun invoice(sourceId: String, provider: String, monthlyCost: Double) = BackendInvoice(
        sourceMessageId = sourceId,
        providerName = provider,
        category = "INTERNET",
        monthlyCost = monthlyCost,
        receivedDate = "2026-08-01",
        verificationStatus = "UNVERIFIED_GMAIL_IMPORT"
    )
}
