package com.example.ui.usa

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun UsaBillsScreen(
    dataMode: AppDataMode,
    services: List<MonitoredService>,
    onServiceClick: (MonitoredService) -> Unit = {},
    onAddServiceClick: () -> Unit = {},
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
            DataStateBadge(mode = dataMode)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Bills & monitoring",
                style = MaterialTheme.typography.displaySmall,
                color = Ink
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Recurring services, recent changes, and monitoring status — not a spending ledger.",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted
            )
        }

        services.forEach { service ->
            ServiceMonitoringRow(
                service = service,
                onClick = { onServiceClick(service) }
            )
        }

        // Add recurring service prompt
        Surface(
            color = White,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MintSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "＋", fontSize = 20.sp, color = Forest)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Add a recurring service",
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Provide details only when they materially improve monitoring or a recommendation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
