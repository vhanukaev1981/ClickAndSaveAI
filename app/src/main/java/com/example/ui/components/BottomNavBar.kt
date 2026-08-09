package com.example.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.ui.theme.FinancialDesignTokens

@Composable
fun BottomNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        NavigationBar(
            modifier = Modifier.testTag("bottom_nav_bar"),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            FinancialNavItem(
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                selectedIcon = { Icon(Icons.Filled.Dashboard, contentDescription = null) },
                unselectedIcon = { Icon(Icons.Outlined.Dashboard, contentDescription = null) },
                label = "בית",
                contentDescription = "המצב הפיננסי",
                testTag = "nav_dashboard"
            )
            FinancialNavItem(
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                selectedIcon = { Icon(Icons.Filled.ReceiptLong, contentDescription = null) },
                unselectedIcon = { Icon(Icons.Outlined.ReceiptLong, contentDescription = null) },
                label = "חשבונות",
                contentDescription = "חשבונות וחיובים",
                testTag = "nav_invoices"
            )
            FinancialNavItem(
                selected = selectedTab == 2,
                onClick = { onTabSelected(2) },
                selectedIcon = { Icon(Icons.Filled.Savings, contentDescription = null) },
                unselectedIcon = { Icon(Icons.Outlined.Savings, contentDescription = null) },
                label = "חיסכון",
                contentDescription = "הזדמנויות חיסכון",
                testTag = "nav_savings"
            )
            FinancialNavItem(
                selected = selectedTab == 3,
                onClick = { onTabSelected(3) },
                selectedIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                unselectedIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                label = "אני",
                contentDescription = "פרופיל והגדרות",
                testTag = "nav_profile"
            )
        }
    }
}

@Composable
private fun FinancialNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    selectedIcon: @Composable () -> Unit,
    unselectedIcon: @Composable () -> Unit,
    label: String,
    contentDescription: String,
    testTag: String
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            if (selected) selectedIcon() else unselectedIcon()
        },
        label = { Text(label) },
        alwaysShowLabel = true,
        colors = navColors(),
        modifier = Modifier
            .heightIn(min = FinancialDesignTokens.minimumTouchTarget)
            .testTag(testTag)
            .semantics { this.contentDescription = contentDescription }
    )
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
)
