package com.example.ui.usa

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.example.ui.theme.Forest
import com.example.ui.theme.Ink
import com.example.ui.theme.Line
import com.example.ui.theme.MintSoft
import com.example.ui.theme.Muted
import com.example.ui.theme.Paper
import com.example.ui.theme.White

@Composable
fun UsaHomeScreen(
    dataMode: AppDataMode,
    summary: HouseholdSummary,
    opportunity: Opportunity,
    onReviewOpportunity: () -> Unit,
    onViewActivity: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Hero
        DataStateBadge(mode = dataMode)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Good morning, ${summary.householdName}",
            style = MaterialTheme.typography.displaySmall,
            color = Ink
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (dataMode == AppDataMode.DEMO) {
                "Explore how household monitoring could work."
            } else {
                "Your household is being watched for unnecessary costs."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Muted
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Financial summary grid
        HouseholdFinancialSummary(
            monitoredAnnual = summary.monitoredAnnual,
            needsReviewCount = summary.needsReviewCount,
            opportunitiesCount = summary.opportunitiesCount,
            renewalsCount = summary.renewalsCount,
            potentialAnnual = summary.potentialAnnual
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Needs your attention
        Column {
            Text(
                text = "NEEDS YOUR ATTENTION",
                style = MaterialTheme.typography.labelSmall,
                color = Forest,
                letterSpacing = 0.08.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Worth reviewing",
                style = MaterialTheme.typography.titleLarge,
                color = Ink
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OpportunityCard(
            opportunity = opportunity,
            onReviewClick = onReviewOpportunity
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Monitoring now / Watchlist
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "MONITORING NOW",
                    style = MaterialTheme.typography.labelSmall,
                    color = Forest,
                    letterSpacing = 0.08.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Guardian watchlist",
                    style = MaterialTheme.typography.titleLarge,
                    color = Ink
                )
            }
            TextButton(onClick = onViewActivity) {
                Text("View activity", color = Forest, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            color = White,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onViewActivity)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MintSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "⚡", fontSize = 16.sp, color = Forest)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "2 renewals approaching",
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Energy in 38 days · Insurance in 23 days",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted
                    )
                }
                Text(text = "›", fontSize = 20.sp, color = Muted)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
