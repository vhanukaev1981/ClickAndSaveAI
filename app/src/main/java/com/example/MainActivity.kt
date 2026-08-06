package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(this)
            }
        }.onFailure {
            android.util.Log.w("MainActivity", "Firebase unavailable: ${it.localizedMessage}")
        }

        enableEdgeToEdge()
        setContent {
            ClickAndSaveTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MainAppStructure(viewModel)
                }
            }
        }
    }
}

@Composable
fun MainAppStructure(viewModel: MainViewModel) {
    val selectedTab by viewModel.selectedTab.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Text(
                    text = "אב־טיפוס: Gmail, AI ומעבר ספקים אינם מחוברים",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
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
                    onOpenReceiptScan = viewModel::reportReceiptScanUnavailable
                )
                1 -> InvoicesScreen(
                    viewModel = viewModel,
                    onOpenReceiptScan = viewModel::reportReceiptScanUnavailable
                )
                2 -> ProvidersScreen(viewModel)
                3 -> AiAssistantScreen(viewModel)
                4 -> ProfileScreen(viewModel)
                else -> viewModel.setTab(0)
            }
        }
    }
}
