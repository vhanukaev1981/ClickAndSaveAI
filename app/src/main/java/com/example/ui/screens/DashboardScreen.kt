package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.InvoiceItem
import com.example.ui.MainViewModel
import com.example.ui.theme.EmeraldSavings
import com.example.ui.theme.TechBluePrimary

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToTab: (Int) -> Unit,
    onOpenReceiptScan: () -> Unit
) {
    val invoices by viewModel.invoices.collectAsState()
    val totalMonthlyCost by viewModel.totalMonthlyCost.collectAsState()
    val totalPotentialSavings by viewModel.totalMonthlySavingsPotential.collectAsState()
    val isConnected by viewModel.isGmailConnected.collectAsState()
    val connectedEmail by viewModel.connectedEmail.collectAsState()
    val isSyncing by viewModel.isSyncingGmail.collectAsState()
    val syncMessage by viewModel.gmailSyncStep.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                    Column {
                        Text(
                            text = "גרסת אב־טיפוס מאובטחת",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "חיבור Gmail, ניתוח AI, ניטור מחירים והעברת בקשות לספקים אינם מחוברים עדיין. לא יוצגו נתוני דמה כהצלחה.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Email, contentDescription = null, tint = TechBluePrimary)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "חיבור Gmail",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isConnected) {
                            "מחובר לחשבון: $connectedEmail"
                        } else {
                            "לא קיים כרגע OAuth token תקף ל-Gmail."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (syncMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = syncMessage,
                                modifier = Modifier.padding(10.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.connectGmail() },
                            enabled = !isSyncing,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                            }
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("בדוק חיבור")
                        }
                        if (isConnected) {
                            OutlinedButton(
                                onClick = { viewModel.disconnectGmail() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("נתק")
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "הוצאה חודשית מתועדת",
                    value = "₪${String.format("%.2f", totalMonthlyCost)}"
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "חיסכון מאומת",
                    value = "₪${String.format("%.2f", totalPotentialSavings)}"
                )
            }
        }

        item {
            Text(
                text = "פעולות זמינות",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DashboardActionButton(
                    text = "נהל חשבוניות ידניות",
                    icon = Icons.Default.ReceiptLong,
                    onClick = { onNavigateToTab(1) }
                )
                DashboardActionButton(
                    text = "צפה בקטלוג ספקים להדגמה",
                    icon = Icons.Default.Storefront,
                    onClick = { onNavigateToTab(2) }
                )
                DashboardActionButton(
                    text = "בדוק את מצב שירות ה-AI",
                    icon = Icons.Default.AutoAwesome,
                    onClick = { onNavigateToTab(3) }
                )
                OutlinedButton(
                    onClick = onOpenReceiptScan,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudOff, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("סריקת קבלה אינה זמינה")
                }
            }
        }

        item {
            Text(
                text = "חשבוניות מקומיות",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (invoices.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "אין חשבוניות",
                    body = "האפליקציה מתחילה כעת ללא נתוני דמה. אפשר להוסיף חשבונית ידנית במסך החשבוניות."
                )
            }
        } else {
            items(invoices, key = { it.id }) { invoice ->
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(invoice.providerName, fontWeight = FontWeight.Bold)
                        Text("${invoice.category} • ₪${String.format("%.2f", invoice.monthlyCost)}")
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = invoice.status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(modifier: Modifier, title: String, value: String) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DashboardActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = TechBluePrimary)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.size(8.dp))
        Text(text)
    }
}

@Composable
private fun EmptyStateCard(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(TechBluePrimary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = TechBluePrimary)
            }
            Spacer(modifier = Modifier.size(10.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun SwitchRequestModal(
    invoice: InvoiceItem,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("מעבר ספק אינו מחובר") },
        text = {
            Column {
                Text(
                    text = "לא תישלח בקשה ל-${invoice.recommendedAlternative}. אין כרגע backend או אינטגרציה עם ספקים, ולכן האפליקציה אינה אוספת פרטים אישיים במסך זה."
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "הפעולה הושבתה כדי למנוע הצגת הצלחה שאינה אמיתית.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("הבנתי") }
        }
    )
}

@Composable
fun AddInvoiceModal(
    onDismiss: () -> Unit,
    onAddInvoice: (String, String, Double, String, Double, Double) -> Unit
) {
    var providerName by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("חשמל") }
    var monthlyCostText by remember { mutableStateOf("") }
    val categories = listOf("חשמל", "סלולר", "אינטרנט", "ביטוח", "טלוויזיה")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("הוספת חשבונית ידנית") },
        text = {
            Column {
                Text(
                    text = "החשבונית תישמר מקומית בלבד. לא יופק חיסכון או ניתוח AI ללא מקור מאומת.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = providerName,
                    onValueChange = { providerName = it },
                    label = { Text("שם הספק") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = monthlyCostText,
                    onValueChange = { monthlyCostText = it },
                    label = { Text("סכום חודשי") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cost = monthlyCostText.toDoubleOrNull() ?: return@Button
                    onAddInvoice(
                        providerName,
                        selectedCategory,
                        cost,
                        "טרם בוצעה השוואה מאומתת",
                        cost,
                        0.0
                    )
                },
                enabled = providerName.isNotBlank() && (monthlyCostText.toDoubleOrNull() ?: 0.0) > 0.0
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(6.dp))
                Text("שמור מקומית")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ביטול") }
        }
    )
}
