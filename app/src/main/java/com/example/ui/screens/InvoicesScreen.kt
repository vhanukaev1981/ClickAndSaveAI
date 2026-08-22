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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.BackendInvoice
import com.example.data.repository.FinancialOpportunity
import com.example.data.repository.FinancialSyncState
import com.example.data.repository.latestScanOrNull
import com.example.ui.MainViewModel
import com.example.ui.components.V3Panel
import com.example.ui.components.V3ScreenHeader
import com.example.ui.components.V3SecondaryButton
import com.example.ui.components.V3SectionHeader
import com.example.ui.theme.TechBluePrimary
import com.example.ui.theme.V3PrimarySoft
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
                eyebrow = "חשבונות מאומתים",
                title = "לתשלום",
                subtitle = "חשבונות שנקלטו ממקור מאומת. שדה שלא קיים בנתונים נשאר לא ידוע."
            )
        }

        item { V3SectionHeader("מה נכנס לתשלום") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) },
                        shape = RoundedCornerShape(999.dp)
                    )
                }
            }
        }
        when {
            filteredBills == null -> item { BillsStateCard(unknownBillsMessage(financialSyncState)) }
            filteredBills.isEmpty() -> item {
                BillsStateCard(if (selectedCategory == "הכל") "בסריקה האחרונה שאומתה לא נמצאו חשבונות להצגה." else "אין חשבונות מאומתים בקטגוריה $selectedCategory.")
            }
            else -> items(filteredBills, key = { "bill:${it.sourceMessageId}" }) { bill -> PayableBillCard(bill) }
        }

        item { V3SectionHeader("האם אפשר לחסוך") }
        when {
            filteredBills == null -> item { BillsStateCard("אין מספיק מידע מאומת כדי לקשור הזדמנות חיסכון לחשבון.") }
            filteredBills.isEmpty() -> item { BillsStateCard("אין כרגע חשבון מאומת שניתן לבדוק מול הזדמנות חיסכון.") }
            else -> items(filteredBills, key = { "saving:${it.sourceMessageId}" }) { bill ->
                InvoiceSavingCard(
                    bill = bill,
                    opportunity = matchingSavingsOpportunity(bill, opportunities),
                    onOpenSavings = onOpenSavings
                )
            }
        }

        item { V3SectionHeader("מעבר לספק לתשלום") }
        item {
            V3Panel(containerColor = V3PrimarySoft) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, null, tint = TechBluePrimary)
                    Spacer(Modifier.size(10.dp))
                    Text("Click & Save לא גובה כסף ולא משלם עבורך. מעבר לתשלום יוצג רק כשקיים יעד תשלום מאומת לחשבון עצמו.")
                }
            }
        }
        if (filteredBills != null) {
            items(filteredBills, key = { "payment:${it.sourceMessageId}" }) { bill -> PaymentTargetCard(bill) }
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val receiptScanKeptForApiCompatibility = onOpenReceiptScan
}

@Composable
private fun PayableBillCard(bill: BackendInvoice) {
    V3Panel(modifier = Modifier.testTag("v3_invoice_item")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, tint = TechBluePrimary)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(bill.providerName.ifBlank { "ספק לא ידוע" }, fontWeight = FontWeight.Bold)
                Text(bill.category.ifBlank { "קטגוריה לא ידועה" }, style = MaterialTheme.typography.bodySmall)
            }
            Text(bill.monthlyCost.asV3Money(), fontWeight = FontWeight.Bold)
        }
        Text("מועד לתשלום: לא ידוע", style = MaterialTheme.typography.bodySmall)
        Text(
            if (bill.receivedDate.isNotBlank()) "זוהה ב: ${bill.receivedDate}" else "מועד הקליטה לא ידוע",
            style = MaterialTheme.typography.bodySmall
        )
        Text("מקור: Gmail בקריאה בלבד • ${verificationLabel(bill.verificationStatus)}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InvoiceSavingCard(
    bill: BackendInvoice,
    opportunity: FinancialOpportunity?,
    onOpenSavings: () -> Unit
) {
    V3Panel {
        Text(bill.providerName.ifBlank { "ספק לא ידוע" }, fontWeight = FontWeight.Bold)
        if (opportunity == null) {
            Text("אין כרגע חיסכון מאומת לחשבון הזה. ערך חסר אינו מוצג כאפס.")
        } else {
            val monthly = opportunity.potentialMonthlySaving
            val annual = opportunity.potentialAnnualSaving
            Text(
                if (monthly != null) "חיסכון פוטנציאלי: ${monthly.asV3Money()} בחודש" else "חיסכון חודשי פוטנציאלי: לא ידוע",
                fontWeight = FontWeight.SemiBold,
                color = TechBluePrimary
            )
            Text(if (annual != null) "חיסכון פוטנציאלי: ${annual.asV3Money()} בשנה" else "חיסכון שנתי פוטנציאלי: לא ידוע")
            Text("זהו פוטנציאל לפי הצעה מאומתת — לא חיסכון ממומש.", style = MaterialTheme.typography.bodySmall)
            V3SecondaryButton("לבדיקת חיסכון", onOpenSavings, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PaymentTargetCard(bill: BackendInvoice) {
    val paymentMode = bill.v3PaymentMode()
    V3Panel {
        Text(bill.providerName.ifBlank { "ספק לא ידוע" }, fontWeight = FontWeight.Bold)
        when (paymentMode) {
            V3InvoicePaymentMode.NO_VERIFIED_PAYMENT_TARGET -> {
                Text("אין יעד תשלום מאומת לחשבון הזה.")
                Text("כפתור תשלום יוצג רק לאחר שיתקבל יעד תשלום מאומת לחשבונית עצמה.", style = MaterialTheme.typography.bodySmall)
            }
            V3InvoicePaymentMode.DIRECT_INVOICE_PAYMENT,
            V3InvoicePaymentMode.PROVIDER_PAYMENT_PORTAL -> {
                Text("מצב יעד תשלום קיים בחוזה, אך לא יוצג כפתור פעולה ללא יעד חשבונית מאומת שניתן לפתוח בפועל.")
            }
        }
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
