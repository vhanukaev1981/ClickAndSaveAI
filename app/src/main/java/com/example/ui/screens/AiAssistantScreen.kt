package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

@Composable
fun AiAssistantScreen(viewModel: MainViewModel) {
    val analysis by viewModel.aiDealAnalysis.collectAsState()
    val isAnalyzing by viewModel.isAnalyzingDeal.collectAsState()
    val messages by viewModel.chatMessages.collectAsState()
    val isChatLoading by viewModel.isAiChatLoading.collectAsState()
    val errorMessage by viewModel.aiErrorMessage.collectAsState()
    val session by viewModel.userSession.collectAsState()

    var analysisInput by remember { mutableStateOf("") }
    var chatInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai_assistant_screen"),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.CloudDone, contentDescription = null)
                    Spacer(modifier = Modifier.size(10.dp))
                    Column {
                        Text("AI דרך Backend מאומת", fontWeight = FontWeight.Bold)
                        Text(
                            "מפתח Gemini נשמר ב-Secret Manager ואינו נמצא ב-APK. השירות אינו רשאי להמציא מחירים או חיסכון, וכל טענה מסחרית דורשת מקור רשמי ומתוארך.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        if (!session.isAuthenticated) {
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Login, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("יש להתחבר במסך הפרופיל לפני שימוש ב-AI.")
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("ניתוח זהיר", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = analysisInput,
                        onValueChange = { analysisInput = it },
                        label = { Text("תיאור חשבון או מסלול") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.analyzeDeal(analysisInput) },
                        enabled = session.isAuthenticated && analysisInput.isNotBlank() && !isAnalyzing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("נתח דרך השרת")
                        }
                    }
                    if (errorMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(errorMessage, color = MaterialTheme.colorScheme.error)
                    }
                    analysis?.let {
                        Spacer(modifier = Modifier.height(10.dp))
                        DealAnalysisResultCard(it)
                    }
                }
            }
        }

        item {
            Text("צ׳אט", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        items(messages, key = { it.id }) { message ->
            ChatMessageBubble(message)
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = chatInput,
                        onValueChange = { chatInput = it },
                        label = { Text("כתוב הודעה") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val message = chatInput
                            chatInput = ""
                            viewModel.sendChatMessage(message)
                        },
                        enabled = session.isAuthenticated && chatInput.isNotBlank() && !isChatLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isChatLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Send, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("שלח")
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(message: ChatMessage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (message.isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(
            text = message.text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun DealAnalysisResultCard(analysis: DealAnalysisResult) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(analysis.recommendation, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(analysis.summary, style = MaterialTheme.typography.bodySmall)
            if (analysis.couponSuggestion.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(analysis.couponSuggestion, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
