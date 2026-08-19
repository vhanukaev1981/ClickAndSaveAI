package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import com.example.ui.components.NextBestActionCard
import com.example.ui.components.SavingsHero
import com.example.ui.theme.ClickAndSaveTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V3PrimaryScreensScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun savingsHeroSeparatesRealizedAndPotential() {
        composeTestRule.setContent {
            ClickAndSaveTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    SavingsHero(realizedMonthly = 126.0, potentialMonthly = 214.0)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/v3-savings-hero.png"
        )
    }

    @Test
    fun savingsHeroKeepsUnknownDistinctFromKnownZero() {
        composeTestRule.setContent {
            ClickAndSaveTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    SavingsHero(realizedMonthly = null, potentialMonthly = null)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/v3-savings-hero-unknown.png"
        )
    }

    @Test
    fun nextBestActionKeepsPotentialExplicit() {
        composeTestRule.setContent {
            ClickAndSaveTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    NextBestActionCard(
                        providerName = "Partner",
                        category = "אינטרנט",
                        potentialMonthlyText = "₪40.00",
                        onClick = {}
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/v3-next-best-action.png"
        )
    }
}
