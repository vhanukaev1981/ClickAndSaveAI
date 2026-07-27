package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "coupon_items")
data class CouponItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storeName: String, // e.g. "Amazon", "Nike", "ASOS", "iHerb", "Shein"
    val promoCode: String,
    val discountText: String, // e.g. "25% OFF Entire Order" or "$15 OFF orders over $70"
    val category: String, // "Fashion", "Tech", "Groceries", "Health", "General"
    val verifiedStatus: String = "Verified Today",
    val copyCount: Int = 1420,
    val isFavorite: Boolean = false,
    val expirationNote: String = "Expires in 3 days",
    val cashbackPercent: Double = 0.0
)
