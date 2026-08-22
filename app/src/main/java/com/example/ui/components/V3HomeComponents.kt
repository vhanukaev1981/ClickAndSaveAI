package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.TechBluePrimary
import com.example.ui.theme.V3Border
import com.example.ui.theme.V3MutedForeground
import com.example.ui.theme.V3PrimarySoft
import com.example.ui.theme.V3Surface
import com.example.ui.theme.V3Warning
import com.example.ui.theme.V3WarningSoft

@Composable
fun NextBestActionCard(
    providerName: String,
    category: String,
    potentialMonthlyText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    V3Panel(modifier = modifier, containerColor = V3PrimarySoft) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = V3Surface, border = BorderStroke(1.dp, V3Border)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = TechBluePrimary, modifier = Modifier.padding(8.dp))
            }
            Text(
                text = "הדבר הכי משתלם לעשות עכשיו",
                modifier = Modifier.padding(horizontal = 9.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = "$providerName · $category",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "עד $potentialMonthlyText חיסכון פוטנציאלי בחודש",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TechBluePrimary
        )
        Text(
            text = "הסכום מבוסס על הצעה מאומתת ועדכנית ואינו חיסכון שכבר מומש.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        V3PrimaryButton("בדיקת האפשרות", onClick, Modifier.fillMaxWidth())
    }
}

@Composable
fun FinancialSnapshot(
    recurringSpendText: String,
    recurringServicesText: String,
    invoicesText: String,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < 340.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SnapshotMetric("חיוב חוזר", recurringSpendText, Modifier.fillMaxWidth())
                SnapshotMetric("שירותים", recurringServicesText, Modifier.fillMaxWidth())
                SnapshotMetric("חשבונות", invoicesText, Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SnapshotMetric("חיוב חוזר", recurringSpendText, Modifier.weight(1f))
                SnapshotMetric("שירותים", recurringServicesText, Modifier.weight(1f))
                SnapshotMetric("חשבונות", invoicesText, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SnapshotMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = V3Surface,
        border = BorderStroke(1.dp, V3Border),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun V3HomeIncreaseCard(
    providerName: String,
    monthlyIncreaseText: String,
    percentIncreaseText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    V3Panel(modifier = modifier, containerColor = V3WarningSoft) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = V3Surface, border = BorderStroke(1.dp, V3Border)) {
                Icon(
                    Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = V3Warning,
                    modifier = Modifier.padding(9.dp).size(20.dp)
                )
            }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("החיוב ב$providerName עלה", fontWeight = FontWeight.Bold)
                Text(
                    buildString {
                        append("עלייה של $monthlyIncreaseText")
                        percentIncreaseText?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = V3MutedForeground
                )
            }
        }
        Text(
            "זו השוואה בין חשבוניות שזוהו — לא הוכחה לשינוי תעריף אצל הספק.",
            style = MaterialTheme.typography.bodySmall,
            color = V3MutedForeground
        )
        V3SecondaryButton("בדוק למה ומה אפשר לעשות", onClick, Modifier.fillMaxWidth())
    }
}

@Composable
fun V3HomeActivityRow(
    providerName: String,
    category: String,
    amountText: String,
    dateText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = V3Surface,
        border = BorderStroke(1.dp, V3Border),
        tonalElevation = 0.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("זוהתה חשבונית · $providerName", fontWeight = FontWeight.Bold)
                Text(
                    "$category · $amountText${if (dateText.isNotBlank()) " · $dateText" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = V3MutedForeground,
                    maxLines = 1
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "פתיחת הפעילות",
                tint = TechBluePrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
