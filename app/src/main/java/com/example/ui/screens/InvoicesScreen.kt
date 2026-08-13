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
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.data.repository.FinancialRecurringService
import com.example.data.repository.FinancialSyncState
import com.example.ui.MainViewModel
import com.example.ui.theme.TechBluePrimary

@Composable
fun InvoicesScreen(viewModel: MainViewModel, onOpenReceiptScan: () -> Unit) {
    val financialSyncState by viewModel.financialSyncState.collectAsState()
    val financialHome by viewModel.authoritativeFinancialHome.collectAsState()
    val latestScan by viewModel.latestRecoveredGmailScan.collectAsState()
    var selectedCategory by remember { mutableStateOf("הכל") }

    val context = financialHome?.context
    val services = context?.recurringServices.orEmpty()
    val categories = listOf("הכל") + services.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
    val filtered = if (selectedCategory == "הכל") services else services.filter { it.category == selectedCategory }
    val observedSpend = context?.observedRecurringMonthlySpend
    val sourceCoverage = context?.sourceCoverage.orEmpty()
    val recovering = financialSyncState is FinancialSyncState.CheckingConnection || financialSyncState is FinancialSyncState.Recovering

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("invoices_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("החשבונות שלי", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "שירותים חוזרים שנבנו מראיות שנתמכות על ידי Core.",
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
                        observedSpend?.let { money(it) } ?: "לא ידוע",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text("הוצאה חודשית חוזרת שנצפתה", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "הסכום אינו מייצג הוצאה ביתית מלאה אלא רק מקורות שנצפו. מקורות: ${sourceCoverage.ifEmpty { listOf("לא ידוע") }.joinToString()}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (recovering && financialHome == null) {
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } }
        } else {
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

            if (filtered.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null)
                            Spacer(Modifier.size(10.dp))
                            Text(if (selectedCategory == "הכל") "לא זוהו עדיין שירותים חוזרים בראיות הנתמכות." else "אין שירות מזוהה בקטגוריה $selectedCategory.")
                        }
                    }
                }
            } else {
                items(filtered, key = { "${it.providerName}:${it.category}" }) { service ->
                    val evidenceStatus = latestScan?.invoices
                        ?.firstOrNull { it.providerName == service.providerName && it.category == service.category }
                        ?.verificationStatus
                    RecurringServiceCard(service, evidenceStatus)
                }
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
                    Text("Bills מציג רק מצב סמכותי שנבנה מהשרת. חיסכון והצעות מוצגים במסך חיסכון ורק כאשר קיימת השוואה נתמכת.")
                }
            }
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val receiptScanKeptForApiCompatibility = onOpenReceiptScan
}

@Composable
private fun RecurringServiceCard(service: FinancialRecurringService, verificationStatus: String?) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(service.providerName, fontWeight = FontWeight.Bold)
            Text(service.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${money(service.latestMonthlyCost)} לחודש", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "ראיות: ${service.observationCount} תצפיות לאורך ${service.observedMonths} חודשים",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "מצב אימות: ${verificationStatus?.takeIf { it.isNotBlank() } ?: "לא זמין"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun money(value: Double): String = "₪${String.format("%.2f", value)}"
