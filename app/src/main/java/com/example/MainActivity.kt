package com.example

import android.app.Activity
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
import com.example.ui.MainViewModel
import com.example.ui.components.BottomNavBar
import com.example.ui.screens.AiAssistantScreen
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

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val authorizationClient by lazy { Identity.getAuthorizationClient(this) }

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
        configureFirebaseAndAppCheck()
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
    }

    private fun configureFirebaseAndAppCheck() {
        runCatching {
            FirebaseApp.initializeApp(this)
            AppCheckInstaller.install()
        }.onFailure {
            Log.w("MainActivity", "Firebase/App Check unavailable: ${it.localizedMessage}")
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
        viewModel.completeGmailAuthorization(serverAuthCode)
    }

    private companion object {
        const val GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
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
            viewModel.gmailRepository.refreshConnectionStatus()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = "Backend הוגדר בקוד; נדרשת פריסת Firebase והשלמת OAuth לפני שימוש אמיתי",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        },
        bottomBar = {
            BottomNavBar(
                selectedTab = selectedTab,
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
                3 -> AiAssistantScreen(viewModel)
                4 -> ProfileScreen(
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
