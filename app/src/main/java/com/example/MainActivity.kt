package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.ui.MainViewModel
import com.example.ui.components.BottomNavBar
import com.example.ui.screens.*
import com.example.ui.theme.ClickAndSaveTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(this)
            }
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Firebase init info: ${e.localizedMessage}")
        }
        enableEdgeToEdge()
        setContent {
            ClickAndSaveTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MainAppStructure(viewModel = viewModel)
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
        bottomBar = {
            BottomNavBar(
                selectedTab = selectedTab,
                onTabSelected = { viewModel.setTab(it) }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToTab = { viewModel.setTab(it) },
                    onOpenReceiptScan = {
                        val sampleBitmap = createSampleReceiptBitmap()
                        viewModel.scanReceipt(sampleBitmap)
                        viewModel.setTab(1)
                    }
                )
                1 -> InvoicesScreen(
                    viewModel = viewModel,
                    onOpenReceiptScan = {
                        val sampleBitmap = createSampleReceiptBitmap()
                        viewModel.scanReceipt(sampleBitmap)
                    }
                )
                2 -> ProvidersScreen(
                    viewModel = viewModel
                )
                3 -> AiAssistantScreen(
                    viewModel = viewModel
                )
                4 -> ProfileScreen(
                    viewModel = viewModel
                )
                else -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToTab = { viewModel.setTab(it) },
                    onOpenReceiptScan = {
                        val sampleBitmap = createSampleReceiptBitmap()
                        viewModel.scanReceipt(sampleBitmap)
                        viewModel.setTab(1)
                    }
                )
            }
        }
    }
}
