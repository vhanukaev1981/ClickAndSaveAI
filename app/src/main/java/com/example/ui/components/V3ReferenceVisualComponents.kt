package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.V3Border
import com.example.ui.theme.V3GradientBlue
import com.example.ui.theme.V3GradientBlueSoft
import com.example.ui.theme.V3GradientIndigo
import com.example.ui.theme.V3GradientViolet
import com.example.ui.theme.V3GradientVioletSoft
import com.example.ui.theme.V3MutedForeground
import com.example.ui.theme.V3Navy
import com.example.ui.theme.V3Success
import com.example.ui.theme.V3Surface
import com.example.ui.v3.asV3Money

private val PremiumHeroShape = RoundedCornerShape(28.dp)
private val PremiumCardShape = RoundedCornerShape(24.dp)

private fun premiumGradient() = Brush.linearGradient(
    colors = listOf(V3GradientBlue, V3GradientIndigo, V3GradientViolet)
)

@Composable
fun V3GradientHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, PremiumHeroShape)
            .clip(PremiumHeroShape)
            .background(premiumGradient())
            .padding(horizontal = 22.dp, vertical = 22.dp)
    ) {
        Box(
            Modifier
                .size(94.dp)
                .align(Alignment.TopStart)
                .background(Color.White.copy(alpha = 0.07f), CircleShape)
        )
        Box(
            Modifier
                .size(48.dp)
                .align(Alignment.BottomStart)
                .background(Color.White.copy(alpha = 0.10f), CircleShape)
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                eyebrow,
                color = Color.White.copy(alpha = 0.86f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                title,
                color = Color.White,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.86f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun V3FinancialOverviewCard(
    observedMonthlySpend: Double?,
    realizedMonthly: Double?,
    potentialMonthly: Double?,
    serviceCount: Int?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().shadow(6.dp, PremiumCardShape),
        shape = PremiumCardShape,
        color = V3Surface,
        tonalElevation = 0.dp
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("המצב הפיננסי שלי", style = MaterialTheme.typography.labelLarge, color = V3MutedForeground)
                    Text(
                        observedMonthlySpend?.asV3Money() ?: "לא ידוע",
                        fontSize = 27.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = V3Navy
                    )
                    Text("חיובים חודשיים שנצפו", style = MaterialTheme.typography.bodySmall, color = V3MutedForeground)
                }
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(V3GradientBlueSoft, V3GradientVioletSoft))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = V3GradientIndigo)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                PremiumMetricTile(
                    label = "חיסכון שמומש",
                    value = realizedMonthly?.asV3Money() ?: "לא ידוע",
                    accent = V3Success,
                    modifier = Modifier.weight(1f)
                )
                PremiumMetricTile(
                    label = "פוטנציאל לחיסכון",
                    value = potentialMonthly?.asV3Money() ?: "לא ידוע",
                    accent = V3GradientBlue,
                    modifier = Modifier.weight(1f)
                )
                PremiumMetricTile(
                    label = "שירותים במעקב",
                    value = serviceCount?.toString() ?: "לא ידוע",
                    accent = V3GradientViolet,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PremiumMetricTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(accent.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(value, fontWeight = FontWeight.Bold, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelSmall, color = V3MutedForeground, maxLines = 2)
    }
}

@Composable
fun V3SavingsDashboardHero(
    realizedMonthly: Double?,
    potentialMonthly: Double?,
    realizedAnnual: Double?,
    potentialAnnual: Double?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, PremiumHeroShape)
            .clip(PremiumHeroShape)
            .background(premiumGradient())
            .padding(20.dp)
    ) {
        Box(
            Modifier
                .size(116.dp)
                .align(Alignment.TopStart)
                .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape)
        )
        Box(
            Modifier
                .size(82.dp)
                .align(Alignment.TopStart)
                .padding(17.dp)
                .background(Color.White.copy(alpha = 0.10f), CircleShape)
        )
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("החיסכון שלי", color = Color.White.copy(alpha = 0.84f), fontWeight = FontWeight.SemiBold)
                    Text(
                        realizedMonthly?.asV3Money() ?: "לא ידוע",
                        color = Color.White,
                        fontSize = 30.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("חיסכון חודשי שמומש", color = Color.White.copy(alpha = 0.76f), style = MaterialTheme.typography.bodySmall)
                }
                Box(
                    Modifier.size(58.dp).background(Color.White.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    SavingsGlyph(contentDescription = "חיסכון", tint = Color.White)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassMetric(
                    label = "פוטנציאל חודשי",
                    value = potentialMonthly?.asV3Money() ?: "לא ידוע",
                    modifier = Modifier.weight(1f)
                )
                GlassMetric(
                    label = "שמומש בשנה",
                    value = realizedAnnual?.asV3Money() ?: "לא ידוע",
                    modifier = Modifier.weight(1f)
                )
                GlassMetric(
                    label = "פוטנציאל שנתי",
                    value = potentialAnnual?.asV3Money() ?: "לא ידוע",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun GlassMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun V3AiExperienceHero(
    title: String,
    subtitle: String,
    status: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, PremiumHeroShape)
            .clip(PremiumHeroShape)
            .background(premiumGradient())
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Box(Modifier.size(132.dp).align(Alignment.Center).border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape))
        Box(Modifier.size(104.dp).align(Alignment.Center).border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape))
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Click & Save AI ✦", color = Color.White.copy(alpha = 0.88f), fontWeight = FontWeight.Bold)
            Box(
                Modifier
                    .size(82.dp)
                    .shadow(16.dp, CircleShape)
                    .background(Color.White.copy(alpha = 0.18f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.42f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("AI", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White.copy(alpha = 0.88f), modifier = Modifier.size(16.dp))
                Text(status, color = Color.White.copy(alpha = 0.86f), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun V3BillVisualCard(
    providerName: String,
    category: String,
    amountText: String,
    dueText: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxWidth().shadow(6.dp, PremiumCardShape),
        shape = PremiumCardShape,
        color = V3Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, V3Border.copy(alpha = 0.75f))
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(Brush.linearGradient(listOf(V3GradientBlueSoft, V3GradientVioletSoft))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = V3GradientIndigo)
                }
                Spacer(Modifier.size(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(providerName, fontWeight = FontWeight.Bold, color = V3Navy)
                    Text(category, style = MaterialTheme.typography.bodySmall, color = V3MutedForeground)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(amountText, fontWeight = FontWeight.Bold, color = V3Navy)
                    Text(dueText, style = MaterialTheme.typography.labelSmall, color = V3MutedForeground)
                }
            }
            content()
        }
    }
}

@Composable
fun V3ProfileHero(
    title: String = "פרופיל",
    displayName: String,
    email: String,
    authenticated: Boolean,
    modifier: Modifier = Modifier
) {
    val name = displayName.ifBlank { if (authenticated) "החשבון שלך" else title }
    val initial = name.trim().firstOrNull()?.uppercase() ?: "C"
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, PremiumHeroShape)
            .clip(PremiumHeroShape)
            .background(premiumGradient())
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        Box(Modifier.size(100.dp).align(Alignment.TopStart).background(Color.White.copy(alpha = 0.07f), CircleShape))
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(title, color = Color.White.copy(alpha = 0.84f), fontWeight = FontWeight.Bold)
            Box(
                Modifier
                    .size(72.dp)
                    .background(Color.White, CircleShape)
                    .border(3.dp, Color.White.copy(alpha = 0.42f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(initial, color = V3GradientIndigo, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            }
            Text(name, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(
                if (email.isNotBlank()) email else if (authenticated) "מחובר" else "לא מחובר",
                color = Color.White.copy(alpha = 0.80f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = Color.White.copy(alpha = 0.82f), modifier = Modifier.size(15.dp))
                Text("הנתונים וההרשאות שלך במקום אחד", color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
