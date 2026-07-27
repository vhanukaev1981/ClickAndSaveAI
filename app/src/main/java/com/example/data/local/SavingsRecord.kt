package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "savings_records")
data class SavingsRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String, // e.g. "AirPods Pro Coupon", "Weekly Grocery Price Match"
    val storeName: String,
    val amountSaved: Double,
    val category: String,
    val dateTimestamp: Long = System.currentTimeMillis(),
    val note: String = ""
)
