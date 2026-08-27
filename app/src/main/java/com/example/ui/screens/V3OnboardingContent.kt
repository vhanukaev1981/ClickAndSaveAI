package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.components.V3PrimaryButton
import com.example.ui.theme.V3Aurora1
import com.example.ui.theme.V3Aurora2
import com.example.ui.theme.V3Aurora3
import com.example.ui.theme.V3Border
import com.example.ui.theme.V3Primary
import com.example.ui.theme.V3PrimarySoft
import com.example.ui.theme.V3Surface

@Composable
fun V3OnboardingContent(
    step: Int,
    authenticated: Boolean,
    onNext: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onConnectGmail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeStep = step.coerceIn(0, 2)
    val content = when (safeStep) {
        0 -> OnboardingStep(
            Icons.Outlined.AutoAwesome,
            "הכסף שלך יכול לעבוד חכם יותר",
            "Click & Save AI מרכז את התמונה הפיננסית שלך כדי לעזור לזהות הוצאות והזדמנויות לחיסכון בלי להמציא מספרים."
        )
        1 -> OnboardingStep(
            Icons.Outlined.Search,
            "אנחנו מחפשים — אתה מחליט",
            "אנחנו מציגים הזדמנויות ומסבירים מה ידוע ומה עדיין דורש בדיקה. שום בקשה לא מוצגת כאילו כבר הושלמה."
        )
        else -> OnboardingStep(
            Icons.Outlined.Lock,
            "אתה בוחר — וממשיך רק כשמתאים לך",
            "אפשר לחבר Gmail בהרשאת קריאה בלבד כדי לאתר חשבונות רלוונטיים. החיבור מתבצע רק לאחר אישור מפורש שלך."
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth().testTag("v3_onboarding_step_${safeStep + 1}"),
        shape = RoundedCornerShape(20.dp),
        color = V3Surface,
        border = BorderStroke(1.dp, V3Border),
        shadowElevation = 3.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(V3Aurora2, V3Aurora3, V3Aurora1))),
                contentAlignment = Alignment.Center
            ) {
                Icon(content.icon, null, Modifier.size(28.dp), tint = Color.White)
            }
            Text(content.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(content.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (safeStep == 2) {
                Surface(
                    color = V3PrimarySoft,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, V3Border)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Lock, null, tint = V3Primary)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("קריאה בלבד", fontWeight = FontWeight.Bold)
                            Text("החיבור לא מאפשר לנו לשלוח, למחוק או לשנות הודעות.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            OnboardingProgress(safeStep)
            Spacer(Modifier.height(2.dp))
            V3PrimaryButton(
                label = when {
                    safeStep < 2 -> "המשך"
                    authenticated -> "חבר Gmail"
                    else -> "התחבר עם Google"
                },
                onClick = when {
                    safeStep < 2 -> onNext
                    authenticated -> onConnectGmail
                    else -> onGoogleSignIn
                },
                modifier = Modifier.fillMaxWidth().testTag("v3_onboarding_primary_action")
            )
            Text(
                when {
                    safeStep < 2 -> "שלב ${safeStep + 1} מתוך 3"
                    authenticated -> "החיבור יתבצע רק לאחר אישור מפורש שלך."
                    else -> "ההתחברות לחשבון נפרדת מהרשאת Gmail."
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun OnboardingProgress(currentStep: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            Box(
                Modifier
                    .size(width = if (index == currentStep) 22.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (index == currentStep) V3Primary else MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

private data class OnboardingStep(val icon: ImageVector, val title: String, val body: String)
