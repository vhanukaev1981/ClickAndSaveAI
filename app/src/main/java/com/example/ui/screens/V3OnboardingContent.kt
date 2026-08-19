package com.example.ui.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

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
            icon = Icons.Outlined.AutoAwesome,
            title = "הכסף שלך יכול לעבוד חכם יותר",
            body = "Click & Save AI מרכז את התמונה הפיננסית שלך כדי לעזור לזהות הוצאות והזדמנויות לחיסכון בלי להמציא מספרים."
        )
        1 -> OnboardingStep(
            icon = Icons.Outlined.Search,
            title = "אנחנו מחפשים — אתה מחליט",
            body = "אנחנו מציגים הזדמנויות ומסבירים מה ידוע ומה עדיין דורש בדיקה. שום בקשה לא מוצגת כאילו כבר הושלמה."
        )
        else -> OnboardingStep(
            icon = Icons.Outlined.Lock,
            title = "מחברים Gmail בצורה מאובטחת",
            body = "החיבור נועד לאתר חשבונות רלוונטיים בהרשאת קריאה בלבד. לפני החיבור תראה בדיוק איזו הרשאה מתבקשת."
        )
    }

    Card(
        modifier = modifier.fillMaxWidth().testTag("v3_onboarding_step_${safeStep + 1}"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = content.icon,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Text(
                text = content.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = content.body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (safeStep == 2) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Lock, contentDescription = null)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("קריאה בלבד", fontWeight = FontWeight.Bold)
                            Text(
                                "החיבור לא מאפשר לנו לשלוח, למחוק או לשנות הודעות.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            OnboardingProgress(currentStep = safeStep)
            Spacer(Modifier.height(2.dp))

            Button(
                onClick = when {
                    safeStep < 2 -> onNext
                    authenticated -> onConnectGmail
                    else -> onGoogleSignIn
                },
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("v3_onboarding_primary_action"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    when {
                        safeStep < 2 -> "המשך"
                        authenticated -> "חבר Gmail"
                        else -> "התחבר עם Google"
                    }
                )
            }

            Text(
                text = when {
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == currentStep) 10.dp else 8.dp)
                    .background(
                        if (index == currentStep) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
            )
        }
    }
}

private data class OnboardingStep(
    val icon: ImageVector,
    val title: String,
    val body: String
)
