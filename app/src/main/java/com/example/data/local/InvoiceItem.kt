package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "invoice_items")
data class InvoiceItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerName: String, // e.g. "חברת החשמל", "סלקום", "בזק", "הראל ביטוח"
    val category: String, // "חשמל", "סלולר", "אינטרנט", "ביטוח", "קניות"
    val monthlyCost: Double, // Current monthly bill amount (e.g. 480.0)
    val recommendedAlternative: String, // e.g. "אלקטרה פאוור - 7% הנחה"
    val alternativeMonthlyCost: Double, // e.g. 446.4
    val potentialMonthlySavings: Double, // e.g. 33.6
    val status: String = "פוענח - הצעה מוכנה", // "פוענח - הצעה מוכנה", "ממתין לפענוח", "מעבר בטיפול"
    val isSwitchRequested: Boolean = false,
    val dateAdded: Long = System.currentTimeMillis(),
    val accountNumber: String = "8934201",
    val billDate: String = "07/2026"
) {
    val potentialAnnualSavings: Double
        get() = potentialMonthlySavings * 12
}
