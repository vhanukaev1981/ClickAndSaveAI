package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.repository.FinancialRefreshReason
import com.example.ui.MainViewModel
import com.example.ui.components.BottomNavBar
import com.example.ui.screens.ActivityScreen
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.InvoicesScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ProvidersScreen
import com.example.ui.theme.ClickAndSaveTheme
import com.example.ui.v3.V3SecondarySurface
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val authorizationClient by lazy { Identity.getAuthorizationClient(this) }

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
                viewModel.reportGmailAuthorizationError("לא הצלחנו להשלים את חיבור Gmail. נסו שוב בעוד רגע.")
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureFirebaseAndAppCheck()
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            )
        )
        setContent {
            ClickAndSaveTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MainAppStructure(
                        viewModel = viewModel,
                        onGoogleSignIn = {
                            val clientId = getString(R.string.google_web_client_id).trim()
                            if (clientId.isBlank()) {
                                viewModel.reportGmailAuthorizationError("לא הצלחנו להתחיל את ההתחברות כרגע. נסו שוב בעוד רגע.")
                            } else {
                                viewModel.signInWithGoogle(this, clientId)
                            }
                        },
                        onRequestGmailAuthorization = ::requestGmailAuthorization
                    )
                }
            }
        }
        maybeTriggerDebugTestPush(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeTriggerDebugTestPush(intent)
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
            .addOnSuccessListener { result -> Log.i("TestPush", "sendTestPush succeeded: ${result.data}") }
            .addOnFailureListener { error -> Log.e("TestPush", "sendTestPush failed", error) }
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
            viewModel.reportGmailAuthorizationError("לא הצלחנו להתחיל את חיבור Gmail כרגע. נסו שוב בעוד רגע.")
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
                        viewModel.reportGmailAuthorizationError("לא הצלחנו לפתוח את מסך ההרשאה של Google. נסו שוב.")
                        return@addOnSuccessListener
                    }
                    gmailAuthorizationLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                } else {
                    handleGmailAuthorizationResult(authorizationResult)
                }
            }
            .addOnFailureListener { error ->
                Log.e("MainActivity", "Gmail authorization failed", error)
                viewModel.reportGmailAuthorizationError("לא הצלחנו להתחיל את חיבור Gmail. נסו שוב בעוד רגע.")
            }
    }

    private fun handleGmailAuthorizationResult(result: AuthorizationResult) {
        if (!result.grantedScopes.contains(GMAIL_READONLY_SCOPE)) {
            viewModel.reportGmailAuthorizationError("הרשאת הקריאה ל-Gmail לא אושרה.")
            return
        }
        val serverAuthCode = result.serverAuthCode?.takeIf { it.isNotBlank() }
        if (serverAuthCode == null) {
            viewModel.reportGmailAuthorizationError("לא הצלחנו להשלים את חיבור Gmail. נסו לנתק ולחבר מחדש.")
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
    var secondarySurfaceName by rememberSaveable { mutableStateOf<String?>(null) }
    val secondarySurface = secondarySurfaceName?.let { savedName ->
        V3SecondarySurface.entries.firstOrNull { it.name == savedName }
    }
    val closeSecondarySurface = { secondarySurfaceName = null }
    val openActivity = { secondarySurfaceName = V3SecondarySurface.ACTIVITY.name }

    BackHandler(enabled = secondarySurface == V3SecondarySurface.ACTIVITY) {
        closeSecondarySurface()
    }

    LaunchedEffect(session.uid) {
        if (session.isAuthenticated) {
            PushRegistration.registerCurrentToken()
            viewModel.refreshFinancialSession(FinancialRefreshReason.STARTUP)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (secondarySurface == V3SecondarySurface.ACTIVITY) {
                Surface(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = closeSecondarySurface, modifier = Modifier.testTag("activity_back")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
                            Text("חזרה")
                        }
                        Text(
                            text = "פעילות",
                            modifier = Modifier.padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (secondarySurface == null) {
                BottomNavBar(
                    selectedTab = selectedTab.coerceIn(0, 4),
                    onTabSelected = { tab ->
                        closeSecondarySurface()
                        viewModel.setTab(tab)
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (secondarySurface == V3SecondarySurface.ACTIVITY) {
                ActivityScreen(viewModel)
            } else {
                when (selectedTab) {
                    0 -> DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToTab = { tab ->
                            if (tab == 3) openActivity() else viewModel.setTab(tab)
                        },
                        onOpenInvoices = { viewModel.setTab(3) },
                        onOpenReceiptScan = viewModel::reportReceiptScanUnavailable,
                        onGoogleSignIn = onGoogleSignIn,
                        onRequestGmailAuthorization = onRequestGmailAuthorization
                    )
                    1 -> ProvidersScreen(viewModel)
                    2 -> AiAssistantScreen(viewModel)
                    3 -> InvoicesScreen(
                        viewModel = viewModel,
                        onOpenReceiptScan = viewModel::reportReceiptScanUnavailable,
                        onOpenSavings = { viewModel.setTab(1) }
                    )
                    4 -> ProfileScreen(
                        viewModel = viewModel,
                        onGoogleSignIn = onGoogleSignIn,
                        onRequestGmailAuthorization = onRequestGmailAuthorization,
                        onOpenActivity = openActivity
                    )
                    else -> viewModel.setTab(0)
                }
            }
        }
    }
}
