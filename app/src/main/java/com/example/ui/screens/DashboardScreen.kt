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
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.example.data.repository.FinancialHomeResult
import com.example.data.repository.FinancialRefreshReason
import com.example.data.repository.FinancialSyncState
import com.example.data.repository.GmailScanResult
import com.example.data.repository.gmailConnectionOrNull
import com.example.data.repository.latestScanOrNull
import com.example.ui.MainViewModel
import com.example.ui.components.NextBestActionCard
import com.example.ui.components.SavingsHero
import com.example.ui.components.V3HomeActivityRow
import com.example.ui.components.V3HomeIncreaseCard
import com.example.ui.components.V3MonitoringLine
import com.example.ui.components.V3Note
import com.example.ui.components.V3Panel
import com.example.ui.components.V3ScreenHeader
import com.example.ui.components.V3SectionHeader
import com.example.ui.components.V3SoftStatusCard
import com.example.ui.theme.TechBluePrimary
import com.example.ui.theme.V3PrimarySoft
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
    val firstName = session.displayName.trim().substringBefore(" ").takeIf { it.isNotBlank() }

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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            V3ScreenHeader(
                eyebrow = "CLICK & SAVE AI",
                title = firstName?.let { "שלום, $it" } ?: "הכסף שלך, במבט אחד",
                subtitle = "כבר בדקנו בשבילך מה כדאי לעשות עכשיו כדי לשלם פחות."
            )
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
                    V3MonitoringLine(
                        label = gmailSyncStep.takeIf(String::isNotBlank) ?: "מחברים ומעדכנים את התמונה שלך",
                        active = false,
                        modifier = Modifier.testTag("v3_monitoring_status")
                    )
                }
                item { HomeStatusCard("המידע הפיננסי עדיין נטען", "לא נציג אפס במקום ערך שעדיין אינו ידוע.") }
            }
            FinancialSyncState.Recovering -> {
                item {
                    V3MonitoringLine(
                        label = gmailSyncStep.takeIf(String::isNotBlank) ?: "מסדרים ומעדכנים את התמונה שלך",
                        active = false,
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
                        V3MonitoringLine(
                            label = gmailSyncStep.takeIf(String::isNotBlank) ?: "חלק מהמידע עדיין מתעדכן",
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
    val connection = syncState.gmailConnectionOrNull
    val recentInvoices = latestScan?.invoices
    val priceIncrease = home.insights.firstOrNull {
        it.type == "PRICE_INCREASE_DETECTED" && (it.monthlyIncrease ?: 0.0) > 0.0
    }

    item {
        V3MonitoringLine(
            label = when {
                isSyncing -> gmailSyncStep.takeIf(String::isNotBlank) ?: "בודקים חשבונות חדשים"
                connection?.connected == true -> "קריאה בלבד · בודקים חשבונות חדשים ברקע"
                else -> "מצב החיבור עדיין מתעדכן"
            },
            active = connection?.connected == true && !isSyncing,
            modifier = Modifier.testTag("v3_monitoring_status")
        )
    }

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
    } else {
        item {
            V3SoftStatusCard(
                "עדיין אוספים את הצעד הבא",
                "נציג המלצה רק כשיש מספיק מידע מאומת כדי להצביע על פעולה אמיתית."
            )
        }
    }

    item {
        V3SectionHeader(
            title = "לתשלום",
            actionLabel = "כל החשבונות",
            onAction = onOpenInvoices,
            modifier = Modifier.testTag("v3_open_invoices")
        )
    }
    when {
        recentInvoices == null -> item {
            V3SoftStatusCard("החשבונות עדיין מתעדכנים", "שדה שעדיין לא ידוע לא יוצג כאפס או כרשימה ריקה.")
        }
        recentInvoices.isEmpty() -> item {
            V3SoftStatusCard("עדיין לא זיהינו חשבוניות", "נמשיך לבדוק את המקורות המחוברים ונציג כאן רק חשבונות שנקלטו בפועל.")
        }
        else -> items(recentInvoices.take(3), key = { "home-bill:${it.sourceMessageId}" }) { invoice ->
            V3Panel {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = V3PrimarySoft) {
                        Icon(
                            Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = TechBluePrimary,
                            modifier = Modifier.padding(9.dp).size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(invoice.providerName.ifBlank { "ספק לא ידוע" }, fontWeight = FontWeight.Bold)
                        Text(invoice.category.ifBlank { "קטגוריה לא ידועה" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(invoice.monthlyCost.asV3Money(), fontWeight = FontWeight.Bold)
                }
                Text("מועד לתשלום: לא ידוע", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (priceIncrease != null) {
        item { V3SectionHeader(title = "שינוי בחשבונות") }
        item {
            V3HomeIncreaseCard(
                providerName = priceIncrease.providerName.ifBlank { "הספק" },
                monthlyIncreaseText = priceIncrease.monthlyIncrease!!.asV3Money(),
                percentIncreaseText = priceIncrease.percentIncrease?.let { "${String.format("%.1f", it)}%" },
                onClick = { onNavigateToTab(1) }
            )
        }
    }

    item {
        V3SectionHeader(
            title = "פעילות אחרונה",
            actionLabel = "כל הפעילות",
            onAction = { onNavigateToTab(3) }
        )
    }
    if (recentInvoices.isNullOrEmpty()) {
        item { V3SoftStatusCard("אין עדיין פעילות", "כל זיהוי וכל פעולה אמיתית יופיעו כאן.") }
    } else {
        items(recentInvoices.take(4), key = { "home-activity:${it.sourceMessageId}" }) { invoice ->
            V3HomeActivityRow(
                providerName = invoice.providerName.ifBlank { "ספק לא ידוע" },
                category = invoice.category.ifBlank { "קטגוריה לא ידועה" },
                amountText = invoice.monthlyCost.asV3Money(),
                dateText = invoice.receivedDate,
                onClick = { onNavigateToTab(3) }
            )
        }
        item {
            Text(
                "רק דברים שקרו באמת",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    item {
        V3Note(
            "הסכומים מבוססים על מסמכים שזוהו בתיבת המייל שלך, ולא על נתוני חיוב מהספק. חיסכון מוצג רק כשיש לו מקור מאומת. Click & Save לא גובה ממך תשלום ולא מבצע תשלומים."
        )
    }
}

@Composable
private fun HomeStatusCard(title: String, body: String, action: (@Composable () -> Unit)? = null) {
    V3Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = TechBluePrimary)
            Spacer(modifier = Modifier.size(8.dp))
            Text(title, fontWeight = FontWeight.Bold)
        }
        Text(body, style = MaterialTheme.typography.bodyMedium)
        action?.invoke()
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
