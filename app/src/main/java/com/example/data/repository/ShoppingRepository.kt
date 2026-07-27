package com.example.data.repository

import com.example.data.local.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

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
    suspend fun updateInvoice(invoice: InvoiceItem) = db.invoiceDao().updateInvoice(invoice)
    suspend fun deleteInvoice(id: Long) = db.invoiceDao().deleteInvoice(id)

    suspend fun requestProviderSwitch(invoice: InvoiceItem) {
        db.invoiceDao().updateInvoice(
            invoice.copy(
                isSwitchRequested = true,
                status = "מעבר בטיפול - בקשה נשלחה"
            )
        )
        // Record saved amount in savings DAO
        db.savingsDao().insertSavings(
            SavingsRecord(
                title = "מעבר ספק: ${invoice.category} - ${invoice.recommendedAlternative}",
                storeName = invoice.providerName,
                amountSaved = invoice.potentialAnnualSavings,
                category = invoice.category,
                note = "בקשת מעבר בקליק נשלחה בהצלחה"
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

    suspend fun seedSampleDataIfNeeded() {
        if (db.invoiceDao().getCount() == 0) {
            val sampleInvoices = listOf(
                InvoiceItem(
                    providerName = "חברת החשמל (IEC)",
                    category = "חשמל",
                    monthlyCost = 520.00,
                    recommendedAlternative = "אלקטרה פאוור - 7% הנחה קבועה בחשבון החשמל",
                    alternativeMonthlyCost = 483.60,
                    potentialMonthlySavings = 36.40,
                    status = "פוענח - הצעה מוכנה",
                    isSwitchRequested = false,
                    accountNumber = "7894201",
                    billDate = "07/2026"
                ),
                InvoiceItem(
                    providerName = "סלקום סלולר",
                    category = "סלולר",
                    monthlyCost = 149.00,
                    recommendedAlternative = "פרטנר 5G - חבילה זוגית 200GB ב-89 ₪ לחודש",
                    alternativeMonthlyCost = 89.00,
                    potentialMonthlySavings = 60.00,
                    status = "פוענח - הצעה מוכנה",
                    isSwitchRequested = false,
                    accountNumber = "9928130",
                    billDate = "07/2026"
                ),
                InvoiceItem(
                    providerName = "בזק אינטרנט סיבים",
                    category = "אינטרנט",
                    monthlyCost = 120.00,
                    recommendedAlternative = "סלקום פייבר 1000Mb - כולל נתב ב-89 ₪ לחודש",
                    alternativeMonthlyCost = 89.00,
                    potentialMonthlySavings = 31.00,
                    status = "פוענח - הצעה מוכנה",
                    isSwitchRequested = false,
                    accountNumber = "4310294",
                    billDate = "06/2026"
                ),
                InvoiceItem(
                    providerName = "הראל ביטוח בריאות",
                    category = "ביטוח",
                    monthlyCost = 340.00,
                    recommendedAlternative = "מגדל ביטוח - ביטול כפילויות וכיסוי מורחב ב-255 ₪",
                    alternativeMonthlyCost = 255.00,
                    potentialMonthlySavings = 85.00,
                    status = "פוענח - הצעה מוכנה",
                    isSwitchRequested = false,
                    accountNumber = "POL-88320",
                    billDate = "07/2026"
                ),
                InvoiceItem(
                    providerName = "yes / HOT טלוויזיה",
                    category = "טלוויזיה",
                    monthlyCost = 199.00,
                    recommendedAlternative = "FreeTV / stingTV - חבילת ערוצים וספורט ב-59 ₪",
                    alternativeMonthlyCost = 59.00,
                    potentialMonthlySavings = 140.00,
                    status = "פוענח - הצעה מוכנה",
                    isSwitchRequested = false,
                    accountNumber = "TV-10928",
                    billDate = "07/2026"
                )
            )
            db.invoiceDao().insertInvoices(sampleInvoices)
        }

        if (db.savingsDao().getCount() == 0) {
            val sampleSavings = listOf(
                SavingsRecord(
                    title = "הוזלת חשבון חשמל במעבר לאלקטרה פאוור",
                    storeName = "חשמל",
                    amountSaved = 436.80,
                    category = "חשמל",
                    note = "מעבר מוצלח דרך Click & Save AI"
                ),
                SavingsRecord(
                    title = "איחוד חבילת סלולר משפחתית 5G",
                    storeName = "סלולר",
                    amountSaved = 720.00,
                    category = "סלולר",
                    note = "מעבר לפרטנר 5G ב-₪29 לקו"
                ),
                SavingsRecord(
                    title = "ביטול כפילויות ביטוח בריאות",
                    storeName = "ביטוח",
                    amountSaved = 1020.00,
                    category = "ביטוח",
                    note = "הוזלה בעקבות סריקת הר הביטוח"
                )
            )
            sampleSavings.forEach { db.savingsDao().insertSavings(it) }
        }
    }
}
