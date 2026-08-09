package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.ui.components.BottomNavBar
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BottomNavBarTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun exposesStableFinancialNavigationDestinations() {
        composeTestRule.setContent {
            MyApplicationTheme {
                BottomNavBar(selectedTab = 2, onTabSelected = {})
            }
        }

        composeTestRule.onNodeWithTag("nav_dashboard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_invoices").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_savings").assertIsDisplayed().assertIsSelected()
        composeTestRule.onNodeWithTag("nav_profile").assertIsDisplayed()
    }
}
