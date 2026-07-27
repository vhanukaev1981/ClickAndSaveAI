package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.theme.AmberDeal
import com.example.ui.theme.EmeraldSavings
import com.example.ui.theme.TechBluePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    var showSettingsScreen by remember { mutableStateOf(false) }

    if (showSettingsScreen) {
        SettingsScreen(
            viewModel = viewModel,
            onBackClick = { showSettingsScreen = false }
        )
        return
    }

    // Preferences from ViewModel
    val savingsGoal by viewModel.monthlySavingsGoal.collectAsState()
    val prefElectricity by viewModel.preferredElectricityProvider.collectAsState()
    val prefCellular by viewModel.preferredCellularProvider.collectAsState()
    val prefInternet by viewModel.preferredInternetProvider.collectAsState()
    val prefInsurance by viewModel.preferredInsuranceProvider.collectAsState()
    val prefStreaming by viewModel.preferredStreamingProvider.collectAsState()

    // User session & email from ViewModel
    val userSession by viewModel.userSession.collectAsState()
    val connectedEmail by viewModel.connectedEmail.collectAsState()
    val isGmailConnected by viewModel.isGmailConnected.collectAsState()
    val isSyncingGmail by viewModel.isSyncingGmail.collectAsState()
    val gmailSyncStep by viewModel.gmailSyncStep.collectAsState()

    var userName by remember(userSession) { mutableStateOf(userSession.displayName.ifBlank { "ישראל ישראלי" }) }
    var primaryEmail by remember(userSession, connectedEmail) { mutableStateOf(userSession.email.ifBlank { connectedEmail }) }
    var userPhone by remember { mutableStateOf("050-1234567") }

    // Mail Accounts & Sync settings
    var isPrimaryEmailConnected by remember { mutableStateOf(true) }
    var isSpouseSyncEnabled by remember { mutableStateOf(true) }
    var spouseEmail by remember { mutableStateOf("spouse.family@gmail.com") }
    var showSpouseDialog by remember { mutableStateOf(false) }

    // Push Notification toggles
    var isPushEnabled by remember { mutableStateOf(true) }
    var isPriceHikePushActive by remember { mutableStateOf(true) }
    var isNewInvoicePushActive by remember { mutableStateOf(true) }
    var isMonthlyReportPushActive by remember { mutableStateOf(true) }

    // Added value
    var isDuplicateDetectionActive by remember { mutableStateOf(true) }

    val totalMonthlyCost by viewModel.totalMonthlyCost.collectAsState()
    val totalMonthlySavingsPotential by viewModel.totalMonthlySavingsPotential.collectAsState()

    if (showSpouseDialog) {
        AlertDialog(
            onDismissRequest = { showSpouseDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GroupAdd, contentDescription = null, tint = TechBluePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("חיבור מייל משפחתי לסריקה", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "הוסף את כתובת ה-Gmail של בן/בת הזוג כדי לאחד ולסרוק את כל חשבוניות הבית (חשמל, סלולר, ביטוח) במקום אחד.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = spouseEmail,
                        onValueChange = { spouseEmail = it },
                        label = { Text("דוא\"ל בן/בת הזוג") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSpouseSyncEnabled = true
                        Toast.makeText(context, "חשבון משפחתי ($spouseEmail) סונכרן בהצלחה!", Toast.LENGTH_SHORT).show()
                        showSpouseDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TechBluePrimary)
                ) {
                    Text("אשר חיבור מייל משפחתי")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSpouseDialog = false }) {
                    Text("ביטול")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("profile_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section Header
        item {
            Column {
                Text(
                    text = "פרופיל והגדרות מערכת",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "ניהול פרטים אישיים, חיבורי מייל לסריקה אוטומטית והגדרות התראות Push",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. User Profile Details Card (פרטי המשתמש)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .background(TechBluePrimary.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = TechBluePrimary,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = primaryEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "טלפון: $userPhone",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            color = EmeraldSavings.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldSavings, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "פעיל",
                                    style = MaterialTheme.typography.labelSmall.copy(color = EmeraldSavings, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 1.5. Savings Goals & Preferred Providers Card (הגדרות והעדפות חיסכון)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_profile_settings_summary"),
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
                                    .size(36.dp)
                                    .background(EmeraldSavings.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, tint = EmeraldSavings, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "הגדרות והעדפות חיסכון",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "יעדי חיסכון חודשיים וספקי שירות מועדפים",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = { showSettingsScreen = true },
                            modifier = Modifier.testTag("btn_edit_settings_profile")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "ערוך הגדרות", tint = TechBluePrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // Monthly Goal Badge
                    Surface(
                        color = EmeraldSavings.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Savings, contentDescription = null, tint = EmeraldSavings, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("יעד חיסכון חודשי:", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            }
                            Text(
                                text = "₪${String.format("%,.0f", savingsGoal)}/חודש",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, color = EmeraldSavings)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Preferred Providers Summary Chips
                    Text(
                        text = "ספקי שירות מועדפים שנבחרו:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("⚡ חשמל:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(prefElectricity, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("📱 סלולר:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(prefCellular, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("🌐 אינטרנט:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(prefInternet, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("🛡️ ביטוח:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(prefInsurance, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = { showSettingsScreen = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ערוך יעדי חיסכון וספקים מועדפים ⚙️", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. Mail Accounts & Auto-Scan Management (ניהול חיבורי המייל והסריקה)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MarkEmailRead, contentDescription = null, tint = TechBluePrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ניהול חיבורי מייל לסריקת חשבוניות",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "סריקה אוטומטית שקטה לקריאת חשבוניות ותשלומי בית בלבד (ללא גישה למיילים אישיים)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Primary Gmail Connection
                    Surface(
                        color = TechBluePrimary.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Mail, contentDescription = null, tint = TechBluePrimary, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("חשבון Gmail ראשי", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                    Text(primaryEmail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.triggerGmailSync()
                                    Toast.makeText(context, "מתחיל סריקת חשבוניות ב-Gmail...", Toast.LENGTH_SHORT).show()
                                },
                                enabled = !isSyncingGmail,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                if (isSyncingGmail) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = TechBluePrimary)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isSyncingGmail) "סורק..." else "סרוק עכשיו", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Spouse / Family Gmail Connection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(TechBluePrimary.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Group, contentDescription = null, tint = TechBluePrimary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("חיבור משפחתי (Family Sharing)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                Text(
                                    text = if (isSpouseSyncEnabled) "מסונכרן עם $spouseEmail" else "סריקת חשבונות מייל של בן/בת הזוג",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isSpouseSyncEnabled,
                            onCheckedChange = { checked ->
                                if (checked) showSpouseDialog = true
                                else isSpouseSyncEnabled = false
                            }
                        )
                    }

                    if (isSpouseSyncEnabled) {
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(
                            onClick = { showSpouseDialog = true },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ערוך כתובת מייל של בן/בת הזוג", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Recognized Senders List
                    Text(
                        text = "ספקים שזוהו ונסרקים באופן אוטומטי:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("⚡ חברת החשמל", "📱 פלאפון", "🌐 פרטנר", "📺 הוט", "🛡️ הראל").forEach { sender ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = sender,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Push Notifications Settings Card (הגדרות התראות Push)
        item {
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
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = TechBluePrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "הגדרות התראות Push חכמות",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Switch(
                            checked = isPushEnabled,
                            onCheckedChange = { isPushEnabled = it }
                        )
                    }

                    if (isPushEnabled) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(14.dp))

                        // Price Hike Alert (14 Days)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(AmberDeal.copy(alpha = 0.12f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Shield, contentDescription = null, tint = AmberDeal, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("התראת שומר מסך (14 יום לפני פקיעה)", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                    Text("התראה מראש לפני זינוק במחיר בסלולר/סיבים", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Switch(
                                checked = isPriceHikePushActive,
                                onCheckedChange = { isPriceHikePushActive = it }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // New Invoice Notification
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(TechBluePrimary.copy(alpha = 0.1f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Receipt, contentDescription = null, tint = TechBluePrimary, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("התראה על חשבונית חדשה שהגיעה במייל", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                    Text("עדכון מידי כשה-AI מזהה חשבונית והזדמנות לחיסכון", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Switch(
                                checked = isNewInvoicePushActive,
                                onCheckedChange = { isNewInvoicePushActive = it }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Monthly Report Push
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(EmeraldSavings.copy(alpha = 0.12f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Insights, contentDescription = null, tint = EmeraldSavings, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("דוח חיסכון חודשי מרוכז", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                    Text("סיכום חודשי של סך ההוצאות והחיסכון שהושג", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Switch(
                                checked = isMonthlyReportPushActive,
                                onCheckedChange = { isMonthlyReportPushActive = it }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Test Push Button
                        OutlinedButton(
                            onClick = {
                                viewModel.triggerSimulatedPushNotification()
                                Toast.makeText(context, "התראת Push לבדיקה נשלחה!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("🧪 שלח התראת Push לבדיקה עכשיו")
                        }
                    }
                }
            }
        }

        // 4. Financial Health Summary Card (דוח בריאות פיננסית למשק הבית)
        item {
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
                            Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = TechBluePrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "דוח בריאות פיננסית למשק הבית",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Surface(
                            color = EmeraldSavings.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "ציון: 94/100",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = EmeraldSavings)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("סך הוצאות חובה", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₪${String.format("%.0f", totalMonthlyCost)}/חודש", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("פוטנציאל חיסכון שנתי", style = MaterialTheme.typography.labelSmall, color = EmeraldSavings)
                            Text("₪${String.format("%.0f", totalMonthlySavingsPotential * 12)}/שנה", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, color = EmeraldSavings))
                        }
                    }
                }
            }
        }

        // 5. Security & Zero Effort Principles Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = TechBluePrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("אבטחה ופרטיות בלעדית", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Click & Save AI קוראת אך ורק חשבוניות ותשלומי בית מורשים.\n• האפליקציה אינה שומרת סיסמאות ואינה מבצעת חיובים או סליקת אשראי.\n• כל העברות המידע מוצפנות בתקן אבטחה בנקאי 256-bit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
