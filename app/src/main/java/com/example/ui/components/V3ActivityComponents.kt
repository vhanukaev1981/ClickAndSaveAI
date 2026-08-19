package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmberDeal
import com.example.ui.theme.EmeraldSavings
import com.example.ui.theme.TechBluePrimary


enum class V3ActivityTone { SUCCESS, INFO, ATTENTION, NEUTRAL }

@Composable
fun ActivityTimelineItem(
    title: String,
    supporting: String?,
    timeLabel: String?,
    tone: V3ActivityTone = V3ActivityTone.INFO,
    modifier: Modifier = Modifier
) {
    val indicator = when (tone) {
        V3ActivityTone.SUCCESS -> EmeraldSavings
        V3ActivityTone.INFO -> TechBluePrimary
        V3ActivityTone.ATTENTION -> AmberDeal
        V3ActivityTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Spacer(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(10.dp)
                .background(indicator, CircleShape)
        )
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            supporting?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            timeLabel?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
