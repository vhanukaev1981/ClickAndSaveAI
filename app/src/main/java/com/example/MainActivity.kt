package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel
import com.example.ui.components.BottomNavBar
import com.example.ui.components.ClickAndSaveLogo
import com.example.ui.screens.ProductBillsScreen
import com.example.ui.screens.ProductDashboardScreen
import com.example.ui.screens.ProductMeScreen
import com.example.ui.screens.ProductSavingsScreen
import com.example.ui.theme.ClickAndSaveTheme
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
            viewModel.reportGmailAuthorizationError("האישור בוטל ולא נשמר מידע.")
            return@registerForActivityResult
        }

        runCatching {
            authorizationClient.getAuthorizationResultFromIntent(result.data!!)
        }.onSuccess(::handleGmailAuthorizationResult)
            .onFailure { error ->
                Log.e("MainActivity", "Gmail authorization result failed", error)
                viewModel.reportGmailAuthorizationError("לא ניתן היה להשלים את אישור הקריאה. אפשר לנסות שוב.")
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                                    "לא ניתן להתחבר כרגע. הגדרת ההתחברות אינה זמינה."
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
                "לא ניתן לחבר כרגע את מקור המסמכים. אפשר לנסות שוב לאחר שהחיבור יוגדר."
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
                        viewModel.reportGmailAuthorizationError("לא ניתן לפתוח את מסך האישור. אפשר לנסות שוב.")
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
                viewModel.reportGmailAuthorizationError("בקשת אישור הקריאה לא הושלמה. אפשר לנסות שוב.")
            }
    }

    private fun handleGmailAuthorizationResult(result: AuthorizationResult) {
        if (!result.grantedScopes.contains(GMAIL_READONLY_SCOPE)) {
            viewModel.reportGmailAuthorizationError("הרשאת הקריאה לא אושרה.")
            return
        }
        val serverAuthCode = result.serverAuthCode?.takeIf { it.isNotBlank() }
        if (serverAuthCode == null) {
            viewModel.reportGmailAuthorizationError(
                "האישור לא הושלם. יש לבטל את החיבור ולאשר מחדש."
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
            viewModel.gmailRepository.refreshConnectionStatus()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = MaterialTheme.colorScheme.surface
            ) {
                ClickAndSaveLogo(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    iconSize = 32.dp,
                    showTagline = false,
                    isDarkTheme = false
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> ProductDashboardScreen(
                    viewModel = viewModel,
                    onNavigateToTab = viewModel::setTab,
                    onGoogleSignIn = onGoogleSignIn,
                    onRequestGmailAuthorization = onRequestGmailAuthorization
                )
                1 -> ProductBillsScreen(viewModel)
                2 -> ProductSavingsScreen(viewModel)
                3, 4 -> ProductMeScreen(
                    viewModel = viewModel,
                    onGoogleSignIn = onGoogleSignIn,
                    onRequestGmailAuthorization = onRequestGmailAuthorization
                )
                else -> ProductDashboardScreen(
                    viewModel = viewModel,
                    onNavigateToTab = viewModel::setTab,
                    onGoogleSignIn = onGoogleSignIn,
                    onRequestGmailAuthorization = onRequestGmailAuthorization
                )
            }
        }
    }
}
