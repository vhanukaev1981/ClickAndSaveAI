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
fun InvoicesScreen(
    viewModel: MainViewModel,
    onOpenReceiptScan: () -> Unit
) {
    val invoices by viewModel.invoices.collectAsState()
    val totalMonthlyCost by viewModel.totalMonthlyCost.collectAsState()
    val verifiedSavings by viewModel.totalMonthlySavingsPotential.collectAsState()
    val session by viewModel.userSession.collectAsState()
    val leadState by viewModel.providerLeadState.collectAsState()

    var selectedCategory by remember { mutableStateOf("הכל") }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedInvoice by remember { mutableStateOf<InvoiceItem?>(null) }

    val categories = listOf("הכל", "חשמל", "סלולר", "אינטרנט", "תקשורת", "ביטוח", "טלוויזיה")
    val filteredInvoices = if (selectedCategory == "הכל") {
        invoices
    } else {
        invoices.filter { it.category == selectedCategory }
    }

    selectedInvoice?.let { invoice ->
        ProviderLeadDialog(
            invoice = invoice,
            initialName = session.displayName,
            initialEmail = session.email,
            state = leadState,
            onDismiss = {
                viewModel.clearProviderLeadState()
                selectedInvoice = null
            },
            onSubmit = { name, phone, email, requestedProvider, consent ->
                viewModel.submitProviderLead(
                    invoice = invoice,
                    contactName = name,
                    phone = phone,
                    contactEmail = email,
                    requestedProvider = requestedProvider,
                    consentAccepted = consent
                )
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
                        Text("חשבוניות ולידים", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "חשבוניות Gmail נשמרות כלא מאומתות. קטגוריית 'תקשורת' משמשת כשספק רב-שירותים זוהה אך סוג השירות אינו חד-משמעי.",
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
                Text("סה״כ חודשי מתועד: ₪${String.format("%.2f", totalMonthlyCost)}")
                Text("חיסכון שאומת: ₪${String.format("%.2f", verifiedSavings)}", fontWeight = FontWeight.Bold)
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
                    Text(
                        if (selectedCategory == "הכל") {
                            "לא נמצאו חשבוניות. אפשר להוסיף ידנית או לייבא מ-Gmail לאחר אישור."
                        } else {
                            "אין כרגע חשבוניות בקטגוריה $selectedCategory."
                        }
                    )
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
                        onLead = {
                            if (session.isAuthenticated) {
                                viewModel.clearProviderLeadState()
                                selectedInvoice = invoice
                            } else {
                                viewModel.setTab(4)
                            }
                        }
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
            Text("סריקת תמונות עדיין אינה זמינה")
        }
    }
}

@Composable
private fun InvoiceCard(
    invoice: InvoiceItem,
    authenticated: Boolean,
    onDelete: () -> Unit,
    onLead: () -> Unit
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
                    Text("${invoice.category} • ${invoice.sourceType}", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "מחק")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("חיוב חודשי: ₪${String.format("%.2f", invoice.monthlyCost)}")
            Text(invoice.status.replace("ליד נשלח ל-CRM", "ליד נשמר בתור הקליטה"), style = MaterialTheme.typography.bodySmall)
            Text("אימות: ${invoice.verificationStatus}", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(10.dp))
            Button(onClick = onLead, modifier = Modifier.fillMaxWidth()) {
                Icon(if (authenticated) Icons.Default.Send else Icons.Default.Login, contentDescription = null)
                Spacer(modifier = Modifier.size(6.dp))
                Text(if (authenticated) "שמור ליד בתור הקליטה" else "התחבר כדי ליצור ליד")
            }
        }
    }
}

@Composable
private fun ProviderLeadDialog(
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
        title = { Text(if (success == null) "יצירת ליד למעבר ספק" else "הליד נשמר") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (success != null) {
                    Text("מזהה ליד: ${success.result.leadId}")
                    Text(
                        if (success.result.duplicate) {
                            "הבקשה כבר נשמרה בעבר ולא נוצרה כפילות."
                        } else {
                            "הליד נמצא בתור הקליטה המאובטח. טרם הועבר ל-CRM חיצוני או לספק."
                        }
                    )
                } else {
                    Text("ספק נוכחי: ${invoice.providerName} • קטגוריה: ${invoice.category}")
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("שם מלא") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !submitting
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("טלפון") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !submitting
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("דוא״ל") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !submitting
                    )
                    OutlinedTextField(
                        value = requestedProvider,
                        onValueChange = { requestedProvider = it },
                        label = { Text("ספק מבוקש, אופציונלי") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !submitting
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = consent,
                            onCheckedChange = { consent = it },
                            enabled = !submitting
                        )
                        Text("אני מאשר/ת לשמור את פרטי הקשר בתור הלידים לצורך טיפול בבקשה זו.")
                    }
                    if (state is ProviderLeadUiState.Error) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            if (success != null) {
                Button(onClick = onDismiss) { Text("סגור") }
            } else {
                Button(
                    onClick = { onSubmit(name, phone, email, requestedProvider, consent) },
                    enabled = !submitting && name.isNotBlank() && phone.isNotBlank() && email.isNotBlank() && consent
                ) {
                    if (submitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("שמור ליד")
                    }
                }
            }
        },
        dismissButton = {
            if (success == null) {
                TextButton(onClick = onDismiss, enabled = !submitting) { Text("ביטול") }
            }
        }
    )
}

@Composable
private fun ManualInvoiceDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, Double) -> Unit
) {
    var provider by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("חשמל") }
    var amount by remember { mutableStateOf("") }
    val categories = listOf("חשמל", "סלולר", "אינטרנט", "תקשורת", "ביטוח", "טלוויזיה")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("הוספת חשבונית ידנית") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = provider,
                    onValueChange = { provider = it },
                    label = { Text("ספק") },
                    modifier = Modifier.fillMaxWidth()
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
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("סכום חודשי") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "הנתון יישמר כלא מאומת ולא יחושב ממנו חיסכון אוטומטי.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(provider, category, amount.toDoubleOrNull() ?: 0.0) },
                enabled = provider.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0.0
            ) {
                Text("שמור")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ביטול") }
        }
    )
}
