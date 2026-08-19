package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.EmeraldSavingsDark
import com.example.ui.theme.TechBluePrimary
import com.example.ui.theme.V3BlueSoft
import com.example.ui.theme.V3EmeraldSoft
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
    Card(
        modifier = modifier.fillMaxWidth().testTag("v3_savings_hero"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("החיסכון שלך", style = MaterialTheme.typography.titleLarge)
            Text(
                "מה כבר התממש ומה עדיין מחכה לפעולה",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SavingsMetric(
                    modifier = Modifier.weight(1f).testTag("v3_realized_savings"),
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
                SavingsMetric(
                    modifier = Modifier.weight(1f).testTag("v3_potential_savings"),
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
        }
    }
}

@Composable
private fun SavingsMetric(
    label: String,
    value: String,
    supporting: String,
    containerColor: androidx.compose.ui.graphics.Color,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = valueColor)
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
