package com.example.data.local

import android.content.Context
import androidx.room.*
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: InvoiceItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoices(invoices: List<InvoiceItem>)

    @Update
    suspend fun updateInvoice(invoice: InvoiceItem)

    @Query("DELETE FROM invoice_items WHERE id = :id")
    suspend fun deleteInvoice(id: Long)

    @Query("SELECT COUNT(*) FROM invoice_items")
    suspend fun getCount(): Int

    @Query("SELECT SUM(potentialMonthlySavings) FROM invoice_items")
    fun getTotalMonthlySavingsPotential(): Flow<Double?>

    @Query("SELECT SUM(monthlyCost) FROM invoice_items")
    fun getTotalMonthlyCost(): Flow<Double?>
}

@Database(
    entities = [WatchlistItem::class, CouponItem::class, SavingsRecord::class, InvoiceItem::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao
    abstract fun couponDao(): CouponDao
    abstract fun savingsDao(): SavingsDao
    abstract fun invoiceDao(): InvoiceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "click_and_save_ai.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

