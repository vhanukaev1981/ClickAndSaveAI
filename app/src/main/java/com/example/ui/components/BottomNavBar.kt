package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
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
                selectedIcon = Icons.Filled.Dashboard,
                unselectedIcon = Icons.Outlined.Dashboard,
                label = "בית",
                contentDescription = "המצב הפיננסי",
                testTag = "nav_dashboard"
            )
            FinancialNavItem(
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                selectedIcon = Icons.Filled.ReceiptLong,
                unselectedIcon = Icons.Outlined.ReceiptLong,
                label = "חשבונות",
                contentDescription = "חשבונות וחיובים",
                testTag = "nav_invoices"
            )
            FinancialNavItem(
                selected = selectedTab == 2,
                onClick = { onTabSelected(2) },
                selectedIcon = Icons.Filled.Savings,
                unselectedIcon = Icons.Outlined.Savings,
                label = "חיסכון",
                contentDescription = "הזדמנויות חיסכון",
                testTag = "nav_savings"
            )
            FinancialNavItem(
                selected = selectedTab == 3,
                onClick = { onTabSelected(3) },
                selectedIcon = Icons.Filled.Person,
                unselectedIcon = Icons.Outlined.Person,
                label = "אני",
                contentDescription = "פרופיל והגדרות",
                testTag = "nav_profile"
            )
        }
    }
}

@Composable
private fun RowScope.FinancialNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    label: String,
    contentDescription: String,
    testTag: String
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = FinancialDesignTokens.minimumTouchTarget)
            .clickable(
                role = Role.Tab,
                onClick = onClick
            )
            .testTag(testTag)
            .semantics {
                this.contentDescription = contentDescription
                this.selected = selected
            }
            .padding(vertical = FinancialDesignTokens.compactSpacing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (selected) selectedIcon else unselectedIcon,
            contentDescription = null,
            tint = contentColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor
        )
    }
}
