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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.InvoiceItem
import com.example.ui.CustomerPresentationPolicy
import com.example.ui.MainViewModel
import com.example.ui.theme.TechBluePrimary

@Composable
fun InvoicesScreen(viewModel: MainViewModel, onOpenReceiptScan: () -> Unit) {
    val invoices by viewModel.invoices.collectAsState()
    val totalMonthlyCost by viewModel.totalMonthlyCost.collectAsState()
    var selectedCategory by remember { mutableStateOf("הכל") }
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<InvoiceItem?>(null) }

    val categories = listOf("הכל") + invoices
        .map { it.category.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()
    val filteredInvoices = if (selectedCategory == "הכל") {
        invoices
    } else {
        invoices.filter { it.category == selectedCategory }
    }
    val categoryTotals = invoices
        .groupBy { it.category.ifBlank { "אחר" } }
        .mapValues { (_, items) -> items.sumOf { it.monthlyCost } }
        .toList()
        .sortedByDescending { it.second }

    if (showAddDialog) {
        ManualInvoiceDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { provider, category, cost ->
                viewModel.addManualInvoice(provider, category, cost, "", 0.0, 0.0)
                showAddDialog = false
            }
        )
    }

    pendingDelete?.let { invoice ->
        DeleteInvoiceDialog(
            invoice = invoice,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                viewModel.deleteInvoice(invoice.id)
                pendingDelete = null
            }
        )
    }

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
                    "תמונת ההוצאות החודשיות שהמערכת זיהתה עבורך.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Card(
                modifier = Modifier.testTag("bills_monthly_overview"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                money(totalMonthlyCost),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "הוצאה חודשית מזוהה",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.testTag("add_manual_bill")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.size(4.dp))
                            Text("הוסף ידנית")
                        }
                    }

                    Text(
                        "החשבונות כאן מתארים את ההוצאות שזוהו. סכומי חיסכון מוצגים באזור החיסכון רק לאחר שנמצאה הצעה מתאימה שניתן לאמת.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (categoryTotals.isNotEmpty()) {
            item {
                Text(
                    "הוצאות לפי קטגוריה",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categoryTotals, key = { it.first }) { (category, total) ->
                        CategorySpendCard(
                            category = category,
                            monthlyAmount = total,
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category }
                        )
                    }
                }
            }
        }

        item {
            LazyRow(
                modifier = Modifier.testTag("bill_category_filters"),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bills_empty_state"),
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
                                "עדיין לא זוהו חשבונות. לאחר החיבור הראשוני מסמכים רלוונטיים ייקלטו אוטומטית, ואפשר גם להוסיף חשבון ידנית."
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
                    onDelete = { pendingDelete = invoice }
                )
            }
        }

        item {
            Card(
                modifier = Modifier.testTag("bills_automatic_savings_explainer"),
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
                        "לא צריך לבקש בדיקה מכל חשבון. אם נמצאת חלופה טובה ומאומתת, היא תופיע אוטומטית באזור החיסכון.",
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
private fun CategorySpendCard(
    category: String,
    monthlyAmount: Double,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(category, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(money(monthlyAmount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "לחודש",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InvoiceCard(invoice: InvoiceItem, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.testTag("bill_${invoice.id}"),
        shape = RoundedCornerShape(18.dp)
    ) {
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
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_bill_${invoice.id}")
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "מחק חשבון")
                }
            }
            Text(
                "${money(invoice.monthlyCost)} לחודש",
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
    val raw = "${invoice.status} ${invoice.verificationStatus}"
    val safe = CustomerPresentationPolicy.safeStatus(raw)
    return when (safe) {
        "המידע מתעדכן", "נמצא בבדיקה" -> "החשבון זוהה ונמצא בבדיקה"
        else -> "נבדק אוטומטית להזדמנויות חיסכון"
    }
}

@Composable
private fun DeleteInvoiceDialog(
    invoice: InvoiceItem,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("להסיר את החשבון?") },
        text = {
            Text(
                "${invoice.providerName} יוסר מהתצוגה המקומית. אם ייקלט בעתיד מסמך חדש מאותו שירות, הוא עשוי להופיע שוב."
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_delete_bill")
            ) { Text("הסר") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_delete_bill")
            ) { Text("ביטול") }
        }
    )
}

@Composable
private fun ManualInvoiceDialog(onDismiss: () -> Unit, onAdd: (String, String, Double) -> Unit) {
    var provider by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("חשמל") }
    var amount by remember { mutableStateOf("") }
    val categories = listOf("חשמל", "סלולר", "אינטרנט", "תקשורת", "ביטוח", "טלוויזיה")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("הוספת חשבון") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "אפשר להוסיף חיוב שלא הגיע דרך מקור מחובר. גם כאן חיסכון יוצג רק אם תימצא הצעה מתאימה ומאומתת.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    provider,
                    { provider = it },
                    label = { Text("ספק") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_bill_provider")
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories) { item ->
                        FilterChip(
                            selected = category == item,
                            onClick = { category = item },
                            label = { Text(item) }
                        )
                    }
                }
                OutlinedTextField(
                    amount,
                    { amount = it },
                    label = { Text("סכום חודשי") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_bill_amount")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(provider, category, amount.toDoubleOrNull() ?: 0.0) },
                enabled = provider.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0.0,
                modifier = Modifier.testTag("save_manual_bill")
            ) {
                Text("שמור")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_manual_bill")
            ) { Text("ביטול") }
        }
    )
}

private fun money(value: Double): String = "₪${String.format("%.2f", value)}"
