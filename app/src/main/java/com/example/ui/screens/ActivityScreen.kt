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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ErrorOutline
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
import com.example.data.repository.FinancialRefreshReason
import com.example.data.repository.FinancialSyncState
import com.example.ui.MainViewModel

@Composable
fun ActivityScreen(viewModel: MainViewModel) {
    val financialSyncState by viewModel.financialSyncState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "פעילות",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "כאן מוצגים רק אירועים שניתן לאמת מהסנכרון הנוכחי.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        when (val state = financialSyncState) {
            FinancialSyncState.Unauthenticated -> ActivityStatusCard(
                title = "החשבון אינו מחובר",
                description = "לא נטען מידע פיננסי עד להתחברות מאומתת.",
                icon = { Icon(Icons.Outlined.MailOutline, contentDescription = null) }
            )

            FinancialSyncState.CheckingConnection -> ActivityStatusCard(
                title = "בודקים את החיבור",
                description = "מאמתים את מקור Gmail בלי להציג התקדמות משוערת.",
                icon = { Icon(Icons.Outlined.CloudSync, contentDescription = null) }
            )

            FinancialSyncState.Disconnected -> ActivityStatusCard(
                title = "Gmail אינו מחובר",
                description = "כדי לשחזר חשבונות מהמקור יש לאשר גישת קריאה בלבד.",
                icon = { Icon(Icons.Outlined.MailOutline, contentDescription = null) }
            )

            FinancialSyncState.Recovering -> ActivityStatusCard(
                title = "מסנכרנים את המידע המאומת שלך",
                description = "חשבונות והקשר פיננסי נטענים מהשרת. לא מוצגים ערכי אפס במקום מידע שעדיין אינו ידוע.",
                icon = { Icon(Icons.Outlined.CloudSync, contentDescription = null) }
            )

            is FinancialSyncState.Ready -> {
                ActivityStatusCard(
                    title = "הסנכרון הושלם",
                    description = "החיבור המאומת פעיל והמידע הפיננסי הנוכחי נטען.",
                    icon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) }
                )
                if (state.latestScan.invoices.isNotEmpty()) {
                    ActivityStatusCard(
                        title = "זוהו חשבונות מהמקור המחובר",
                        description = "החשבונות שזוהו זמינים במסך החשבונות.",
                        icon = { Icon(Icons.Outlined.MailOutline, contentDescription = null) }
                    )
                }
            }

            is FinancialSyncState.Partial -> {
                ActivityStatusCard(
                    title = "המידע האחרון נשמר",
                    description = "חלק מהסנכרון אינו זמין כרגע. ערכים שלא אומתו נשארים לא ידועים.",
                    icon = { Icon(Icons.Outlined.ErrorOutline, contentDescription = null) }
                )
                Button(onClick = { viewModel.refreshFinancialSession(FinancialRefreshReason.RETRY) }) {
                    Text("נסה שוב")
                }
            }

            is FinancialSyncState.Failed -> {
                ActivityStatusCard(
                    title = "הסנכרון לא הושלם",
                    description = "לא הומצאו נתונים חלופיים. אפשר לנסות שוב.",
                    icon = { Icon(Icons.Outlined.ErrorOutline, contentDescription = null) }
                )
                Button(onClick = { viewModel.refreshFinancialSession(FinancialRefreshReason.RETRY) }) {
                    Text("נסה שוב")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun ActivityStatusCard(
    title: String,
    description: String,
    icon: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            icon()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
