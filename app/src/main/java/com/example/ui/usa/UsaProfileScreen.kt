package com.example.ui.usa

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
fun UsaProfileScreen(
    onHouseholdClick: () -> Unit,
    onAccountsClick: () -> Unit,
    onMonitoringClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onSecurityClick: () -> Unit,
    onHelpClick: () -> Unit,
    onStatesClick: () -> Unit,
    onSignOutClick: () -> Unit,
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
        Column {
            Text(
                text = "Profile & control",
                style = MaterialTheme.typography.displaySmall,
                color = Ink
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "See what is connected, monitored, and permitted.",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        val menuItems = listOf(
            ProfileMenuItem("⌂", "Household", "3 of 7 optional details added", onHouseholdClick),
            ProfileMenuItem("↗", "Connected accounts", "1 authorized connection", onAccountsClick),
            ProfileMenuItem("◎", "Monitoring preferences", "8 categories enabled", onMonitoringClick),
            ProfileMenuItem("♢", "Notifications", "High-value events only", onNotificationsClick),
            ProfileMenuItem("◈", "Privacy & data", "Review permissions and controls", onPrivacyClick),
            ProfileMenuItem("◇", "Security", "Sign-in and device settings", onSecurityClick),
            ProfileMenuItem("?", "Help & About", "Support and product guidance", onHelpClick),
            ProfileMenuItem("✦", "State library", "Edge cases & truthful states", onStatesClick)
        )

        Surface(
            color = White,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                menuItems.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(onClick = item.onClick)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MintSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = item.icon, fontSize = 16.sp, color = Forest)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = item.title, style = MaterialTheme.typography.titleSmall, color = Ink)
                            Text(text = item.subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                        Text(text = "›", fontSize = 20.sp, color = Muted)
                    }
                    if (index < menuItems.size - 1) {
                        androidx.compose.material3.HorizontalDivider(
                            color = Line.copy(alpha = 0.5f),
                            modifier = Modifier.padding(start = 66.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        TextButton(
            onClick = onSignOutClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text("Sign out", color = Danger, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

private data class ProfileMenuItem(
    val icon: String,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)
