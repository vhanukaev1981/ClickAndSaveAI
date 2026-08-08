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
import androidx.compose.runtime.LaunchedEffect
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
import com.example.data.repository.BackendRepository
import com.example.data.repository.FinancialHomeResult
import com.example.data.repository.FinancialOpportunity
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
    val localTotalMonthlyCost by viewModel.totalMonthlyCost.collectAsState()
    val session by viewModel.userSession.collectAsState()
    val isConnected by viewModel.isGmailConnected.collectAsState()
    val isSyncing by viewModel.isSyncingGmail.collectAsState()
    var showGmailConsent by remember { mutableStateOf(false) }
    var financialHome by remember { mutableStateOf<FinancialHomeResult?>(null) }
    var financialHomeError by remember { mutableStateOf("") }
    val backendRepository = remember { BackendRepository() }

    LaunchedEffect(session.isAuthenticated, isConnected, invoices.size) {
        if (session.isAuthenticated && isConnected) {
            runCatching { backendRepository.getFinancialHome() }
                .onSuccess {
                    financialHome = it
                    financialHomeError = ""
                }
                .onFailure {
                    financialHomeError = it.localizedMessage ?: "הנתונים עדיין לא זמינים."
                }
        } else {
            financialHome = null
            financialHomeError = ""
        }
    }

    val verifiedOpportunities = financialHome?.opportunities
        .orEmpty()
        .filter { (it.potentialMonthlySaving ?: 0.0) > 0.0 && it.matchedOffer != null }
    val verifiedMonthlySavings = verifiedOpportunities.sumOf { it.potentialMonthlySaving ?: 0.0 }
    val verifiedAnnualSavings = verifiedOpportunities.sumOf { it.potentialAnnualSaving ?: 0.0 }
    val detectedOpportunities = financialHome?.opportunities.orEmpty()
    val observedMonthlySpend = financialHome?.context?.observedRecurringMonthlySpend
        ?.takeIf { it > 0.0 }
        ?: localTotalMonthlyCost
    val recurringServiceCount = financialHome?.context?.recurringServiceCount ?: invoices.size

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
                    text = "המצב הפיננסי שלך",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "המערכת עוקבת אחרי ההוצאות החוזרות ומחפשת עבורך אפשרויות לחסוך.",
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
                opportunities = verifiedOpportunities.size,
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
                    title = "הוצאה חודשית מזוהה",
                    value = money(observedMonthlySpend),
                    supporting = "חיובים חוזרים שנצפו"
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "שירותים שזוהו",
                    value = recurringServiceCount.toString(),
                    supporting = "נבדקים אוטומטית"
                )
            }
        }

        if (detectedOpportunities.isNotEmpty()) {
            item {
                Text(
                    "מה מצאנו עבורך",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(detectedOpportunities.take(3), key = { it.id }) { opportunity ->
                ProactiveOpportunityCard(opportunity)
            }
        } else if (isConnected) {
            item {
                EmptyStateCard(
                    title = "הבדיקה ממשיכה ברקע",
                    body = "כרגע אין הזדמנות חיסכון מאומתת. אם מחיר ישתנה או תופיע הצעה טובה ומתאימה יותר, היא תיבדק אוטומטית."
                )
            }
        }

        if (financialHomeError.isNotBlank()) {
            item {
                Text(
                    financialHomeError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Text(
                "ניהול",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                DashboardActionButton(
                    text = "החשבונות שלי",
                    subtitle = "כל החשבוניות והחיובים שהמערכת זיהתה",
                    icon = Icons.Default.ReceiptLong,
                    onClick = { onNavigateToTab(1) }
                )
                DashboardActionButton(
                    text = "החיסכון שלי",
                    subtitle = "הצעות שנבדקו מול השירותים והמחירים שלך",
                    icon = Icons.Default.Storefront,
                    onClick = { onNavigateToTab(2) }
                )
                DashboardActionButton(
                    text = "אני והעדפות",
                    subtitle = "חשבון, יעדי חיסכון, פרטיות וחיבורים",
                    icon = Icons.Default.Tune,
                    onClick = { onNavigateToTab(3) }
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
                    title = if (isConnected) "עדיין אין חשבונות להצגה" else "חיבור אחד כדי להתחיל",
                    body = if (isConnected) {
                        "כשנזהה חשבונית חדשה היא תופיע כאן ותיבדק אוטומטית להזדמנויות חיסכון."
                    } else {
                        "בחיבור הראשון נבדוק עד 6 חודשים אחורה. לאחר מכן חשבוניות חדשות נקלטות אוטומטית."
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
                                "נבדק ברקע",
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
private fun ProactiveOpportunityCard(opportunity: FinancialOpportunity) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = TechBluePrimary)
            Spacer(modifier = Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${opportunity.providerName} • ${opportunity.category}",
                    fontWeight = FontWeight.Bold
                )
                val matchedOffer = opportunity.matchedOffer
                if (matchedOffer != null && (opportunity.potentialMonthlySaving ?: 0.0) > 0.0) {
                    val effectiveMonthly = matchedOffer.effectiveMonthlyPrice ?: matchedOffer.monthlyPrice
                    Text(
                        "מצאנו ${matchedOffer.providerName} בעלות חודשית אפקטיבית של ${money(effectiveMonthly)}.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "חיסכון מאומת: ${money(opportunity.potentialMonthlySaving ?: 0.0)} בחודש • ${money(opportunity.potentialAnnualSaving ?: 0.0)} בשנה",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = TechBluePrimary
                    )
                } else {
                    val detectionText = if (opportunity.type == "COMPARE_AFTER_PRICE_INCREASE") {
                        "זוהתה עליית מחיר של ${String.format("%.1f", opportunity.percentIncrease)}%. אנחנו בודקים חלופה תואמת."
                    } else {
                        "זהו שירות חודשי חוזר. אנחנו בודקים אם קיימת חלופה טובה ומתאימה יותר."
                    }
                    Text(detectionText, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "סכום חיסכון יוצג רק לאחר שתימצא הצעה עדכנית שניתן לאמת ולהתאים לשירות שלך.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Email, contentDescription = null, tint = TechBluePrimary)
                Spacer(modifier = Modifier.size(9.dp))
                Text("חיבור אחד כדי להתחיל", fontWeight = FontWeight.Bold)
            }
            Text(
                "נשתמש בגישה לקריאה בלבד כדי לזהות חשבוניות ומסמכי חיוב. בחיבור הראשון נבדוק עד 6 חודשים אחורה, ובהמשך מסמכים חדשים ייקלטו אוטומטית.",
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
                Text(if (authenticated) "חבר את החשבון" else "התחבר כדי להתחיל")
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
                    "החיסכון שמצאנו עבורך",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                if (opportunities > 0) money(annualSavings) else "עדיין בבדיקה",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                if (opportunities > 0) {
                    "בשנה • ${money(monthlySavings)} בחודש"
                } else {
                    "נציג כאן סכום רק אחרי שנמצא ונאמת חיסכון אמיתי"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
            if (opportunities > 0) {
                Button(onClick = onOpenSavings, modifier = Modifier.fillMaxWidth()) {
                    Text("צפה ב-$opportunities הזדמנויות מאומתות")
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
        title = { Text("אישור גישה לקריאה בלבד") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("הגישה משמשת לאיתור חשבוניות וקבלות ולחילוץ פרטי החיוב הדרושים לצורך בדיקת חיסכון.")
                Text("אין אפשרות לשלוח, למחוק או לערוך הודעות. אפשר לבטל את החיבור בכל עת דרך פרטיות וחיבורים בפרופיל.")
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
