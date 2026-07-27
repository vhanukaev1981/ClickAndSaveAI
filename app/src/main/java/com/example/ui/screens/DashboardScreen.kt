package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.InvoiceItem
import com.example.data.local.IsraeliMarketData
import com.example.data.local.MarketProviderOption
import com.example.ui.MainViewModel
import com.example.ui.components.DailySavingsTipCard
import com.example.ui.theme.AmberDeal
import com.example.ui.theme.EmeraldSavings
import com.example.ui.theme.TechBluePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToTab: (Int) -> Unit,
    onOpenReceiptScan: () -> Unit
) {
    val context = LocalContext.current
    val invoices by viewModel.invoices.collectAsState()
    val totalMonthlySavingsPotential by viewModel.totalMonthlySavingsPotential.collectAsState()
    val totalMonthlyCost by viewModel.totalMonthlyCost.collectAsState()
    val totalAnnualSavingsPotential = totalMonthlySavingsPotential * 12

    val isGmailConnected by viewModel.isGmailConnected.collectAsState()
    val isSyncingGmail by viewModel.isSyncingGmail.collectAsState()
    val gmailSyncStep by viewModel.gmailSyncStep.collectAsState()
    val connectedEmail by viewModel.connectedEmail.collectAsState()

    val showPushBanner by viewModel.showPushBanner.collectAsState()
    val pushTitle by viewModel.pushNotificationTitle.collectAsState()
    val pushBody by viewModel.pushNotificationBody.collectAsState()

    val priceHikeAlerts by viewModel.priceHikeAlerts.collectAsState()

    var selectedInvoiceForSwitch by remember { mutableStateOf<InvoiceItem?>(null) }
    var showAddInvoiceDialog by remember { mutableStateOf(false) }
    var showMarketDataModal by remember { mutableStateOf(false) }
    var showExportReportDialog by remember { mutableStateOf(false) }
    var showSettingsModal by remember { mutableStateOf(false) }

    if (showSettingsModal) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showSettingsModal = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                SettingsScreen(
                    viewModel = viewModel,
                    onBackClick = { showSettingsModal = false }
                )
            }
        }
    }

    if (showExportReportDialog) {
        val savingsRecords by viewModel.savingsRecords.collectAsState()
        val totalSavings by viewModel.totalSavings.collectAsState()
        val reportText = com.example.ui.screens.generateMonthlySavingsReport(
            totalSavings = if (totalSavings > 0) totalSavings else totalMonthlySavingsPotential,
            records = savingsRecords
        )
        com.example.ui.screens.ExportSavingsReportDialog(
            reportText = reportText,
            onDismiss = { showExportReportDialog = false }
        )
    }

    // Dialog state for 1-Click Switch lead request
    selectedInvoiceForSwitch?.let { invoice ->
        SwitchRequestModal(
            invoice = invoice,
            onDismiss = { selectedInvoiceForSwitch = null },
            onSubmit = { fullName, phone, email ->
                viewModel.requestProviderSwitch(invoice)
                Toast.makeText(
                    context,
                    "בקשת מעבר ל-${invoice.recommendedAlternative} נשלחה בהצלחה! נציג יצור איתך קשר.",
                    Toast.LENGTH_LONG
                ).show()
                selectedInvoiceForSwitch = null
            }
        )
    }

    if (showAddInvoiceDialog) {
        AddInvoiceModal(
            onDismiss = { showAddInvoiceDialog = false },
            onAddInvoice = { provider, category, monthlyCost, altProvider, altCost, savings ->
                viewModel.addManualInvoice(
                    providerName = provider,
                    category = category,
                    monthlyCost = monthlyCost,
                    recommendedAlternative = altProvider,
                    alternativeCost = altCost,
                    savings = savings
                )
                Toast.makeText(context, "חשבונית נוספה ופוענחה בהצלחה!", Toast.LENGTH_SHORT).show()
                showAddInvoiceDialog = false
            }
        )
    }

    if (showMarketDataModal) {
        IsraeliMarketDatabaseModal(
            onDismiss = { showMarketDataModal = false },
            onSelectPlan = { option ->
                showMarketDataModal = false
                Toast.makeText(
                    context,
                    "נבחר מסלול: ${option.providerName} - ${option.planName}. לחץ על ⚡ בקש מעבר להשלמה.",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!isGmailConnected && !isSyncingGmail) {
            // 3. Landing & Onboarding Screen (מסך כניסה וחיבור)
            GmailLandingScreen(
                onConnectGmail = { viewModel.connectGmail() },
                isSyncing = isSyncingGmail,
                syncStep = gmailSyncStep
            )
        } else if (isSyncingGmail) {
            // Syncing / Scanning Loading State Screen
            GmailScanningProgressScreen(syncStep = gmailSyncStep)
        } else {
            // 5. Main Dashboard Screen after Connection (מסך ראשי לאחר התחברות)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("dashboard_screen"),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                // Header with Gmail Connection Status
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_click_save_logo_1785138917633),
                                    contentDescription = "Click & Save AI Logo",
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Click & Save AI",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    Text(
                                        text = "מחובר: $connectedEmail",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldSavings,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Gmail Status, Settings & Refresh
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { showSettingsModal = true },
                                    modifier = Modifier.testTag("btn_open_settings")
                                ) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = "הגדרות והעדפות חיסכון",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.triggerGmailSync() },
                                    modifier = Modifier.testTag("btn_refresh_gmail")
                                ) {
                                    Icon(
                                        Icons.Default.Sync,
                                        contentDescription = "רענן סריקה",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                TextButton(onClick = { viewModel.disconnectGmail() }) {
                                    Text("התנתק", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }

                // Daily Savings Tip Component (רכיב 'טיפ החיסכון היומי' מעל הדשבורד)
                item {
                    DailySavingsTipCard(
                        viewModel = viewModel,
                        onApplyTipAction = { tip ->
                            when (tip.category) {
                                "חשמל", "סלולר", "אינטרנט" -> onNavigateToTab(2) // Providers Tab
                                "ביטוח" -> onNavigateToTab(1) // Invoices Tab
                                else -> onNavigateToTab(3) // AI Assistant Tab
                            }
                        }
                    )
                }

                // Push Notification Demo Trigger Button
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("card_push_demo"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TechBluePrimary.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(TechBluePrimary.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = TechBluePrimary, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("הדגמת התראות פוש חכמות", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Text("בדוק התראה על חשבונית חדשה או התראת שומר מסך 14 יום לפני התייקרות", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.triggerSimulatedPushNotification() },
                                    colors = ButtonDefaults.buttonColors(containerColor = TechBluePrimary),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("btn_trigger_push")
                                ) {
                                    Text("🔔 פוש חשבונית", style = MaterialTheme.typography.labelSmall)
                                }

                                Button(
                                    onClick = { viewModel.triggerPriceHikeSimulatedPush() },
                                    colors = ButtonDefaults.buttonColors(containerColor = AmberDeal),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("btn_trigger_price_hike_push")
                                ) {
                                    Text("🛡️ פוש שומר מסך (14 יום)", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                // Status Banner (הודעת סטטוס לאחר סריקה)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("status_banner_gmail"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = EmeraldSavings.copy(alpha = 0.08f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldSavings.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(EmeraldSavings.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldSavings,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "שלום! המערכת מחוברת למייל וסורקת חשבוניות באופן אוטומטי",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldSavings
                                    )
                                )
                                Text(
                                    text = "מנוע ה-AI סרק באופן אוטומטי ושקט ברקע את חברות החשמל, הסלולר, האינטרנט והביטוח ב-Gmail שלך.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Opening Banner - Annual Savings Potential
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("banner_annual_savings"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                            MaterialTheme.colorScheme.surface
                                        )
                                    )
                                )
                                .padding(20.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Savings,
                                            contentDescription = null,
                                            tint = EmeraldSavings,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "פוטנציאל חיסכון שנתי מזהה",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                    }
                                    Surface(
                                        color = EmeraldSavings,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "חיסכון של ${(totalMonthlySavingsPotential / totalMonthlyCost * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = "₪${String.format("%,.2f", totalAnnualSavingsPotential)}",
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = EmeraldSavings
                                    )
                                )

                                Text(
                                    text = "מבוסס על סריקת ${invoices.size} חשבוניות שנמצאו בתיבת ה-Gmail שלך",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { showMarketDataModal = true },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("btn_browse_israel_market"),
                                        colors = ButtonDefaults.buttonColors(containerColor = TechBluePrimary),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.ManageSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("מאגר ספקים בישראל")
                                    }

                                    OutlinedButton(
                                        onClick = { showAddInvoiceDialog = true },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("btn_add_manual_bill"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("הוסף חשבונית")
                                    }
                                }
                            }
                        }
                    }
                }

                // KPI Module
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = "מדדי הוצאות וחיסכון חודשיים",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.padding(bottom = 10.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            KpiCard(
                                modifier = Modifier.weight(1f),
                                title = "סך הוצאה חודשית",
                                value = "₪${String.format("%,.0f", totalMonthlyCost)}",
                                icon = Icons.Default.AccountBalanceWallet,
                                accentColor = MaterialTheme.colorScheme.primary
                            )

                            KpiCard(
                                modifier = Modifier.weight(1f),
                                title = "חיסכון חודשי צפוי",
                                value = "₪${String.format("%.2f", totalMonthlySavingsPotential)}",
                                icon = Icons.Default.TrendingDown,
                                accentColor = EmeraldSavings
                            )

                            KpiCard(
                                modifier = Modifier.weight(1f),
                                title = "הצעות מעבר מוכנות",
                                value = "${invoices.count { !it.isSwitchRequested }} הצעות",
                                icon = Icons.Default.Bolt,
                                accentColor = AmberDeal
                            )
                        }
                    }
                }

                // Financial Health Report with Visual Bar Chart (דוח בריאות פיננסית עם גרף עמודות)
                item {
                    FinancialHealthReportBarChartCard(
                        invoices = invoices,
                        totalMonthlyCost = totalMonthlyCost,
                        totalMonthlySavingsPotential = totalMonthlySavingsPotential,
                        onBulkSwitchClick = {
                            selectedInvoiceForSwitch = invoices.firstOrNull { !it.isSwitchRequested } ?: invoices.firstOrNull()
                        },
                        onExportReportClick = {
                            showExportReportDialog = true
                        }
                    )
                }

                // Price Hike Saver Alerts (שומר מסך מפני התייקרויות - התראה 14 יום לפני)
                if (priceHikeAlerts.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = AmberDeal,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "שומר מסך מפני התייקרויות",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }

                                Surface(
                                    color = AmberDeal.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "התראה 14 יום לפני פקיעה",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = AmberDeal),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            priceHikeAlerts.forEach { alert ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AmberDeal.copy(alpha = 0.4f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    color = AmberDeal.copy(alpha = 0.15f),
                                                    shape = CircleShape,
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Default.Timer, contentDescription = null, tint = AmberDeal, modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(alert.serviceName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                                    Text(alert.planName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }

                                            Surface(
                                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "14 ימים לסיום המבצע",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                        Spacer(modifier = Modifier.height(12.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text("מחיר נוכחי", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("₪${String.format("%.2f", alert.currentPrice)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                            }

                                            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.CenterVertically))

                                            Column {
                                                Text("התייקרות צפויה", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                                Text("₪${String.format("%.2f", alert.expectedPriceAfterHike)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error))
                                            }

                                            Column {
                                                Text("חלופה מוזלת", style = MaterialTheme.typography.labelSmall, color = EmeraldSavings)
                                                Text("₪${String.format("%.2f", alert.alternativePrice)} ב-${alert.alternativeProvider}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = EmeraldSavings))
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        Button(
                                            onClick = {
                                                viewModel.triggerPriceHikeSimulatedPush()
                                                Toast.makeText(context, "התראת שומר מסך 14 יום לפני פקיעה נשלחה למכשירך!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = AmberDeal),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("⚡ מנע התייקרות — מעבר בקליק ל-${alert.alternativeProvider}", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Action Cards Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ElectricBolt,
                                contentDescription = null,
                                tint = AmberDeal,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "הצעות לחיסכון מיידי - מעבר בלחיצת כפתור",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Text(
                            text = "${invoices.size} חשבוניות",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Action Cards List
                items(invoices, key = { it.id }) { invoice ->
                    SavingsActionCard(
                        invoice = invoice,
                        onRequestSwitch = { selectedInvoiceForSwitch = invoice }
                    )
                }

                // Gmail Invoices Table Log
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 24.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Mail,
                                    contentDescription = null,
                                    tint = TechBluePrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "חשבוניות שנמצאו בסריקת ה-Gmail",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            IconButton(onClick = { showAddInvoiceDialog = true }) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = "הוסף חשבונית", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(vertical = 10.dp, horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("ספק / קטגוריה", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1.3f))
                                    Text("סכום חודשי", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(0.9f))
                                    Text("סטטוס סריקת AI", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1.2f))
                                }

                                Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                invoices.forEach { inv ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp, horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1.3f)) {
                                            Text(
                                                text = inv.providerName,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "מייל: ${inv.accountNumber}@gmail • ${inv.billDate}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Text(
                                            text = "₪${String.format("%.0f", inv.monthlyCost)}",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.weight(0.9f)
                                        )

                                        Surface(
                                            modifier = Modifier.weight(1.2f),
                                            color = if (inv.isSwitchRequested) EmeraldSavings.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = if (inv.isSwitchRequested) "מעבר בטיפול" else inv.status,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = if (inv.isSwitchRequested) EmeraldSavings else MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Animated Push Notification Banner at Top
        AnimatedVisibility(
            visible = showPushBanner,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.dismissPushBanner()
                        // Find electricity invoice and show switch modal
                        val elecInv = invoices.find { it.category == "חשמל" } ?: invoices.firstOrNull()
                        elecInv?.let { selectedInvoiceForSwitch = it }
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, TechBluePrimary)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(TechBluePrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(pushTitle, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TechBluePrimary))
                        Text(pushBody, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("לחץ על ההתראה לצפייה בניתוח ה-AI ובקשת מעבר בקליק ⚡", style = MaterialTheme.typography.labelSmall.copy(color = EmeraldSavings, fontWeight = FontWeight.Bold))
                    }

                    IconButton(onClick = { viewModel.dismissPushBanner() }) {
                        Icon(Icons.Default.Close, contentDescription = "סגור", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// 3. Landing & Onboarding Screen (מסך כניסה וחיבור)
@Composable
fun GmailLandingScreen(
    onConnectGmail: () -> Unit,
    isSyncing: Boolean,
    syncStep: String
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("gmail_landing_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // App Logo
            Image(
                painter = painterResource(id = R.drawable.img_click_save_logo_1785138917633),
                contentDescription = "Click & Save AI",
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Main Vision Headline
            Text(
                text = "Click & Save AI — המערכת שחוסכת לך כסף בלי שתזיז אצבע",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Vision Description
            Text(
                text = "מנוע ה-AI שלנו סורק באופן אוטומטי, שקט ושקוף ברקע את החשבוניות המגיעות למייל שלך (חשמל, סלולר, אינטרנט, ביטוח ומנויים). המערכת מוצאת כפילויות ומחירים מופרזים ומאפשרת לך לעבור לספקים מוזלים בלחיצת כפתור אחת!",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // SINGLE CENTRAL GOOGLE LOGIN BUTTON WITH GOOGLE LOGO
            Button(
                onClick = onConnectGmail,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .testTag("btn_connect_gmail_central"),
                colors = ButtonDefaults.buttonColors(containerColor = TechBluePrimary),
                shape = RoundedCornerShape(20.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        color = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "G",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF4285F4),
                                    fontSize = 20.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "להתחברות וסריקה אוטומטית עם Gmail",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 17.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = EmeraldSavings,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "אבטחה מקסימלית • הצפנת 256-bit • ללא שמירת סיסמאות",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Value Propositions Grid Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    FeatureRow(
                        icon = Icons.Default.Mail,
                        title = "חיבור Gmail שקט ברקע",
                        subtitle = "ללא צורך בהזנת נתונים ידנית, העלאת מסמכים או סריקת קבלות."
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(16.dp))
                    FeatureRow(
                        icon = Icons.Default.NotificationsActive,
                        title = "התראות פוש בנייד מניתוח חשבוניות",
                        subtitle = "התראה מיידית עם הגעת חשבונית חדשה למייל והצגת אלטרנטיבות מוזלות."
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(16.dp))
                    FeatureRow(
                        icon = Icons.Default.ElectricBolt,
                        title = "אישור מעבר בלחיצת כפתור אחת",
                        subtitle = "בקש מעבר לספק מוזל בקליק אחד בלבד - ללא בירוקרטיה."
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// Progress screen while AI Gmail sync is taking place
@Composable
fun GmailScanningProgressScreen(syncStep: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(56.dp),
                    color = TechBluePrimary,
                    strokeWidth = 5.dp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "מנוע ה-AI עובד בשבילך...",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = syncStep,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                    color = EmeraldSavings,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(TechBluePrimary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = TechBluePrimary, modifier = Modifier.size(22.dp))
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun KpiCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(accentColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = accentColor),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SavingsActionCard(
    invoice: InvoiceItem,
    onRequestSwitch: () -> Unit
) {
    val categoryColor = when (invoice.category) {
        "חשמל" -> AmberDeal
        "סלולר" -> TechBluePrimary
        "אינטרנט" -> Color(0xFF6366F1) // Indigo
        "ביטוח" -> Color(0xFF8B5CF6) // Purple
        else -> Color(0xFF0D9488) // Teal
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("action_card_${invoice.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Category Badge & Provider Name
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = categoryColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = invoice.category,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = categoryColor,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = invoice.providerName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                // Monthly Savings Badge
                Surface(
                    color = EmeraldSavings.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "חיסכון ₪${String.format("%.0f", invoice.potentialMonthlySavings)}/חודש",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = EmeraldSavings,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pricing Comparison Box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "תשלום נוכחי",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "₪${String.format("%.2f", invoice.monthlyCost)}",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                textDecoration = TextDecoration.LineThrough,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    Icon(
                        Icons.Default.ArrowBack, // Pointing towards lower cost in RTL context
                        contentDescription = null,
                        tint = EmeraldSavings,
                        modifier = Modifier.size(20.dp)
                    )

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "הצעה מוזלת בשוק",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldSavings,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "₪${String.format("%.2f", invoice.alternativeMonthlyCost)}",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSavings
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Alternative Description
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = EmeraldSavings,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = invoice.recommendedAlternative,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Button: "⚡ בקש מעבר בקליק"
            if (invoice.isSwitchRequested) {
                OutlinedButton(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Done, contentDescription = null, tint = EmeraldSavings)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("בקשת מעבר נשלחה • בטיפול נציג", color = EmeraldSavings, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onRequestSwitch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_switch_${invoice.id}"),
                    colors = ButtonDefaults.buttonColors(containerColor = TechBluePrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ElectricBolt, contentDescription = null, tint = Color.Yellow)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "⚡ בקש מעבר בקליק (חיסכון ₪${String.format("%.0f", invoice.potentialAnnualSavings)}/שנה)",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SwitchRequestModal(
    invoice: InvoiceItem,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String) -> Unit
) {
    var fullName by remember { mutableStateOf("ישראל ישראלי") }
    var phone by remember { mutableStateOf("050-1234567") }
    var email by remember { mutableStateOf("vadim.hanukaev1981@gmail.com") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ElectricBolt, contentDescription = null, tint = AmberDeal)
                Spacer(modifier = Modifier.width(8.dp))
                Text("אישור מעבר ספק בלחיצת כפתור", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    text = "מעביר אותך למסלול מוזל ב-${invoice.recommendedAlternative}. הפרטים שלך (שם, נייד, מייל) יישלחו בצורה מאובטחת להשלמת המעבר מספק '${invoice.providerName}'.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = EmeraldSavings.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "חיסכון צפוי: ₪${String.format("%.2f", invoice.potentialMonthlySavings)} בחודש (₪${String.format("%.0f", invoice.potentialAnnualSavings)} בשנה)",
                            style = MaterialTheme.typography.labelMedium.copy(color = EmeraldSavings, fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "⏱️ נציג יחזור אליך תוך 24 שעות להשלמת המעבר ללא עלות וללא בירוקרטיה.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("שם מלא") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("מספר טלפון") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("כתובת דוא\"ל למעקב") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(fullName, phone, email) },
                colors = ButtonDefaults.buttonColors(containerColor = TechBluePrimary)
            ) {
                Text("אישור והעברת בקשת מעבר בקליק")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ביטול")
            }
        }
    )
}

@Composable
fun AddInvoiceModal(
    onDismiss: () -> Unit,
    onAddInvoice: (String, String, Double, String, Double, Double) -> Unit
) {
    var providerName by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("חשמל") }
    var monthlyCostText by remember { mutableStateOf("") }

    val categories = listOf("חשמל", "סלולר", "אינטרנט", "ביטוח", "קניות")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("העלאת חשבונית ידנית לניתוח AI", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "הזן את שם הספק והסכום החודשי. מנוע ה-AI יאתר אוטומטית חלופות מוזלות בשוק.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text("קטגוריה:", style = MaterialTheme.typography.labelMedium)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = (selectedCategory == cat),
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = providerName,
                    onValueChange = { providerName = it },
                    label = { Text("שם הספק הנוכחי") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = monthlyCostText,
                    onValueChange = { monthlyCostText = it },
                    label = { Text("סכום חשבונית חודשית ב-₪") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cost = monthlyCostText.toDoubleOrNull() ?: 150.0
                    val provider = if (providerName.isBlank()) "ספק $selectedCategory" else providerName
                    val discountPercent = when (selectedCategory) {
                        "חשמל" -> 0.07
                        "סלולר" -> 0.35
                        "אינטרנט" -> 0.25
                        "ביטוח" -> 0.20
                        else -> 0.12
                    }
                    val savings = cost * discountPercent
                    val altCost = cost - savings
                    val altProvider = when (selectedCategory) {
                        "חשמל" -> "אלקטרה פאוור / אמישראגז (7% הנחה)"
                        "סלולר" -> "חבילת 5G מוזלת בשוק (תחרות סלולר)"
                        "אינטרנט" -> "ספק סיבים אופטיים במבצע"
                        "ביטוח" -> "פוליסה ללא כפילויות במחיר מופחת"
                        else -> "רשת דיסקאונט מוזלת"
                    }
                    onAddInvoice(provider, selectedCategory, cost, altProvider, altCost, savings)
                },
                enabled = monthlyCostText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = TechBluePrimary)
            ) {
                Text("פענח חשבונית ומצא חיסכון")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ביטול")
            }
        }
    )
}

@Composable
fun IsraeliMarketDatabaseModal(
    onDismiss: () -> Unit,
    onSelectPlan: (MarketProviderOption) -> Unit
) {
    var selectedCategoryFilter by remember { mutableStateOf("הכל") }
    val options = IsraeliMarketData.getOptionsForCategory(selectedCategoryFilter)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = TechBluePrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("מאגר ספקי שירות ותעריפים בישראל", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 450.dp)) {
                Text(
                    text = "סקור את רשימת הספקים, המסלולים וההנחות המעודכנות בישראל:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    items(IsraeliMarketData.allCategories) { cat ->
                        FilterChip(
                            selected = (selectedCategoryFilter == cat),
                            onClick = { selectedCategoryFilter = cat },
                            label = { Text(cat) }
                        )
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(options) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectPlan(item) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.providerName,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TechBluePrimary)
                                    )
                                    Surface(
                                        color = EmeraldSavings.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = item.priceRange,
                                            style = MaterialTheme.typography.labelSmall.copy(color = EmeraldSavings, fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = item.planName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )

                                Text(
                                    text = item.highlights,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = TechBluePrimary)
            ) {
                Text("סגור מאגר")
            }
        }
    )
}

@Composable
fun FinancialHealthReportBarChartCard(
    invoices: List<com.example.data.local.InvoiceItem>,
    totalMonthlyCost: Double,
    totalMonthlySavingsPotential: Double,
    onBulkSwitchClick: () -> Unit,
    onExportReportClick: (() -> Unit)? = null
) {
    val totalOptimizedCost = (totalMonthlyCost - totalMonthlySavingsPotential).coerceAtLeast(0.0)
    val maxExpense = invoices.maxOfOrNull { it.monthlyCost } ?: 500.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("card_financial_health_report"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, TechBluePrimary.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Elegant Visual Card for Financial Health Score (רכיב בריאות פיננסית)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = EmeraldSavings.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldSavings.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(EmeraldSavings.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🟢", fontSize = 18.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "בריאות פיננסית: 94/100 🟢",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = EmeraldSavings
                                    )
                                )
                                Text(
                                    text = "ציון מעולה • מנוע ה-AI זיהה פוטנציאל חיסכון משמעותי בחשבונות",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Subtle Linear Progress Bar
                    LinearProgressIndicator(
                        progress = { 0.94f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = EmeraldSavings,
                        trackColor = EmeraldSavings.copy(alpha = 0.15f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section Subheader: Visual Bar Chart of Monthly Home Expenses vs Savings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = TechBluePrimary.copy(alpha = 0.12f),
                        shape = CircleShape,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.BarChart, contentDescription = null, tint = TechBluePrimary, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "דוח הוצאות הבית לעומת חיסכון מוצע",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Chart Legend
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(TechBluePrimary, RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("הוצאה נוכחית (₪)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(EmeraldSavings, RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("לאחר מעבר מוזל (₪)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = EmeraldSavings)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Visual Bars for each Invoice Category
            Text(
                text = "השוואת עלויות חודשיות לפי קטגוריית שירות (₪/חודש):",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            invoices.take(5).forEach { invoice ->
                val currentRatio = (invoice.monthlyCost / maxExpense).toFloat().coerceIn(0.15f, 1.0f)
                val alternativeRatio = (invoice.alternativeMonthlyCost / maxExpense).toFloat().coerceIn(0.12f, 1.0f)

                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${getCategoryIconSymbol(invoice.category)} ${invoice.providerName} (${invoice.category})",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                        )

                        Surface(
                            color = EmeraldSavings.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "חיסכון: ₪${String.format("%.1f", invoice.potentialMonthlySavings)}/חודש",
                                style = MaterialTheme.typography.labelSmall.copy(color = EmeraldSavings, fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Bar 1: Current Cost
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(currentRatio)
                                .height(16.dp)
                                .background(TechBluePrimary.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "₪${invoice.monthlyCost.toInt()}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TechBluePrimary)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Bar 2: Alternative / Savings Cost
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(alternativeRatio)
                                .height(16.dp)
                                .background(EmeraldSavings, RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "₪${invoice.alternativeMonthlyCost.toInt()}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = EmeraldSavings)
                        )
                    }
                }
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Summary Totals Box
            Surface(
                color = EmeraldSavings.copy(alpha = 0.08f),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldSavings.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("סך הוצאה נוכחית:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("₪${String.format("%.2f", totalMonthlyCost)} / חודש", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("סך הוצאה מוזלת מומלצת:", style = MaterialTheme.typography.bodySmall, color = EmeraldSavings)
                        Text("₪${String.format("%.2f", totalOptimizedCost)} / חודש", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = EmeraldSavings))
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Divider(color = EmeraldSavings.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("חיסכון מצטבר צפוי:", style = MaterialTheme.typography.labelSmall, color = EmeraldSavings)
                            Text(
                                text = "₪${String.format("%.2f", totalMonthlySavingsPotential)}/חודש (₪${String.format("%,.0f", totalMonthlySavingsPotential * 12)}/שנה)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold, color = EmeraldSavings)
                            )
                        }

                        Surface(
                            color = EmeraldSavings,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "-${(totalMonthlySavingsPotential / totalMonthlyCost * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium.copy(color = Color.White, fontWeight = FontWeight.ExtraBold),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onBulkSwitchClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = TechBluePrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("⚡ מעבר בלחיצה אחת לכל המסלולים המוזלים", fontWeight = FontWeight.Bold)
            }

            if (onExportReportClick != null) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onExportReportClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_export_monthly_report_card"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Assessment, contentDescription = null, tint = EmeraldSavings, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("📊 ייצוא דו\"ח חיסכון חודשי", fontWeight = FontWeight.Bold, color = EmeraldSavings)
                }
            }
        }
    }
}

private fun getCategoryIconSymbol(cat: String): String {
    return when {
        cat.contains("חשמל") -> "⚡"
        cat.contains("סלולר") -> "📱"
        cat.contains("אינטרנט") || cat.contains("סיבים") -> "🌐"
        cat.contains("ביטוח") -> "🛡️"
        cat.contains("טלוויזיה") || cat.contains("TV") -> "📺"
        else -> "📄"
    }
}
