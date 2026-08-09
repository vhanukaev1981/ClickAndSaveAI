package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.CouponItem
import com.example.data.local.InvoiceItem
import com.example.data.local.SavingsRecord
import com.example.data.local.WatchlistItem
import kotlinx.coroutines.flow.Flow

internal fun mergeObservedGmailInvoice(existing: InvoiceItem, incoming: InvoiceItem): InvoiceItem {
    val existingSourceId = existing.sourceMessageId?.trim().orEmpty()
    val incomingSourceId = incoming.sourceMessageId?.trim().orEmpty()
    require(existingSourceId.isNotEmpty() && existingSourceId == incomingSourceId) {
        "Gmail invoice merge requires the same non-empty sourceMessageId"
    }

    return existing.copy(
        providerName = incoming.providerName,
        category = incoming.category,
        monthlyCost = incoming.monthlyCost,
        billDate = incoming.billDate,
        sourceType = incoming.sourceType,
        verificationStatus = incoming.verificationStatus
    )
}

internal fun normalizeRemovedGmailSourceIds(sourceMessageIds: List<String>): List<String> =
    sourceMessageIds
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()

class ShoppingRepository(private val db: AppDatabase) {
    val watchlistItems: Flow<List<WatchlistItem>> = db.watchlistDao().getAllWatchlistItems()
    val coupons: Flow<List<CouponItem>> = db.couponDao().getAllCoupons()
    val savingsRecords: Flow<List<SavingsRecord>> = db.savingsDao().getAllSavings()
    val totalSavingsAmount: Flow<Double?> = db.savingsDao().getTotalSavingsAmount()
    val invoices: Flow<List<InvoiceItem>> = db.invoiceDao().getAllInvoices()
    val totalMonthlySavingsPotential: Flow<Double?> = db.invoiceDao().getTotalMonthlySavingsPotential()
    val totalMonthlyCost: Flow<Double?> = db.invoiceDao().getTotalMonthlyCost()

    suspend fun addWatchlistItem(item: WatchlistItem) = db.watchlistDao().insertItem(item)
    suspend fun removeWatchlistItem(id: Long) = db.watchlistDao().deleteItemById(id)
    suspend fun updateWatchlistItem(item: WatchlistItem) = db.watchlistDao().updateItem(item)

    suspend fun addInvoice(invoice: InvoiceItem) = db.invoiceDao().insertInvoice(invoice)

    suspend fun upsertObservedGmailInvoice(invoice: InvoiceItem) {
        val sourceMessageId = invoice.sourceMessageId?.trim().orEmpty()
        if (sourceMessageId.isEmpty()) {
            db.invoiceDao().insertInvoice(invoice)
            return
        }

        val existing = db.invoiceDao().findBySourceMessageId(sourceMessageId)
        if (existing != null) {
            db.invoiceDao().updateInvoice(mergeObservedGmailInvoice(existing, invoice))
            return
        }

        val insertedId = db.invoiceDao().insertInvoice(invoice)
        if (insertedId == -1L) {
            // Defensive race recovery: another coroutine may have inserted the same Gmail
            // source between the lookup and INSERT IGNORE.
            db.invoiceDao().findBySourceMessageId(sourceMessageId)?.let { concurrent ->
                db.invoiceDao().updateInvoice(mergeObservedGmailInvoice(concurrent, invoice))
            }
        }
    }

    suspend fun getObservedGmailSourceIds(): List<String> =
        normalizeRemovedGmailSourceIds(db.invoiceDao().getObservedGmailSourceIds())

    suspend fun deleteObservedGmailInvoicesBySourceIds(sourceMessageIds: List<String>) {
        val normalized = normalizeRemovedGmailSourceIds(sourceMessageIds)
        if (normalized.isEmpty()) return
        db.invoiceDao().deleteObservedGmailInvoicesBySourceIds(normalized)
    }

    suspend fun updateInvoice(invoice: InvoiceItem) = db.invoiceDao().updateInvoice(invoice)
    suspend fun deleteInvoice(id: Long) = db.invoiceDao().deleteInvoice(id)

    /**
     * Records local interest only. No provider or external system is contacted.
     */
    suspend fun requestProviderSwitch(invoice: InvoiceItem) {
        db.invoiceDao().updateInvoice(
            invoice.copy(
                isSwitchRequested = true,
                status = "התעניינות נשמרה מקומית - לא נשלחה לספק"
            )
        )
    }

    suspend fun toggleCouponFavorite(coupon: CouponItem) {
        db.couponDao().updateCoupon(coupon.copy(isFavorite = !coupon.isFavorite))
    }

    suspend fun incrementCouponCopy(coupon: CouponItem) {
        db.couponDao().updateCoupon(coupon.copy(copyCount = coupon.copyCount + 1))
    }

    suspend fun addSavings(record: SavingsRecord) = db.savingsDao().insertSavings(record)

    /**
     * Production builds must start empty. Demo data belongs in previews, fixtures or tests.
     */
    suspend fun seedSampleDataIfNeeded() = Unit
}
