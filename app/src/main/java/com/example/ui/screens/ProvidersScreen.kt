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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.data.repository.FinancialOpportunity
import com.example.data.repository.FinancialRefreshReason
import com.example.data.repository.FinancialSyncState
import com.example.data.repository.OpportunityActionRepository
import com.example.ui.MainViewModel
import com.example.ui.components.OpportunityLifecycleChip
import com.example.ui.components.SavingsGlyph
import com.example.ui.components.V3EmptyState
import com.example.ui.components.V3GradientHeader
import com.example.ui.components.V3Note
import com.example.ui.components.V3Panel
import com.example.ui.components.V3PrimaryButton
import com.example.ui.components.V3SavingsDashboardHero
import com.example.ui.components.V3SectionHeader
import com.example.ui.components.V3SummaryItem
import com.example.ui.components.V3SummaryStrip
import com.example.ui.components.VerificationBadge
import com.example.ui.theme.TechBluePrimary
import com.example.ui.theme.V3PrimarySoft
import com.example.ui.theme.V3Success
import com.example.ui.v3.V3SavingsActionMode
import com.example.ui.v3.asV3Money
import com.example.ui.v3.hasAuthoritativeV3Offer
import com.example.ui.v3.hasQualifiedBillIncrease
import com.example.ui.v3.hasVerifiedSavingsActionTarget
import com.example.ui.v3.toV3SavingsSummary
import com.example.ui.v3.v3LifecycleLabel
import com.example.ui.v3.v3SavingsActionMode
import kotlinx.coroutines.launch

@Composable
fun ProvidersScreen(viewModel: MainViewModel) {
    val session by viewModel.userSession.collectAsState()
    val financialSyncState by viewModel.financialSyncState.collectAsState()
    val financialHome by viewModel.authoritativeFinancialHome.collectAsState()
    val actionRepository = remember { OpportunityActionRepository() }
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf("") }
    var actionMessage by remember { mutableStateOf("") }
    var selectedOpportunity by remember { mutableStateOf<FinancialOpportunity?>(null) }

    val allOpportunities = financialHome?.opportunities.orEmpty()
    val openOpportunities = allOpportunities.filter { it.savingRealizationState != "REALIZED" }
    val inProgress = openOpportunities.filter(FinancialOpportunity::hasActionInProgress)
    val realized = allOpportunities.filter {
        it.savingRealizationState == "REALIZED" && it.realizedMonthlySaving != null
    }
    val summary = financialHome?.toV3SavingsSummary()

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("providers_screen"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            V3GradientHeader(
                eyebrow = "חיסכון",
                title = "החיסכון שלך",
                subtitle = "מה כבר התממש, מה בתהליך ואיפה אפשר לשלם פחות."
            )
        }

        if (summary != null) {
            item {
                V3SavingsDashboardHero(
                    realizedMonthly = summary.realizedMonthly,
                    realizedAnnual = summary.realizedAnnual,
                    potentialMonthly = summary.potentialMonthly,
                    potentialAnnual = summary.potentialAnnual,
                    modifier = Modifier.testTag("v3_savings_hero")
                )
            }
            item {
                V3SummaryStrip(
                    listOf(
                        V3SummaryItem(
                            label = "שירותים במעקב",
                            value = financialHome?.context?.recurringServiceCount?.toString() ?: "לא ידוע"
                        ),
                        V3SummaryItem(
                            label = "שווים בדיקה",
                            value = openOpportunities.size.toString(),
                            emphasized = true
                        ),
                        V3SummaryItem(
                            label = "חיסכון שאומת",
                            value = summary.realizedMonthly?.asV3Money()
                                ?: if (summary.realizedKnownZero) "₪0.00" else "לא ידוע",
                            positive = summary.realizedMonthly != null && summary.realizedMonthly > 0.0
                        )
                    )
                )
            }
        }

        when {
            financialSyncState == FinancialSyncState.Unauthenticated || financialSyncState == FinancialSyncState.Disconnected -> item {
                V3EmptyState("עדיין אין מספיק מידע", "יש להשלים חיבור מאומת לפני שנוכל להציג הזדמנויות חיסכון שנבדקו.")
            }
            (financialSyncState == FinancialSyncState.CheckingConnection || financialSyncState == FinancialSyncState.Recovering) && financialHome == null -> item {
                V3Panel(containerColor = V3PrimarySoft) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                    Text("בודקים את המידע המאומת…", style = MaterialTheme.typography.bodySmall)
                }
            }
            financialSyncState is FinancialSyncState.Failed && financialHome == null -> item {
                V3EmptyState(
                    title = "לא הצלחנו להשלים את הבדיקה",
                    body = "המידע הפיננסי אינו זמין כרגע. לא נציג ערכי חיסכון משוערים במקום מידע שחסר.",
                    actionLabel = "נסה שוב",
                    onAction = { viewModel.refreshFinancialSession(FinancialRefreshReason.RETRY) }
                )
            }
            financialSyncState is FinancialSyncState.Partial && financialHome == null -> item {
                V3EmptyState("חלק מהמידע עדיין מתעדכן", "אין כרגע מספיק מידע מאומת להצגת חיסכון. ערך חסר אינו אפס.")
            }
            else -> {
                item { V3SectionHeader("אפשר לחסוך") }
                if (openOpportunities.isEmpty()) {
                    item { V3EmptyState("כרגע אין הזדמנויות מאומתות", "נמשיך לבדוק עבורך כשהמידע יתעדכן.") }
                } else {
                    items(openOpportunities, key = { "opportunity:${it.id}" }) { opportunity ->
                        OpportunityCard(
                            opportunity = opportunity,
                            onAccept = {
                                if (opportunity.hasVerifiedSavingsActionTarget()) {
                                    val offerId = opportunity.matchedOffer?.offerId.orEmpty()
                                    if (offerId.isBlank()) {
                                        error = "אין כרגע יעד פעולה מאומת להצעה."
                                    } else {
                                        scope.launch {
                                            runCatching {
                                                actionRepository.recordSavingsActionStarted(opportunity.id, offerId)
                                            }.onSuccess {
                                                error = ""
                                                selectedOpportunity = opportunity
                                            }.onFailure {
                                                error = "לא ניתן להתחיל את בקשת החיסכון כרגע. ההצעה תיבדק מחדש לפני ניסיון נוסף."
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                item { V3SectionHeader("בתהליך") }
                if (inProgress.isEmpty()) {
                    item {
                        V3EmptyState(
                            "אין כרגע פעולה בתהליך",
                            "פעולה תופיע כאן רק אחרי שתאשר אותה, עם המצב האמיתי שלה."
                        )
                    }
                } else {
                    items(inProgress, key = { "progress:${it.id}" }) { opportunity ->
                        SavingsProgressCard(opportunity)
                    }
                }

                item { V3SectionHeader("נחסך בפועל") }
                if (realized.isEmpty()) {
                    item {
                        V3EmptyState(
                            "עדיין לא נחסך סכום מאומת",
                            "ברגע שמעבר יושלם והחיסכון יאומת, הסכום החודשי והשנתי יופיעו כאן."
                        )
                    }
                } else {
                    items(realized, key = { "realized:${it.id}" }) { opportunity ->
                        RealizedSavingCard(opportunity)
                    }
                }
            }
        }

        if (actionMessage.isNotBlank()) item { MessageCard("מצב הבקשה", actionMessage) }
        if (error.isNotBlank()) item { MessageCard("לא ניתן להשלים את הבדיקה", error) }
        item {
            V3Note("חיסכון פוטנציאלי אינו חיסכון ממומש. פתיחת בדיקה או בקשה אינה הוכחה למסירה לספק, להשלמת עסקה או לחיסכון בפועל.")
        }
    }

    selectedOpportunity?.let { opportunity ->
        if (opportunity.hasVerifiedSavingsActionTarget()) {
            SavingsActionDialog(
                opportunity = opportunity,
                defaultName = session.displayName,
                defaultEmail = session.email,
                onDismiss = { selectedOpportunity = null },
                onSubmit = { name, phone, email, consent ->
                    val offerId = opportunity.matchedOffer?.offerId.orEmpty()
                    selectedOpportunity = null
                    scope.launch {
                        runCatching {
                            actionRepository.acceptSavingsOpportunity(
                                opportunityId = opportunity.id,
                                expectedOfferId = offerId,
                                contactName = name,
                                phone = phone,
                                contactEmail = email,
                                consentAccepted = consent
                            )
                        }.onSuccess {
                            actionMessage = "הפעולה נרשמה. מסירה לספק, השלמת עסקה וחיסכון ממומש יוצגו רק לפי ראיה מאומתת."
                            error = ""
                            viewModel.refreshFinancialSession(FinancialRefreshReason.RETRY)
                        }.onFailure {
                            error = "ההצעה השתנתה, אינה זמינה או שאין כרגע מסלול מעבר מאומת."
                        }
                    }
                }
            )
        } else {
            selectedOpportunity = null
        }
    }
}

@Composable
private fun OpportunityCard(opportunity: FinancialOpportunity, onAccept: () -> Unit) {
    val matched = opportunity.matchedOffer?.takeIf { opportunity.hasAuthoritativeV3Offer() }
    val monthlySaving = opportunity.potentialMonthlySaving
    val actionMode = opportunity.v3SavingsActionMode()
    val actionAvailable = opportunity.hasVerifiedSavingsActionTarget() &&
        matched != null && monthlySaving != null && monthlySaving > 0.0

    V3Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SavingsGlyph(modifier = Modifier.size(23.dp), contentDescription = "חיסכון")
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(opportunity.providerName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(opportunity.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OpportunityLifecycleChip(opportunity.v3LifecycleLabel())
        }

        Text("החיוב שנצפה: ${opportunity.currentMonthlyCost.asV3Money()} לחודש")

        if (matched != null && monthlySaving != null && monthlySaving > 0.0) {
            val effectiveMonthly = matched.effectiveMonthlyPrice ?: matched.monthlyPrice
            Text("חלופה שנבדקה: ${matched.providerName} · ${effectiveMonthly.asV3Money()} לחודש")
            Text(
                "חיסכון פוטנציאלי: ${monthlySaving.asV3Money()} בחודש",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TechBluePrimary
            )
            Text(
                opportunity.potentialAnnualSaving?.let { "חיסכון פוטנציאלי: ${it.asV3Money()} בשנה" }
                    ?: "חיסכון שנתי פוטנציאלי: לא ידוע",
                fontWeight = FontWeight.SemiBold
            )
            VerificationBadge("אומת")
            Text("חיסכון פוטנציאלי אינו חיסכון ממומש.", style = MaterialTheme.typography.bodySmall)
        } else {
            val detectionText = if (opportunity.type == "COMPARE_AFTER_PRICE_INCREASE") {
                if (opportunity.hasQualifiedBillIncrease()) {
                    "השוואה בין חיובים: החשבון עלה ב-${opportunity.monthlyIncrease!!.asV3Money()} וב-${String.format("%.1f", opportunity.percentIncrease)}%. זו לא הוכחה לשינוי תעריף."
                } else {
                    "זוהה שינוי בין חיובים, אך אין כרגע ראיה שעומדת גם בסף ₪5 וגם בסף 5%, או שחסרים נתונים. לא הוכחה לשינוי תעריף."
                }
            } else {
                "זהו שירות חודשי חוזר. לא נציג חיסכון או יעד פעולה בלי הצעה מאומתת."
            }
            Text(detectionText)
        }

        when {
            actionAvailable -> V3PrimaryButton("בדיקת ההצעה", onAccept, Modifier.fillMaxWidth())
            actionMode == V3SavingsActionMode.VIEW_ONLY -> Text("ההזדמנות היא לצפייה בלבד; אין יעד פעולה מאומת.", style = MaterialTheme.typography.bodySmall)
            actionMode == V3SavingsActionMode.NO_VERIFIED_ACTION_TARGET -> Text("אין כרגע יעד פעולה מאומת להצעה.", style = MaterialTheme.typography.bodySmall)
            actionMode == V3SavingsActionMode.DIRECT_PLAN_JOIN -> Text("חיבור ישיר למסלול אינו זמין ללא יעד מאומת.", style = MaterialTheme.typography.bodySmall)
            else -> Unit
        }
    }
}

private fun FinancialOpportunity.hasActionInProgress(): Boolean =
    savingRealizationState != "REALIZED" && (
        consentState != "NOT_CONSENTED" ||
            requestState != "NOT_CREATED" ||
            deliveryAttemptState != "NOT_ATTEMPTED" ||
            submissionState != "NOT_SUBMITTED" ||
            completionState == "DEAL_COMPLETED"
        )

@Composable
private fun SavingsProgressCard(opportunity: FinancialOpportunity) {
    V3Panel(containerColor = V3PrimarySoft) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SavingsGlyph(modifier = Modifier.size(22.dp), contentDescription = "פעולת חיסכון")
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(opportunity.providerName.ifBlank { "ספק לא ידוע" }, fontWeight = FontWeight.Bold)
                Text(opportunity.category.ifBlank { "קטגוריה לא ידועה" }, style = MaterialTheme.typography.bodySmall)
            }
            OpportunityLifecycleChip(opportunity.v3LifecycleLabel())
        }
        Text(
            "הבקשה בתהליך. מסירה לספק, השלמת עסקה וחיסכון ממומש יוצגו רק אחרי ראיה מתאימה.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun RealizedSavingCard(opportunity: FinancialOpportunity) {
    V3Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SavingsGlyph(modifier = Modifier.size(23.dp), tint = V3Success, contentDescription = "חיסכון שאומת")
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(opportunity.providerName.ifBlank { "ספק לא ידוע" }, fontWeight = FontWeight.Bold)
                Text(opportunity.category.ifBlank { "קטגוריה לא ידועה" }, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            "${opportunity.realizedMonthlySaving!!.asV3Money()} נחסכו בפועל בחודש",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = V3Success
        )
        Text(
            opportunity.realizedAnnualSaving?.let { "${it.asV3Money()} בשנה לפי הראיה שנקלטה" }
                ?: "החיסכון השנתי עדיין לא ידוע",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun realizedSavingMessage(opportunity: FinancialOpportunity): String? = when {
    opportunity.savingRealizationState == "REALIZED" && opportunity.realizedMonthlySaving != null -> buildString {
        append("חיסכון ממומש לפי ראיה שנקלטה: ${opportunity.realizedMonthlySaving.asV3Money()} בחודש")
        append(opportunity.realizedAnnualSaving?.let { " · ${it.asV3Money()} בשנה" } ?: " · שנתי: לא ידוע")
    }
    opportunity.savingRealizationState == "NOT_REALIZED" && opportunity.realizedMonthlySaving == 0.0 ->
        "לפי הראיה שנקלטה, החיסכון הממומש הידוע הוא ₪0.00 בחודש."
    opportunity.savingRealizationState == "UNKNOWN" && opportunity.completionState == "DEAL_COMPLETED" ->
        "החיסכון הממומש עדיין אינו ידוע."
    else -> null
}

@Composable
private fun SavingsActionDialog(
    opportunity: FinancialOpportunity,
    defaultName: String,
    defaultEmail: String,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, Boolean) -> Unit
) {
    var name by remember(opportunity.id) { mutableStateOf(defaultName) }
    var phone by remember(opportunity.id) { mutableStateOf("") }
    var email by remember(opportunity.id) { mutableStateOf(defaultEmail) }
    var consent by remember(opportunity.id) { mutableStateOf(false) }
    val canSubmit = name.isNotBlank() && phone.isNotBlank() && email.isNotBlank() && consent

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("בקשת חיסכון אצל ${opportunity.providerName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("הפרטים יישלחו רק לאחר אישור. פתיחת בקשה אינה הוכחה למסירה, התקשרות, עסקה או חיסכון ממומש.")
                OutlinedTextField(name, { name = it }, label = { Text("שם") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text("טלפון") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, { email = it }, label = { Text("דוא״ל") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(consent, { consent = it })
                    Text("אני מאשר להעביר את פרטי הקשר לצורך בקשת החיסכון הזו.")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(name.trim(), phone.trim(), email.trim(), consent) }, enabled = canSubmit) { Text("שלח בקשה") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}

@Composable
private fun MessageCard(title: String, body: String) {
    V3Panel(containerColor = V3PrimarySoft) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodySmall)
    }
}
