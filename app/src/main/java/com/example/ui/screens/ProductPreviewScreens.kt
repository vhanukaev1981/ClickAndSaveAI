package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.InvoiceItem
import com.example.data.repository.BackendRepository
import com.example.data.repository.FinancialHomeResult
import com.example.data.repository.FinancialOpportunity
import com.example.data.repository.OpportunityActionRepository
import com.example.ui.MainViewModel
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.DividerLight
import com.example.ui.theme.EmeraldSavings
import com.example.ui.theme.FinancialDesignTokens
import com.example.ui.theme.SavingsSurface
import com.example.ui.theme.TechBluePrimary
import kotlinx.coroutines.launch

private const val PRODUCT_PROVIDER_REQUEST = "IN_APP_PROVIDER_REQUEST"

@Composable
fun ProductDashboardScreen(
    viewModel: MainViewModel,
    onNavigateToTab: (Int) -> Unit,
    onGoogleSignIn: () -> Unit,
    onRequestGmailAuthorization: () -> Unit
) {
    val session by viewModel.userSession.collectAsState()
    val connected by viewModel.isGmailConnected.collectAsState()
    val syncing by viewModel.isSyncingGmail.collectAsState()
    var showConsent by remember { mutableStateOf(false) }
    val state = rememberFinancialHome(session.isAuthenticated, connected)

    if (showConsent) {
        ProductGmailConsentDialog(
            onDismiss = { showConsent = false },
            onApprove = {
                showConsent = false
                onRequestGmailAuthorization()
            }
        )
    }

    val opportunities = state.result?.opportunities.orEmpty()
    val verified = opportunities.filter(::isVerifiedOpportunity)
    val verifiedMonthly = verified.sumOf { it.potentialMonthlySaving ?: 0.0 }
    val annualValues = verified.mapNotNull { it.potentialAnnualSaving?.takeIf(::positiveFinite) }
    val verifiedAnnual = annualValues.takeIf { it.size == verified.size && verified.isNotEmpty() }?.sum()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("product_dashboard_screen"),
        contentPadding = productScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(FinancialDesignTokens.sectionSpacing)
    ) {
        item {
            ProductScreenIntro(
                title = "החיסכון שלך, בלי לנחש",
                subtitle = "אנחנו בודקים שירותים חוזרים ומציגים חיסכון רק אחרי אימות."
            )
        }

        if (!session.isAuthenticated || !connected) {
            item {
                ProductConnectionCard(
                    authenticated = session.isAuthenticated,
                    syncing = syncing,
                    onSignIn = onGoogleSignIn,
                    onConnect = { showConsent = true }
                )
            }
        }

        item {
            ProductSavingsHero(
                monthlySaving = verifiedMonthly.takeIf { verified.isNotEmpty() && positiveFinite(it) },
                annualSaving = verifiedAnnual,
                count = verified.size,
                loading = state.loading,
                onOpenSavings = { onNavigateToTab(2) }
            )
        }

        state.result?.context?.let { context ->
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(FinancialDesignTokens.cardSpacing)
                ) {
                    ProductMetricCard(
                        modifier = Modifier.weight(1f),
                        value = context.recurringServiceCount.toString(),
                        label = "שירותים חוזרים"
                    )
                    ProductMetricCard(
                        modifier = Modifier.weight(1f),
                        value = context.observedRecurringMonthlySpend.takeIf(::positiveFinite)?.let(::money) ?: "—",
                        label = "חיוב חודשי שנצפה"
                    )
                }
            }
        }

        when {
            state.error -> item {
                ProductMessageCard(
                    title = "לא הצלחנו לעדכן כרגע",
                    body = "לא נציג נתונים חלקיים כאילו הם עדכניים.",
                    testTag = "product_dashboard_error"
                )
            }
            state.loading -> item {
                ProductMessageCard(
                    title = "בודקים את הנתונים שלך",
                    body = "החיווי מופיע רק בזמן בקשה אמיתית למערכת.",
                    testTag = "product_dashboard_loading",
                    showProgress = true
                )
            }
        }

        if (connected && !state.loading && !state.error) {
            item {
                ProductSectionTitle(
                    title = "הזדמנויות חיסכון",
                    actionLabel = if (opportunities.isNotEmpty()) "הצג הכל" else null,
                    onAction = if (opportunities.isNotEmpty()) ({ onNavigateToTab(2) }) else null
                )
            }

            if (opportunities.isEmpty()) {
                item {
                    ProductEmptyStateCard(
                        icon = Icons.Default.Savings,
                        title = "אנחנו עדיין בודקים עבורך",
                        body = "כשנמצא חלופה תואמת ונאמת את המחיר והתנאים היא תופיע כאן.",
                        detail = state.result?.context?.recurringServiceCount
                            ?.takeIf { it > 0 }
                            ?.let { "נבדקים $it שירותים חוזרים שזוהו." },
                        testTag = "product_dashboard_under_review"
                    )
                }
            } else {
                items(opportunities.take(3), key = { it.id }) { opportunity ->
                    ProductOpportunityPreviewCard(
                        opportunity = opportunity,
                        onClick = { onNavigateToTab(2) }
                    )
                }
            }
        }

        item {
            ProductTruthStrip(
                title = "אתה נשאר בשליטה",
                body = "אנחנו לא מחליפים ספק ולא משלמים בשמך. העברת פרטים לספק דורשת אישור מפורש.",
                testTag = "product_dashboard_truth"
            )
        }
    }
}

@Composable
fun ProductBillsScreen(viewModel: MainViewModel) {
    val invoices by viewModel.invoices.collectAsState()
    var selectedInvoice by remember { mutableStateOf<InvoiceItem?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("product_bills_screen"),
        contentPadding = productScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(FinancialDesignTokens.sectionSpacing)
    ) {
        item {
            ProductScreenIntro(
                title = "חשבונות שזוהו",
                subtitle = "כל חשבון שזוהה מוצג כאן כבסיס לבדיקה ולהשוואה."
            )
        }

        if (invoices.isEmpty()) {
            item {
                ProductEmptyStateCard(
                    icon = Icons.Default.ReceiptLong,
                    title = "עדיין אין חשבונות להצגה",
                    body = "לאחר חיבור מקור המסמכים, חשבונות שנזהה יופיעו כאן.",
                    testTag = "product_bills_empty"
                )
            }
        } else {
            item { ProductSectionTitle(title = "החשבונות שלך") }
            items(invoices, key = { it.id }) { invoice ->
                ProductBillCard(
                    invoice = invoice,
                    onView = { selectedInvoice = invoice }
                )
            }
        }

        item {
            ProductTruthStrip(
                title = "תשלום נשאר אצל הספק",
                body = "כפתור תשלום יוצג רק עם כתובת תשלום רשמית של הספק שניתן לאמת. Click&SaveAI אינה שומרת כרטיסי אשראי ואינה סולקת תשלומים.",
                icon = Icons.Default.Payments,
                testTag = "product_bills_payment_truth"
            )
        }
    }

    selectedInvoice?.let { invoice ->
        ProductBillDetailsDialog(
            invoice = invoice,
            onDismiss = { selectedInvoice = null }
        )
    }
}

@Composable
fun ProductSavingsScreen(viewModel: MainViewModel) {
    val session by viewModel.userSession.collectAsState()
    val connected by viewModel.isGmailConnected.collectAsState()
    val state = rememberFinancialHome(session.isAuthenticated, connected)
    val actionRepository = remember { OpportunityActionRepository() }
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<FinancialOpportunity?>(null) }
    var actionStarting by remember { mutableStateOf(false) }
    var actionSubmitting by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("product_savings_screen"),
        contentPadding = productScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(FinancialDesignTokens.sectionSpacing)
    ) {
        item {
            ProductScreenIntro(
                title = "הזדמנויות החיסכון שלך",
                subtitle = "מחיר נוכחי מול חלופה שנבדקה — רק כשיש מספיק ראיות להשוואה."
            )
        }

        when {
            !session.isAuthenticated || !connected -> item {
                ProductEmptyStateCard(
                    icon = Icons.Default.Email,
                    title = "צריך לחבר מקור מסמכים",
                    body = "החיבור מתבצע ממסך הבית ובקריאה בלבד.",
                    testTag = "product_savings_requires_connection"
                )
            }
            state.loading -> item {
                ProductMessageCard(
                    title = "בודקים הזדמנויות",
                    body = "החיווי פעיל רק בזמן בקשה אמיתית למערכת.",
                    testTag = "product_savings_loading",
                    showProgress = true
                )
            }
            state.error -> item {
                ProductMessageCard(
                    title = "לא הצלחנו לעדכן כרגע",
                    body = "לא נציג הצעה ישנה כאילו נבדקה עכשיו.",
                    testTag = "product_savings_error"
                )
            }
            state.result?.opportunities.orEmpty().isEmpty() -> {
                item {
                    ProductEmptyStateCard(
                        icon = Icons.Default.Savings,
                        title = "אין עדיין חיסכון מאומת",
                        body = "אנחנו עדיין בודקים עבורך חלופות תואמות. חיסכון יוצג כאן רק אחרי אימות מחיר ותנאים.",
                        detail = state.result?.context?.recurringServiceCount
                            ?.takeIf { it > 0 }
                            ?.let { "כרגע מוכרים למערכת $it שירותים חוזרים לבדיקה." },
                        testTag = "product_savings_under_review"
                    )
                }
                item {
                    ProductTruthStrip(
                        title = "חיסכון מאומת בלבד",
                        body = "לא נציג ₪0 כדי לרמוז שבדיקה הסתיימה, ולא נייצר הצעה או סכום שאין להם מקור מאומת.",
                        testTag = "product_savings_empty_truth"
                    )
                }
            }
            else -> {
                item { ProductSectionTitle(title = "הצעות שנבדקו") }
                items(state.result?.opportunities.orEmpty(), key = { it.id }) { opportunity ->
                    ProductSavingsOpportunityCard(
                        opportunity = opportunity,
                        enabled = !actionStarting && !actionSubmitting,
                        onAccept = {
                            val offerId = opportunity.matchedOffer?.offerId.orEmpty()
                            if (offerId.isBlank() || opportunity.actionMode != PRODUCT_PROVIDER_REQUEST) return@ProductSavingsOpportunityCard
                            actionStarting = true
                            actionError = false
                            actionMessage = null
                            scope.launch {
                                runCatching {
                                    actionRepository.recordSavingsActionStarted(opportunity.id, offerId)
                                }.onSuccess {
                                    selected = opportunity
                                }.onFailure {
                                    actionError = true
                                }
                                actionStarting = false
                            }
                        }
                    )
                }
            }
        }

        if (actionStarting) {
            item {
                ProductMessageCard(
                    title = "מאמתים את ההצעה שבחרת",
                    body = "לא נפתח אישור העברת פרטים לפני בדיקה של ההצעה המדויקת.",
                    testTag = "product_savings_action_starting",
                    showProgress = true
                )
            }
        }
        if (actionSubmitting) {
            item {
                ProductMessageCard(
                    title = "רושמים את האישור שלך",
                    body = "נשלחים רק פרטי הקשר וההצעה שאישרת. אין כאן אחוזי התקדמות מדומים.",
                    testTag = "product_savings_action_submitting",
                    showProgress = true
                )
            }
        }
        actionMessage?.let { message ->
            item {
                ProductMessageCard(
                    title = "האישור נקלט",
                    body = message,
                    testTag = "product_savings_action_success",
                    success = true
                )
            }
        }
        if (actionError) {
            item {
                ProductMessageCard(
                    title = "הפעולה לא הושלמה",
                    body = "לא העברנו פרטים ולא שינינו שירות. אפשר לנסות שוב לאחר רענון ההצעה.",
                    testTag = "product_savings_action_error"
                )
            }
        }
    }

    selected?.let { opportunity ->
        ProductProviderConsentDialog(
            opportunity = opportunity,
            defaultName = session.displayName,
            defaultEmail = session.email,
            onDismiss = { selected = null },
            onSubmit = { name, phone, email ->
                if (actionSubmitting) return@ProductProviderConsentDialog
                val offerId = opportunity.matchedOffer?.offerId.orEmpty()
                if (offerId.isBlank()) {
                    selected = null
                    actionError = true
                    return@ProductProviderConsentDialog
                }
                selected = null
                actionSubmitting = true
                actionError = false
                actionMessage = null
                scope.launch {
                    runCatching {
                        actionRepository.acceptSavingsOpportunity(
                            opportunityId = opportunity.id,
                            expectedOfferId = offerId,
                            contactName = name,
                            phone = phone,
                            contactEmail = email
                        )
                    }.onSuccess {
                        actionMessage = "אישרת להעביר את פרטי הקשר לספק עבור ההצעה שבחרת. הבקשה נקלטה. Click&SaveAI לא ביצעה מעבר שירות. סטטוס 'הפרטים הועברו לספק' יוצג רק לאחר שתהיה למערכת הוכחת מסירה בפועל."
                    }.onFailure {
                        actionError = true
                    }
                    actionSubmitting = false
                }
            }
        )
    }
}

@Composable
fun ProductMeScreen(
    viewModel: MainViewModel,
    onGoogleSignIn: () -> Unit,
    onRequestGmailAuthorization: () -> Unit
) {
    val session by viewModel.userSession.collectAsState()
    val connected by viewModel.isGmailConnected.collectAsState()
    val connectedEmail by viewModel.connectedEmail.collectAsState()
    val syncing by viewModel.isSyncingGmail.collectAsState()
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var showConsent by remember { mutableStateOf(false) }

    if (showConsent) {
        ProductGmailConsentDialog(
            onDismiss = { showConsent = false },
            onApprove = {
                showConsent = false
                onRequestGmailAuthorization()
            }
        )
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("להתנתק מהחשבון?") },
            text = { Text("ההתנתקות תסיים את ההפעלה במכשיר הזה.") },
            confirmButton = {
                Button(onClick = {
                    confirmSignOut = false
                    viewModel.signOut()
                }) { Text("התנתק") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("ביטול") } }
        )
    }
    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("לבטל את חיבור המסמכים?") },
            text = { Text("מסמכים חדשים לא ייקלטו עד לחיבור מחדש.") },
            confirmButton = {
                Button(onClick = {
                    confirmDisconnect = false
                    viewModel.disconnectGmail()
                }) { Text("בטל חיבור") }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("השאר מחובר") } }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("product_me_screen"),
        contentPadding = productScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(FinancialDesignTokens.sectionSpacing)
    ) {
        item {
            ProductScreenIntro(
                title = "אני",
                subtitle = "החשבון, החיבורים והפרטיות שלך."
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(FinancialDesignTokens.cardRadius),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, DividerLight),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(FinancialDesignTokens.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(FinancialDesignTokens.cardSpacing)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = TechBluePrimary)
                        Spacer(Modifier.size(FinancialDesignTokens.cardSpacing))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("החשבון שלי", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BrandNavy)
                            if (session.isAuthenticated) {
                                Text(
                                    session.displayName.ifBlank { "משתמש מחובר" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(session.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text("עדיין לא מחובר", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (session.isAuthenticated) {
                        TextButton(
                            onClick = { confirmSignOut = true },
                            modifier = Modifier.testTag("product_sign_out")
                        ) { Text("התנתק") }
                    } else {
                        Button(
                            onClick = onGoogleSignIn,
                            modifier = Modifier.fillMaxWidth().testTag("product_sign_in")
                        ) {
                            Icon(Icons.Default.Login, contentDescription = null)
                            Spacer(Modifier.size(FinancialDesignTokens.compactSpacing))
                            Text("התחבר עם Google")
                        }
                    }
                }
            }
        }

        item { ProductSectionTitle(title = "חיבורים") }

        item {
            ProductSettingsRow(
                icon = Icons.Default.Email,
                title = "מקור מסמכים",
                subtitle = if (connected) {
                    "קריאה בלבד${connectedEmail.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""}"
                } else {
                    "לא מחובר • קריאה בלבד"
                },
                actionLabel = when {
                    session.isAuthenticated && !connected -> "חבר"
                    connected -> "בטל חיבור"
                    else -> null
                },
                onAction = when {
                    session.isAuthenticated && !connected -> ({ showConsent = true })
                    connected -> ({ confirmDisconnect = true })
                    else -> null
                },
                enabled = !syncing,
                testTag = "product_document_source"
            )
        }

        item {
            ProductTruthStrip(
                title = "פרטיות לפני פעולה",
                body = "Click&SaveAI קוראת רק מידע שנדרש לזיהוי חשבונות. העברת פרטים לספק דורשת אישור מפורש; מעבר שירות או תשלום אינם מתבצעים אצלנו.",
                testTag = "product_me_privacy"
            )
        }
    }
}

private data class ProductFinancialHomeState(
    val result: FinancialHomeResult? = null,
    val loading: Boolean = false,
    val error: Boolean = false
)

@Composable
private fun rememberFinancialHome(authenticated: Boolean, connected: Boolean): ProductFinancialHomeState {
    var result by remember { mutableStateOf<FinancialHomeResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val repository = remember { BackendRepository() }

    LaunchedEffect(authenticated, connected) {
        if (!authenticated || !connected) {
            result = null
            loading = false
            error = false
            return@LaunchedEffect
        }
        loading = true
        error = false
        runCatching { repository.getFinancialHome() }
            .onSuccess { result = it }
            .onFailure {
                result = null
                error = true
            }
        loading = false
    }
    return ProductFinancialHomeState(result = result, loading = loading, error = error)
}

@Composable
private fun ProductScreenIntro(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(FinancialDesignTokens.compactSpacing)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = BrandNavy
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProductSectionTitle(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = BrandNavy
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun ProductEmptyStateCard(
    icon: ImageVector,
    title: String,
    body: String,
    testTag: String,
    detail: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        shape = RoundedCornerShape(FinancialDesignTokens.cardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, DividerLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(FinancialDesignTokens.cardPadding),
            verticalAlignment = Alignment.Top
        ) {
            Icon(icon, contentDescription = null, tint = TechBluePrimary)
            Spacer(Modifier.size(FinancialDesignTokens.cardSpacing))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(FinancialDesignTokens.compactSpacing)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BrandNavy)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                detail?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = TechBluePrimary)
                }
            }
        }
    }
}

@Composable
private fun ProductTruthStrip(
    title: String,
    body: String,
    testTag: String,
    icon: ImageVector = Icons.Default.Security
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        shape = RoundedCornerShape(FinancialDesignTokens.compactCardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, DividerLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(FinancialDesignTokens.compactCardPadding),
            verticalAlignment = Alignment.Top
        ) {
            Icon(icon, contentDescription = null, tint = TechBluePrimary)
            Spacer(Modifier.size(FinancialDesignTokens.cardSpacing))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(FinancialDesignTokens.compactSpacing)
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = BrandNavy)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProductSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    enabled: Boolean,
    testTag: String
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        shape = RoundedCornerShape(FinancialDesignTokens.cardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, DividerLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(FinancialDesignTokens.compactCardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = TechBluePrimary)
            Spacer(Modifier.size(FinancialDesignTokens.cardSpacing))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = BrandNavy)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction, enabled = enabled) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun ProductSavingsHero(
    monthlySaving: Double?,
    annualSaving: Double?,
    count: Int,
    loading: Boolean,
    onOpenSavings: () -> Unit
) {
    val verified = monthlySaving?.takeIf(::positiveFinite)
    Card(
        onClick = onOpenSavings,
        modifier = Modifier.testTag("product_savings_hero"),
        shape = RoundedCornerShape(FinancialDesignTokens.heroRadius),
        colors = CardDefaults.cardColors(containerColor = SavingsSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(FinancialDesignTokens.heroPadding),
            verticalArrangement = Arrangement.spacedBy(FinancialDesignTokens.cardSpacing)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Savings, contentDescription = null, tint = EmeraldSavings)
                Spacer(Modifier.size(FinancialDesignTokens.cardSpacing))
                Text("חיסכון מאומת שמצאנו", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BrandNavy)
            }
            when {
                verified != null -> {
                    Text(
                        money(verified),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldSavings
                    )
                    Text(
                        annualSaving?.takeIf(::positiveFinite)?.let { "בחודש • ${money(it)} בשנה" }
                            ?: "בחודש • חיסכון שנתי יוצג רק לאחר אימות",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("$count הזדמנויות מאומתות", style = MaterialTheme.typography.labelMedium, color = BrandNavy)
                }
                loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(FinancialDesignTokens.cardSpacing))
                        Text("בודקים עבורך עכשיו", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = BrandNavy)
                    }
                }
                else -> {
                    Text("עדיין בודקים עבורך", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = BrandNavy)
                    Text(
                        "לא נציג ₪0 ולא ננחש סכום. חיסכון יופיע רק כשיש הצעה תואמת שנבדקה.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductMetricCard(modifier: Modifier, value: String, label: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(FinancialDesignTokens.compactCardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, DividerLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(FinancialDesignTokens.compactCardPadding),
            verticalArrangement = Arrangement.spacedBy(FinancialDesignTokens.compactSpacing)
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = BrandNavy)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProductOpportunityPreviewCard(opportunity: FinancialOpportunity, onClick: () -> Unit) {
    val matched = opportunity.matchedOffer
    val saving = opportunity.potentialMonthlySaving?.takeIf(::positiveFinite)
    Card(
        onClick = onClick,
        modifier = Modifier.testTag("product_opportunity_${opportunity.id}"),
        shape = RoundedCornerShape(FinancialDesignTokens.cardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, DividerLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(FinancialDesignTokens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(FinancialDesignTokens.cardSpacing)
        ) {
            Text("${opportunity.providerName} • ${opportunity.category}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = BrandNavy)
            if (matched != null && saving != null) {
                val offerPrice = matched.effectiveMonthlyPrice ?: matched.monthlyPrice
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("היום", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(money(opportunity.currentMonthlyCost), fontWeight = FontWeight.SemiBold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("הצעה שנבדקה", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(money(offerPrice), fontWeight = FontWeight.SemiBold)
                    }
                }
                Text("חיסכון ${money(saving)} בחודש", color = EmeraldSavings, fontWeight = FontWeight.Bold)
            } else {
                Text("זיהינו את השירות. חלופה ומחיר יוצגו רק אחרי אימות.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProductBillCard(invoice: InvoiceItem, onView: () -> Unit) {
    Card(
        modifier = Modifier.testTag("product_bill_${invoice.id}"),
        shape = RoundedCornerShape(FinancialDesignTokens.cardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, DividerLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(FinancialDesignTokens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(FinancialDesignTokens.compactSpacing)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(invoice.providerName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BrandNavy)
                    Text(invoice.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(money(invoice.monthlyCost), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BrandNavy)
            }
            invoice.billDate.takeIf { it.isNotBlank() }?.let {
                Text("תאריך חשבון: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(
                onClick = onView,
                modifier = Modifier.testTag("product_view_bill_${invoice.id}")
            ) {
                Text("צפייה בחשבון")
            }
        }
    }
}

@Composable
private fun ProductBillDetailsDialog(invoice: InvoiceItem, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("פרטי החשבון") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FinancialDesignTokens.cardSpacing)) {
                Text(invoice.providerName, fontWeight = FontWeight.Bold, color = BrandNavy)
                Text("שירות: ${invoice.category}")
                Text("סכום: ${money(invoice.monthlyCost)}")
                invoice.billDate.takeIf { it.isNotBlank() }?.let { Text("תאריך חשבון: $it") }
                invoice.accountNumber.takeIf { it.isNotBlank() }?.let { Text("מספר חשבון: $it") }
                HorizontalDivider(color = DividerLight)
                Text(
                    "אלה הפרטים שנקלטו בחשבון. תשלום אצל הספק יוצע רק אם תהיה כתובת רשמית ומאומתת.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("סגור") } }
    )
}

@Composable
private fun ProductSavingsOpportunityCard(
    opportunity: FinancialOpportunity,
    enabled: Boolean,
    onAccept: () -> Unit
) {
    val matched = opportunity.matchedOffer
    val saving = opportunity.potentialMonthlySaving?.takeIf(::positiveFinite)
    val verified = matched != null && saving != null
    Card(
        modifier = Modifier.testTag("product_savings_opportunity_${opportunity.id}"),
        shape = RoundedCornerShape(FinancialDesignTokens.cardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, DividerLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(FinancialDesignTokens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(FinancialDesignTokens.cardSpacing)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (verified) Icons.Default.Verified else Icons.Default.Savings,
                    contentDescription = null,
                    tint = if (verified) EmeraldSavings else TechBluePrimary
                )
                Spacer(Modifier.size(FinancialDesignTokens.cardSpacing))
                Column(modifier = Modifier.weight(1f)) {
                    Text("${opportunity.providerName} • ${opportunity.category}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = BrandNavy)
                    Text("מחיר נוכחי: ${money(opportunity.currentMonthlyCost)} לחודש", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (verified && matched != null && saving != null) {
                val offerPrice = matched.effectiveMonthlyPrice ?: matched.monthlyPrice
                Card(
                    colors = CardDefaults.cardColors(containerColor = SavingsSurface),
                    shape = RoundedCornerShape(FinancialDesignTokens.compactCardRadius),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(FinancialDesignTokens.compactCardPadding),
                        verticalArrangement = Arrangement.spacedBy(FinancialDesignTokens.compactSpacing)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("היום", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(money(opportunity.currentMonthlyCost), fontWeight = FontWeight.SemiBold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${matched.providerName}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(money(offerPrice), fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Text("חיסכון מאומת: ${money(saving)} בחודש", color = EmeraldSavings, fontWeight = FontWeight.Bold)
                        opportunity.potentialAnnualSaving?.takeIf(::positiveFinite)?.let {
                            Text("${money(it)} בשנה", color = EmeraldSavings, fontWeight = FontWeight.SemiBold)
                        }
                        matched.firstYearCost?.takeIf(::positiveFinite)?.let {
                            Text("עלות שנה ראשונה: ${money(it)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (opportunity.actionMode == PRODUCT_PROVIDER_REQUEST) {
                    Button(
                        onClick = onAccept,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth().testTag("product_accept_savings_${opportunity.id}")
                    ) {
                        Text("אני רוצה לחסוך ${money(saving)} בחודש")
                    }
                    Text(
                        "Click&SaveAI לא מבצעת את המעבר. אחרי אישור מפורש נרשמת בקשה להעברת פרטי הקשר לספק.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "ההצעה מוצגת לצפייה בלבד. אין כרגע מסלול מאומת להעברת פרטים מתוך האפליקציה.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    "אנחנו עדיין בודקים חלופה תואמת. אין כאן סכום חיסכון עד שנוכל לאמת אותו.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProductProviderConsentDialog(
    opportunity: FinancialOpportunity,
    defaultName: String,
    defaultEmail: String,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String) -> Unit
) {
    var name by remember(opportunity.id) { mutableStateOf(defaultName) }
    var phone by remember(opportunity.id) { mutableStateOf("") }
    var email by remember(opportunity.id) { mutableStateOf(defaultEmail) }
    var accepted by remember(opportunity.id) { mutableStateOf(false) }
    val provider = opportunity.matchedOffer?.providerName.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("אישור העברת פרטים לספק") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FinancialDesignTokens.cardSpacing)) {
                Text("עבור ההצעה של $provider נעביר רק שם, טלפון, אימייל ומזהה ההצעה שאישרת. תוכן תיבת הדואר ונתוני הוצאות אחרים אינם נשלחים.")
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("שם") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("טלפון") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("אימייל") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                    Text("אני מאשר/ת במפורש את העברת פרטי הקשר לספק עבור ההצעה הזו.")
                }
                Text(
                    "האישור אינו מבצע מעבר ספק. השלמת עסקה, אם תהיה, נעשית מול הספק.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(name.trim(), phone.trim(), email.trim()) },
                enabled = accepted && name.isNotBlank() && phone.isNotBlank() && email.isNotBlank(),
                modifier = Modifier.testTag("product_submit_provider_details")
            ) { Text("אישור העברת הפרטים לספק") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}

@Composable
private fun ProductGmailConsentDialog(onDismiss: () -> Unit, onApprove: () -> Unit) {
    var accepted by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("חיבור לקריאה בלבד") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FinancialDesignTokens.cardSpacing)) {
                Text("הגישה משמשת לזיהוי חשבוניות ומסמכי חיוב כדי לבדוק הזדמנויות חיסכון.")
                Text("Click&SaveAI אינה שולחת, מוחקת או עורכת הודעות. אפשר לבטל את החיבור בכל עת.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                    Text("קראתי ואני מאשר/ת גישה לקריאה בלבד.")
                }
            }
        },
        confirmButton = {
            Button(onClick = onApprove, enabled = accepted) { Text("המשך לאישור") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}

@Composable
private fun ProductConnectionCard(
    authenticated: Boolean,
    syncing: Boolean,
    onSignIn: () -> Unit,
    onConnect: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(FinancialDesignTokens.cardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, DividerLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(FinancialDesignTokens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(FinancialDesignTokens.cardSpacing)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Email, contentDescription = null, tint = TechBluePrimary)
                Spacer(Modifier.size(FinancialDesignTokens.cardSpacing))
                Text("חיבור אחד כדי להתחיל", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BrandNavy)
            }
            Text(
                "מקור המסמכים מחובר לקריאה בלבד כדי לזהות חשבונות ולבדוק חיסכון.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = if (authenticated) onConnect else onSignIn,
                enabled = !syncing,
                modifier = Modifier.fillMaxWidth().testTag("product_connect_account")
            ) {
                Icon(if (authenticated) Icons.Default.Security else Icons.Default.Login, contentDescription = null)
                Spacer(Modifier.size(FinancialDesignTokens.compactSpacing))
                Text(if (authenticated) "חבר מסמכים לקריאה בלבד" else "התחבר כדי להתחיל")
            }
        }
    }
}

@Composable
private fun ProductMessageCard(
    title: String,
    body: String,
    testTag: String,
    showProgress: Boolean = false,
    success: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        shape = RoundedCornerShape(FinancialDesignTokens.compactCardRadius),
        colors = CardDefaults.cardColors(
            containerColor = if (success) SavingsSurface else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, if (success) EmeraldSavings.copy(alpha = 0.25f) else DividerLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(FinancialDesignTokens.compactCardPadding),
            verticalArrangement = Arrangement.spacedBy(FinancialDesignTokens.cardSpacing)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(FinancialDesignTokens.cardSpacing))
                } else if (success) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldSavings)
                    Spacer(Modifier.size(FinancialDesignTokens.cardSpacing))
                }
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (success) EmeraldSavings else BrandNavy)
            }
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun isVerifiedOpportunity(opportunity: FinancialOpportunity): Boolean =
    opportunity.matchedOffer != null && opportunity.potentialMonthlySaving?.let(::positiveFinite) == true

private fun positiveFinite(value: Double): Boolean = value.isFinite() && value > 0.0

private fun money(value: Double): String = "₪${String.format("%.2f", value)}"

private fun productScreenPadding() = PaddingValues(
    start = FinancialDesignTokens.screenHorizontalPadding,
    top = FinancialDesignTokens.screenTopPadding,
    end = FinancialDesignTokens.screenHorizontalPadding,
    bottom = FinancialDesignTokens.screenBottomNavigationClearance
)
