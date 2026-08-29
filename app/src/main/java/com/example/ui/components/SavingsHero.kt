package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.EmeraldSavingsDark
import com.example.ui.theme.TechBluePrimary
import com.example.ui.theme.V3BlueSoft
import com.example.ui.theme.V3EmeraldSoft
import com.example.ui.theme.V3MutedForeground
import com.example.ui.theme.V3PrimarySoft
import com.example.ui.v3.asV3Money

@Composable
fun SavingsHero(
    realizedMonthly: Double?,
    potentialMonthly: Double?,
    realizedAnnual: Double? = null,
    potentialAnnual: Double? = null,
    realizedKnownZero: Boolean = false,
    modifier: Modifier = Modifier
) {
    V3Panel(
        modifier = modifier.testTag("v3_savings_hero"),
        containerColor = V3PrimarySoft,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = V3PrimarySoft) {
                SavingsGlyph(
                    modifier = Modifier.padding(7.dp).size(20.dp),
                    contentDescription = "חיסכון"
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text("החיסכון שלך", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "מומש מול פוטנציאל — בלי לערבב ביניהם",
                    style = MaterialTheme.typography.labelSmall,
                    color = V3MutedForeground
                )
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 300.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RealizedCompactMetric(realizedMonthly, realizedAnnual, realizedKnownZero, Modifier.fillMaxWidth())
                    PotentialCompactMetric(potentialMonthly, potentialAnnual, Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    RealizedCompactMetric(realizedMonthly, realizedAnnual, realizedKnownZero, Modifier.weight(1f))
                    PotentialCompactMetric(potentialMonthly, potentialAnnual, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RealizedCompactMetric(
    realizedMonthly: Double?,
    realizedAnnual: Double?,
    realizedKnownZero: Boolean,
    modifier: Modifier
) {
    CompactSavingsMetric(
        modifier = modifier.testTag("v3_realized_savings"),
        label = "חיסכון שמומש",
        value = when {
            realizedMonthly != null -> realizedMonthly.asV3Money()
            realizedKnownZero -> 0.0.asV3Money()
            else -> "לא ידוע"
        },
        supporting = when {
            realizedMonthly != null || realizedKnownZero -> "שנתי: ${realizedAnnual?.asV3Money() ?: "לא ידוע"}"
            else -> "עדיין אין ראיה מספקת"
        },
        containerColor = V3EmeraldSoft,
        valueColor = EmeraldSavingsDark
    )
}

@Composable
private fun PotentialCompactMetric(
    potentialMonthly: Double?,
    potentialAnnual: Double?,
    modifier: Modifier
) {
    CompactSavingsMetric(
        modifier = modifier.testTag("v3_potential_savings"),
        label = "פוטנציאל לחיסכון",
        value = potentialMonthly?.asV3Money() ?: "לא ידוע",
        supporting = if (potentialMonthly != null) {
            "שנתי: ${potentialAnnual?.asV3Money() ?: "לא ידוע"}"
        } else {
            "לא חיסכון ממומש"
        },
        containerColor = V3BlueSoft,
        valueColor = TechBluePrimary
    )
}

@Composable
private fun CompactSavingsMetric(
    label: String,
    value: String,
    supporting: String,
    containerColor: Color,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        color = containerColor,
        tonalElevation = 0.dp
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = V3MutedForeground, maxLines = 1)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                supporting,
                style = MaterialTheme.typography.labelSmall,
                color = V3MutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
