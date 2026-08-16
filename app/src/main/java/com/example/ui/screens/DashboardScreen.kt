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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Storefront
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
import com.example.data.repository.FinancialOpportunity
import com.example.data.repository.FinancialRefreshReason
import com.example.data.repository.FinancialSyncState
import com.example.data.repository.latestScanOrNull
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
    val financialSyncState by viewModel.financialSyncState.collectAsState()
    val financialHome by viewModel.authoritativeFinancialHome.collectAsState()
    val latestScan = financialSyncState.latestScanOrNull
    val session by viewModel.userSession.collectAsState()
    val isSyncing by viewModel.isSyncingGmail.collectAsState()
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "החיסכון שלך מתחיל במה שאנחנו יודעים באמת",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Click&SaveAI מזהה חיובים חוזרים ומציגה הזדמנויות רק כשהנתונים וההצעה ניתנים לאימות.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when (val state = financialSyncState) {
            FinancialSyncState.Unauthenticated -> item {
                InitialGmailOnboardingCard(
                    authenticated = false,
                    syncing = isSyncing,
                    onGoogleSignIn = onGoogleSignIn,
                    onConnectGmail = { showGmailConsent = true }
                )
            }

            FinancialSyncState.CheckingConnection,
            FinancialSyncState.Recovering -> item {
                HomeStatusCard(
                    title = "המידע הפיננסי עדיין נטען",
                    body = "אנחנו משחזרים את המידע המאומת מהשרת. עד שהבדיקה תסתיים לא נציג אפס במקום ערך שעדיין אינו ידוע."
                )
            }

            FinancialSyncState.Disconnected -> item {
                InitialGmailOnboardingCard(
                    authenticated = session.isAuthenticated,
                    syncing = isSyncing,
                    onGoogleSignIn = onGoogleSignIn,
                    onConnectGmail = { showGmailConsent = true }
                )
            }

            is FinancialSyncState.Partial -> {
                item {
                    HomeStatusCard(
                        title = "המידע האחרון נשמר",
                        body = "חלק מהסנכרון אינו זמין כרגע. אנחנו מציגים רק מידע סמכותי שכבר אומת, ושומרים את השאר כלא ידוע."
                    )
                }
                if (financialHome != null) {
                    authoritativeHomeItems(
                        home = financialHome!!,
                        onNavigateToTab = onNavigateToTab
                    )
                }
                item {
                    Button(onClick = { viewModel.refreshFinancialSession(FinancialRefreshReason.RETRY) }) {
                        Text("נסה לסנכרן שוב")
                    }
                }
            }

            is FinancialSyncState.Failed -> item {
                HomeStatusCard(
                    title = "לא הצלחנו להשלים את הסנכרון",
                    body = "לא הומצאו נתונים חלופיים. אפשר לנסות שוב כשהחיבור זמין."
                ) {
                    Button(onClick = { viewModel.refreshFinancialSession(FinancialRefreshReason.RETRY) }) {
                        Text("נסה שוב")
                    }
                }
            }

            is FinancialSyncState.Ready -> {
                authoritativeHomeItems(
                    home = state.financialHome,
                    onNavigateToTab = onNavigateToTab
                )
            }
        }

        item {
            Text(
                "גישה מהירה",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                DashboardActionButton(
                    text = "החשבונות שלי",
                    subtitle = "החשבוניות והחיובים שהמערכת זיהתה",
                    icon = Icons.Default.ReceiptLong,
                    onClick = { onNavigateToTab(1) }
                )
                DashboardActionButton(
                    text = "הזדמנויות חיסכון",
                    subtitle = "השוואות שנבדקו מול השירותים שלך",
                    icon = Icons.Default.Storefront,
                    onClick = { onNavigateToTab(2) }
                )
                DashboardActionButton(
                    text = "פעילות וסנכרון",
                    subtitle = "מצב השחזור והפעילות שניתן לאמת",
                    icon = Icons.Outlined.CloudSync,
                    onClick = { onNavigateToTab(3) }
                )
                DashboardActionButton(
                    text = "אני והעדפות",
                    subtitle = "חשבון, פרטיות וחיבורים",
                    icon = Icons.Default.Tune,
                    onClick = { onNavigateToTab(4) }
                )
            }
        }

        val recentInvoices = latestScan?.invoices
        if (recentInvoices != null) {
            if (recentInvoices.isNotEmpty()) {
                item {
                    Text(
                        "חשבונות שזוהו לאחרונה",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(recentInvoices.take(3), key = { it.sourceMessageId }) { invoice ->
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = TechBluePrimary)
                            Spacer(modifier = Modifier.size(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(invoice.providerName, fontWeight = FontWeight.Bold)
                                Text(
                                    invoice.category,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(money(invoice.monthlyCost), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                item {
                    HomeStatusCard(
                        title = "אין כרגע חשבונות מזוהים להצגה",
                        body = "זהו מצב ריק מהסריקה הסמכותית האחרונה, לא ערך שנוצר כברירת מחדל."
                    )
                }
            }
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val receiptScanCallbackKeptForApiCompatibility = onOpenReceiptScan
}

private fun androidx.compose.foundation.lazy.LazyListScope.authoritativeHomeItems(
    home: FinancialHomeResult,
    onNavigateToTab: (Int) -> Unit
) {
    item {
        AuthoritativeSummaryCard(home)
    }

    if (home.opportunities.isNotEmpty()) {
        item {
            Text(
                "מה מצאנו עבורך",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        items(home.opportunities.take(3), key = { it.id }) { opportunity ->
            ProactiveOpportunityCard(opportunity)
        }
        item {
            TextButton(
                onClick = { onNavigateToTab(2) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("לכל הזדמנויות החיסכון")
            }
        }
    } else {
        item {
            HomeStatusCard(
                title = "אין כרגע הזדמנות חיסכון מאומתת",
                body = "המערכת בדקה את ההקשר הפיננסי הזמין. חיסכון יוצג רק לאחר שתימצא הצעה עדכנית ומתאימה שניתן לאמת."
            )
        }
    }
}

@Composable
private fun AuthoritativeSummaryCard(home: FinancialHomeResult) {
    val context = home.context
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("התמונה שזוהתה", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "חיוב חודשי חוזר שזוהה",
                    value = context.observedRecurringMonthlySpend?.let(::money) ?: "לא ידוע",
                    supporting = if (context.isCompleteHouseholdSpend) "כיסוי שסומן כמלא" else "כיסוי חלקי מהמקורות המחוברים"
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "שירותים חוזרים",
                    value = context.recurringServiceCount?.toString() ?: "לא ידוע",
                    supporting = "לפי ההקשר הסמכותי"
                )
            }
            if (context.sourceCoverage.isNotEmpty()) {
                Text(
                    text = "מקור נתונים: ${context.sourceCoverage.joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProactiveOpportunityCard(opportunity: FinancialOpportunity) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = TechBluePrimary)
            Spacer(modifier = Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("${opportunity.providerName} • ${opportunity.category}", fontWeight = FontWeight.Bold)
                val offer = opportunity.matchedOffer?.takeIf { matched ->
                    opportunity.offerVerificationState == "VERIFIED" &&
                        opportunity.offerFreshnessState == "FRESH" &&
                        opportunity.userEligibilityState == "ELIGIBLE" &&
                        matched.verificationState == "VERIFIED" &&
                        matched.freshnessState == "FRESH" &&
                        matched.eligibilityState == "ELIGIBLE"
                }
                val saving = opportunity.potentialMonthlySaving
                if (offer != null && saving != null && saving > 0.0) {
                    val newPrice = offer.effectiveMonthlyPrice ?: offer.monthlyPrice
                    Text("המחיר הנוכחי שזוהה: ${money(opportunity.currentMonthlyCost)}")
                    Text("הצעה מאומתת, עדכנית ומתאימה: ${money(newPrice)} לחודש")
                    Text(
                        "חיסכון פוטנציאלי לפי ההצעה: ${money(saving)} בחודש — לא חיסכון ממומש",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        "זוהתה אפשרות לבדיקה, אך אין עדיין סכום חיסכון מאומת, עדכני ומתאים להצגה.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeStatusCard(
    title: String,
    body: String,
    action: (@Composable () -> Unit)? = null
) {
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
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Email, contentDescription = null, tint = TechBluePrimary)
                Spacer(modifier = Modifier.size(9.dp))
                Text("חיבור מאובטח כדי להתחיל", fontWeight = FontWeight.Bold)
            }
            Text(
                "Gmail משמש לקריאה בלבד כדי לזהות מסמכי חיוב. לא נשלח מידע לספק ללא פעולה מפורשת שלך.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = if (authenticated) onConnectGmail else onGoogleSignIn,
                enabled = !syncing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (authenticated) "חבר Gmail לקריאה בלבד" else "התחבר עם Google")
            }
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    supporting: String
) {
    Card(modifier = modifier, shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(supporting, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = TechBluePrimary)
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GmailConsentDialog(
    onDismiss: () -> Unit,
    onApprove: () -> Unit
) {
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
        confirmButton = {
            Button(onClick = onApprove, enabled = accepted) { Text("המשך ל-Google") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}

private fun money(value: Double): String = "₪${String.format("%,.0f", value)}"
