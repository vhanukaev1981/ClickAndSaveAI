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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.example.data.repository.FinancialHomeResult
import com.example.data.repository.FinancialRefreshReason
import com.example.data.repository.FinancialSyncState
import com.example.data.repository.GmailScanResult
import com.example.data.repository.gmailConnectionOrNull
import com.example.data.repository.latestScanOrNull
import com.example.ui.MainViewModel
import com.example.ui.components.FinancialSnapshot
import com.example.ui.components.MonitoringStatus
import com.example.ui.components.NextBestActionCard
import com.example.ui.components.SavingsHero
import com.example.ui.components.V3SectionHeader
import com.example.ui.components.V3SoftStatusCard
import com.example.ui.theme.TechBluePrimary
import com.example.ui.v3.asV3Money
import com.example.ui.v3.toV3SavingsSummary

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToTab: (Int) -> Unit,
    onOpenInvoices: () -> Unit = {},
    onOpenReceiptScan: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onRequestGmailAuthorization: () -> Unit
) {
    val financialSyncState by viewModel.financialSyncState.collectAsState()
    val financialHome by viewModel.authoritativeFinancialHome.collectAsState()
    val latestScan = financialSyncState.latestScanOrNull
    val session by viewModel.userSession.collectAsState()
    val isSyncing by viewModel.isSyncingGmail.collectAsState()
    val gmailSyncStep by viewModel.gmailSyncStep.collectAsState()
    val onboardingStep by viewModel.onboardingStep.collectAsState()
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
        modifier = Modifier.fillMaxSize().testTag("dashboard_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("הכסף שלך, במבט אחד", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "מה כבר חסכת, מה עוד אפשר לחסוך ומה כדאי לעשות עכשיו.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when (val state = financialSyncState) {
            FinancialSyncState.Unauthenticated -> item {
                V3OnboardingContent(
                    step = onboardingStep,
                    authenticated = false,
                    onNext = viewModel::nextOnboardingStep,
                    onGoogleSignIn = onGoogleSignIn,
                    onConnectGmail = { showGmailConsent = true }
                )
            }
            FinancialSyncState.CheckingConnection -> {
                item {
                    MonitoringStatus(
                        title = "מחברים את החשבון",
                        subtitle = gmailSyncStep.takeIf(String::isNotBlank) ?: "אנחנו מעדכנים את התמונה שלך",
                        active = true,
                        modifier = Modifier.testTag("v3_monitoring_status")
                    )
                }
                item { HomeStatusCard("המידע הפיננסי עדיין נטען", "לא נציג אפס במקום ערך שעדיין אינו ידוע.") }
            }
            FinancialSyncState.Recovering -> {
                item {
                    MonitoringStatus(
                        title = "מסדרים את התמונה שלך",
                        subtitle = gmailSyncStep.takeIf(String::isNotBlank) ?: "אנחנו מעדכנים את התמונה שלך",
                        active = true,
                        modifier = Modifier.testTag("v3_monitoring_status")
                    )
                }
                item { HomeStatusCard("המידע הפיננסי עדיין נטען", "לא נציג אפס במקום ערך שעדיין אינו ידוע.") }
            }
            FinancialSyncState.Disconnected -> item {
                V3OnboardingContent(
                    step = 2,
                    authenticated = session.isAuthenticated,
                    onNext = viewModel::nextOnboardingStep,
                    onGoogleSignIn = onGoogleSignIn,
                    onConnectGmail = { showGmailConsent = true }
                )
            }
            is FinancialSyncState.Partial -> {
                item { V3SoftStatusCard("המידע האחרון נשמר", "חלק מהמידע עדיין מתעדכן. אנחנו ממשיכים להציג רק את מה שכבר אומת.") }
                val home = financialHome
                if (home != null) {
                    authoritativeHomeItems(home, latestScan, state, isSyncing, gmailSyncStep, onNavigateToTab, onOpenInvoices)
                } else {
                    item {
                        MonitoringStatus(
                            title = "חלק מהמידע עדיין מתעדכן",
                            subtitle = gmailSyncStep.takeIf(String::isNotBlank),
                            active = state.gmailConnectionOrNull?.connected == true,
                            modifier = Modifier.testTag("v3_monitoring_status")
                        )
                    }
                }
                item {
                    TextButton(onClick = { viewModel.refreshFinancialSession(FinancialRefreshReason.RETRY) }, modifier = Modifier.fillMaxWidth()) {
                        Text("נסה לסנכרן שוב")
                    }
                }
            }
            is FinancialSyncState.Failed -> item {
                HomeStatusCard("לא הצלחנו להשלים את העדכון", "לא נציג מידע משוער במקום מידע שחסר. אפשר לנסות שוב כשהחיבור זמין.") {
                    Button(onClick = { viewModel.refreshFinancialSession(FinancialRefreshReason.RETRY) }) { Text("נסה שוב") }
                }
            }
            is FinancialSyncState.Ready -> {
                authoritativeHomeItems(state.financialHome, state.latestScan, state, isSyncing, gmailSyncStep, onNavigateToTab, onOpenInvoices)
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeShortcut("פעילות", Icons.Outlined.CloudSync, { onNavigateToTab(3) }, Modifier.weight(1f))
                HomeShortcut("פרופיל", Icons.Default.Tune, { onNavigateToTab(4) }, Modifier.weight(1f))
            }
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val receiptScanCallbackKeptForApiCompatibility = onOpenReceiptScan
}

private fun androidx.compose.foundation.lazy.LazyListScope.authoritativeHomeItems(
    home: FinancialHomeResult,
    latestScan: GmailScanResult?,
    syncState: FinancialSyncState,
    isSyncing: Boolean,
    gmailSyncStep: String,
    onNavigateToTab: (Int) -> Unit,
    onOpenInvoices: () -> Unit
) {
    val summary = home.toV3SavingsSummary()
    val nextBest = summary.nextBestOpportunityId?.let { id -> home.opportunities.firstOrNull { it.id == id } }

    item {
        SavingsHero(
            realizedMonthly = summary.realizedMonthly,
            realizedAnnual = summary.realizedAnnual,
            potentialMonthly = summary.potentialMonthly,
            potentialAnnual = summary.potentialAnnual,
            realizedKnownZero = summary.realizedKnownZero,
            modifier = Modifier.testTag("v3_savings_hero")
        )
    }

    if (nextBest != null && nextBest.potentialMonthlySaving != null) {
        item {
            NextBestActionCard(
                providerName = nextBest.providerName,
                category = nextBest.category,
                potentialMonthlyText = nextBest.potentialMonthlySaving.asV3Money(),
                onClick = { onNavigateToTab(1) },
                modifier = Modifier.testTag("v3_next_best_action")
            )
        }
    }

    item {
        val connection = syncState.gmailConnectionOrNull
        MonitoringStatus(
            title = when {
                isSyncing -> "אנחנו בודקים את החשבונות שלך"
                connection?.connected == true -> "הניטור פעיל"
                else -> "מצב החיבור עדיין מתעדכן"
            },
            subtitle = gmailSyncStep.takeIf(String::isNotBlank)
                ?: connection?.email?.takeIf(String::isNotBlank)?.let { "Gmail מחובר לקריאה בלבד" },
            active = connection?.connected == true && !isSyncing,
            modifier = Modifier.testTag("v3_monitoring_status")
        )
    }

    item { V3SectionHeader(title = "התמונה שלך") }
    item {
        FinancialSnapshot(
            recurringSpendText = home.context.observedRecurringMonthlySpend?.asV3Money() ?: "לא ידוע",
            recurringServicesText = home.context.recurringServiceCount?.toString() ?: "לא ידוע",
            invoicesText = latestScan?.invoices?.size?.toString() ?: "לא ידוע"
        )
    }

    val recentInvoices = latestScan?.invoices
    if (recentInvoices != null && recentInvoices.isNotEmpty()) {
        item {
            V3SectionHeader(
                title = "זוהה לאחרונה",
                actionLabel = "כל החשבונות",
                onAction = onOpenInvoices,
                modifier = Modifier.testTag("v3_open_invoices")
            )
        }
        items(recentInvoices.take(3), key = { it.sourceMessageId }) { invoice ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = TechBluePrimary)
                    Spacer(modifier = Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(invoice.providerName, fontWeight = FontWeight.Bold)
                        Text(invoice.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(invoice.monthlyCost.asV3Money(), fontWeight = FontWeight.Bold)
                }
            }
        }
    } else if (recentInvoices != null) {
        item {
            V3SectionHeader(
                title = "חשבונות",
                actionLabel = "כל החשבונות",
                onAction = onOpenInvoices,
                modifier = Modifier.testTag("v3_open_invoices")
            )
        }
        item { V3SoftStatusCard("עדיין לא זיהינו חשבוניות", "נמשיך לבדוק את המקורות המחוברים ונציג כאן רק חשבונות שנקלטו בפועל.") }
    } else {
        item {
            TextButton(onClick = onOpenInvoices, modifier = Modifier.fillMaxWidth().testTag("v3_open_invoices")) {
                Text("כל החשבונות")
            }
        }
    }
}

@Composable
private fun HomeShortcut(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = TechBluePrimary)
            Text(title, modifier = Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HomeStatusCard(title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(title, fontWeight = FontWeight.Bold)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
            action?.invoke()
        }
    }
}

@Composable
private fun GmailConsentDialog(onDismiss: () -> Unit, onApprove: () -> Unit) {
    var accepted by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("חיבור Gmail לקריאה בלבד") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ההרשאה משמשת לזיהוי חשבוניות וחיובים. Click&SaveAI אינה שולחת הודעות ואינה משנה מידע ב-Gmail.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                    Text("אני מאשר/ת גישת קריאה בלבד")
                }
            }
        },
        confirmButton = { Button(onClick = onApprove, enabled = accepted) { Text("המשך ל-Google") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}
