package com.example.ui.components

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun BottomNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    NavigationBar(
        modifier = Modifier.testTag("bottom_nav_bar"),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            icon = {
                Icon(
                    if (selectedTab == 0) Icons.Filled.Dashboard else Icons.Outlined.Dashboard,
                    contentDescription = "המצב הפיננסי"
                )
            },
            label = { Text("בית") },
            colors = navColors(),
            modifier = Modifier.testTag("nav_dashboard")
        )

        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            icon = {
                Icon(
                    if (selectedTab == 1) Icons.Filled.ReceiptLong else Icons.Outlined.ReceiptLong,
                    contentDescription = "חשבונות וחיובים"
                )
            },
            label = { Text("חשבונות") },
            colors = navColors(),
            modifier = Modifier.testTag("nav_invoices")
        )

        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            icon = {
                Icon(
                    if (selectedTab == 2) Icons.Filled.Savings else Icons.Outlined.Savings,
                    contentDescription = "הזדמנויות חיסכון"
                )
            },
            label = { Text("חיסכון") },
            colors = navColors(),
            modifier = Modifier.testTag("nav_savings")
        )

        NavigationBarItem(
            selected = selectedTab == 3,
            onClick = { onTabSelected(3) },
            icon = {
                Icon(
                    if (selectedTab == 3) Icons.Filled.Person else Icons.Outlined.Person,
                    contentDescription = "פרופיל והגדרות"
                )
            },
            label = { Text("אני") },
            colors = navColors(),
            modifier = Modifier.testTag("nav_profile")
        )
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
)
