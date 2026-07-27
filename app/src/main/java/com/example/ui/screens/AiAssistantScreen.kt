package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.DealAnalysisResult
import com.example.ui.ChatMessage
import com.example.ui.MainViewModel
import com.example.ui.theme.AiVioletPrimary
import com.example.ui.theme.AmberDeal
import com.example.ui.theme.EmeraldSavings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(viewModel: MainViewModel) {
    var subTabState by remember { mutableIntStateOf(0) } // 0 = Household Bill Inspector, 1 = Live AI Chat

    val dealAnalysis by viewModel.aiDealAnalysis.collectAsState()
    val isAnalyzing by viewModel.isAnalyzingDeal.collectAsState()

    val chatMessages by viewModel.chatMessages.collectAsState()
    val isChatLoading by viewModel.isAiChatLoading.collectAsState()

    var queryInput by remember { mutableStateOf("") }
    var chatInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai_assistant_screen")
    ) {
        // Tab Header Switcher
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "עוזר AI להוזלת הוצאות הבית",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "ניתוח חשבוניות, תעריפי חשמל, סלולר, סיבים וכפילויות ביטוח",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                TabRow(
                    selectedTabIndex = subTabState,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Tab(
                        selected = subTabState == 0,
                        onClick = { subTabState = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("מנתח חשבוניות ותעריפים")
                            }
                        }
                    )
                    Tab(
                        selected = subTabState == 1,
                        onClick = { subTabState = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("צ'אט סייען AI")
                            }
                        }
                    )
                }
            }
        }

        if (subTabState == 0) {
            // --- HOUSEHOLD BILL INSPECTOR ---
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = queryInput,
                        onValueChange = { queryInput = it },
                        placeholder = { Text("הכנס שם ספק, סכום חשבונית או מסלול לבדיקה...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_inspector_input"),
                        shape = RoundedCornerShape(16.dp),
                        leadingIcon = {
                            Icon(Icons.Default.ElectricBolt, contentDescription = null)
                        },
                        trailingIcon = {
                            Button(
                                onClick = {
                                    if (queryInput.isNotBlank()) {
                                        viewModel.analyzeDeal(queryInput)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .padding(4.dp)
                                    .testTag("ai_inspector_submit_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = AiVioletPrimary)
                            ) {
                                if (isAnalyzing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("נתח")
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (isAnalyzing) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = AiVioletPrimary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "מנוע ה-AI מצליב תעריפי חשמל, סלולר וסיבים מול שוק התקשורת והאנרגיה...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "סורק אלטרנטיבות מוזלות ומחשב חיסכון שנתי צפוי",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else if (dealAnalysis != null) {
                    val analysis = dealAnalysis!!
                    item {
                        DealAnalysisResultCard(analysis = analysis)
                    }
                } else {
                    item {
                        // Israeli Market Rate Database Quick Categories
                        Text(
                            text = "מאגר תעריפי ספקים מעודכן בישראל (2026):",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        var selectedMarketCategory by remember { mutableStateOf("הכל") }
                        
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            items(com.example.data.local.IsraeliMarketData.allCategories) { cat ->
                                FilterChip(
                                    selected = selectedMarketCategory == cat,
                                    onClick = { selectedMarketCategory = cat },
                                    label = { Text(cat) },
                                    leadingIcon = if (selectedMarketCategory == cat) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }

                        // Render options from IsraeliMarketData
                        val marketOptions = com.example.data.local.IsraeliMarketData.getOptionsForCategory(selectedMarketCategory)
                        
                        marketOptions.take(6).forEach { option ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        queryInput = "${option.providerName} - ${option.planName}"
                                        viewModel.analyzeDeal(queryInput)
                                    },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = AiVioletPrimary.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = option.category,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = AiVioletPrimary),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = option.providerName,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }

                                        Surface(
                                            color = EmeraldSavings.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = option.priceRange,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, color = EmeraldSavings),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = option.planName,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = option.highlights,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = {
                                                queryInput = "${option.providerName} - ${option.planName}"
                                                viewModel.analyzeDeal(queryInput)
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = AiVioletPrimary)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("נתח תעריף ב-AI", style = MaterialTheme.typography.labelSmall.copy(color = AiVioletPrimary, fontWeight = FontWeight.Bold))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Sample questions
                        Text(
                            text = "שאלות ונושאי חשבונית נפוצים לבדיקה:",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        val sampleItems = listOf(
                            "חשבונית חשמל תקופתית IEC ₪520 — האם מעבר לאלקטרה פאוור משתלם?",
                            "חבילת סלולר סלקום/פרטנר זוגית ב-₪149 — בדיקת אלטרנטיבות 5G",
                            "אינטרנט בזק סיבים 1000Mb — השוואה מול סלקום פייבר ₪89",
                            "כפילויות ביטוח בריאות ודירה — איך לבצע סריקה בהר הביטוח?",
                            "מנוי טלוויזיה YES/HOT ב-₪199 — מעבר ל-FreeTV/stingTV ב-₪59"
                        )

                        sampleItems.forEach { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        queryInput = item
                                        viewModel.analyzeDeal(item)
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AiVioletPrimary)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(item, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                    }
                                    Icon(Icons.Default.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // --- LIVE AI CHAT ASSISTANT ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp)
            ) {
                // Quick prompt chips
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val chips = listOf(
                        "כמה חוסכים במעבר מספק חשמל IEC לאלקטרה פאוור?",
                        "איזו חבילת סלולר 5G הכי משתלמת למשפחה?",
                        "איך להוזיל את מנוי אינטרנט הסיבים בבית?",
                        "איך לבדוק כפילויות ביטוח בריאות בהר הביטוח?"
                    )
                    items(chips) { chipText ->
                        SuggestionChip(
                            onClick = { viewModel.sendChatMessage(chipText) },
                            label = { Text(chipText) },
                            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }

                // Chat Messages List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    reverseLayout = true
                ) {
                    items(chatMessages.reversed()) { msg ->
                        ChatMessageBubble(message = msg)
                    }
                }

                if (isChatLoading) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AiVioletPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "סייען ה-AI מנתח את החשבונית ותעריפי השוק...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Chat Input Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = chatInput,
                            onValueChange = { chatInput = it },
                            placeholder = { Text("שאל שאלה לגבי חשבון חשמל, סלולר, סיבים או ביטוח...") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ai_chat_input"),
                            shape = RoundedCornerShape(24.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                if (chatInput.isNotBlank()) {
                                    val textToSend = chatInput
                                    chatInput = ""
                                    viewModel.sendChatMessage(textToSend)
                                }
                            },
                            modifier = Modifier
                                .background(AiVioletPrimary, CircleShape)
                                .testTag("ai_chat_send_btn")
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "שלח", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(message: ChatMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            if (!message.isUser) {
                Surface(
                    color = AiVioletPrimary.copy(alpha = 0.15f),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(32.dp)
                        .padding(top = 2.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = AiVioletPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Surface(
                color = if (message.isUser) AiVioletPrimary else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (message.isUser) 18.dp else 4.dp,
                    bottomEnd = if (message.isUser) 4.dp else 18.dp
                ),
                tonalElevation = if (!message.isUser) 2.dp else 0.dp,
                border = if (!message.isUser) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) else null
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (!message.isUser) {
                        Text(
                            text = "עוזר AI — השוואת תעריפים וחיסכון לבית",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = AiVioletPrimary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Text(
                        text = message.text,
                        color = if (message.isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
                    )
                }
            }
        }
    }
}

@Composable
fun DealAnalysisResultCard(analysis: DealAnalysisResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(EmeraldSavings.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${analysis.dealScore}",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = EmeraldSavings
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("ציון כדאיות AI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = analysis.recommendation,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSavings
                            )
                        )
                    }
                }

                Surface(
                    color = AmberDeal.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "תעריף יעד: ₪${String.format("%.2f", analysis.predictedLowestPrice)}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = AmberDeal)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = analysis.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = AmberDeal)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = analysis.couponSuggestion,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (analysis.storeComparison.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "השוואת ספקים ואלטרנטיבות:",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(8.dp))

                analysis.storeComparison.forEach { storeInfo ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(storeInfo.storeName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                        Text("₪${String.format("%.2f", storeInfo.price)} / חודש", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldSavings))
                    }
                }
            }
        }
    }
}
