package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.FinancialOpportunity
import com.example.data.repository.FinancialRefreshReason
import com.example.data.repository.FinancialSyncState
import com.example.data.repository.OpportunityActionRepository
import com.example.ui.MainViewModel
import com.example.ui.theme.TechBluePrimary
import kotlinx.coroutines.launch

private const val IN_APP_PROVIDER_REQUEST = "IN_APP_PROVIDER_REQUEST"
private val lockedStatuses = setOf("USER_ACCEPTED", "PROVIDER_PROCESSING", "ACTIVATED", "COMPLETED")

@Composable
fun ProvidersScreen(viewModel: MainViewModel) {
    val session by viewModel.userSession.collectAsState()
    val isGmailConnected by viewModel.isGmailConnected.collectAsState()
    val financialSyncState by viewModel.financialSyncState.collectAsState()
    val financialHome by viewModel.authoritativeFinancialHome.collectAsState()
    val actionRepository = remember { OpportunityActionRepository() }
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<FinancialOpportunity?>(null) }
    var message by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    val recovering = financialSyncState is FinancialSyncState.CheckingConnection ||
        financialSyncState is FinancialSyncState.Recovering
    val syncError = when (val state = financialSyncState) {
        is FinancialSyncState.Partial -> state.reason
        is FinancialSyncState.Failed -> state.reason
        else -> ""
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("providers_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = TechBluePrimary)
                        Spacer(Modifier.size(8.dp))
                        Text("הזדמנויות ש-Click&SaveAI מצאה", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Text("סכום חיסכון מוצג רק כאשר Core מחזיק מחיר נוכחי והצעה בני-השוואה.")
                }
            }
        }

        when {
            !session.isAuthenticated || !isGmailConnected || financialSyncState is FinancialSyncState.Disconnected ->
                item { MessageCard("אין מקור פיננסי מחובר", "חבר Gmail לקריאה בלבד כדי לשחזר את המצב מהשרת.") }
            recovering && financialHome == null ->
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } }
            else -> {
                val opportunities = financialHome?.opportunities.orEmpty()
                if (opportunities.isEmpty()) {
                    item { MessageCard("אין כרגע הזדמנות נתמכת", "לא נמצא חיסכון שניתן לבסס על הנתונים הזמינים.") }
                } else {
                    items(opportunities, key = { it.id }) { opportunity ->
                        OpportunityCard(opportunity) {
                            val offerId = opportunity.matchedOffer?.offerId.orEmpty()
                            if (opportunity.actionMode != IN_APP_PROVIDER_REQUEST || offerId.isBlank()) {
                                error = "אין כרגע מסלול ספק מאומת להצעה הזו."
                            } else scope.launch {
                                runCatching { actionRepository.recordSavingsActionStarted(opportunity.id, offerId) }
                                    .onSuccess { error = ""; selected = opportunity }
                                    .onFailure { error = it.localizedMessage ?: "לא ניתן להתחיל את הבקשה כרגע." }
                            }
                        }
                    }
                }
            }
        }

        if (submitting) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } }
        if (message.isNotBlank()) item { MessageCard("הבקשה נוצרה", message) }
        if (syncError.isNotBlank()) item { MessageCard("המידע האחרון נשמר", syncError) }
        if (error.isNotBlank()) item { MessageCard("לא ניתן להשלים את הבקשה", error) }
    }

    selected?.let { opportunity ->
        SavingsActionDialog(
            opportunity = opportunity,
            defaultName = session.displayName,
            defaultEmail = session.email,
            onDismiss = { selected = null }
        ) { name, phone, email ->
            val offerId = opportunity.matchedOffer?.offerId.orEmpty()
            selected = null
            submitting = true
            scope.launch {
                runCatching {
                    actionRepository.acceptSavingsOpportunity(opportunity.id, offerId, name, phone, email)
                }.onSuccess { result ->
                    val saving = result.potentialMonthlySaving?.let { "${money(it)} בחודש" } ?: "סכום לא ידוע"
                    message = "נוצרה בקשה. החיסכון הנתמך כעת: $saving. טרם אושר שהספק קיבל את הפרטים."
                    error = ""
                    viewModel.refreshFinancialSession(FinancialRefreshReason.RETRY)
                }.onFailure {
                    error = it.localizedMessage ?: "ההצעה השתנתה או אינה זמינה."
                }
                submitting = false
            }
        }
    }
}

@Composable
private fun OpportunityCard(opportunity: FinancialOpportunity, onAccept: () -> Unit) {
    val matched = opportunity.matchedOffer
    val monthlySaving = opportunity.potentialMonthlySaving
    val supported = matched != null && matched.verifiedAt.isNotBlank() && monthlySaving != null && monthlySaving > 0.0

    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (supported) Icons.Default.Verified else Icons.Default.Savings, null, tint = TechBluePrimary)
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("${opportunity.providerName} • ${opportunity.category}", fontWeight = FontWeight.Bold)
                    Text("החיוב שנצפה: ${money(opportunity.currentMonthlyCost)} לחודש", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (supported && matched != null && monthlySaving != null) {
                Text("הצעה: ${matched.providerName} • ${money(matched.effectiveMonthlyPrice ?: matched.monthlyPrice)} לחודש")
                Text(buildString {
                    append("חיסכון פוטנציאלי נתמך: ${money(monthlySaving)} בחודש")
                    opportunity.potentialAnnualSaving?.let { append(" • ${money(it)} בתקופה השנתית המחושבת") }
                }, color = TechBluePrimary, fontWeight = FontWeight.SemiBold)
                Text(buildString {
                    append("אומת לאחרונה: ${matched.verifiedAt}.")
                    if (matched.validUntil.isNotBlank()) append(" תוקף ידוע עד: ${matched.validUntil}.")
                }, style = MaterialTheme.typography.bodySmall)
                when {
                    opportunity.status.uppercase() in lockedStatuses ->
                        Text(lifecycleMessage(opportunity.status), fontWeight = FontWeight.Bold)
                    opportunity.actionMode == IN_APP_PROVIDER_REQUEST ->
                        Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) { Text("לחסוך ${money(monthlySaving)} בחודש") }
                    else -> Text("אין מסלול מסירה מאומת לספק; הפרטים שלך לא יישלחו.")
                }
            } else {
                Text("סכום החיסכון אינו ידוע כרגע. לא נציג ₪0 ולא סכום משוער כאילו הוא עובדה.")
            }
        }
    }
}

private fun lifecycleMessage(status: String) = when (status.uppercase()) {
    "USER_ACCEPTED" -> "הבקשה נוצרה ונשמרה. טרם אושר שהספק קיבל את הפרטים."
    "PROVIDER_PROCESSING" -> "הבקשה בתהליך. קבלת הספק תאושר רק לפי סטטוס מסירה סמכותי."
    "ACTIVATED" -> "השירות סומן כמופעל. חיסכון ממומש דורש ראיה מתאימה."
    "COMPLETED" -> "התהליך סומן כהושלם. חיסכון ממומש יוצג רק עם ראיה תומכת."
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
        title = { Text("בדיקה ואישור לפני מסירת פרטים") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("יועברו לספק רק פרטי הקשר ומזהי ההצעה. תוכן Gmail לא יישלח.")
                OutlinedTextField(name, { name = it }, label = { Text("שם") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("טלפון") }, singleLine = true)
                OutlinedTextField(email, { email = it }, label = { Text("אימייל") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(accepted, { accepted = it })
                    Text("אני מאשר/ת למסור לספק את פרטי הקשר לצורך ההצעה שבחרתי.")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(name.trim(), phone.trim(), email.trim()) },
                enabled = accepted && name.isNotBlank() && phone.isNotBlank() && email.isNotBlank()
            ) { Text("שלחו את הפרטים לספק") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}

@Composable
private fun MessageCard(title: String, body: String) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body)
        }
    }
}

private fun money(value: Double) = "₪${String.format("%.2f", value)}"
