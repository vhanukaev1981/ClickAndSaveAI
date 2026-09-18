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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
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
fun UsaLaunchScreen(
    onGetStarted: () -> Unit,
    onExploreDemo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Paper)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Forest),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "C",
                color = White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Click & Save AI",
            style = MaterialTheme.typography.displayMedium,
            color = Ink
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Your household financial guardian",
            style = MaterialTheme.typography.bodyLarge,
            color = Muted
        )

        Spacer(modifier = Modifier.height(56.dp))

        Button(
            onClick = onGetStarted,
            colors = ButtonDefaults.buttonColors(containerColor = Forest),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text("Get started", color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onExploreDemo,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text("Explore illustrative demo", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Demo values are examples, never your financial data.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun UsaOnboardStepScreen(
    step: Int, // 1, 2, or 3
    title: String,
    body: String,
    iconSymbol: String,
    isConnectStep: Boolean = false,
    onBackClick: () -> Unit,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Paper)
            .padding(24.dp)
    ) {
        // Step indicator (3 bars)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (i in 1..3) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i <= step) Forest else Line)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = onBackClick,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text("← Back", color = Forest, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Large icon orbit
        Box(
            modifier = Modifier
                .size(72.dp)
                .align(Alignment.CenterHorizontally)
                .clip(CircleShape)
                .background(MintSoft),
            contentAlignment = Alignment.Center
        ) {
            Text(text = iconSymbol, fontSize = 28.sp, color = Forest)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            color = Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        if (isConnectStep) {
            Spacer(modifier = Modifier.height(24.dp))
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
                    Text(text = "✓", color = Forest, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Recurring bill discovery", style = MaterialTheme.typography.titleSmall, color = Ink)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Requires account connection and explicit authorization.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onPrimaryClick,
            colors = ButtonDefaults.buttonColors(containerColor = Forest),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text(
                text = if (isConnectStep) "Authorize connection" else "Continue",
                color = White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        if (onSecondaryClick != null) {
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(
                onClick = onSecondaryClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Not now — use demo", color = Muted)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun UsaInitialDiscoveryScreen(
    onViewHousehold: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Paper)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = Forest,
            strokeWidth = 4.dp,
            modifier = Modifier.size(52.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "INITIAL DISCOVERY",
            style = MaterialTheme.typography.labelSmall,
            color = Forest,
            letterSpacing = 0.08.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Building your first household picture",
            style = MaterialTheme.typography.displaySmall,
            color = Ink,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Reviewing authorized information for recurring expenses and meaningful changes.",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MintSoft)
                .border(1.dp, Forest.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(14.dp)
        ) {
            Text(
                text = "No percentage is shown because completion time depends on the connected account. You can leave this screen and return later.",
                style = MaterialTheme.typography.bodySmall,
                color = Ink
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = onViewHousehold,
            colors = ButtonDefaults.buttonColors(containerColor = Forest),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text("View first household picture", color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
