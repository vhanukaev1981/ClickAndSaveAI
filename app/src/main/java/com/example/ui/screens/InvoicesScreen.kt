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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.data.local.InvoiceItem
import com.example.data.repository.FinancialSyncState
import com.example.ui.MainViewModel
import com.example.ui.theme.TechBluePrimary

@Composable
fun InvoicesScreen(viewModel: MainViewModel, onOpenReceiptScan: () -> Unit) {
    val invoices by viewModel.invoices.collectAsState()
    val financialSyncState by viewModel.financialSyncState.collectAsState()
    val financialHome by viewModel.authoritativeFinancialHome.collectAsState()
    var selectedCategory by remember { mutableStateOf("הכל") }
    val categories = listOf("הכל", "חשמל", "סלולר", "אינטרנט", "תקשורת", "ביטוח", "טלוויזיה")
    val filteredInvoices = if (selectedCategory == "הכל") invoices else invoices.filter { it.category == selectedCategory }
    val observedMonthlySpend = financialHome?.context?.observedRecurringMonthlySpend

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("invoices_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "החשבונות שלי",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "כל החיובים שהמערכת זיהתה במקום אחד.",
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
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        formatAuthoritativeMoney(observedMonthlySpend),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "הוצאה חודשית מזוהה",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        financialTruthStatus(financialSyncState),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "חיסכון יוצג רק אחרי אימות של הצעה מתאימה.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TechBluePrimary,
                        fontWeight = FontWeight.SemiBold
                    )
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

        if (filteredInvoices.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null)
                        Spacer(Modifier.size(10.dp))
                        Text(
                            if (selectedCategory == "הכל") {
                                when (financialSyncState) {
                                    FinancialSyncState.CheckingConnection,
                                    FinancialSyncState.Recovering -> "המידע עדיין נטען. לא יוצג אפס במקום מידע שעדיין אינו ידוע."
                                    is FinancialSyncState.Failed -> "הסנכרון לא הושלם. לא הומצאו נתונים חלופיים."
                                    is FinancialSyncState.Partial -> "המידע חלקי כרגע. מוצגים רק נתונים שכבר אומתו."
                                    else -> "עדיין לא זוהו חשבונות מהמקור המחובר."
                                }
                            } else {
                                "אין כרגע חשבונות בקטגוריה $selectedCategory."
                            }
                        )
                    }
                }
            }
        } else {
            items(filteredInvoices, key = { it.id }) { invoice ->
                InvoiceCard(
                    invoice = invoice,
                    onDelete = { viewModel.deleteInvoice(invoice.id) }
                )
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = TechBluePrimary)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "חשבונות הם מקור המידע. אם נמצאת חלופה טובה ומאומתת, הפעולה תופיע במסך חיסכון — לא צריך לבקש בדיקה מכל חשבון בנפרד.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val receiptScanKeptForApiCompatibility = onOpenReceiptScan
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

@Composable
private fun InvoiceCard(invoice: InvoiceItem, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(invoice.providerName, fontWeight = FontWeight.Bold)
                    Text(
                        invoice.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "מחק")
                }
            }
            Text(
                "₪${String.format("%.2f", invoice.monthlyCost)} לחודש",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                customerStatus(invoice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun customerStatus(invoice: InvoiceItem): String {
    val raw = "${invoice.status} ${invoice.verificationStatus}".uppercase()
    return when {
        raw.contains("LEAD") || raw.contains("CRM") || raw.contains("ליד") -> "בקשת חיסכון קודמת נמצאת בטיפול"
        raw.contains("UNVERIFIED") || raw.contains("NOT_FOUND") || raw.contains("GMAIL_READONLY") -> "החשבון זוהה ונמצא בבדיקה"
        else -> "נבדק אוטומטית להזדמנויות חיסכון"
    }
}
