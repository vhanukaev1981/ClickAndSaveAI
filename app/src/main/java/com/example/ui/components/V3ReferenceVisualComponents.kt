package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
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

private val PremiumHeroShape = RoundedCornerShape(22.dp)
private val PremiumCardShape = RoundedCornerShape(18.dp)

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
            .shadow(3.dp, PremiumHeroShape)
            .clip(PremiumHeroShape)
            .background(premiumGradient())
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Box(
            Modifier
                .size(74.dp)
                .align(Alignment.TopStart)
                .background(Color.White.copy(alpha = 0.06f), CircleShape)
        )
        Box(
            Modifier
                .size(34.dp)
                .align(Alignment.BottomStart)
                .background(Color.White.copy(alpha = 0.08f), CircleShape)
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                eyebrow,
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                title,
                color = Color.White,
                fontSize = 24.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodySmall
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
        modifier = modifier.fillMaxWidth().shadow(2.dp, PremiumCardShape),
        shape = PremiumCardShape,
        color = V3Surface,
        border = BorderStroke(1.dp, V3Border.copy(alpha = 0.72f)),
        tonalElevation = 0.dp
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("המצב הפיננסי שלי", style = MaterialTheme.typography.labelMedium, color = V3MutedForeground)
                    Text(
                        observedMonthlySpend?.asV3Money() ?: "לא ידוע",
                        fontSize = 24.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = V3Navy
                    )
                    Text("חיובים חודשיים שנצפו", style = MaterialTheme.typography.labelSmall, color = V3MutedForeground)
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(V3GradientBlueSoft, V3GradientVioletSoft))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = V3GradientIndigo,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
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
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.075f))
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
            .shadow(4.dp, PremiumHeroShape)
            .clip(PremiumHeroShape)
            .background(premiumGradient())
            .padding(horizontal = 16.dp, vertical = 15.dp)
    ) {
        Box(
            Modifier
                .size(88.dp)
                .align(Alignment.TopStart)
                .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
        )
        Box(
            Modifier
                .size(52.dp)
                .align(Alignment.TopStart)
                .padding(10.dp)
                .background(Color.White.copy(alpha = 0.08f), CircleShape)
        )
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        "החיסכון שלי",
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        realizedMonthly?.asV3Money() ?: "לא ידוע",
                        color = Color.White,
                        fontSize = 26.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("חיסכון חודשי שמומש", color = Color.White.copy(alpha = 0.74f), style = MaterialTheme.typography.labelSmall)
                }
                Box(
                    Modifier.size(48.dp).background(Color.White.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    SavingsGlyph(contentDescription = "חיסכון", tint = Color.White, modifier = Modifier.size(23.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
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
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(label, color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelSmall, maxLines = 2)
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
            .shadow(4.dp, PremiumHeroShape)
            .clip(PremiumHeroShape)
            .background(premiumGradient())
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Box(Modifier.size(108.dp).align(Alignment.Center).border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape))
        Box(Modifier.size(86.dp).align(Alignment.Center).border(1.dp, Color.White.copy(alpha = 0.24f), CircleShape))
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text("Click & Save AI ✦", color = Color.White.copy(alpha = 0.86f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Box(
                Modifier.size(68.dp)
                    .shadow(5.dp, CircleShape)
                    .background(Color.White.copy(alpha = 0.16f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.36f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("AI", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            }
            Text(title, color = Color.White, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.White.copy(alpha = 0.80f), style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White.copy(alpha = 0.84f), modifier = Modifier.size(14.dp))
                Text(status, color = Color.White.copy(alpha = 0.84f), style = MaterialTheme.typography.labelSmall)
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
        modifier = modifier.fillMaxWidth().shadow(2.dp, PremiumCardShape),
        shape = PremiumCardShape,
        color = V3Surface,
        border = BorderStroke(1.dp, V3Border.copy(alpha = 0.72f)),
        tonalElevation = 0.dp
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Brush.linearGradient(listOf(V3GradientBlueSoft, V3GradientVioletSoft))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = V3GradientIndigo, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.size(9.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(providerName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = V3Navy)
                    Text(category, style = MaterialTheme.typography.labelSmall, color = V3MutedForeground)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(amountText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = V3Navy)
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
            .shadow(4.dp, PremiumHeroShape)
            .clip(PremiumHeroShape)
            .background(premiumGradient())
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Box(Modifier.size(82.dp).align(Alignment.TopStart).background(Color.White.copy(alpha = 0.06f), CircleShape))
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(title, color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Box(
                Modifier.size(56.dp)
                    .background(Color.White, CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.36f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(initial, color = V3GradientIndigo, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
            Text(name, color = Color.White, fontSize = 21.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold)
            Text(
                if (email.isNotBlank()) email else if (authenticated) "מחובר" else "לא מחובר",
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = Color.White.copy(alpha = 0.78f), modifier = Modifier.size(14.dp))
                Text("הנתונים וההרשאות שלך במקום אחד", color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
