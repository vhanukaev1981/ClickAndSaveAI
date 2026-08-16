package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "invoice_items",
    indices = [Index(value = ["sourceMessageId"], unique = true)]
)
data class InvoiceItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerName: String,
    val category: String,
    val monthlyCost: Double,
    val recommendedAlternative: String = "טרם בוצעה השוואה מאומתת",
    val alternativeMonthlyCost: Double = 0.0,
    val potentialMonthlySavings: Double = 0.0,
    val status: String = "ממתין לאימות",
    val isSwitchRequested: Boolean = false,
    val dateAdded: Long = System.currentTimeMillis(),
    val accountNumber: String = "",
    val billDate: String = "",
    val sourceMessageId: String? = null,
    val sourceType: String = "MANUAL",
    val verificationStatus: String = "UNVERIFIED"
) {
    val potentialAnnualSavings: Double
        get() = potentialMonthlySavings * 12
}
