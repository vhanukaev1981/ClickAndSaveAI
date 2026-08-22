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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import com.example.ui.components.V3Panel
import com.example.ui.components.V3ScreenHeader
import com.example.ui.components.VerificationBadge
import com.example.ui.theme.TechBluePrimary
import com.example.ui.theme.V3AiSoft
import com.example.ui.theme.V3AiViolet
import com.example.ui.theme.V3Border
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

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("ai_assistant_screen"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            V3ScreenHeader(
                eyebrow = "AI · CLICK & SAVE",
                title = "עוזר החיסכון שלך",
                subtitle = "הצעות מדורגות לפי מידע פיננסי מאומת שזמין כרגע.",
                action = {
                    Surface(shape = RoundedCornerShape(14.dp), color = V3AiSoft, border = BorderStroke(1.dp, V3Border)) {
                        Icon(Icons.Default.AutoAwesome, null, tint = V3AiViolet, modifier = Modifier.padding(10.dp).size(21.dp))
                    }
                }
            )
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                VerificationBadge("הצעות יזומות: מבוססות על מידע מאומת")
                Text(
                    "בשיחה חופשית ההקשר הפיננסי המלא עדיין אינו מובטח; כשאין נתון סמכותי, לא נניח אותו.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!authenticated) {
            item {
                V3Panel(containerColor = V3PrimarySoft) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.Login, null, tint = TechBluePrimary)
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("כדי להשתמש בעוזר צריך להתחבר", fontWeight = FontWeight.Bold)
                            Text("אפשר להתחבר דרך מסך פרופיל. העוזר לא יציג מידע אישי לפני התחברות.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        item { Text("הצעות בשבילך", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (primarySuggestions.isEmpty()) {
            item {
                V3Panel {
                    Text("אין כרגע הצעות אישיות מאומתות", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    primarySuggestions.forEachIndexed { index, suggestion ->
                        AssistChip(
                            onClick = { if (authenticated && !isChatLoading) onSend(suggestion.prompt) },
                            enabled = authenticated && !isChatLoading,
                            label = { Text(suggestion.label) },
                            leadingIcon = { Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp), tint = V3AiViolet) },
                            modifier = Modifier.fillMaxWidth().testTag("ai_primary_suggestion_$index")
                        )
                    }
                }
            }
        }

        item { Text("אפשר לבדוק גם", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                secondarySuggestions.forEachIndexed { index, prompt ->
                    AssistChip(
                        onClick = { if (authenticated && !isChatLoading) onSend(prompt) },
                        enabled = authenticated && !isChatLoading,
                        label = { Text(prompt) },
                        leadingIcon = { Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp), tint = V3AiViolet) },
                        modifier = Modifier.fillMaxWidth().testTag("ai_secondary_suggestion_$index")
                    )
                }
            }
        }

        item {
            V3Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = V3PrimarySoft) {
                        Icon(Icons.Default.Shield, null, tint = TechBluePrimary, modifier = Modifier.padding(9.dp).size(19.dp))
                    }
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("בדיקת חשבון או מסלול", fontWeight = FontWeight.Bold)
                        Text(
                            "תאר את מה שאתה רוצה לבדוק. המלצה מסחרית נשארת כפופה למידע ומקור שניתן לאמת.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedTextField(
                    value = analysisInput,
                    onValueChange = { analysisInput = it },
                    label = { Text("מה לבדוק?") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = RoundedCornerShape(14.dp)
                )
                Button(
                    onClick = { onAnalyze(analysisInput) },
                    enabled = authenticated && analysisInput.isNotBlank() && !isAnalyzing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(if (isAnalyzing) "בודק..." else "בדיקת המסלול")
                }
                if (errorMessage.isNotBlank()) {
                    Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                analysis?.let { DealAnalysisResultCard(it) }
            }
        }

        if (messages.isNotEmpty()) {
            item { Text("השיחה שלך", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(messages, key = { it.id }) { message -> ChatMessageBubble(message) }
        }

        item {
            V3Panel {
                OutlinedTextField(
                    value = chatInput,
                    onValueChange = { chatInput = it },
                    label = { Text("שאל את עוזר החיסכון") },
                    modifier = Modifier.fillMaxWidth().testTag("ai_message_input"),
                    minLines = 2,
                    shape = RoundedCornerShape(14.dp)
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
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isChatLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.AutoMirrored.Filled.Send, null)
                    Spacer(Modifier.size(7.dp))
                    Text(if (isChatLoading) "בודק..." else "שלח")
                }
            }
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
                Text(message.text, style = MaterialTheme.typography.bodyMedium)
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
