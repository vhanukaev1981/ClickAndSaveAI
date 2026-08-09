package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.ui.MainViewModel
import com.example.ui.components.BottomNavBar
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.InvoicesScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ProvidersScreen
import com.example.ui.theme.ClickAndSaveTheme
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val authorizationClient by lazy { Identity.getAuthorizationClient(this) }
    private var lastObservedBillsResumeRefreshElapsedRealtimeMs = 0L

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) PushRegistration.registerCurrentToken()
    }

    private val gmailAuthorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            viewModel.reportGmailAuthorizationError("הרשאת Gmail בוטלה ולא נשמר מידע.")
            return@registerForActivityResult
        }

        runCatching {
            authorizationClient.getAuthorizationResultFromIntent(result.data!!)
        }.onSuccess(::handleGmailAuthorizationResult)
            .onFailure { error ->
                Log.e("MainActivity", "Gmail authorization result failed", error)
                viewModel.reportGmailAuthorizationError(
                    error.localizedMessage ?: "לא ניתן היה להשלים את הרשאת Gmail."
                )
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The authenticated startup effect below performs the initial authoritative refresh.
        // Seed the resume clock so the first onResume does not immediately duplicate that work.
        lastObservedBillsResumeRefreshElapsedRealtimeMs = SystemClock.elapsedRealtime()
        configureFirebaseAndAppCheck()
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()

        setContent {
            ClickAndSaveTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MainAppStructure(
                        viewModel = viewModel,
                        onGoogleSignIn = {
                            val clientId = getString(R.string.google_web_client_id).trim()
                            if (clientId.isBlank()) {
                                viewModel.reportGmailAuthorizationError(
                                    "חסר google_web_client_id. יש להשלים את הגדרת Firebase/OAuth."
                                )
                            } else {
                                viewModel.signInWithGoogle(this, clientId)
                            }
                        },
                        onRequestGmailAuthorization = ::requestGmailAuthorization
                    )
                }
            }
        }

        applyPushDestination(intent)
        maybeTriggerDebugTestPush(intent)
    }

    override fun onResume() {
        super.onResume()
        if (FirebaseAuth.getInstance().currentUser == null) return

        val now = SystemClock.elapsedRealtime()
        if (!shouldRefreshObservedBillsOnResume(
                lastRefreshElapsedRealtimeMs = lastObservedBillsResumeRefreshElapsedRealtimeMs,
                nowElapsedRealtimeMs = now
            )
        ) {
            return
        }

        lastObservedBillsResumeRefreshElapsedRealtimeMs = now
        lifecycleScope.launch {
            // Resume uses only the bounded backend-authoritative snapshot. It never launches
            // the six-month Gmail backfill/scan path.
            viewModel.gmailRepository.refreshObservedBillsSnapshotIfConnected()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyPushDestination(intent)
        maybeTriggerDebugTestPush(intent)
    }

    private fun applyPushDestination(intent: Intent?) {
        val pushType = intent?.getStringExtra(PUSH_TYPE_EXTRA) ?: return
        val destinationTab = destinationTabForPushType(pushType) ?: return
        viewModel.setTab(destinationTab)
        // Consume only a known allowlisted navigation instruction so configuration changes or
        // repeated delivery cannot unexpectedly re-route a user later.
        intent.removeExtra(PUSH_TYPE_EXTRA)
    }

    private fun maybeTriggerDebugTestPush(intent: Intent?) {
        if (!BuildConfig.DEBUG || intent?.getBooleanExtra(DEBUG_SEND_TEST_PUSH, false) != true) return
        intent.removeExtra(DEBUG_SEND_TEST_PUSH)
        if (FirebaseAuth.getInstance().currentUser == null) {
            Log.w("TestPush", "Debug test push skipped because no Firebase user is signed in")
            return
        }
        PushRegistration.registerCurrentToken()
        FirebaseFunctions.getInstance("europe-west1")
            .getHttpsCallable("sendTestPush")
            .call()
            .addOnSuccessListener { result ->
                Log.i("TestPush", "sendTestPush succeeded: ${result.data}")
            }
            .addOnFailureListener { error ->
                Log.e("TestPush", "sendTestPush failed", error)
            }
    }

    private fun configureFirebaseAndAppCheck() {
        runCatching {
            FirebaseApp.initializeApp(this)
            AppCheckInstaller.install()
        }.onFailure {
            Log.w("MainActivity", "Firebase/App Check unavailable: ${it.localizedMessage}")
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            PushRegistration.registerCurrentToken()
        }
    }

    private fun requestGmailAuthorization() {
        val clientId = getString(R.string.google_web_client_id).trim()
        if (clientId.isBlank()) {
            viewModel.reportGmailAuthorizationError(
                "חסר google_web_client_id. יש להשלים את הגדרת Firebase/OAuth."
            )
            return
        }

        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(GMAIL_READONLY_SCOPE)))
            .requestOfflineAccess(clientId)
            .setPrompt(AuthorizationRequest.Prompt.CONSENT)
            .build()

        authorizationClient.authorize(request)
            .addOnSuccessListener { authorizationResult ->
                if (authorizationResult.hasResolution()) {
                    val pendingIntent = authorizationResult.pendingIntent
                    if (pendingIntent == null) {
                        viewModel.reportGmailAuthorizationError("Google לא החזיר מסך הרשאה תקף.")
                        return@addOnSuccessListener
                    }
                    gmailAuthorizationLauncher.launch(
                        IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    )
                } else {
                    handleGmailAuthorizationResult(authorizationResult)
                }
            }
            .addOnFailureListener { error ->
                Log.e("MainActivity", "Gmail authorization failed", error)
                viewModel.reportGmailAuthorizationError(
                    error.localizedMessage ?: "בקשת הרשאת Gmail נכשלה."
                )
            }
    }

    private fun handleGmailAuthorizationResult(result: AuthorizationResult) {
        if (!result.grantedScopes.contains(GMAIL_READONLY_SCOPE)) {
            viewModel.reportGmailAuthorizationError("הרשאת gmail.readonly לא אושרה.")
            return
        }
        val serverAuthCode = result.serverAuthCode?.takeIf { it.isNotBlank() }
        if (serverAuthCode == null) {
            viewModel.reportGmailAuthorizationError(
                "Google לא החזיר קוד שרת. יש לנתק את ההרשאה ולאשר מחדש."
            )
            return
        }
        PushRegistration.registerCurrentToken()
        viewModel.completeGmailAuthorization(serverAuthCode)
    }

    private companion object {
        const val GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
        const val DEBUG_SEND_TEST_PUSH = "debugSendTestPush"
    }
}

@Composable
fun MainAppStructure(
    viewModel: MainViewModel,
    onGoogleSignIn: () -> Unit,
    onRequestGmailAuthorization: () -> Unit
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val session by viewModel.userSession.collectAsState()

    LaunchedEffect(session.uid) {
        if (session.isAuthenticated) {
            PushRegistration.registerCurrentToken()
            viewModel.gmailRepository.refreshConnectionStatusAndUpgradeIfNeeded()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = "Click&SaveAI עובדת ברקע ומחפשת התייעלויות עבורך",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        },
        bottomBar = {
            BottomNavBar(
                selectedTab = selectedTab.coerceIn(0, 3),
                onTabSelected = viewModel::setTab
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToTab = viewModel::setTab,
                    onOpenReceiptScan = viewModel::reportReceiptScanUnavailable,
                    onGoogleSignIn = onGoogleSignIn,
                    onRequestGmailAuthorization = onRequestGmailAuthorization
                )
                1 -> InvoicesScreen(
                    viewModel = viewModel,
                    onOpenReceiptScan = viewModel::reportReceiptScanUnavailable
                )
                2 -> ProvidersScreen(viewModel)
                3, 4 -> ProfileScreen(
                    viewModel = viewModel,
                    onGoogleSignIn = onGoogleSignIn,
                    onRequestGmailAuthorization = onRequestGmailAuthorization
                )
                else -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToTab = viewModel::setTab,
                    onOpenReceiptScan = viewModel::reportReceiptScanUnavailable,
                    onGoogleSignIn = onGoogleSignIn,
                    onRequestGmailAuthorization = onRequestGmailAuthorization
                )
            }
        }
    }
}
