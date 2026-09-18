package com.example.ui.usa

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Danger
import com.example.ui.theme.Forest
import com.example.ui.theme.Ink
import com.example.ui.theme.Line
import com.example.ui.theme.MintSoft
import com.example.ui.theme.Muted
import com.example.ui.theme.Paper
import com.example.ui.theme.White

@Composable
fun UsaConnectedAccountsScreen(
    accounts: List<ConnectedAccount>,
    onBackClick: () -> Unit,
    onDisconnectConfirmed: (ConnectedAccount) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var accountToDisconnect by remember { mutableStateOf<ConnectedAccount?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(
            onClick = onBackClick,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text("← Profile", color = Forest, fontWeight = FontWeight.SemiBold)
        }

        Text(
            text = "Connected accounts",
            style = MaterialTheme.typography.displaySmall,
            color = Ink
        )

        Text(
            text = "Control what is connected and what may be monitored.",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted
        )

        accounts.forEach { account ->
            ConnectedAccountRow(
                account = account,
                onReviewPermissions = {},
                onDisconnect = { accountToDisconnect = account }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MintSoft)
                .border(1.dp, Forest.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(
                text = "Account deletion, data deletion, and connection revocation behavior require backend confirmation before implementation.",
                style = MaterialTheme.typography.bodySmall,
                color = Ink
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (accountToDisconnect != null) {
        AlertDialog(
            onDismissRequest = { accountToDisconnect = null },
            title = { Text("Disconnect account?", color = Ink, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will disconnect recurring bill discovery. You can reconnect at any time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val acc = accountToDisconnect
                        accountToDisconnect = null
                        if (acc != null) onDisconnectConfirmed(acc)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Danger)
                ) {
                    Text("Disconnect", color = White)
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDisconnect = null }) {
                    Text("Cancel", color = Ink)
                }
            },
            containerColor = White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun UsaHouseholdProfileScreen(
    details: List<HouseholdDetailItem>,
    onBackClick: () -> Unit,
    onAddDetailClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(
            onClick = onBackClick,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text("← Profile", color = Forest, fontWeight = FontWeight.SemiBold)
        }

        Text(
            text = "Household profile",
            style = MaterialTheme.typography.displaySmall,
            color = Ink
        )

        Text(
            text = "Add details gradually, only when they improve a recommendation.",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted
        )

        details.forEach { detail ->
            Surface(
                color = White,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = detail.label, style = MaterialTheme.typography.titleSmall, color = Ink)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = detail.value, style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MintSoft)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(text = detail.tag, color = Forest, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Button(
            onClick = onAddDetailClick,
            colors = ButtonDefaults.buttonColors(containerColor = Forest),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text("Add a useful detail", color = White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun UsaNotificationsScreen(
    notifications: List<NotificationItem>,
    onBackClick: () -> Unit,
    onNotificationAction: (NotificationItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(
            onClick = onBackClick,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text("← Back", color = Forest, fontWeight = FontWeight.SemiBold)
        }

        Text(
            text = "Notifications",
            style = MaterialTheme.typography.displaySmall,
            color = Ink
        )

        Text(
            text = "High-value alerts say what happened, why it matters, and what to do.",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted
        )

        notifications.forEach { notif ->
            Surface(
                color = White,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = notif.title.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Forest,
                        letterSpacing = 0.08.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = notif.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(
                        onClick = { onNotificationAction(notif) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("${notif.actionText} →", color = Forest, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun UsaStateLibraryScreen(
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(
            onClick = onBackClick,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text("← Profile", color = Forest, fontWeight = FontWeight.SemiBold)
        }

        Text(
            text = "Truthful product states",
            style = MaterialTheme.typography.displaySmall,
            color = Ink
        )

        Text(
            text = "Reference patterns for edge cases.",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted
        )

        TruthfulEmptyState(
            emoji = "✓",
            title = "Nothing worth changing right now",
            subtitle = "We’ll keep watching."
        )

        TruthfulEmptyState(
            emoji = "↗",
            title = "Connect an account",
            subtitle = "Authorize a connection so monitoring can begin."
        )

        TruthfulEmptyState(
            emoji = "?",
            title = "No verified option yet",
            subtitle = "We found the increase, but haven’t verified a better option yet."
        )

        OfflineBanner(onRetry = onRetry)

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun UsaGenericSettingsScreen(
    title: String,
    description: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(
            onClick = onBackClick,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text("← Profile", color = Forest, fontWeight = FontWeight.SemiBold)
        }

        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            color = Ink
        )

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = Muted
        )

        Surface(
            color = White,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Controls and policy settings are active and monitored.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
