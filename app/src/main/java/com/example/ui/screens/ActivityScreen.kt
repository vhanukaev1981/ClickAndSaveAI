package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.FinancialActivityEvent
import com.example.data.repository.FinancialActivityResult
import com.example.data.repository.FinancialRefreshReason
import com.example.data.repository.FinancialSyncState
import com.example.data.repository.activityOrNull
import com.example.ui.MainViewModel

@Composable
fun ActivityScreen(viewModel: MainViewModel) {
    val financialSyncState by viewModel.financialSyncState.collectAsState()
    val authoritativeFinancialActivity by viewModel.authoritativeFinancialActivity.collectAsState()
    val ledger = financialSyncState.activityOrNull ?: authoritativeFinancialActivity

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("פעילות", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "כאן מוצגים רק אירועים שנשמרו במקור הסמכותי. מצב UI נוכחי אינו הופך אוטומטית להיסטוריה.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        when (val state = financialSyncState) {
            FinancialSyncState.Unauthenticated -> ActivityStatusCard(
                "החשבון אינו מחובר",
                "לא ניתן לדעת את פעילות החשבון לפני התחברות מאומתת."
            )
            FinancialSyncState.CheckingConnection -> ActivityStatusCard(
                "בודקים את החיבור",
                "יומן הפעילות עדיין לא ידוע."
            )
            FinancialSyncState.Disconnected -> ActivityStatusCard(
                "Gmail אינו מחובר",
                "אין כרגע מקור Gmail מחובר שממנו ניתן לשחזר אירועים חדשים."
            )
            FinancialSyncState.Recovering -> ActivityStatusCard(
                "משחזרים את יומן הפעילות",
                "לא יוצגו אירועים משוערים בזמן הטעינה."
            )
            is FinancialSyncState.Failed -> {
                ActivityStatusCard("טעינת הפעילות נכשלה", state.reason)
                RetryActivity(viewModel)
            }
            is FinancialSyncState.Partial -> {
                ActivityStatusCard(
                    "הכיסוי חלקי",
                    "ייתכן שחסרים אירועים חדשים. מוצגים רק אירועים שכבר קיימים ב-ledger הסמכותי."
                )
                AuthoritativeLedger(ledger)
                RetryActivity(viewModel)
            }
            is FinancialSyncState.Ready -> AuthoritativeLedger(ledger)
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun AuthoritativeLedger(activity: FinancialActivityResult?) {
    if (activity == null) {
        ActivityStatusCard("מצב הפעילות לא ידוע", "לא התקבלה תשובת ledger סמכותית.")
        return
    }
    if (activity.events.isEmpty()) {
        ActivityStatusCard(
            "אין אירועים מתועדים בחלון הכיסוי הזמין",
            if (activity.isCompleteHistory) {
                "המקור הסמכותי מסמן שההיסטוריה המכוסה מלאה."
            } else {
                "היסטוריה מלאה אינה ידועה; רשימה זו אינה הוכחה שמעולם לא הייתה פעילות."
            }
        )
        return
    }

    activity.events.forEach { event -> ActivityEventCard(event) }
    if (!activity.isCompleteHistory) {
        Text(
            "כיסוי היסטורי חלקי • ${activity.sourceCoverage.joinToString()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActivityEventCard(event: FinancialActivityEvent) {
    val title = when (event.type) {
        "GMAIL_CONNECTED" -> "Gmail חובר לקריאה בלבד"
        "SCAN_COMPLETED" -> "סריקת Gmail הושלמה"
        "BILL_DETECTED" -> "זוהה חשבון"
        "RECURRING_SERVICE_DETECTED" -> "זוהה שירות חוזר"
        "OPPORTUNITY_FOUND" -> "נמצאה הזדמנות"
        else -> event.type
    }
    val details = buildList {
        add("מצב: ${event.status}")
        event.providerName?.let { add("ספק: $it") }
        event.category?.let { add("קטגוריה: $it") }
        event.observedAmount?.let { add("סכום שנצפה: ${money(it)}") }
        event.verificationStatus?.let { add("אימות: $it") }
        add("יעד: ${event.destination}")
    }.joinToString(" • ")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                if (event.type == "GMAIL_CONNECTED") Icons.Outlined.MailOutline else Icons.Outlined.History,
                contentDescription = null
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(event.timestamp, style = MaterialTheme.typography.labelSmall)
                Text(details, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ActivityStatusCard(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun RetryActivity(viewModel: MainViewModel) {
    Button(onClick = { viewModel.refreshFinancialSession(FinancialRefreshReason.RETRY) }) {
        Icon(Icons.Outlined.CloudSync, contentDescription = null)
        Spacer(Modifier.padding(3.dp))
        Text("נסה שוב")
    }
}
