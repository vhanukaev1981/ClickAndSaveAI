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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
fun UsaOpportunityDetailScreen(
    dataMode: AppDataMode,
    opportunity: Opportunity,
    onBackClick: () -> Unit,
    onCompareTrueCost: () -> Unit,
    onReviewChoices: () -> Unit,
    onRemindLater: () -> Unit,
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
            Text("← Savings", color = Forest, fontWeight = FontWeight.SemiBold)
        }

        DataStateBadge(mode = dataMode)

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(GoldSoft)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text("New opportunity", color = GoldDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        Text(
            text = opportunity.title,
            style = MaterialTheme.typography.headlineMedium,
            color = Ink
        )

        Text(
            text = "Your current 500 Mbps plan is now $94/month. A comparable option is shown at an estimated $62/month.",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted
        )

        Surface(
            color = White,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Potential difference", style = MaterialTheme.typography.bodySmall, color = Muted)
                        Text(
                            "$${(opportunity.currentMonthly - opportunity.potentialMonthly).toInt()}/month",
                            style = MaterialTheme.typography.titleLarge,
                            color = Ink,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Annual estimate", style = MaterialTheme.typography.bodySmall, color = Muted)
                        Text(
                            "$${opportunity.annualSavings.toInt()}",
                            style = MaterialTheme.typography.titleLarge,
                            color = Forest,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Line)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Comparable speed tier · Availability and eligibility must be verified · No action has been taken",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
            }
        }

        Text(
            text = "Why this may be better",
            style = MaterialTheme.typography.titleMedium,
            color = Ink
        )

        Surface(
            color = White,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Lower estimated true cost with a comparable speed tier. Keeping your current plan remains a valid choice.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onCompareTrueCost,
                colors = ButtonDefaults.buttonColors(containerColor = Forest),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Compare true cost", color = White, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onReviewChoices,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Review your choices", color = Ink, fontWeight = FontWeight.SemiBold)
            }

            TextButton(
                onClick = onRemindLater,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Remind me later", color = Muted)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun UsaActionHandoffScreen(
    onBackClick: () -> Unit,
    onOpenHandoff: () -> Unit,
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
            Text("← Opportunity", color = Forest, fontWeight = FontWeight.SemiBold)
        }

        Text(
            text = "YOU REMAIN IN CONTROL",
            style = MaterialTheme.typography.labelSmall,
            color = Forest,
            letterSpacing = 0.08.sp
        )

        Text(
            text = "Continue with the provider",
            style = MaterialTheme.typography.displaySmall,
            color = Ink
        )

        Text(
            text = "Review final availability, terms, and pricing before you authorize any service change.",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MintSoft)
                .border(1.dp, Forest.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(
                text = "Opening a provider handoff is not approval to switch. Final provider terms may differ.",
                style = MaterialTheme.typography.bodySmall,
                color = Ink
            )
        }

        Surface(
            color = White,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Before you continue",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink
                )
                Spacer(modifier = Modifier.height(12.dp))
                val checklist = listOf(
                    "Confirm service address",
                    "Review final monthly and first-year cost",
                    "Check installation and termination terms",
                    "Approve only in the supported provider flow"
                )
                checklist.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "✓", color = Forest, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = item, style = MaterialTheme.typography.bodyMedium, color = Ink)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onOpenHandoff,
            colors = ButtonDefaults.buttonColors(containerColor = Forest),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text("Open provider handoff", color = White, fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
            onClick = onBackClick,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text("Not now", color = Ink)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun UsaContinuedMonitoringScreen(
    onSimulateNewBill: () -> Unit,
    onReturnHome: () -> Unit,
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
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MintSoft)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text("Monitoring continues", color = Forest, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        Text(
            text = "We’ll watch what happens next",
            style = MaterialTheme.typography.displaySmall,
            color = Ink
        )

        Text(
            text = "This reference records that a handoff was opened. It does not assume a provider transaction occurred.",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted
        )

        Surface(
            color = White,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MintSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "⌁", fontSize = 18.sp, color = Forest)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text("Internet opportunity", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Handoff opened · Outcome not yet confirmed", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSimulateNewBill,
            colors = ButtonDefaults.buttonColors(containerColor = Forest),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text("Simulate new bill arrival", color = White, fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
            onClick = onReturnHome,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text("Return home", color = Ink)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun UsaNewBillReviewScreen(
    onReviewVerification: () -> Unit,
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
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(GoldSoft)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text("Review needed", color = GoldDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        Text(
            text = "A new internet bill is ready to verify",
            style = MaterialTheme.typography.displaySmall,
            color = Ink
        )

        Text(
            text = "Compare the expected amount with the actual bill before counting savings as verified.",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted
        )

        Surface(
            color = White,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Expected bill", style = MaterialTheme.typography.bodySmall, color = Muted)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "$64.99",
                        style = MaterialTheme.typography.displaySmall,
                        color = Ink,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Actual new bill", style = MaterialTheme.typography.bodySmall, color = Muted)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "$66.21",
                        style = MaterialTheme.typography.displaySmall,
                        color = Ink,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onReviewVerification,
            colors = ButtonDefaults.buttonColors(containerColor = Forest),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text("Review verification", color = White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun UsaSavingsVerificationScreen(
    onContinueMonitoring: () -> Unit,
    onViewActivity: () -> Unit,
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
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MintSoft)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text("Savings verified", color = Forest, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        Text(
            text = "Your new bill is close to the expected amount",
            style = MaterialTheme.typography.displaySmall,
            color = Ink
        )

        Text(
            text = "The difference is within the reviewed terms for this illustrative example.",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted
        )

        // Success banner
        Surface(
            color = Forest,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Verified saving", style = MaterialTheme.typography.bodySmall, color = Mint)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "$23.79/month",
                    style = MaterialTheme.typography.displayMedium,
                    color = White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Potential is now counted as verified for this billing cycle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Mint
                )
            }
        }

        // Cost table
        Surface(
            color = White,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val items = listOf(
                    "Previous bill" to "$90.00",
                    "Expected new bill" to "$64.99",
                    "Actual new bill" to "$66.21"
                )
                items.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Ink)
                        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Ink)
                    }
                    HorizontalDivider(color = Line.copy(alpha = 0.5f))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Verified difference", style = MaterialTheme.typography.titleMedium, color = Forest, fontWeight = FontWeight.Bold)
                    Text("$23.79", style = MaterialTheme.typography.titleMedium, color = Forest, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onContinueMonitoring,
            colors = ButtonDefaults.buttonColors(containerColor = Forest),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text("Continue monitoring", color = White, fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
            onClick = onViewActivity,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text("View activity", color = Ink)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
