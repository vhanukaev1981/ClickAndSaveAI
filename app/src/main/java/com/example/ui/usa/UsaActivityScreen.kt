package com.example.ui.usa

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Ink
import com.example.ui.theme.Muted
import com.example.ui.theme.Paper

@Composable
fun UsaActivityScreen(
    events: List<GuardianTimelineEvent>,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text(
                text = "Guardian activity",
                style = MaterialTheme.typography.displaySmall,
                color = Ink
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Only meaningful household events and decisions appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        events.forEach { event ->
            GuardianTimelineRow(event = event)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
