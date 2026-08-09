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
import androidx.compose.foundation.lazy.LazyRow
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
import com.example.ui.CustomerPresentationPolicy
import com.example.ui.FinancialUiState
import com.example.ui.FinancialUiStatePolicy
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
    var financialHomeTemporarilyUnavailable by remember { mutableStateOf(false) }
    var financialHomeRefreshKey by remember { mutableStateOf(0) }
    val backendRepository = remember { BackendRepository() }

    LaunchedEffect(session.isAuthenticated, isConnected, invoices.size, financialHomeRefreshKey) {
        if (session.isAuthenticated && isConnected) {
            runCatching { backendRepository.getFinancialHome() }
                .onSuccess {
                    financialHome = it
                    financialHomeTemporarilyUnavailable = false
                }
                .onFailure {
                    financialHomeTemporarilyUnavailable = true
                }
        } else {
            financialHome = null
            financialHomeTemporarilyUnavailable = false
        }
    }

    val allOpportunities = financialHome?.opportunities.orEmpty()
    val verifiedOpportunities = allOpportunities.filter {
        (it.potentialMonthlySaving ?: 0.0) > 0.0 && it.matchedOffer != null
    }
    val verifiedMonthlySavings = verifiedOpportunities.sumOf { it.potentialMonthlySaving ?: 0.0 }
    val verifiedAnnualSavings = verifiedOpportunities.sumOf {
        it.potentialAnnualSaving?.takeIf { annual -> annual > 0.0 }
            ?: ((it.potentialMonthlySaving ?: 0.0) * 12.0)
    }
    val observedMonthlySpend = financialHome?.context?.observedRecurringMonthlySpend
        ?.takeIf { it > 0.0 }
        ?: localTotalMonthlyCost
    val recurringServiceCount = financialHome?.context?.recurringServiceCount ?: invoices.size
    val categoryTotals = invoices
        .groupBy { it.category.ifBlank { "אחר" } }
        .mapValues { (_, items) -> items.sumOf { it.monthlyCost } }
        .toList()
        .sortedByDescending { it.second }
    val financialHomeLoading = session.isAuthenticated && isConnected && financialHome == null && !financialHomeTemporarilyUnavailable

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
                    text = "מקום אחד להבין מה יוצא בכל חודש ומה אפשר לחסוך.",
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
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_secondary_metrics"),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "הוצאה חודשית",
                    value = money(observedMonthlySpend),
                    supporting = "חיובים חוזרים שזוהו"
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "שירותים במעקב",
                    value = recurringServiceCount.toString(),
                    supporting = "נבדקים אוטומטית"
                )
            }
        }

        if (financialHomeLoading) {
            item {
                val message = FinancialUiStatePolicy.message(FinancialUiState.LOADING)
                EmptyStateCard(
                    title = message.title,
                    body = message.body,
                    testTag = "dashboard_loading_state"
                )
            }
        }

        if (financialHomeTemporarilyUnavailable && isConnected) {
            item {
                val message = FinancialUiStatePolicy.message(FinancialUiState.ERROR)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EmptyStateCard(
                        title = message.title,
                        body = message.body,
                        testTag = "dashboard_error_state"
                    )
                    TextButton(
                        onClick = {
                            financialHomeTemporarilyUnavailable = false
                            financialHomeRefreshKey += 1
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_retry_financial_home")
                    ) {
                        Text("נסה שוב")
                    }
                }
            }
        }

        if (categoryTotals.isNotEmpty()) {
            item { SectionTitle("לאן הכסף הולך") }
            item {
                LazyRow(
                    modifier = Modifier.testTag("dashboard_category_snapshot"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categoryTotals.take(6), key = { it.first }) { (category, amount) ->
                        CategorySnapshotCard(category = category, amount = amount)
                    }
                }
            }
        }

        if (allOpportunities.isNotEmpty()) {
            item { SectionTitle("מה מצאנו עבורך") }
            items(allOpportunities.take(3), key = { it.id }) { opportunity ->
                ProactiveOpportunityCard(
                    opportunity = opportunity,
                    onClick = { onNavigateToTab(2) }
                )
            }
            if (allOpportunities.size > 3) {
                item {
                    TextButton(
                        onClick = { onNavigateToTab(2) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_all_savings")
                    ) {
                        Text("לכל הזדמנויות החיסכון")
                    }
                }
            }
        } else if (isConnected && !financialHomeLoading && !financialHomeTemporarilyUnavailable) {
            item {
                val message = FinancialUiStatePolicy.message(FinancialUiState.UNDER_REVIEW)
                EmptyStateCard(
                    title = message.title,
                    body = message.body,
                    testTag = "dashboard_savings_under_review"
                )
            }
        }

        item { SectionTitle("פעילות אחרונה") }

        if (invoices.isEmpty()) {
            item {
                EmptyStateCard(
                    title = if (isConnected) "עדיין אין חשבונות להצגה" else "חיבור אחד כדי להתחיל",
                    body = if (isConnected) {
                        "כשנזהה חשבון חדש הוא יופיע כאן וייבדק אוטומטית להזדמנויות חיסכון."
                    } else {
                        "בחיבור הראשון נבדוק עד 6 חודשים אחורה. לאחר מכן מסמכים חדשים נקלטים אוטומטית."
                    },
                    testTag = "dashboard_recent_bills_empty"
                )
            }
        } else {
            items(invoices.take(4), key = { it.id }) { invoice ->
                Card(
                    onClick = { onNavigateToTab(1) },
                    modifier = Modifier.testTag("dashboard_bill_${invoice.id}"),
                    shape = RoundedCornerShape(18.dp)
                ) {
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
                                "לחודש",
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_all_bills")
                ) {
                    Text("לכל החשבונות")
                }
            }
        }

        item { SectionTitle("ניהול") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                DashboardActionButton(
                    text = "החשבונות שלי",
                    subtitle = "ההוצאות והחיובים שזוהו",
                    icon = Icons.Default.ReceiptLong,
                    testTag = "dashboard_manage_bills",
                    onClick = { onNavigateToTab(1) }
                )
                DashboardActionButton(
                    text = "החיסכון שלי",
                    subtitle = "הצעות שנבדקו מול השירותים שלך",
                    icon = Icons.Default.Storefront,
                    testTag = "dashboard_manage_savings",
                    onClick = { onNavigateToTab(2) }
                )
                DashboardActionButton(
                    text = "אני והעדפות",
                    subtitle = "יעדים, העדפות, פרטיות וחיבורים",
                    icon = Icons.Default.Tune,
                    testTag = "dashboard_manage_profile",
                    onClick = { onNavigateToTab(3) }
                )
            }
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val receiptScanCallbackKeptForApiCompatibility = onOpenReceiptScan
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun CategorySnapshotCard(category: String, amount: Double) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(category, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(money(amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "לחודש",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProactiveOpportunityCard(
    opportunity: FinancialOpportunity,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.testTag("dashboard_opportunity_${opportunity.id}"),
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
                val verifiedLabel = if (matchedOffer != null) {
                    CustomerPresentationPolicy.verifiedSavingsLabel(
                        opportunity.potentialMonthlySaving,
                        opportunity.potentialAnnualSaving
                    )
                } else {
                    null
                }
                if (verifiedLabel != null) {
                    Text(
                        verifiedLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TechBluePrimary
                    )
                    Text(
                        "הצעה שנבדקה והותאמה לשירות שלך",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val detectionText = if (opportunity.type == "COMPARE_AFTER_PRICE_INCREASE") {
                        "זוהתה עליית מחיר של ${String.format("%.1f", opportunity.percentIncrease)}%. אנחנו בודקים חלופה תואמת."
                    } else {
                        "זהו שירות חודשי חוזר. אנחנו בודקים אם קיימת חלופה טובה ומתאימה יותר."
                    }
                    Text(detectionText, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        CustomerPresentationPolicy.underReviewLabel(),
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
        modifier = Modifier.testTag("dashboard_initial_connection"),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_connect_account")
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
    val verified = opportunities > 0 && monthlySavings > 0.0
    val displayAnnual = annualSavings.takeIf { it > 0.0 } ?: (monthlySavings * 12.0)
    Card(
        onClick = onOpenSavings,
        modifier = Modifier.testTag("dashboard_savings_hero"),
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
                if (verified) money(displayAnnual) else "עדיין בבדיקה",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                if (verified) {
                    "בשנה • ${money(monthlySavings)} בחודש"
                } else {
                    "נציג כאן סכום רק אחרי שנמצא ונאמת חיסכון אמיתי"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
            if (verified) {
                Text(
                    "$opportunities הזדמנויות מאומתות • לחץ לפרטים",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary
                )
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
                    Checkbox(
                        checked = accepted,
                        onCheckedChange = { accepted = it },
                        modifier = Modifier.testTag("gmail_consent_checkbox")
                    )
                    Text("קראתי ואני מאשר/ת גישה זו במפורש.")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onApprove,
                enabled = accepted,
                modifier = Modifier.testTag("gmail_consent_continue")
            ) { Text("המשך ל-Google") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("gmail_consent_cancel")
            ) { Text("ביטול") }
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
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
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
private fun EmptyStateCard(title: String, body: String, testTag: String = "dashboard_state_card") {
    Card(
        modifier = Modifier.testTag(testTag),
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