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
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.BackendInvoice
import com.example.data.repository.FinancialOpportunity
import com.example.data.repository.FinancialSyncState
import com.example.data.repository.latestScanOrNull
import com.example.ui.MainViewModel
import com.example.ui.components.V3Note
import com.example.ui.components.V3Panel
import com.example.ui.components.V3ScreenHeader
import com.example.ui.components.V3SecondaryButton
import com.example.ui.components.V3SectionHeader
import com.example.ui.components.V3SummaryItem
import com.example.ui.components.V3SummaryStrip
import com.example.ui.theme.TechBluePrimary
import com.example.ui.theme.V3MutedForeground
import com.example.ui.theme.V3Primary
import com.example.ui.theme.V3PrimarySoft
import com.example.ui.theme.V3Surface
import com.example.ui.v3.V3InvoicePaymentMode
import com.example.ui.v3.asV3Money
import com.example.ui.v3.hasAuthoritativeV3Offer
import com.example.ui.v3.v3PaymentMode

@Composable
fun InvoicesScreen(
    viewModel: MainViewModel,
    onOpenReceiptScan: () -> Unit,
    onOpenSavings: () -> Unit = {}
) {
    val financialSyncState by viewModel.financialSyncState.collectAsState()
    val financialHome by viewModel.authoritativeFinancialHome.collectAsState()
    var selectedCategory by remember { mutableStateOf("הכל") }
    val categories = listOf("הכל", "חשמל", "סלולר", "אינטרנט", "תקשורת", "ביטוח", "טלוויזיה")
    val authoritativeBills = financialSyncState.latestScanOrNull?.invoices
    val filteredBills = authoritativeBills?.let { bills ->
        if (selectedCategory == "הכל") bills else bills.filter { it.category == selectedCategory }
    }
    val opportunities = financialHome?.opportunities.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("invoices_screen").testTag("v3_invoice_list"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            V3ScreenHeader(
                eyebrow = "חשבוניות",
                title = "לתשלום",
                subtitle = "החשבוניות שנכנסו ומה כדאי לבדוק לפני התשלום."
            )
        }

        if (authoritativeBills != null) {
            item {
                V3SummaryStrip(
                    listOf(
                        V3SummaryItem("מסמכים שזוהו", authoritativeBills.size.toString()),
                        V3SummaryItem("בתצוגה", filteredBills?.size?.toString() ?: "לא ידוע", emphasized = true),
                        V3SummaryItem("מועד תשלום ידוע", "לא ידוע")
                    )
                )
            }
        }

        item {
            V3Panel(containerColor = V3PrimarySoft) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, null, tint = TechBluePrimary)
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("התשלום עצמו מתבצע מול הספק", fontWeight = FontWeight.Bold)
                        Text(
                            "Click & Save לא גובה כסף ולא משלם עבורך. כשאין יעד תשלום מאומת — לא נשלח אותך לקישור שאיננו מוודאים.",
                            style = MaterialTheme.typography.bodySmall,
                            color = V3MutedForeground
                        )
                    }
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) },
                        shape = RoundedCornerShape(999.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = V3Surface,
                            labelColor = V3MutedForeground,
                            selectedContainerColor = V3Primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        item {
            V3SectionHeader(title = "מה נכנס לתשלום")
        }
        when {
            filteredBills == null -> item { BillsStateCard(unknownBillsMessage(financialSyncState)) }
            filteredBills.isEmpty() -> item {
                BillsStateCard(if (selectedCategory == "הכל") "בסריקה האחרונה שאומתה לא נמצאו חשבונות להצגה." else "אין חשבונות מאומתים בקטגוריה $selectedCategory.")
            }
            else -> items(filteredBills, key = { "bill:${it.sourceMessageId}" }) { bill ->
                PremiumPayableBillCard(
                    bill = bill,
                    opportunity = matchingSavingsOpportunity(bill, opportunities),
                    onOpenSavings = onOpenSavings
                )
            }
        }

        item {
            V3Note("״לא ידוע״ אינו אפס. מועד תשלום, סכום או יעד תשלום שלא אומתו — לא יוצגו כאילו אומתו.")
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val receiptScanKeptForApiCompatibility = onOpenReceiptScan
}

@Composable
private fun PremiumPayableBillCard(
    bill: BackendInvoice,
    opportunity: FinancialOpportunity?,
    onOpenSavings: () -> Unit
) {
    val paymentMode = bill.v3PaymentMode()
    V3Panel(modifier = Modifier.testTag("v3_invoice_item")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Surface(shape = RoundedCornerShape(12.dp), color = V3PrimarySoft) {
                Icon(
                    Icons.AutoMirrored.Filled.ReceiptLong,
                    null,
                    tint = TechBluePrimary,
                    modifier = Modifier.padding(9.dp).size(20.dp)
                )
            }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(bill.providerName.ifBlank { "ספק לא ידוע" }, fontWeight = FontWeight.Bold)
                Text(bill.category.ifBlank { "קטגוריה לא ידועה" }, style = MaterialTheme.typography.bodySmall, color = V3MutedForeground)
            }
            Text(bill.monthlyCost.asV3Money(), fontWeight = FontWeight.Bold)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("מועד לתשלום: לא ידוע", style = MaterialTheme.typography.bodySmall, color = V3MutedForeground)
            Text(
                if (bill.receivedDate.isNotBlank()) bill.receivedDate else "מועד קליטה לא ידוע",
                style = MaterialTheme.typography.bodySmall,
                color = V3MutedForeground
            )
        }

        Text("האם אפשר לחסוך", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = V3MutedForeground)
        if (opportunity == null) {
            Text("אין כרגע חיסכון מאומת לחשבון הזה.", style = MaterialTheme.typography.bodySmall)
        } else {
            val monthly = opportunity.potentialMonthlySaving
            val annual = opportunity.potentialAnnualSaving
            Text(
                if (monthly != null) "חיסכון פוטנציאלי: ${monthly.asV3Money()} בחודש" else "חיסכון חודשי פוטנציאלי: לא ידוע",
                fontWeight = FontWeight.Bold,
                color = TechBluePrimary
            )
            Text(
                if (annual != null) "${annual.asV3Money()} פוטנציאל בשנה" else "חיסכון שנתי פוטנציאלי: לא ידוע",
                style = MaterialTheme.typography.bodySmall
            )
            Text("פוטנציאל לפי הצעה מאומתת — לא חיסכון ממומש.", style = MaterialTheme.typography.bodySmall, color = V3MutedForeground)
            V3SecondaryButton("בדיקת חיסכון לפני התשלום", onOpenSavings, Modifier.fillMaxWidth())
        }

        Text("מעבר לספק לתשלום", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = V3MutedForeground)
        when (paymentMode) {
            V3InvoicePaymentMode.NO_VERIFIED_PAYMENT_TARGET -> Text(
                "אין יעד תשלום מאומת לחשבון הזה.",
                style = MaterialTheme.typography.bodySmall,
                color = V3MutedForeground
            )
            V3InvoicePaymentMode.DIRECT_INVOICE_PAYMENT,
            V3InvoicePaymentMode.PROVIDER_PAYMENT_PORTAL -> Text(
                "יעד תשלום יוצג רק כאשר ניתן לפתוח יעד חשבונית מאומת בפועל.",
                style = MaterialTheme.typography.bodySmall,
                color = V3MutedForeground
            )
        }

        Text(
            "מקור: Gmail בקריאה בלבד · ${verificationLabel(bill.verificationStatus)}",
            style = MaterialTheme.typography.labelSmall,
            color = V3MutedForeground
        )
    }
}

@Composable
private fun BillsStateCard(message: String) {
    V3Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, tint = TechBluePrimary)
            Spacer(Modifier.size(10.dp))
            Text(message)
        }
    }
}

private fun matchingSavingsOpportunity(
    bill: BackendInvoice,
    opportunities: List<FinancialOpportunity>
): FinancialOpportunity? = opportunities
    .asSequence()
    .filter { it.hasAuthoritativeV3Offer() && (it.potentialMonthlySaving ?: 0.0) > 0.0 }
    .filter { opportunity ->
        opportunity.providerName.trim().equals(bill.providerName.trim(), ignoreCase = true) &&
            (bill.category.isBlank() || opportunity.category.trim().equals(bill.category.trim(), ignoreCase = true))
    }
    .maxByOrNull { it.potentialMonthlySaving ?: 0.0 }

private fun unknownBillsMessage(state: FinancialSyncState): String = when (state) {
    FinancialSyncState.Unauthenticated -> "נדרשת התחברות כדי לדעת אילו חשבונות קיימים."
    FinancialSyncState.CheckingConnection -> "מצב החשבונות עדיין לא ידוע; בודקים את החיבור."
    FinancialSyncState.Disconnected -> "Gmail אינו מחובר, ולכן אין כרגע מקור מחובר לחשבונות."
    FinancialSyncState.Recovering -> "החשבונות עדיין נטענים; רשימה ריקה לא תוצג כמידע אמיתי."
    is FinancialSyncState.Partial -> "כיסוי החשבונות עדיין אינו ידוע במלואו."
    is FinancialSyncState.Failed -> "טעינת החשבונות נכשלה; לא נציג רשימה ריקה במקום שגיאה."
    is FinancialSyncState.Ready -> "מצב החשבונות לא ידוע."
}

private fun verificationLabel(status: String): String = when (status.uppercase()) {
    "VERIFIED" -> "אומת"
    "UNVERIFIED", "PENDING" -> "ממתין לאימות"
    else -> "מצב האימות עדיין לא ידוע"
}
