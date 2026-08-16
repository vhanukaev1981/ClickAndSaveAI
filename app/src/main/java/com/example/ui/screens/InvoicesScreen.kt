package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.BackendInvoice
import com.example.data.repository.FinancialHomeResult
import com.example.data.repository.FinancialSyncState
import com.example.data.repository.latestScanOrNull
import com.example.ui.MainViewModel
import com.example.ui.theme.TechBluePrimary

@Composable
fun InvoicesScreen(viewModel: MainViewModel, onOpenReceiptScan: () -> Unit) {
    val financialSyncState by viewModel.financialSyncState.collectAsState()
    val financialHome by viewModel.authoritativeFinancialHome.collectAsState()
    var selectedCategory by remember { mutableStateOf("הכל") }
    val categories = listOf("הכל", "חשמל", "סלולר", "אינטרנט", "תקשורת", "ביטוח", "טלוויזיה")
    val authoritativeBills = financialSyncState.latestScanOrNull?.invoices
    val filteredBills = authoritativeBills?.let { bills ->
        if (selectedCategory == "הכל") bills else bills.filter { it.category == selectedCategory }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("invoices_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("החשבונות שלי", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "חיובים שנצפו במקור הסמכותי המחובר.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        formatAuthoritativeMoney(financialHome?.context?.observedRecurringMonthlySpend),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text("הוצאה חודשית חוזרת שנצפתה")
                    Text(financialTruthStatus(financialSyncState), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) }
                    )
                }
            }
        }

        when {
            filteredBills == null -> item {
                BillsStateCard(unknownBillsMessage(financialSyncState))
            }
            filteredBills.isEmpty() -> item {
                BillsStateCard(
                    if (selectedCategory == "הכל") {
                        "הסריקה הסמכותית הזמינה לא החזירה חשבונות מזוהים. זהו מצב ריק ידוע של אותה סריקה."
                    } else {
                        "בסריקה הסמכותית הזמינה אין חשבונות בקטגוריה $selectedCategory."
                    }
                )
            }
            else -> items(filteredBills, key = { it.sourceMessageId }) { bill ->
                AuthoritativeBillCard(bill = bill, financialHome = financialHome)
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = TechBluePrimary)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "רשומה מוצגת רק כאשר היא קיימת בתוצאת הסריקה הסמכותית. שדות חסרים נשארים לא ידועים.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val receiptScanKeptForApiCompatibility = onOpenReceiptScan
}

@Composable
private fun AuthoritativeBillCard(
    bill: BackendInvoice,
    financialHome: FinancialHomeResult?
) {
    val recurring = financialHome?.context?.recurringServices?.firstOrNull {
        it.providerName == bill.providerName && it.category == bill.category
    }
    val opportunity = financialHome?.opportunities?.firstOrNull {
        it.providerName == bill.providerName && it.category == bill.category
    }

    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(bill.providerName.ifBlank { "ספק לא ידוע" }, fontWeight = FontWeight.Bold)
            Text(bill.category.ifBlank { "קטגוריה לא ידועה" }, style = MaterialTheme.typography.bodySmall)
            Text("סכום שנצפה: ${money(bill.monthlyCost)}", fontWeight = FontWeight.SemiBold)
            Text(
                if (recurring != null) {
                    "חיוב חוזר שזוהה • ${recurring.observationCount} תצפיות"
                } else {
                    "מצב מחזור החיוב לא ידוע"
                },
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "מקור: Gmail קריאה בלבד • ${bill.verificationStatus}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (opportunity != null) {
                Text("מצב הזדמנות: ${opportunity.status}", style = MaterialTheme.typography.bodySmall)
                opportunity.potentialMonthlySaving?.let { saving ->
                    Text("חיסכון פוטנציאלי: ${money(saving)} לחודש", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun BillsStateCard(message: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null)
            Spacer(Modifier.size(10.dp))
            Text(message)
        }
    }
}

private fun unknownBillsMessage(state: FinancialSyncState): String = when (state) {
    FinancialSyncState.Unauthenticated -> "נדרשת התחברות כדי לדעת אילו חשבונות קיימים."
    FinancialSyncState.CheckingConnection -> "מצב החשבונות עדיין לא ידוע; בודקים את החיבור."
    FinancialSyncState.Disconnected -> "Gmail אינו מחובר, ולכן אין כרגע מקור סמכותי לחשבונות."
    FinancialSyncState.Recovering -> "החשבונות עדיין נטענים; רשימה ריקה לא תוצג כמידע אמיתי."
    is FinancialSyncState.Partial -> "כיסוי החשבונות עדיין אינו ידוע במלואו."
    is FinancialSyncState.Failed -> "טעינת החשבונות נכשלה; לא נציג רשימה ריקה במקום שגיאה."
    is FinancialSyncState.Ready -> "מצב החשבונות לא ידוע."
}

private fun financialTruthStatus(state: FinancialSyncState): String = when (state) {
    FinancialSyncState.Unauthenticated -> "נדרשת התחברות כדי לטעון מידע סמכותי."
    FinancialSyncState.CheckingConnection -> "בודקים את מקור המידע."
    FinancialSyncState.Disconnected -> "המקור הפיננסי אינו מחובר."
    FinancialSyncState.Recovering -> "המידע הסמכותי עדיין נטען."
    is FinancialSyncState.Partial -> "המידע חלקי; ערכים חסרים נשארים לא ידועים."
    is FinancialSyncState.Failed -> "הסנכרון נכשל; ערכים חסרים נשארים לא ידועים."
    is FinancialSyncState.Ready -> "הנתונים מבוססים על הסנכרון הסמכותי האחרון."
}
