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
    var error by remember { mutableStateOf("") }
    var selectedOpportunity by remember { mutableStateOf<FinancialOpportunity?>(null) }
    var actionMessage by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(session.isAuthenticated, isGmailConnected, refreshKey) {
        if (!session.isAuthenticated || !isGmailConnected) {
            financialHome = null
            return@LaunchedEffect
        }
        loading = true
        runCatching { backendRepository.getFinancialHome() }
            .onSuccess {
                financialHome = it
                error = ""
            }
            .onFailure {
                error = it.localizedMessage ?: "לא ניתן לטעון כרגע את הזדמנויות החיסכון."
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
                                "הזדמנויות ש-Click&SaveAI מצאה",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "המערכת מדרגת לפי הערך עבורך. מעבר מתוך Click&SaveAI זמין רק כשקיים מסלול ספק מאומת שניתן לעקוב אחריו עד להשלמת העסקה.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (!session.isAuthenticated || !isGmailConnected) {
                item {
                    MessageCard(
                        title = "המערכת עדיין לא יכולה לעבוד ברקע",
                        body = "התחבר וחבר Gmail פעם אחת. לאחר מכן Click&SaveAI תזהה ותבדוק הזדמנויות עבורך אוטומטית."
                    )
                }
            } else if (loading && financialHome == null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                val opportunities = financialHome?.opportunities.orEmpty()
                if (opportunities.isEmpty()) {
                    item {
                        MessageCard(
                            title = "ה-AI ממשיך לבדוק",
                            body = "כרגע אין צורך לחפש ידנית. אם יזוהה שירות שניתן לייעל או תימצא הצעה מתאימה, היא תופיע כאן אוטומטית."
                        )
                    }
                } else {
                    items(opportunities, key = { it.id }) { opportunity ->
                        OpportunityCard(
                            opportunity = opportunity,
                            onAccept = {
                                if (opportunity.actionMode == IN_APP_PROVIDER_REQUEST) {
                                    selectedOpportunity = opportunity
                                }
                            }
                        )
                    }
                }
            }

            if (actionMessage.isNotBlank()) {
                item { MessageCard(title = "הבקשה נקלטה", body = actionMessage) }
            }
            if (error.isNotBlank()) {
                item { MessageCard(title = "לא ניתן להשלים את הבדיקה", body = error) }
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
                    val displayedOfferId = opportunity.matchedOffer?.offerId.orEmpty()
                    selectedOpportunity = null
                    loading = true
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
                            actionMessage = "יצרנו בקשה מאומתת ל-${opportunity.matchedOffer?.providerName.orEmpty()}. החיסכון שנבדק: ${money(result.potentialMonthlySaving)} בחודש."
                            error = ""
                            refreshKey += 1
                        }.onFailure { throwable ->
                            error = throwable.localizedMessage ?: "ההצעה השתנתה, אינה זמינה או שאין כרגע מסלול מעבר מאומת."
                        }
                        loading = false
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
    onAccept: () -> Unit
) {
    val matched = opportunity.matchedOffer
    val monthlySaving = opportunity.potentialMonthlySaving
    val verifiedSaving = matched != null && monthlySaving != null && monthlySaving > 0.0
    val lifecycleLocked = opportunity.status.uppercase() in lockedOpportunityStatuses
    val inAppActionAvailable = opportunity.actionMode == IN_APP_PROVIDER_REQUEST

    Card(shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (verifiedSaving) Icons.Default.Verified else Icons.Default.Savings,
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

            if (verifiedSaving && matched != null) {
                val effectiveMonthly = matched.effectiveMonthlyPrice ?: matched.monthlyPrice
                if (effectiveMonthly != null) {
                    Text(
                        "מצאנו ${matched.providerName} בעלות חודשית אפקטיבית של ${money(effectiveMonthly)}.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    "חיסכון מאומת: ${money(monthlySaving ?: 0.0)} בחודש • ${money(opportunity.potentialAnnualSaving ?: 0.0)} בשנה",
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
                        Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
                            Text("אני רוצה לחסוך ${money(monthlySaving ?: 0.0)} בחודש")
                        }
                    }
                    else -> {
                        Text(
                            "זו ההצעה הטובה ביותר שמצאנו כרגע. מעבר ישיר דרך Click&SaveAI עדיין לא זמין להצעה הזו, ולכן לא נשלח את פרטיך לספק.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                val detectionText = if (opportunity.type == "COMPARE_AFTER_PRICE_INCREASE") {
                    "זוהתה עליית מחיר של ${String.format("%.1f", opportunity.percentIncrease)}%. Click&SaveAI מחפשת עבורך חלופה מתאימה."
                } else {
                    "זהו שירות חודשי חוזר. Click&SaveAI בודקת באופן יזום אם קיימת חלופה טובה ומתאימה יותר."
                }
                Text(detectionText, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "לא נציג סכום חיסכון ולא נפנה לספק עד שתימצא הצעה שניתן לאמת ולהתאים לשירות שלך.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun opportunityLifecycleMessage(status: String): String = when (status.uppercase()) {
    "USER_ACCEPTED" -> "הבקשה נשלחה וננעלה להצעה שאישרת."
    "PROVIDER_PROCESSING" -> "הספק מטפל בבקשה שלך."
    "ACTIVATED" -> "השירות החדש הופעל. אנחנו ממתינים לאישור סופי של העסקה."
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
        title = { Text("אישור פנייה לספק") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    "Click&SaveAI תעביר לספק רק את פרטי הקשר הדרושים ואת ההצעה שבחרת. לא נשלח תוכן Gmail."
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("שם") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("טלפון") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("אימייל") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                    Text("אני מאשר/ת להעביר לספק את פרטי הקשר לצורך קבלת ההצעה.")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(name.trim(), phone.trim(), email.trim()) },
                enabled = accepted && name.isNotBlank() && phone.isNotBlank() && email.isNotBlank()
            ) {
                Text("שלח בקשה")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}

@Composable
private fun MessageCard(title: String, body: String) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun money(value: Double): String = "₪${String.format("%.2f", value)}"
