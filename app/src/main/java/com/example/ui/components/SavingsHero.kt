package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import com.example.ui.theme.EmeraldSavingsDark
import com.example.ui.theme.TechBluePrimary
import com.example.ui.theme.V3BlueSoft
import com.example.ui.theme.V3Border
import com.example.ui.theme.V3EmeraldSoft
import com.example.ui.theme.V3PrimarySoft
import com.example.ui.theme.V3Surface
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
        containerColor = V3Surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = V3PrimarySoft,
                border = BorderStroke(1.dp, V3Border)
            ) {
                SavingsGlyph(
                    modifier = Modifier.padding(10.dp).size(24.dp),
                    contentDescription = "חיסכון"
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("החיסכון שלך", style = MaterialTheme.typography.titleLarge)
                Text(
                    "מה כבר התממש ומה עדיין מחכה לפעולה",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 350.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RealizedMetric(realizedMonthly, realizedAnnual, realizedKnownZero, Modifier.fillMaxWidth())
                    PotentialMetric(potentialMonthly, potentialAnnual, Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RealizedMetric(realizedMonthly, realizedAnnual, realizedKnownZero, Modifier.weight(1f))
                    PotentialMetric(potentialMonthly, potentialAnnual, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RealizedMetric(
    realizedMonthly: Double?,
    realizedAnnual: Double?,
    realizedKnownZero: Boolean,
    modifier: Modifier
) {
    SavingsMetric(
        modifier = modifier.testTag("v3_realized_savings"),
        label = "חיסכון שמומש",
        value = when {
            realizedMonthly != null -> realizedMonthly.asV3Money()
            realizedKnownZero -> 0.0.asV3Money()
            else -> "לא ידוע"
        },
        supporting = when {
            realizedMonthly != null || realizedKnownZero ->
                "שנתי: ${realizedAnnual?.asV3Money() ?: "לא ידוע"} · לפי ראיה שנקלטה"
            else -> "חודשי ושנתי: לא ידוע · עדיין אין ראיה מספקת"
        },
        containerColor = V3EmeraldSoft,
        valueColor = EmeraldSavingsDark
    )
}

@Composable
private fun PotentialMetric(
    potentialMonthly: Double?,
    potentialAnnual: Double?,
    modifier: Modifier
) {
    SavingsMetric(
        modifier = modifier.testTag("v3_potential_savings"),
        label = "פוטנציאל לחיסכון",
        value = potentialMonthly?.asV3Money() ?: "לא ידוע",
        supporting = if (potentialMonthly != null) {
            "שנתי: ${potentialAnnual?.asV3Money() ?: "לא ידוע"} · לא חיסכון ממומש"
        } else {
            "חודשי ושנתי: לא ידוע · לא חיסכון ממומש"
        },
        containerColor = V3BlueSoft,
        valueColor = TechBluePrimary
    )
}

@Composable
private fun SavingsMetric(
    label: String,
    value: String,
    supporting: String,
    containerColor: Color,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, V3Border.copy(alpha = 0.8f)),
        tonalElevation = 0.dp
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = valueColor)
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
