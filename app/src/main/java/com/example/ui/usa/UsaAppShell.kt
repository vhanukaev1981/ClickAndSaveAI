package com.example.ui.usa

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Forest
import com.example.ui.theme.Ink
import com.example.ui.theme.Line
import com.example.ui.theme.MintSoft
import com.example.ui.theme.Muted
import com.example.ui.theme.Paper
import com.example.ui.theme.White
import com.example.ui.usa.theme.UsaTheme
import kotlinx.coroutines.launch

@Composable
fun UsaAppShell(
    modifier: Modifier = Modifier,
    initialDataMode: AppDataMode = AppDataMode.DEMO
) {
    // Force LTR layout for approved USA product experience
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        UsaTheme {
            var dataMode by remember { mutableStateOf(initialDataMode) }
            var currentTab by remember { mutableStateOf(UsaTab.HOME) }
            val backStack = remember { mutableStateListOf<UsaDestination>() }
            var activeSheet by remember { mutableStateOf(UsaSheetType.NONE) }
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()

            fun navigateTo(dest: UsaDestination) {
                backStack.add(dest)
            }

            fun navigateBack(): Boolean {
                return if (backStack.isNotEmpty()) {
                    backStack.removeAt(backStack.size - 1)
                    true
                } else {
                    false
                }
            }

            fun showNotice(text: String) {
                scope.launch {
                    snackbarHostState.showSnackbar(text)
                }
            }

            BackHandler(enabled = backStack.isNotEmpty() || activeSheet != UsaSheetType.NONE) {
                if (activeSheet != UsaSheetType.NONE) {
                    activeSheet = UsaSheetType.NONE
                } else {
                    navigateBack()
                }
            }

            val currentDestination = backStack.lastOrNull() ?: UsaDestination.MainApp

            Scaffold(
                modifier = modifier.fillMaxSize(),
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    if (currentDestination is UsaDestination.MainApp) {
                        Column {
                            GuardianTopAppBar(
                                title = when (currentTab) {
                                    UsaTab.HOME -> "Click & Save AI"
                                    UsaTab.BILLS -> "Bills & monitoring"
                                    UsaTab.SAVINGS -> "Ways to save"
                                    UsaTab.ACTIVITY -> "Guardian activity"
                                    UsaTab.PROFILE -> "Profile & control"
                                },
                                onNotificationsClick = {
                                    navigateTo(UsaDestination.Notifications)
                                }
                            )
                            // Compact Demo/Live mode switcher strip for testing & user clarity
                            Surface(
                                color = Paper,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "DATA MODE:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Muted
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (dataMode == AppDataMode.DEMO) Forest else White)
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        TextButton(
                                            onClick = { dataMode = AppDataMode.DEMO },
                                            modifier = Modifier.height(24.dp)
                                        ) {
                                            Text(
                                                "Demo",
                                                color = if (dataMode == AppDataMode.DEMO) White else Ink,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (dataMode == AppDataMode.REAL) Forest else White)
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        TextButton(
                                            onClick = { dataMode = AppDataMode.REAL },
                                            modifier = Modifier.height(24.dp)
                                        ) {
                                            Text(
                                                "Live",
                                                color = if (dataMode == AppDataMode.REAL) White else Ink,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                bottomBar = {
                    if (currentDestination is UsaDestination.MainApp) {
                        GuardianBottomNavigation(
                            currentTab = currentTab,
                            onTabSelected = { selected ->
                                currentTab = selected
                            }
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentDestination) {
                        is UsaDestination.MainApp -> {
                            if (dataMode == AppDataMode.REAL) {
                                // In Real mode: if user hasn't authenticated/connected an account yet,
                                // show the truthful empty state with NO demo leakage!
                                TruthfulEmptyState(
                                    emoji = "↗",
                                    title = "Connect an account",
                                    subtitle = "Connect an account so Click & Save AI can start monitoring household bills.",
                                    modifier = Modifier
                                        .padding(20.dp)
                                        .align(Alignment.Center)
                                )
                            } else {
                                when (currentTab) {
                                    UsaTab.HOME -> UsaHomeScreen(
                                        dataMode = dataMode,
                                        summary = UsaDemoData.summary,
                                        opportunity = UsaDemoData.primaryOpportunity,
                                        onReviewOpportunity = { navigateTo(UsaDestination.OpportunityDetail) },
                                        onViewActivity = { currentTab = UsaTab.ACTIVITY }
                                    )
                                    UsaTab.BILLS -> UsaBillsScreen(
                                        dataMode = dataMode,
                                        services = UsaDemoData.monitoredServices,
                                        onServiceClick = { navigateTo(UsaDestination.OpportunityDetail) },
                                        onAddServiceClick = { showNotice("Add service flow requires connected account") }
                                    )
                                    UsaTab.SAVINGS -> UsaSavingsScreen(
                                        dataMode = dataMode,
                                        opportunity = UsaDemoData.primaryOpportunity,
                                        onReviewOpportunity = { navigateTo(UsaDestination.OpportunityDetail) },
                                        onKeepPlanClick = { showNotice("Current plan kept. Monitoring continues.") }
                                    )
                                    UsaTab.ACTIVITY -> UsaActivityScreen(
                                        events = UsaDemoData.timelineEvents
                                    )
                                    UsaTab.PROFILE -> UsaProfileScreen(
                                        onHouseholdClick = { navigateTo(UsaDestination.HouseholdProfile) },
                                        onAccountsClick = { navigateTo(UsaDestination.ConnectedAccounts) },
                                        onMonitoringClick = { navigateTo(UsaDestination.MonitoringPreferences) },
                                        onNotificationsClick = { navigateTo(UsaDestination.Notifications) },
                                        onPrivacyClick = { navigateTo(UsaDestination.PrivacyData) },
                                        onSecurityClick = { navigateTo(UsaDestination.Security) },
                                        onHelpClick = { navigateTo(UsaDestination.HelpAbout) },
                                        onStatesClick = { navigateTo(UsaDestination.StateLibrary) },
                                        onSignOutClick = {
                                            dataMode = AppDataMode.DEMO
                                            currentTab = UsaTab.HOME
                                            showNotice("Signed out successfully.")
                                        }
                                    )
                                }
                            }
                        }
                        is UsaDestination.OpportunityDetail -> UsaOpportunityDetailScreen(
                            dataMode = dataMode,
                            opportunity = UsaDemoData.primaryOpportunity,
                            onBackClick = { navigateBack() },
                            onCompareTrueCost = { activeSheet = UsaSheetType.TRUE_COST },
                            onReviewChoices = { activeSheet = UsaSheetType.DECISION },
                            onRemindLater = {
                                showNotice("Reminder set for next week")
                                navigateBack()
                            }
                        )
                        is UsaDestination.ActionHandoff -> UsaActionHandoffScreen(
                            onBackClick = { navigateBack() },
                            onOpenHandoff = { navigateTo(UsaDestination.ContinuedMonitoring) }
                        )
                        is UsaDestination.ContinuedMonitoring -> UsaContinuedMonitoringScreen(
                            onSimulateNewBill = { navigateTo(UsaDestination.NewBillReview) },
                            onReturnHome = {
                                backStack.clear()
                                currentTab = UsaTab.HOME
                            }
                        )
                        is UsaDestination.NewBillReview -> UsaNewBillReviewScreen(
                            onReviewVerification = { navigateTo(UsaDestination.SavingsVerification) }
                        )
                        is UsaDestination.SavingsVerification -> UsaSavingsVerificationScreen(
                            onContinueMonitoring = {
                                backStack.clear()
                                currentTab = UsaTab.HOME
                            },
                            onViewActivity = {
                                backStack.clear()
                                currentTab = UsaTab.ACTIVITY
                            }
                        )
                        is UsaDestination.ConnectedAccounts -> UsaConnectedAccountsScreen(
                            accounts = UsaDemoData.connectedAccounts,
                            onBackClick = { navigateBack() },
                            onDisconnectConfirmed = {
                                showNotice("Account disconnected.")
                                navigateBack()
                            }
                        )
                        is UsaDestination.HouseholdProfile -> UsaHouseholdProfileScreen(
                            details = UsaDemoData.householdDetails,
                            onBackClick = { navigateBack() },
                            onAddDetailClick = { showNotice("Add useful detail prompt") }
                        )
                        is UsaDestination.Notifications -> UsaNotificationsScreen(
                            notifications = UsaDemoData.notifications,
                            onBackClick = { navigateBack() },
                            onNotificationAction = { notif ->
                                when (notif.targetScreen) {
                                    "opportunity" -> navigateTo(UsaDestination.OpportunityDetail)
                                    "bills" -> {
                                        backStack.clear()
                                        currentTab = UsaTab.BILLS
                                    }
                                    "newbill" -> navigateTo(UsaDestination.NewBillReview)
                                    "verified" -> navigateTo(UsaDestination.SavingsVerification)
                                    else -> navigateBack()
                                }
                            }
                        )
                        is UsaDestination.StateLibrary -> UsaStateLibraryScreen(
                            onBackClick = { navigateBack() },
                            onRetry = { showNotice("Checking connection...") }
                        )
                        is UsaDestination.MonitoringPreferences -> UsaGenericSettingsScreen(
                            title = "Monitoring preferences",
                            description = "Choose which household categories are actively watched for price changes and savings.",
                            onBackClick = { navigateBack() }
                        )
                        is UsaDestination.PrivacyData -> UsaGenericSettingsScreen(
                            title = "Privacy & data controls",
                            description = "Your financial data is protected. You can disconnect accounts and request data deletion at any time.",
                            onBackClick = { navigateBack() }
                        )
                        is UsaDestination.Security -> UsaGenericSettingsScreen(
                            title = "Security & device",
                            description = "Biometric sign-in, App Check verification, and session management.",
                            onBackClick = { navigateBack() }
                        )
                        is UsaDestination.HelpAbout -> UsaGenericSettingsScreen(
                            title = "Help & About",
                            description = "Click & Save AI USA Native Mobile v1.0. Read product guidance and contact support.",
                            onBackClick = { navigateBack() }
                        )
                        is UsaDestination.Launch -> UsaLaunchScreen(
                            onGetStarted = { navigateTo(UsaDestination.Onboard1) },
                            onExploreDemo = {
                                dataMode = AppDataMode.DEMO
                                backStack.clear()
                                currentTab = UsaTab.HOME
                            }
                        )
                        is UsaDestination.Onboard1 -> UsaOnboardStepScreen(
                            step = 1,
                            title = "Keep your household bills on watch",
                            body = "Connect once. Click & Save AI is designed to keep watching recurring costs and surface worthwhile changes.",
                            iconSymbol = "◎",
                            onBackClick = { navigateBack() },
                            onPrimaryClick = { navigateTo(UsaDestination.Onboard2) }
                        )
                        is UsaDestination.Onboard2 -> UsaOnboardStepScreen(
                            step = 2,
                            title = "Privacy before permissions",
                            body = "You choose what to connect and what may be monitored. Significant actions still require your explicit approval.",
                            iconSymbol = "◈",
                            onBackClick = { navigateBack() },
                            onPrimaryClick = { navigateTo(UsaDestination.OnboardConnect) }
                        )
                        is UsaDestination.OnboardConnect -> UsaOnboardStepScreen(
                            step = 3,
                            title = "Connect with clear purpose",
                            body = "Account access helps identify recurring bills, price changes, promotions, and renewal dates. Only authorize what you want included.",
                            iconSymbol = "↗",
                            isConnectStep = true,
                            onBackClick = { navigateBack() },
                            onPrimaryClick = { navigateTo(UsaDestination.OnboardMonitoring) },
                            onSecondaryClick = {
                                dataMode = AppDataMode.DEMO
                                backStack.clear()
                                currentTab = UsaTab.HOME
                            }
                        )
                        is UsaDestination.OnboardMonitoring -> UsaInitialDiscoveryScreen(
                            onViewHousehold = {
                                dataMode = AppDataMode.REAL
                                backStack.clear()
                                currentTab = UsaTab.HOME
                            }
                        )
                    }

                    // Sheets
                    when (activeSheet) {
                        UsaSheetType.TRUE_COST -> {
                            TrueCostBottomSheet(
                                details = UsaDemoData.primaryOpportunity.trueCost,
                                onDismiss = { activeSheet = UsaSheetType.NONE },
                                onContinueToChoices = { activeSheet = UsaSheetType.DECISION }
                            )
                        }
                        UsaSheetType.DECISION -> {
                            DecisionBottomSheet(
                                onDismiss = { activeSheet = UsaSheetType.NONE },
                                onHandoff = {
                                    activeSheet = UsaSheetType.NONE
                                    navigateTo(UsaDestination.ActionHandoff)
                                },
                                onKeepCurrent = {
                                    activeSheet = UsaSheetType.NONE
                                    showNotice("Current plan kept")
                                },
                                onRemindLater = {
                                    activeSheet = UsaSheetType.NONE
                                    showNotice("Reminder set")
                                }
                            )
                        }
                        UsaSheetType.NONE -> {}
                    }
                }
            }
        }
    }
}
