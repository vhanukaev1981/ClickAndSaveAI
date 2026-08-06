package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.InvoiceItem
import com.example.ui.MainViewModel
import com.example.ui.theme.EmeraldSavings

@Composable
fun InvoicesScreen(
    viewModel: MainViewModel,
    onOpenReceiptScan: () -> Unit
) {
    val context = LocalContext.current
    val invoices by viewModel.invoices.collectAsState()
    val totalMonthlyCost by viewModel.totalMonthlyCost.collectAsState()
    val totalPotentialSavings by viewModel.totalMonthlySavingsPotential.collectAsState()

    var selectedCategory by remember { mutableStateOf("הכל") }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedInvoice by remember { mutableStateOf<InvoiceItem?>(null) }

    val categories = listOf("הכל", "חשמל", "סלולר", "אינטרנט", "ביטוח", "טלוויזיה")
    val filteredInvoices = if (selectedCategory == "הכל") {
        invoices
    } else {
        invoices.filter { it.category == selectedCategory }
    }

    selectedInvoice?.let { invoice ->
        SwitchRequestModal(
            invoice = invoice,
            onDismiss = { selectedInvoice = null },
            onSubmit = { _, _, _ -> selectedInvoice = null }
        )
    }

    if (showAddDialog) {
        AddInvoiceModal(
            onDismiss = { showAddDialog = false },
            onAddInvoice = { provider, category, cost, alternative, alternativeCost, savings ->
                viewModel.addManualInvoice(
                    providerName = provider,
                    category = category,
                    monthlyCost = cost,
                    recommendedAlternative = alternative,
                    alternativeCost = alternativeCost,
                    savings = savings
                )
                showAddDialog = false
                Toast.makeText(
                    context,
                    "החשבונית נשמרה מקומית ללא ניתוח או המלצה.",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("invoices_screen")
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("חשבוניות מקומיות", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "הסכומים נשמרים במכשיר. אין כרגע פענוח AI או שליחת בקשות לספקים.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Button(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("הוסף")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("סה״כ חודשי: ₪${String.format("%.2f", totalMonthlyCost)}")
                Text(
                    "חיסכון מאומת: ₪${String.format("%.2f", totalPotentialSavings)}",
                    color = EmeraldSavings,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
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
        }

        if (filteredInvoices.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Spacer(modifier = Modifier.size(10.dp))
                    Text("לא נמצאו חשבוניות. נתוני דמה אינם מוזנים עוד אוטומטית.")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredInvoices, key = { it.id }) { invoice ->
                    InvoiceCard(
                        invoice = invoice,
                        onDelete = { viewModel.deleteInvoice(invoice.id) },
                        onUnavailableSwitch = { selectedInvoice = invoice }
                    )
                }
            }
        }

        OutlinedButton(
            onClick = onOpenReceiptScan,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(Icons.Default.CloudOff, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text("סריקת קבלה אינה זמינה")
        }
    }
}

@Composable
private fun InvoiceCard(
    invoice: InvoiceItem,
    onDelete: () -> Unit,
    onUnavailableSwitch: () -> Unit
) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(invoice.providerName, fontWeight = FontWeight.Bold)
                    Text(invoice.category, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "מחק")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("חיוב חודשי: ₪${String.format("%.2f", invoice.monthlyCost)}")
            Text(
                invoice.status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(onClick = onUnavailableSwitch, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(modifier = Modifier.size(6.dp))
                Text("מעבר ספק אינו מחובר")
            }
        }
    }
}
