package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.ui.components.BottomNavBar
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
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

    @Test
    fun exposesCustomerFacingAccessibilityDescriptions() {
        composeTestRule.setContent {
            MyApplicationTheme {
                BottomNavBar(selectedTab = 0, onTabSelected = {})
            }
        }

        composeTestRule.onNodeWithContentDescription("בית והחיסכון שלך").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("חשבונות שזוהו").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("הזדמנויות חיסכון").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("חשבון, חיבורים ופרטיות").assertIsDisplayed()
    }

    @Test
    fun everyVisibleDestinationInvokesItsExpectedTab() {
        var selected = -1
        composeTestRule.setContent {
            MyApplicationTheme {
                BottomNavBar(selectedTab = 0, onTabSelected = { selected = it })
            }
        }

        composeTestRule.onNodeWithTag("nav_dashboard").performClick()
        assertEquals(0, selected)
        composeTestRule.onNodeWithTag("nav_invoices").performClick()
        assertEquals(1, selected)
        composeTestRule.onNodeWithTag("nav_savings").performClick()
        assertEquals(2, selected)
        composeTestRule.onNodeWithTag("nav_profile").performClick()
        assertEquals(3, selected)
    }
}
