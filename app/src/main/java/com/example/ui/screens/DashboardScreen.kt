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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
    val verifiedMonthlySavings by viewModel.totalMonthlySavingsPotential.collectAsState()
    val session by viewModel.userSession.collectAsState()
    val isConnected by viewModel.isGmailConnected.collectAsState()
    val isSyncing by viewModel.isSyncingGmail.collectAsState()
    var showGmailConsent by remember { mutableStateOf(false) }

    val verifiedAnnualSavings = verifiedMonthlySavings * 12.0
    val actionableInvoices = invoices.count { it.potentialMonthlySavings > 0.0 }

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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "הכסף שלך, פשוט יותר",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "אנחנו מרכזים את החשבונות ומראים רק חיסכון שניתן לאמת.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!session.isAuthenticated || !isConnected) {
            item {
                InitialGmailOnboardingCard(
                    authenticated = session.isAuthenticated,
                    syncing = isSyncing,
                    onGoogleSignIn = onGoogleSignIn,
                    onConnectGmail = { showGmailConsent = true }
                )
            }
        }

        item {
            SavingsHeroCard(
                annualSavings = verifiedAnnualSavings,
                monthlySavings = verifiedMonthlySavings,
                opportunities = actionableInvoices,
                onOpenSavings = { onNavigateToTab(2) }
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "הוצאה חודשית",
                    value = money(totalMonthlyCost),
                    supporting = "מחשבונות שנקלטו"
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "חשבונות במעקב",
                    value = invoices.size.toString(),
                    supporting = "נבדקים לחיסכון"
                )
            }
        }

        item {
            Text(
                "מה תרצה לעשות?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                DashboardActionButton(
                    text = "החשבונות שלי",
                    subtitle = "צפה בחשבוניות ובסטטוס הבדיקה",
                    icon = Icons.Default.ReceiptLong,
                    onClick = { onNavigateToTab(1) }
                )
                DashboardActionButton(
                    text = "בדוק איפה אפשר לחסוך",
                    subtitle = "הצעות ותוכניות שייבדקו מול החשבונות שלך",
                    icon = Icons.Default.Storefront,
                    onClick = { onNavigateToTab(2) }
                )
                DashboardActionButton(
                    text = "הגדרות וחיבורים",
                    subtitle = "ניהול חשבון, Gmail והרשאות",
                    icon = Icons.Default.Tune,
                    onClick = { onNavigateToTab(4) }
                )
            }
        }

        item {
            Text(
                "פעילות אחרונה",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (invoices.isEmpty()) {
            item {
                EmptyStateCard(
                    title = if (isConnected) "עדיין אין חשבונות להצגה" else "חבר Gmail כדי להתחיל",
                    body = if (isConnected) {
                        "כשנזהה חשבונית חדשה היא תופיע כאן אוטומטית ונבדוק אם יש דרך לחסוך."
                    } else {
                        "בחיבור הראשון נבדוק עד 6 חודשים אחורה. לאחר מכן המעקב מתבצע אוטומטית."
                    }
                )
            }
        } else {
            items(invoices.take(4), key = { it.id }) { invoice ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                Icons.Default.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.padding(10.dp),
                                tint = TechBluePrimary
                            )
                        }
                        Spacer(modifier = Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(invoice.providerName, fontWeight = FontWeight.Bold)
                            Text(
                                invoice.category,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(money(invoice.monthlyCost), fontWeight = FontWeight.Bold)
                            Text(
                                if (invoice.potentialMonthlySavings > 0.0) {
                                    "חיסכון: ${money(invoice.potentialMonthlySavings)}/חודש"
                                } else {
                                    "בבדיקה"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                TextButton(
                    onClick = { onNavigateToTab(1) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("לכל החשבונות")
                }
            }
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val receiptScanCallbackKeptForApiCompatibility = onOpenReceiptScan
}

@Composable
private fun InitialGmailOnboardingCard(
    authenticated: Boolean,
    syncing: Boolean,
    onGoogleSignIn: () -> Unit,
    onConnectGmail: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Email, contentDescription = null, tint = TechBluePrimary)
                Spacer(modifier = Modifier.size(9.dp))
                Text("נתחיל מהחשבונות שלך", fontWeight = FontWeight.Bold)
            }
            Text(
                "בחיבור הראשון נבדוק עד 6 חודשים אחורה. לאחר מכן חשבוניות חדשות נקלטות אוטומטית בהרשאת קריאה בלבד.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = if (authenticated) onConnectGmail else onGoogleSignIn,
                enabled = !syncing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (authenticated) Icons.Default.Security else Icons.Default.Login,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.size(7.dp))
                Text(if (authenticated) "חבר Gmail" else "התחבר כדי להתחיל")
            }
        }
    }
}

@Composable
private fun SavingsHeroCard(
    annualSavings: Double,
    monthlySavings: Double,
    opportunities: Int,
    onOpenSavings: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = TechBluePrimary)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Savings, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    "החיסכון המאומת שלך",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                money(annualSavings),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                if (annualSavings > 0.0) {
                    "בשנה • ${money(monthlySavings)} בחודש"
                } else {
                    "בשנה • עדיין לא נמצא חיסכון שניתן לאמת"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
            if (opportunities > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(onClick = onOpenSavings, modifier = Modifier.fillMaxWidth()) {
                    Text("צפה ב-$opportunities הזדמנויות חיסכון")
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
                Text("האפליקציה מבקשת gmail.readonly בלבד כדי לאתר חשבוניות וקבלות ולחלץ מהן פרטי חיוב מינימליים.")
                Text("אין אפשרות לשלוח, למחוק או לערוך הודעות. ניתן לבטל את החיבור בכל עת דרך הפרופיל.")
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
private fun MetricCard(
    modifier: Modifier,
    title: String,
    value: String,
    supporting: String
) {
    Card(modifier = modifier, shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DashboardActionButton(
    text: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(13.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(9.dp),
                    tint = TechBluePrimary
                )
            }
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyStateCard(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun money(value: Double): String = "₪${String.format("%.2f", value)}"
