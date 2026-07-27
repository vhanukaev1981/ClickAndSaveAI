package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watchlist_items")
data class WatchlistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val storeName: String, // e.g. "Amazon", "eBay", "Nike", "Supermarket"
    val originalPrice: Double,
    val currentPrice: Double,
    val targetPrice: Double,
    val category: String, // "Electronics", "Fashion", "Groceries", "Home", "Travel"
    val imageUrl: String = "",
    val priceHistoryCsv: String = "", // e.g. "120,115,110,99,89,84"
    val isAlertEnabled: Boolean = true,
    val dateAdded: Long = System.currentTimeMillis(),
    val productUrl: String = ""
) {
    val priceDropPercentage: Int
        get() {
            if (originalPrice <= 0 || currentPrice >= originalPrice) return 0
            return (((originalPrice - currentPrice) / originalPrice) * 100).toInt()
        }

    val isTargetMet: Boolean
        get() = currentPrice <= targetPrice
}
