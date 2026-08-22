package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.ui.components.BottomNavBar
import com.example.ui.components.MonitoringStatus
import com.example.ui.components.NextBestActionCard
import com.example.ui.components.SavingsGlyph
import com.example.ui.components.SavingsHero
import com.example.ui.components.V3Panel
import com.example.ui.components.V3ScreenHeader
import com.example.ui.components.V3SectionHeader
import com.example.ui.components.V3SettingsGroup
import com.example.ui.components.V3SettingsRow
import com.example.ui.screens.AiAssistantContent
import com.example.ui.screens.V3OnboardingContent
import com.example.ui.theme.ClickAndSaveTheme
import com.example.ui.theme.V3Background
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
    fun premiumHomeScreen() {
        capture("src/test/screenshots/v3-home-premium.png") {
            PremiumShell(selectedTab = 0) {
                V3ScreenHeader("הכסף שלך, במבט אחד", "מה כבר חסכת, מה עוד אפשר לחסוך ומה כדאי לעשות עכשיו.", "CLICK & SAVE")
                SavingsHero(null, null)
                MonitoringStatus("הניטור פעיל", "Gmail מחובר לקריאה בלבד", true)
                V3SectionHeader("התמונה שלך")
                V3Panel { androidx.compose.material3.Text("ערכים חסרים נשארים לא ידועים.") }
            }
        }
    }

    @Test
    fun premiumSavingsScreen() {
        capture("src/test/screenshots/v3-savings-premium.png") {
            PremiumShell(selectedTab = 1) {
                V3ScreenHeader("החיסכון שלך", "מה כבר מומש, מה פוטנציאלי ומה באמת ניתן לבצע עכשיו.", "CLICK & SAVE")
                SavingsHero(null, null)
                V3SectionHeader("הזדמנויות פתוחות")
                V3Panel {
                    SavingsGlyph(contentDescription = "חיסכון")
                    androidx.compose.material3.Text("אין כרגע יעד פעולה מאומת להצעה.")
                    androidx.compose.material3.Text("פוטנציאל אינו חיסכון ממומש.")
                }
            }
        }
    }

    @Test
    fun premiumAiScreen() {
        capture("src/test/screenshots/v3-ai-premium.png") {
            AiAssistantContent(
                authenticated = false,
                analysis = null,
                isAnalyzing = false,
                messages = emptyList(),
                isChatLoading = false,
                errorMessage = "",
                primarySuggestions = emptyList(),
                secondarySuggestions = listOf("מה כדאי לבדוק השבוע?", "תסביר לי את החשבון הזה"),
                onAnalyze = {},
                onSend = {}
            )
        }
    }

    @Test
    fun premiumBillsScreen() {
        capture("src/test/screenshots/v3-bills-premium.png") {
            PremiumShell(selectedTab = 3) {
                V3ScreenHeader("לתשלום", "חשבונות שנקלטו ממקור מאומת. שדה שחסר נשאר לא ידוע.", "חשבונות מאומתים")
                V3SectionHeader("מה נכנס לתשלום")
                V3Panel { androidx.compose.material3.Text("מועד לתשלום: לא ידוע") }
                V3SectionHeader("האם אפשר לחסוך")
                V3Panel { androidx.compose.material3.Text("אין כרגע חיסכון מאומת לחשבון הזה.") }
                V3SectionHeader("מעבר לספק לתשלום")
                V3Panel { androidx.compose.material3.Text("אין יעד תשלום מאומת לחשבון הזה.") }
            }
        }
    }

    @Test
    fun premiumProfileScreen() {
        capture("src/test/screenshots/v3-profile-premium.png") {
            PremiumShell(selectedTab = 4) {
                V3ScreenHeader("פרופיל", "החיבורים, הפרטיות, הפעילות והאבטחה שלך במקום אחד.", "החשבון שלך")
                V3SectionHeader("חיבור ונתונים")
                V3SettingsGroup("") {
                    V3SettingsRow("Gmail", "Gmail מחובר לקריאה בלבד.", Icons.Default.Link, "קריאה בלבד")
                }
                V3SectionHeader("פעילות והתראות")
                V3SettingsGroup("") {
                    V3SettingsRow("פעילות", "יומן פעילות מבוסס אירועים שמורים.", Icons.Default.History, "פתח")
                }
                V3SectionHeader("חשבון ואבטחה")
                V3SettingsGroup("") {
                    V3SettingsRow("חשבון", "התחברות וחיבור Gmail הם מצבים נפרדים.", Icons.Default.AccountCircle)
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xhdpi", sdk = [36])
    fun premiumPrimaryCompactRtl() {
        capture("src/test/screenshots/v3-primary-compact-rtl.png") {
            PremiumShell(selectedTab = 1) {
                V3ScreenHeader("החיסכון שלך", "מסך קומפקטי שומר על היררכיה וקריאות.", "CLICK & SAVE")
                SavingsHero(null, null)
                V3Panel { androidx.compose.material3.Text("ערך חסר אינו אפס.") }
            }
        }
    }

    @Test
    fun savingsHeroSeparatesRealizedAndPotential() {
        capture("src/test/screenshots/v3-savings-hero.png") {
            SavingsHero(realizedMonthly = 126.0, potentialMonthly = 214.0)
        }
    }

    @Test
    fun savingsHeroKeepsUnknownDistinctFromKnownZero() {
        capture("src/test/screenshots/v3-savings-hero-unknown.png") {
            SavingsHero(realizedMonthly = null, potentialMonthly = null)
        }
    }

    @Test
    fun nextBestActionKeepsPotentialExplicit() {
        capture("src/test/screenshots/v3-next-best-action.png") {
            NextBestActionCard(
                providerName = "ספק לדוגמה",
                category = "אינטרנט",
                potentialMonthlyText = "₪40.00",
                onClick = {}
            )
        }
    }

    @Test
    fun onboardingStartsWithTheApprovedV3Promise() {
        capture("src/test/screenshots/v3-onboarding-step-1.png") {
            V3OnboardingContent(
                step = 0,
                authenticated = false,
                onNext = {},
                onGoogleSignIn = {},
                onConnectGmail = {}
            )
        }
    }

    @Test
    fun onboardingMakesReadOnlyGmailTrustExplicit() {
        capture("src/test/screenshots/v3-onboarding-step-3.png") {
            V3OnboardingContent(
                step = 2,
                authenticated = true,
                onNext = {},
                onGoogleSignIn = {},
                onConnectGmail = {}
            )
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi", sdk = [36])
    fun onboardingPrivacyStepFitsCompactRtlWidth() {
        capture("src/test/screenshots/v3-onboarding-step-3-compact-rtl.png") {
            V3OnboardingContent(
                step = 2,
                authenticated = true,
                onNext = {},
                onGoogleSignIn = {},
                onConnectGmail = {}
            )
        }
    }

    @Test
    fun firstSyncUsesNeutralTruthSafePresentation() {
        capture("src/test/screenshots/v3-first-sync.png") {
            MonitoringStatus(
                title = "מסדרים את התמונה שלך",
                subtitle = "אנחנו מעדכנים את התמונה שלך",
                active = true
            )
        }
    }

    private fun capture(filePath: String, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            ClickAndSaveTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    content()
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = filePath)
    }
}

@Composable
private fun PremiumShell(selectedTab: Int, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(V3Background)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
            Spacer(Modifier.height(4.dp))
        }
        Box(Modifier.fillMaxWidth().align(androidx.compose.ui.Alignment.BottomCenter)) {
            BottomNavBar(selectedTab = selectedTab, onTabSelected = {})
        }
    }
}
