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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
fun UsaSavingsScreen(
    dataMode: AppDataMode,
    opportunity: Opportunity,
    onReviewOpportunity: () -> Unit,
    onKeepPlanClick: () -> Unit = {},
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
        Column {
            DataStateBadge(mode = dataMode)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Ways your household may save",
                style = MaterialTheme.typography.displaySmall,
                color = Ink
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Potential, expected, and verified savings are kept separate.",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted
            )
        }

        SavingsStageSummary(
            potentialMonthly = 197.0,
            expectedMonthly = 24.0,
            verifiedMonthly = 0.0
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Opportunities",
                style = MaterialTheme.typography.titleLarge,
                color = Ink
            )
            Text(
                text = "Household relevance",
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
        }

        OpportunityCard(
            opportunity = opportunity,
            onReviewClick = onReviewOpportunity
        )

        // Stay recommended card
        Surface(
            color = White,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MintSoft)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Stay recommended",
                        color = Forest,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Mobile plan checked",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "No better verified option was found after coverage and true cost were considered.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onKeepPlanClick,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("Keep current plan", color = Ink)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
