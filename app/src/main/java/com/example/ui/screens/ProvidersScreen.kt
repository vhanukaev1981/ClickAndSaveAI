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
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.BackendRepository
import com.example.data.repository.FinancialHomeResult
import com.example.data.repository.FinancialOpportunity
import com.example.data.repository.OpportunityActionRepository
import com.example.ui.CustomerPresentationPolicy
import com.example.ui.FinancialUiState
import com.example.ui.FinancialUiStatePolicy
import com.example.ui.MainViewModel
import com.example.ui.theme.TechBluePrimary
import kotlinx.coroutines.launch

private const val IN_APP_PROVIDER_REQUEST = "IN_APP_PROVIDER_REQUEST"
private val lockedOpportunityStatuses = setOf(
    "USER_ACCEPTED",
    "PROVIDER_PROCESSING",
    "ACTIVATED",
    "COMPLETED"
)

@Composable
fun ProvidersScreen(viewModel: MainViewModel) {
    val session by viewModel.userSession.collectAsState()
    val isGmailConnected by viewModel.isGmailConnected.collectAsState()
    val backendRepository = remember { BackendRepository() }
    val actionRepository = remember { OpportunityActionRepository() }
    val scope = rememberCoroutineScope()
    var financialHome by remember { mutableStateOf<FinancialHomeResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var actionIntentStarting by remember { mutableStateOf(false) }
    var actionSubmitting by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var selectedOpportunity by remember { mutableStateOf<FinancialOpportunity?>(null) }
    var actionMessage by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(session.isAuthenticated, isGmailConnected, refreshKey) {
        if (!session.isAuthenticated || !isGmailConnected) {
            financialHome = null
            hasError = false
            return@LaunchedEffect
        }
        loading = true
        runCatching { backendRepository.getFinancialHome() }
            .onSuccess {
                financialHome = it
                hasError = false
            }
            .onFailure {
                hasError = true
            }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("providers_screen")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.testTag("savings_screen_intro"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = TechBluePrimary)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                "הזדמנויות החיסכון שלך",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "אנחנו בודקים את השירותים שלך ברקע ומציגים חיסכון רק כשההצעה מתאימה וניתנת לאימות. אם אפשר לפנות ישירות עבור ההצעה, תראה כאן פעולה ברורה.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (!session.isAuthenticated || !isGmailConnected) {
                item {
                    MessageCard(
                        title = "חיבור אחד כדי להתחיל",
                        body = "חבר את מקור המסמכים פעם אחת דרך מסך הבית. לאחר מכן הבדיקה ממשיכה אוטומטית ברקע.",
                        testTag = "savings_requires_connection"
                    )
                }
            } else if (loading && financialHome == null) {
                item {
                    val message = FinancialUiStatePolicy.message(FinancialUiState.LOADING)
                    MessageCard(
                        title = message.title,
                        body = message.body,
                        testTag = "savings_loading_state",
                        showProgress = true
                    )
                }
            } else {
                val opportunities = financialHome?.opportunities.orEmpty()
                if (opportunities.isEmpty()) {
                    item {
                        val message = FinancialUiStatePolicy.message(FinancialUiState.UNDER_REVIEW)
                        MessageCard(
                            title = message.title,
                            body = message.body,
                            testTag = "savings_under_review_state"
                        )
                    }
                } else {
                    items(opportunities, key = { it.id }) { opportunity ->
                        OpportunityCard(
                            opportunity = opportunity,
                            actionEnabled = !actionIntentStarting && !actionSubmitting,
                            onAccept = {
                                if (
                                    !actionIntentStarting &&
                                    !actionSubmitting &&
                                    opportunity.actionMode == IN_APP_PROVIDER_REQUEST
                                ) {
                                    val displayedOfferId = opportunity.matchedOffer?.offerId.orEmpty()
                                    if (displayedOfferId.isBlank()) {
                                        hasError = true
                                    } else {
                                        actionIntentStarting = true
                                        actionMessage = ""
                                        hasError = false
                                        scope.launch {
                                            runCatching {
                                                actionRepository.recordSavingsActionStarted(
                                                    opportunityId = opportunity.id,
                                                    expectedOfferId = displayedOfferId
                                                )
                                            }.onSuccess {
                                                selectedOpportunity = opportunity
                                            }.onFailure {
                                                hasError = true
                                            }
                                            actionIntentStarting = false
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }

            if (actionIntentStarting) {
                item {
                    MessageCard(
                        title = "בודקים שההצעה עדיין זמינה",
                        body = "מאמתים את ההצעה שבחרת לפני שנבקש ממך אישור להעברת פרטי קשר.",
                        testTag = "savings_action_starting",
                        showProgress = true
                    )
                }
            }
            if (actionSubmitting) {
                item {
                    MessageCard(
                        title = "שולחים את הבקשה",
                        body = "אנחנו מאמתים שוב את ההצעה ושולחים רק את הפרטים שאישרת.",
                        testTag = "savings_action_submitting",
                        showProgress = true
                    )
                }
            }
            if (actionMessage.isNotBlank()) {
                item {
                    MessageCard(
                        title = "הבקשה נקלטה",
                        body = actionMessage,
                        testTag = "savings_action_success"
                    )
                }
            }
            if (hasError) {
                item {
                    val message = FinancialUiStatePolicy.message(FinancialUiState.ERROR)
                    MessageCard(
                        title = message.title,
                        body = message.body,
                        testTag = "savings_error_state"
                    )
                }
            }
        }
    }

    selectedOpportunity?.let { opportunity ->
        if (opportunity.actionMode == IN_APP_PROVIDER_REQUEST) {
            SavingsActionDialog(
                opportunity = opportunity,
                defaultName = session.displayName,
                defaultEmail = session.email,
                onDismiss = { selectedOpportunity = null },
                onSubmit = { name, phone, email ->
                    if (actionSubmitting) return@SavingsActionDialog
                    val displayedOfferId = opportunity.matchedOffer?.offerId.orEmpty()
                    if (displayedOfferId.isBlank()) {
                        selectedOpportunity = null
                        hasError = true
                        return@SavingsActionDialog
                    }
                    selectedOpportunity = null
                    actionMessage = ""
                    hasError = false
                    actionSubmitting = true
                    scope.launch {
                        runCatching {
                            actionRepository.acceptSavingsOpportunity(
                                opportunityId = opportunity.id,
                                expectedOfferId = displayedOfferId,
                                contactName = name,
                                phone = phone,
                                contactEmail = email
                            )
                        }.onSuccess { result ->
                            val savingLabel = CustomerPresentationPolicy.verifiedSavingsLabel(
                                result.potentialMonthlySaving,
                                result.potentialAnnualSaving
                            ) ?: "הבקשה נשמרה וההצעה תיבדק שוב לפני המשך הטיפול."
                            actionMessage = "הבקשה להצעה של ${opportunity.matchedOffer?.providerName.orEmpty()} נקלטה. $savingLabel"
                            hasError = false
                            refreshKey += 1
                        }.onFailure {
                            hasError = true
                        }
                        actionSubmitting = false
                    }
                }
            )
        } else {
            selectedOpportunity = null
        }
    }
}

@Composable
private fun OpportunityCard(
    opportunity: FinancialOpportunity,
    actionEnabled: Boolean,
    onAccept: () -> Unit
) {
    val matched = opportunity.matchedOffer
    val verifiedLabel = if (matched != null) {
        CustomerPresentationPolicy.verifiedSavingsLabel(
            opportunity.potentialMonthlySaving,
            opportunity.potentialAnnualSaving
        )
    } else {
        null
    }
    val monthlySaving = opportunity.potentialMonthlySaving?.takeIf { it > 0.0 }
    val lifecycleLocked = opportunity.status.uppercase() in lockedOpportunityStatuses
    val inAppActionAvailable = opportunity.actionMode == IN_APP_PROVIDER_REQUEST

    Card(
        modifier = Modifier.testTag("savings_opportunity_${opportunity.id}"),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (verifiedLabel != null) Icons.Default.Verified else Icons.Default.Savings,
                    contentDescription = null,
                    tint = TechBluePrimary
                )
                Spacer(modifier = Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${opportunity.providerName} • ${opportunity.category}",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "החיוב שנצפה: ${money(opportunity.currentMonthlyCost)} לחודש",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (verifiedLabel != null && matched != null && monthlySaving != null) {
                val effectiveMonthly = matched.effectiveMonthlyPrice ?: matched.monthlyPrice
                if (effectiveMonthly != null) {
                    Text(
                        "מצאנו ${matched.providerName} בעלות חודשית אפקטיבית של ${money(effectiveMonthly)}.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    verifiedLabel,
                    color = TechBluePrimary,
                    fontWeight = FontWeight.SemiBold
                )
                matched.firstYearCost?.let {
                    Text(
                        "עלות שנה ראשונה: ${money(it)}${matched.oneTimeFees?.takeIf { fee -> fee > 0.0 }?.let { fee -> " • כולל ${money(fee)} עלויות חד-פעמיות" } ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                matched.requiredRecurringFees?.takeIf { it > 0.0 }?.let { recurringFee ->
                    Text(
                        "העלות כוללת ${money(recurringFee)} לחודש דמי חובה${matched.requiredRecurringFeesDescription.takeIf { it.isNotBlank() }?.let { description -> " ($description)" } ?: ""}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "החיסכון חושב לפי מחיר צרכני מלא לשנה הראשונה וההצעה נבדקת מחדש לפני כל פעולה.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val lifecycleMessage = opportunityLifecycleMessage(opportunity.status)
                when {
                    lifecycleLocked -> Text(lifecycleMessage, fontWeight = FontWeight.Bold)
                    inAppActionAvailable -> {
                        if (opportunity.status.equals("PROVIDER_REJECTED", ignoreCase = true)) {
                            Text(
                                "הפנייה הקודמת לא הושלמה. אם ההצעה עדיין בתוקף אפשר לנסות שוב.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = onAccept,
                            enabled = actionEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("accept_savings_${opportunity.id}")
                        ) {
                            Text(if (actionEnabled) "אני רוצה לחסוך ${money(monthlySaving)} בחודש" else "הבקשה נבדקת…")
                        }
                    }
                    else -> {
                        Text(
                            "זו ההצעה הטובה ביותר שמצאנו כרגע. פעולה ישירה מתוך האפליקציה עדיין אינה זמינה להצעה הזו.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                val detectionText = if (opportunity.type == "COMPARE_AFTER_PRICE_INCREASE") {
                    "זוהתה עליית מחיר של ${String.format("%.1f", opportunity.percentIncrease)}%. אנחנו מחפשים עבורך חלופה מתאימה."
                } else {
                    "זהו שירות חודשי חוזר. אנחנו בודקים באופן יזום אם קיימת חלופה טובה ומתאימה יותר."
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

private fun opportunityLifecycleMessage(status: String): String = when (status.uppercase()) {
    "USER_ACCEPTED" -> "הבקשה נשלחה וננעלה להצעה שאישרת."
    "PROVIDER_PROCESSING" -> "הבקשה שלך נמצאת בטיפול."
    "ACTIVATED" -> "השירות החדש הופעל. אנחנו ממתינים לאישור סופי של השלמת התהליך."
    "COMPLETED" -> "המעבר הושלם והחיסכון נרשם."
    else -> ""
}

@Composable
private fun SavingsActionDialog(
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("אישור פנייה להצעה") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    "נעביר לנותן השירות רק את פרטי הקשר הדרושים ואת ההצעה שבחרת. תוכן תיבת הדואר ותמונת ההוצאות המלאה שלך אינם נשלחים."
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("שם") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("savings_contact_name")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("טלפון") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("savings_contact_phone")
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("אימייל") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("savings_contact_email")
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = accepted,
                        onCheckedChange = { accepted = it },
                        modifier = Modifier.testTag("savings_contact_consent")
                    )
                    Text("אני מאשר/ת להעביר את פרטי הקשר לצורך קבלת ההצעה שבחרתי.")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(name.trim(), phone.trim(), email.trim()) },
                enabled = accepted && name.isNotBlank() && phone.isNotBlank() && email.isNotBlank(),
                modifier = Modifier.testTag("submit_savings_request")
            ) {
                Text("שלח בקשה")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_savings_request")
            ) { Text("ביטול") }
        }
    )
}

@Composable
private fun MessageCard(
    title: String,
    body: String,
    testTag: String = "savings_message_card",
    showProgress: Boolean = false
) {
    Card(
        modifier = Modifier.testTag(testTag),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showProgress) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.size(10.dp))
                    Text(title, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(title, fontWeight = FontWeight.Bold)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun money(value: Double): String = "₪${String.format("%.2f", value)}"
