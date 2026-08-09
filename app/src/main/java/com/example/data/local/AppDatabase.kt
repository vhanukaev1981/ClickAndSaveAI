package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist_items ORDER BY id DESC")
    fun getAllWatchlistItems(): Flow<List<WatchlistItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: WatchlistItem): Long

    @Query("DELETE FROM watchlist_items WHERE id = :id")
    suspend fun deleteItemById(id: Long)

    @Update
    suspend fun updateItem(item: WatchlistItem)

    @Query("SELECT COUNT(*) FROM watchlist_items")
    suspend fun getCount(): Int
}

@Dao
interface CouponDao {
    @Query("SELECT * FROM coupon_items ORDER BY isFavorite DESC, id ASC")
    fun getAllCoupons(): Flow<List<CouponItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoupons(coupons: List<CouponItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoupon(coupon: CouponItem): Long

    @Update
    suspend fun updateCoupon(coupon: CouponItem)

    @Query("SELECT COUNT(*) FROM coupon_items")
    suspend fun getCount(): Int
}

@Dao
interface SavingsDao {
    @Query("SELECT * FROM savings_records ORDER BY dateTimestamp DESC")
    fun getAllSavings(): Flow<List<SavingsRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavings(record: SavingsRecord): Long

    @Query("SELECT SUM(amountSaved) FROM savings_records")
    fun getTotalSavingsAmount(): Flow<Double?>

    @Query("SELECT COUNT(*) FROM savings_records")
    suspend fun getCount(): Int
}

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoice_items ORDER BY dateAdded DESC")
    fun getAllInvoices(): Flow<List<InvoiceItem>>

    @Query("SELECT * FROM invoice_items WHERE sourceMessageId = :sourceMessageId LIMIT 1")
    suspend fun findBySourceMessageId(sourceMessageId: String): InvoiceItem?

    @Query("SELECT sourceMessageId FROM invoice_items WHERE sourceType = 'GMAIL_READONLY' AND sourceMessageId IS NOT NULL")
    suspend fun getObservedGmailSourceIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInvoice(invoice: InvoiceItem): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInvoices(invoices: List<InvoiceItem>): List<Long>

    @Update
    suspend fun updateInvoice(invoice: InvoiceItem)

    @Query("DELETE FROM invoice_items WHERE id = :id")
    suspend fun deleteInvoice(id: Long)

    @Query("DELETE FROM invoice_items WHERE sourceType = 'GMAIL_READONLY' AND sourceMessageId IN (:sourceMessageIds)")
    suspend fun deleteObservedGmailInvoicesBySourceIds(sourceMessageIds: List<String>)

    @Query("DELETE FROM invoice_items")
    suspend fun deleteAllInvoices()

    @Query("SELECT COUNT(*) FROM invoice_items")
    suspend fun getCount(): Int

    @Query("SELECT SUM(potentialMonthlySavings) FROM invoice_items")
    fun getTotalMonthlySavingsPotential(): Flow<Double?>

    @Query("SELECT SUM(monthlyCost) FROM invoice_items")
    fun getTotalMonthlyCost(): Flow<Double?>
}

@Database(
    entities = [WatchlistItem::class, CouponItem::class, SavingsRecord::class, InvoiceItem::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao
    abstract fun couponDao(): CouponDao
    abstract fun savingsDao(): SavingsDao
    abstract fun invoiceDao(): InvoiceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE invoice_items ADD COLUMN sourceMessageId TEXT")
                db.execSQL("ALTER TABLE invoice_items ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'MANUAL'")
                db.execSQL("ALTER TABLE invoice_items ADD COLUMN verificationStatus TEXT NOT NULL DEFAULT 'UNVERIFIED'")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_invoice_items_sourceMessageId " +
                        "ON invoice_items(sourceMessageId)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "click_and_save_ai.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
