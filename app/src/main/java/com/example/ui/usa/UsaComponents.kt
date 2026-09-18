package com.example.ui.usa

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Danger
import com.example.ui.theme.DangerSoft
import com.example.ui.theme.Forest
import com.example.ui.theme.Gold
import com.example.ui.theme.GoldDark
import com.example.ui.theme.GoldSoft
import com.example.ui.theme.Ink
import com.example.ui.theme.Line
import com.example.ui.theme.Mint
import com.example.ui.theme.MintSoft
import com.example.ui.theme.Muted
import com.example.ui.theme.Paper
import com.example.ui.theme.White

@Composable
fun GuardianTopAppBar(
    title: String? = null,
    onNotificationsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = White,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Line)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Forest),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "C",
                        color = White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title ?: "Click & Save AI",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink
                )
            }
            if (onNotificationsClick != null) {
                IconButton(
                    onClick = onNotificationsClick,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Notifications" }
                ) {
                    Text(
                        text = "♢",
                        fontSize = 20.sp,
                        color = Ink
                    )
                }
            }
        }
    }
}

@Composable
fun DataStateBadge(
    mode: AppDataMode,
    modifier: Modifier = Modifier
) {
    val isDemo = mode == AppDataMode.DEMO
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isDemo) GoldSoft else MintSoft)
            .border(1.dp, if (isDemo) GoldDark.copy(alpha = 0.3f) else Forest.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = if (isDemo) "Illustrative demo" else "Live household data",
            color = if (isDemo) GoldDark else Forest,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics {
                contentDescription = if (isDemo) "Illustrative demo data" else "Live authenticated household data"
            }
        )
    }
}

@Composable
fun FinancialMetricCard(
    label: String,
    value: String,
    suffix: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = White,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    color = Ink,
                    fontWeight = FontWeight.Bold
                )
                if (suffix != null) {
                    Text(
                        text = suffix,
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HouseholdFinancialSummary(
    monitoredAnnual: Double,
    needsReviewCount: Int,
    opportunitiesCount: Int,
    renewalsCount: Int,
    potentialAnnual: Double,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FinancialMetricCard(
                label = "Expenses monitored",
                value = "$${monitoredAnnual.toInt()}",
                suffix = "/yr",
                modifier = Modifier.weight(1f)
            )
            FinancialMetricCard(
                label = "Needs review",
                value = "$needsReviewCount",
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FinancialMetricCard(
                label = "Opportunities",
                value = "$opportunitiesCount",
                modifier = Modifier.weight(1f)
            )
            FinancialMetricCard(
                label = "Renewals ahead",
                value = "$renewalsCount",
                modifier = Modifier.weight(1f)
            )
        }
        // Wide card for potential savings
        Surface(
            color = Forest,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Potential savings identified",
                    style = MaterialTheme.typography.bodySmall,
                    color = Mint
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "$${potentialAnnual.toInt()}",
                        style = MaterialTheme.typography.displayMedium,
                        color = White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "/year",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mint,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun OpportunityCard(
    opportunity: Opportunity,
    onReviewClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = White,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onReviewClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(GoldSoft)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "New opportunity",
                        color = GoldDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = opportunity.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = opportunity.title,
                style = MaterialTheme.typography.titleMedium,
                color = Ink
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = opportunity.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Line)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Current", style = MaterialTheme.typography.bodySmall, color = Muted)
                    Text(
                        "$${opportunity.currentMonthly.toInt()}/mo",
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink
                    )
                }
                Column {
                    Text("Potential", style = MaterialTheme.typography.bodySmall, color = Muted)
                    Text(
                        "$${opportunity.potentialMonthly.toInt()}/mo",
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("May save", style = MaterialTheme.typography.bodySmall, color = Muted)
                    Text(
                        "$${opportunity.annualSavings.toInt()}/yr",
                        style = MaterialTheme.typography.titleMedium,
                        color = Forest,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onReviewClick,
                colors = ButtonDefaults.buttonColors(containerColor = Forest),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Review opportunity", color = White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ServiceMonitoringRow(
    service: MonitoredService,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        color = White,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MintSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = service.icon, fontSize = 16.sp, color = Forest)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = service.name, style = MaterialTheme.typography.titleSmall, color = Ink)
                    Text(text = service.provider, style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                Text(
                    text = service.costDisplay,
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Line)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = service.changeNote, style = MaterialTheme.typography.bodySmall, color = Muted)
                MonitoringStatusChip(status = service.status)
            }
        }
    }
}

@Composable
fun MonitoringStatusChip(
    status: MonitoringStatus,
    modifier: Modifier = Modifier
) {
    val (label, bg, fg) = when (status) {
        MonitoringStatus.OPPORTUNITY -> Triple("Opportunity", GoldSoft, GoldDark)
        MonitoringStatus.WATCHING -> Triple("Watching", MintSoft, Forest)
        MonitoringStatus.RENEWAL -> Triple("Renewal", GoldSoft, GoldDark)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SavingsStageSummary(
    potentialMonthly: Double,
    expectedMonthly: Double,
    verifiedMonthly: Double,
    modifier: Modifier = Modifier
) {
    Surface(
        color = White,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Potential", style = MaterialTheme.typography.bodySmall, color = Muted)
                Text(
                    "$${potentialMonthly.toInt()}/mo",
                    style = MaterialTheme.typography.titleLarge,
                    color = Ink,
                    fontWeight = FontWeight.Bold
                )
            }
            Column {
                Text("Expected", style = MaterialTheme.typography.bodySmall, color = Muted)
                Text(
                    "$${expectedMonthly.toInt()}/mo",
                    style = MaterialTheme.typography.titleLarge,
                    color = Ink,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Verified", style = MaterialTheme.typography.bodySmall, color = Muted)
                Text(
                    "$${verifiedMonthly.toInt()}",
                    style = MaterialTheme.typography.titleLarge,
                    color = Forest,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrueCostBottomSheet(
    details: TrueCostDetails,
    onDismiss: () -> Unit,
    onContinueToChoices: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = White,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "True cost comparison",
                style = MaterialTheme.typography.titleLarge,
                color = Ink
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Savings should be real, not just look good on paper.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
            Spacer(modifier = Modifier.height(16.dp))

            val costRows = listOf(
                "Monthly service" to "$${details.monthlyService.toInt()}",
                "Equipment" to "$${details.equipment.toInt()}",
                "Activation" to "$${details.activation.toInt()}",
                "Termination cost" to "$${details.terminationCost.toInt()}",
                "Lost bundle discount" to "$${details.lostBundleDiscount.toInt()}",
                "First-year total" to "$${details.firstYearTotal.toInt()}",
                "Post-promo price" to details.postPromoPrice
            )

            costRows.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Ink)
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                }
                HorizontalDivider(color = Line.copy(alpha = 0.5f))
            }

            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Paper)
                    .padding(12.dp)
            ) {
                Text(
                    text = "Illustrative terms. Eligibility and final provider terms require verification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onContinueToChoices,
                colors = ButtonDefaults.buttonColors(containerColor = Forest),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Continue to choices", color = White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecisionBottomSheet(
    onDismiss: () -> Unit,
    onHandoff: () -> Unit,
    onKeepCurrent: () -> Unit,
    onRemindLater: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = White,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "What would you like to do?",
                style = MaterialTheme.typography.titleLarge,
                color = Ink
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Nothing changes until you explicitly choose a next step.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Paper)
                    .padding(10.dp)
            ) {
                Text(
                    text = "Click & Save AI may receive compensation from participating providers. Compensation must not determine how opportunities are presented or ranked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
            }
            Spacer(modifier = Modifier.height(14.dp))

            // Choice 1: Open provider handoff
            Surface(
                color = Paper,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onHandoff)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Open provider handoff", style = MaterialTheme.typography.titleSmall, color = Ink)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Continue to the provider or supported action flow", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Choice 2: Keep current plan
            Surface(
                color = Paper,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onKeepCurrent)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Keep current plan", style = MaterialTheme.typography.titleSmall, color = Ink)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Dismiss this opportunity and continue monitoring", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Choice 3: Remind me later
            Surface(
                color = Paper,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRemindLater)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Remind me later", style = MaterialTheme.typography.titleSmall, color = Ink)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Return to this review next week", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Cancel", color = Ink, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun GuardianTimelineRow(
    event: GuardianTimelineEvent,
    modifier: Modifier = Modifier
) {
    Surface(
        color = White,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MintSoft),
                contentAlignment = Alignment.Center
            ) {
                Text(text = event.iconSymbol, color = Forest, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = event.timeLabel, style = MaterialTheme.typography.labelMedium, color = Muted)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = event.title, style = MaterialTheme.typography.titleSmall, color = Ink)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = event.detail, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
    }
}

@Composable
fun ConnectedAccountRow(
    account: ConnectedAccount,
    onReviewPermissions: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = White,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MintSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = account.iconSymbol, color = Forest, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = account.name, style = MaterialTheme.typography.titleSmall, color = Ink)
                    Text(text = "${account.purpose} · Connected", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MintSoft)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(text = account.status, color = Forest, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Line)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onReviewPermissions,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Review permissions", color = Ink)
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onDisconnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Disconnect", color = Danger, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun TruthfulEmptyState(
    emoji: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = White,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MintSoft),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 22.sp, color = Forest)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Ink,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun OfflineBanner(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = White,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "You're offline · Showing last updated information",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Try again", color = Ink)
            }
        }
    }
}

@Composable
fun GuardianBottomNavigation(
    currentTab: UsaTab,
    onTabSelected: (UsaTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = White,
        shadowElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Line)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                Triple(UsaTab.HOME, "⌂", "Home"),
                Triple(UsaTab.BILLS, "▤", "Bills"),
                Triple(UsaTab.SAVINGS, "↓", "Savings"),
                Triple(UsaTab.ACTIVITY, "◷", "Activity"),
                Triple(UsaTab.PROFILE, "○", "Profile")
            )
            tabs.forEach { (tab, icon, label) ->
                val isSelected = currentTab == tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            role = Role.Tab,
                            onClick = { onTabSelected(tab) }
                        )
                        .padding(vertical = 4.dp)
                        .semantics {
                            contentDescription = label
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = icon,
                        fontSize = 18.sp,
                        color = if (isSelected) Forest else Muted,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Forest else Muted
                    )
                }
            }
        }
    }
}
