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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel
import com.example.ui.theme.TechBluePrimary

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToTab: (Int) -> Unit,
    onOpenReceiptScan: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onRequestGmailAuthorization: () -> Unit
) {
    val invoices by viewModel.invoices.collectAsState()
    val totalMonthlyCost by viewModel.totalMonthlyCost.collectAsState()
    val verifiedSavings by viewModel.totalMonthlySavingsPotential.collectAsState()
    val session by viewModel.userSession.collectAsState()
    val isConnected by viewModel.isGmailConnected.collectAsState()
    val connectedEmail by viewModel.connectedEmail.collectAsState()
    val isSyncing by viewModel.isSyncingGmail.collectAsState()
    val syncMessage by viewModel.gmailSyncStep.collectAsState()
    var showGmailConsent by remember { mutableStateOf(false) }

    if (showGmailConsent) {
        GmailConsentDialog(
            onDismiss = { showGmailConsent = false },
            onApprove = {
                showGmailConsent = false
                onRequestGmailAuthorization()
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Security, contentDescription = null)
                    Spacer(modifier = Modifier.size(10.dp))
                    Column {
                        Text("תשתית Backend מאובטחת", fontWeight = FontWeight.Bold)
                        Text(
                            "Gmail, AI ולידים פועלים רק דרך Firebase Functions מאומתות. לפני פריסה והגדרת הסודות, הפעולות יחזירו שגיאה ולא נתוני דמה.",
                            style = MaterialTheme.typography.bodySmall
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
                        Text("Gmail לקריאת חשבוניות", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        when {
                            !session.isAuthenticated -> "יש להתחבר תחילה ל-Google/Firebase."
                            isConnected -> "מחובר בהרשאת קריאה בלבד: ${connectedEmail.ifBlank { session.email }}"
                            else -> "טרם אושרה הרשאת gmail.readonly."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "השרת מחפש נושאי חשבונית/קבלה בלבד. אין הרשאת שליחה, מחיקה או שינוי הודעות.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (syncMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(syncMessage, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    when {
                        !session.isAuthenticated -> {
                            Button(onClick = onGoogleSignIn, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Login, contentDescription = null)
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("התחבר ל-Google")
                            }
                        }
                        !isConnected -> {
                            Button(
                                onClick = { showGmailConsent = true },
                                enabled = !isSyncing,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null)
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("קרא ואשר הרשאת Gmail")
                            }
                        }
                        else -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = viewModel::triggerGmailSync,
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
                                    Text("סרוק")
                                }
                                OutlinedButton(
                                    onClick = viewModel::disconnectGmail,
                                    enabled = !isSyncing,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("נתק ובטל הרשאה")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "הוצאה חודשית מתועדת",
                    value = "₪${String.format("%.2f", totalMonthlyCost)}"
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "חיסכון שאומת",
                    value = "₪${String.format("%.2f", verifiedSavings)}"
                )
            }
        }

        item {
            Text("פעולות", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DashboardActionButton("נהל חשבוניות ולידים", Icons.Default.ReceiptLong) { onNavigateToTab(1) }
                DashboardActionButton("עיין בקטלוג ספקים", Icons.Default.Storefront) { onNavigateToTab(2) }
                DashboardActionButton("ניתוח AI דרך השרת", Icons.Default.AutoAwesome) { onNavigateToTab(3) }
                OutlinedButton(onClick = onOpenReceiptScan, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CloudOff, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("סריקת תמונות עדיין אינה זמינה")
                }
            }
        }

        item {
            Text("חשבוניות", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (invoices.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "אין חשבוניות",
                    body = "אפשר להוסיף חשבונית ידנית או לייבא לאחר חיבור Gmail. כל יבוא מסומן כלא מאומת."
                )
            }
        } else {
            items(invoices, key = { it.id }) { invoice ->
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(invoice.providerName, fontWeight = FontWeight.Bold)
                        Text("${invoice.category} • ₪${String.format("%.2f", invoice.monthlyCost)}")
                        Text(invoice.status, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun GmailConsentDialog(onDismiss: () -> Unit, onApprove: () -> Unit) {
    var accepted by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("אישור גישה מוגבלת ל-Gmail") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("האפליקציה מבקשת gmail.readonly בלבד כדי לחפש הודעות שנושאיהן קשורים לחשבוניות וקבלות.")
                Text("היא אינה יכולה לשלוח, למחוק, לערוך או לסמן הודעות. קוד ההרשאה נשלח לשרת, וה-refresh token נשמר מוצפן.")
                Text("ניתן לבטל את החיבור וההרשאה בכל עת.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                    Text("קראתי ואני מאשר/ת גישה זו במפורש.")
                }
            }
        },
        confirmButton = {
            Button(onClick = onApprove, enabled = accepted) { Text("המשך ל-Google") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ביטול") }
        }
    )
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
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
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
            Icon(Icons.Default.Info, contentDescription = null)
            Spacer(modifier = Modifier.size(10.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
