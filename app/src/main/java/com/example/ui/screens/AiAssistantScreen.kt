package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.DealAnalysisResult
import com.example.ui.ChatMessage
import com.example.ui.MainViewModel
import com.example.ui.components.V3AiExperienceHero
import com.example.ui.components.V3Note
import com.example.ui.components.V3Panel
import com.example.ui.components.V3PrimaryButton
import com.example.ui.components.V3SecondaryButton
import com.example.ui.components.V3SectionHeader
import com.example.ui.components.VerificationBadge
import com.example.ui.theme.TechBluePrimary
import com.example.ui.theme.V3AiSoft
import com.example.ui.theme.V3AiViolet
import com.example.ui.theme.V3Border
import com.example.ui.theme.V3GradientBlueSoft
import com.example.ui.theme.V3MutedForeground
import com.example.ui.theme.V3Primary
import com.example.ui.theme.V3PrimarySoft
import com.example.ui.theme.V3Surface
import com.example.ui.v3.V3AiSuggestion
import com.example.ui.v3.v3RankedAiSuggestions

private val SECONDARY_SAVINGS_ASSISTANT_PROMPTS = listOf(
    "איפה אני משלם יותר מדי?",
    "מה אפשר לבטל?",
    "מה כדאי לבדוק השבוע?",
    "איפה החיסכון הגדול ביותר?",
    "תסביר לי את החשבון הזה"
)

private fun consumerAiError(message: String): String = when {
    message.contains("temporarily unavailable", ignoreCase = true) ||
        message.contains("unavailable", ignoreCase = true) ->
        "שירות ה-AI אינו זמין כרגע. נסה שוב בעוד רגע."
    else -> "לא הצלחנו להשלים את הבדיקה כרגע. נסה שוב בעוד רגע."
}

private fun consumerAiMessage(message: ChatMessage): String {
    if (message.isUser) return message.text
    return when {
        message.text.contains("מחירים ותנאים עדיין דורשים", ignoreCase = true) ->
            "אני כאן כדי לעזור לך להבין מה כדאי לבדוק ולחסוך. אציג רק מידע שאפשר לבסס על המקורות המחוברים."
        message.text.contains("temporarily unavailable", ignoreCase = true) ||
            message.text.contains("unavailable", ignoreCase = true) ->
            "שירות ה-AI אינו זמין כרגע. נסה שוב בעוד רגע."
        else -> message.text
    }
}

@Composable
fun AiAssistantScreen(viewModel: MainViewModel) {
    val analysis by viewModel.aiDealAnalysis.collectAsState()
    val isAnalyzing by viewModel.isAnalyzingDeal.collectAsState()
    val messages by viewModel.chatMessages.collectAsState()
    val isChatLoading by viewModel.isAiChatLoading.collectAsState()
    val errorMessage by viewModel.aiErrorMessage.collectAsState()
    val session by viewModel.userSession.collectAsState()
    val financialHome by viewModel.authoritativeFinancialHome.collectAsState()

    AiAssistantContent(
        authenticated = session.isAuthenticated,
        analysis = analysis,
        isAnalyzing = isAnalyzing,
        messages = messages,
        isChatLoading = isChatLoading,
        errorMessage = errorMessage,
        primarySuggestions = financialHome.v3RankedAiSuggestions(),
        secondarySuggestions = SECONDARY_SAVINGS_ASSISTANT_PROMPTS,
        onAnalyze = viewModel::analyzeDeal,
        onSend = viewModel::sendChatMessage
    )
}

@Composable
fun AiAssistantContent(
    authenticated: Boolean,
    analysis: DealAnalysisResult?,
    isAnalyzing: Boolean,
    messages: List<ChatMessage>,
    isChatLoading: Boolean,
    errorMessage: String,
    primarySuggestions: List<V3AiSuggestion> = emptyList(),
    secondarySuggestions: List<String> = SECONDARY_SAVINGS_ASSISTANT_PROMPTS,
    onAnalyze: (String) -> Unit,
    onSend: (String) -> Unit
) {
    var analysisInput by remember { mutableStateOf("") }
    var chatInput by remember { mutableStateOf("") }
    var showDetailedCheck by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("ai_assistant_screen"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            V3AiExperienceHero(
                title = "עוזר החיסכון שלך",
                subtitle = "אני מרכז עבורך את הדברים שכדאי לבדוק עכשיו — רק לפי המידע שיש לנו.",
                status = if (authenticated) "מוכן לבדיקה" else "התחברות נדרשת"
            )
        }

        if (!authenticated) {
            item {
                V3Panel(containerColor = V3PrimarySoft) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.Login, null, tint = TechBluePrimary)
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("כדי להשתמש בעוזר צריך להתחבר", fontWeight = FontWeight.Bold)
                            Text("אחרי ההתחברות נציג רק מידע שניתן לבסס על המקורות המחוברים.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        } else {
            item {
                if (primarySuggestions.isNotEmpty()) {
                    V3Panel(containerColor = V3GradientBlueSoft) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(12.dp), color = V3Surface, border = BorderStroke(1.dp, V3Border)) {
                                Icon(Icons.Default.AutoAwesome, null, tint = V3AiViolet, modifier = Modifier.padding(9.dp).size(20.dp))
                            }
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("מצאתי מה כדאי לבדוק עכשיו", fontWeight = FontWeight.Bold)
                                Text(primarySuggestions.first().label, style = MaterialTheme.typography.bodySmall, color = V3MutedForeground)
                            }
                        }
                        VerificationBadge("מבוסס על המידע הזמין כרגע")
                    }
                } else {
                    V3Panel {
                        Text("עדיין אין לי מספיק מידע כדי להיות יזום", fontWeight = FontWeight.Bold)
                        Text(
                            "ברגע שיהיה איתות פיננסי מאומת, אציג כאן את הדבר שהכי כדאי לבדוק.",
                            style = MaterialTheme.typography.bodySmall,
                            color = V3MutedForeground
                        )
                    }
                }
            }
        }

        item { V3SectionHeader("מה שכדאי לבדוק עכשיו") }
        if (primarySuggestions.isEmpty()) {
            item {
                V3Panel {
                    Text("אין כרגע הצעות אישיות מאומתות", style = MaterialTheme.typography.bodyMedium, color = V3MutedForeground)
                }
            }
        } else {
            items(primarySuggestions.take(6), key = { "primary:${it.prompt}" }) { suggestion ->
                AiSuggestionCard(
                    label = suggestion.label,
                    enabled = authenticated && !isChatLoading,
                    primary = true,
                    onClick = { onSend(suggestion.prompt) }
                )
            }
        }

        item { V3SectionHeader("עוד דברים שכדאי לבדוק") }
        item {
            AiSuggestionGrid(
                prompts = secondarySuggestions.take(6),
                enabled = authenticated && !isChatLoading,
                onSelect = onSend
            )
        }

        item { V3SectionHeader("שיחה") }
        if (messages.isNotEmpty()) {
            items(messages, key = { it.id }) { message -> ChatMessageBubble(message) }
        }
        item {
            V3Panel {
                OutlinedTextField(
                    value = chatInput,
                    onValueChange = { chatInput = it },
                    placeholder = { Text("מה תרצה לבדוק?") },
                    modifier = Modifier.fillMaxWidth().testTag("ai_message_input"),
                    minLines = 1,
                    maxLines = 3,
                    shape = RoundedCornerShape(16.dp)
                )
                Button(
                    onClick = {
                        val message = chatInput.trim()
                        if (message.isNotEmpty()) {
                            chatInput = ""
                            onSend(message)
                        }
                    },
                    enabled = authenticated && chatInput.isNotBlank() && !isChatLoading,
                    modifier = Modifier.fillMaxWidth().testTag("ai_send"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = V3Primary)
                ) {
                    if (isChatLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.AutoMirrored.Filled.Send, null)
                    Spacer(Modifier.size(7.dp))
                    Text(if (isChatLoading) "בודק..." else "שאל")
                }
            }
        }

        item {
            V3SecondaryButton(
                label = if (showDetailedCheck) "סגור בדיקה מפורטת" else "בדיקה מפורטת של מסלול",
                onClick = { showDetailedCheck = !showDetailedCheck },
                modifier = Modifier.fillMaxWidth(),
                enabled = authenticated
            )
        }
        if (showDetailedCheck) {
            item {
                V3Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(12.dp), color = V3PrimarySoft) {
                            Icon(Icons.Default.Shield, null, tint = TechBluePrimary, modifier = Modifier.padding(9.dp).size(19.dp))
                        }
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("בדיקה מפורטת", fontWeight = FontWeight.Bold)
                            Text(
                                "המלצה מסחרית נשארת כפופה למידע שניתן לאמת.",
                                style = MaterialTheme.typography.bodySmall,
                                color = V3MutedForeground
                            )
                        }
                    }
                    OutlinedTextField(
                        value = analysisInput,
                        onValueChange = { analysisInput = it },
                        label = { Text("מה לבדוק?") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(16.dp)
                    )
                    V3PrimaryButton(
                        label = if (isAnalyzing) "בודק..." else "בדוק את המסלול",
                        onClick = { onAnalyze(analysisInput) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = authenticated && analysisInput.isNotBlank() && !isAnalyzing
                    )
                    analysis?.let { DealAnalysisResultCard(it) }
                }
            }
        } else if (analysis != null) {
            item {
                V3Panel(containerColor = V3AiSoft) {
                    Text("תוצאת הבדיקה האחרונה", fontWeight = FontWeight.Bold)
                    DealAnalysisResultCard(analysis)
                }
            }
        }

        if (errorMessage.isNotBlank()) {
            item {
                V3Panel {
                    Text(
                        consumerAiError(errorMessage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        item {
            V3Note("העוזר עונה לפי המידע הזמין מהמקורות המחוברים. הוא לא ממציא סכומים, לא מבטיח חיסכון, וכל פעולה מול ספק מתבצעת רק באישור מפורש שלך.")
        }
    }
}

@Composable
private fun AiSuggestionGrid(
    prompts: List<String>,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        prompts.chunked(2).forEach { rowPrompts ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowPrompts.forEach { prompt ->
                    AiSuggestionCard(
                        label = prompt,
                        enabled = enabled,
                        primary = false,
                        onClick = { onSelect(prompt) },
                        modifier = Modifier.weight(1f),
                        compact = true
                    )
                }
                if (rowPrompts.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AiSuggestionCard(
    label: String,
    enabled: Boolean,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (primary) V3Surface else V3GradientBlueSoft,
        border = BorderStroke(1.dp, V3Border),
        shadowElevation = if (primary) 3.dp else 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = if (compact) 11.dp else 15.dp,
                vertical = if (compact) 10.dp else 14.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                null,
                Modifier.size(if (compact) 16.dp else 18.dp),
                tint = V3AiViolet
            )
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else V3MutedForeground
            )
        }
    }
}

@Composable
fun ChatMessageBubble(message: ChatMessage) {
    val isUser = message.isUser
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.Start else Arrangement.End) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.88f),
            shape = RoundedCornerShape(18.dp),
            color = if (isUser) V3PrimarySoft else V3Surface,
            border = BorderStroke(1.dp, V3Border),
            shadowElevation = if (isUser) 0.dp else 1.dp
        ) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (isUser) "אתה" else "Click & Save AI", style = MaterialTheme.typography.labelMedium, color = if (isUser) TechBluePrimary else V3AiViolet)
                Text(consumerAiMessage(message), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun DealAnalysisResultCard(analysis: DealAnalysisResult) {
    Surface(shape = RoundedCornerShape(16.dp), color = V3AiSoft, border = BorderStroke(1.dp, V3Border)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(analysis.recommendation, fontWeight = FontWeight.Bold)
            Text(analysis.summary, style = MaterialTheme.typography.bodySmall)
            if (analysis.couponSuggestion.isNotBlank()) Text(analysis.couponSuggestion, style = MaterialTheme.typography.bodySmall)
        }
    }
}
