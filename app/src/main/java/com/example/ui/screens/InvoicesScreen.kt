package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.ui.MainViewModel
import com.example.ui.ProviderLeadUiState

@Composable
fun InvoicesScreen(viewModel: MainViewModel, onOpenReceiptScan: () -> Unit) {
    val invoices by viewModel.invoices.collectAsState()
    val totalMonthlyCost by viewModel.totalMonthlyCost.collectAsState()
    val verifiedSavings by viewModel.totalMonthlySavingsPotential.collectAsState()
    val session by viewModel.userSession.collectAsState()
    val requestState by viewModel.providerLeadState.collectAsState()
    var selectedCategory by remember { mutableStateOf("הכל") }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedInvoice by remember { mutableStateOf<InvoiceItem?>(null) }
    val categories = listOf("הכל", "חשמל", "סלולר", "אינטרנט", "תקשורת", "ביטוח", "טלוויזיה")
    val filteredInvoices = if (selectedCategory == "הכל") invoices else invoices.filter { it.category == selectedCategory }

    selectedInvoice?.let { invoice ->
        SavingsRequestDialog(
            invoice = invoice,
            initialName = session.displayName,
            initialEmail = session.email,
            state = requestState,
            onDismiss = {
                viewModel.clearProviderLeadState()
                selectedInvoice = null
            },
            onSubmit = { name, phone, email, requestedProvider, consent ->
                // Backend naming is intentionally kept for compatibility; it is not exposed to customers.
                viewModel.submitProviderLead(invoice, name, phone, email, requestedProvider, consent)
            }
        )
    }

    if (showAddDialog) {
        ManualInvoiceDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { provider, category, cost ->
                viewModel.addManualInvoice(provider, category, cost, "", 0.0, 0.0)
                showAddDialog = false
            }
        )
    }

    Column(Modifier.fillMaxSize().testTag("invoices_screen")) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("החשבונות שלי", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Click&SaveAI מזהה חיובים ובודקת עבורם הזדמנויות חיסכון באופן אוטומטי.", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.size(4.dp))
                        Text("הוסף")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("הוצאה חודשית מזוהה: ₪${String.format("%.2f", totalMonthlyCost)}")
                Text("חיסכון חודשי מאומת: ₪${String.format("%.2f", verifiedSavings)}", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories) { category ->
                        FilterChip(selected = selectedCategory == category, onClick = { selectedCategory = category }, label = { Text(category) })
                    }
                }
            }
        }

        if (filteredInvoices.isEmpty()) {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Spacer(Modifier.size(10.dp))
                    Text(if (selectedCategory == "הכל") "עדיין לא זוהו חשבונות. אפשר להוסיף חשבון ידנית או לחבר Gmail." else "אין כרגע חשבונות בקטגוריה $selectedCategory.")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredInvoices, key = { it.id }) { invoice ->
                    InvoiceCard(
                        invoice = invoice,
                        authenticated = session.isAuthenticated,
                        onDelete = { viewModel.deleteInvoice(invoice.id) },
                        onSavingsRequest = {
                            if (session.isAuthenticated) {
                                viewModel.clearProviderLeadState()
                                selectedInvoice = invoice
                            } else viewModel.setTab(4)
                        }
                    )
                }
            }
        }

        OutlinedButton(onClick = onOpenReceiptScan, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Icon(Icons.Default.CloudOff, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("סריקת חשבון מתמונה — בקרוב")
        }
    }
}

@Composable
private fun InvoiceCard(invoice: InvoiceItem, authenticated: Boolean, onDelete: () -> Unit, onSavingsRequest: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(invoice.providerName, fontWeight = FontWeight.Bold)
                    Text(invoice.category, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, contentDescription = "מחק") }
            }
            Spacer(Modifier.height(8.dp))
            Text("חיוב חודשי: ₪${String.format("%.2f", invoice.monthlyCost)}")
            Text(customerStatus(invoice), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            Button(onClick = onSavingsRequest, modifier = Modifier.fillMaxWidth()) {
                Icon(if (authenticated) Icons.Default.Send else Icons.Default.Login, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(if (authenticated) "בדוק אפשרות לחיסכון" else "התחבר כדי לבדוק חיסכון")
            }
        }
    }
}

private fun customerStatus(invoice: InvoiceItem): String {
    val raw = "${invoice.status} ${invoice.verificationStatus}".uppercase()
    return when {
        raw.contains("LEAD") || raw.contains("CRM") || raw.contains("ליד") -> "בקשת החיסכון התקבלה ונמצאת בטיפול"
        raw.contains("UNVERIFIED") || raw.contains("NOT_FOUND") || raw.contains("GMAIL_READONLY") -> "החשבון זוהה ונמצא בבדיקה"
        else -> "החשבון נמצא במעקב לחיסכון"
    }
}

@Composable
private fun SavingsRequestDialog(
    invoice: InvoiceItem,
    initialName: String,
    initialEmail: String,
    state: ProviderLeadUiState,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String, Boolean) -> Unit
) {
    var name by remember(invoice.id) { mutableStateOf(initialName) }
    var phone by remember(invoice.id) { mutableStateOf("") }
    var email by remember(invoice.id) { mutableStateOf(initialEmail) }
    var requestedProvider by remember(invoice.id) { mutableStateOf("") }
    var consent by remember(invoice.id) { mutableStateOf(false) }
    val submitting = state is ProviderLeadUiState.Submitting
    val success = state as? ProviderLeadUiState.Success

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(if (success == null) "בדיקת אפשרות לחיסכון" else "הבקשה נשמרה") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (success != null) {
                    Text(if (success.result.duplicate) "הבקשה כבר קיימת ונמצאת בטיפול. לא נוצרה בקשה כפולה." else "הבקשה נשמרה בהצלחה. Click&SaveAI תמשיך את הבדיקה ותעדכן כשיהיה מה להציע.")
                } else {
                    Text("נבדוק אפשרויות חיסכון עבור ${invoice.providerName} בקטגוריית ${invoice.category}.")
                    OutlinedTextField(name, { name = it }, label = { Text("שם מלא") }, modifier = Modifier.fillMaxWidth(), enabled = !submitting)
                    OutlinedTextField(phone, { phone = it }, label = { Text("טלפון") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), enabled = !submitting)
                    OutlinedTextField(email, { email = it }, label = { Text("דוא״ל") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(), enabled = !submitting)
                    OutlinedTextField(requestedProvider, { requestedProvider = it }, label = { Text("ספק מועדף, אם יש") }, modifier = Modifier.fillMaxWidth(), enabled = !submitting)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = consent, onCheckedChange = { consent = it }, enabled = !submitting)
                        Text("אני מאשר/ת להשתמש בפרטי הקשר לצורך טיפול בבקשת החיסכון הזו.")
                    }
                    if (state is ProviderLeadUiState.Error) Text("לא הצלחנו לשמור את הבקשה כרגע. אפשר לנסות שוב.", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (success != null) Button(onClick = onDismiss) { Text("סגור") }
            else Button(
                onClick = { onSubmit(name, phone, email, requestedProvider, consent) },
                enabled = !submitting && name.isNotBlank() && phone.isNotBlank() && email.isNotBlank() && consent
            ) {
                if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("שלח בקשת חיסכון")
            }
        },
        dismissButton = { if (success == null) TextButton(onClick = onDismiss, enabled = !submitting) { Text("ביטול") } }
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
        title = { Text("הוספת חשבון ידנית") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(provider, { provider = it }, label = { Text("ספק") }, modifier = Modifier.fillMaxWidth())
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories) { item -> FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) }) }
                }
                OutlinedTextField(amount, { amount = it }, label = { Text("סכום חודשי") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Text("החשבון יישמר וייבדק. סכום חיסכון יוצג רק לאחר שנמצא ונאמת פתרון מתאים.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(provider, category, amount.toDoubleOrNull() ?: 0.0) }, enabled = provider.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0.0) { Text("שמור") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}
