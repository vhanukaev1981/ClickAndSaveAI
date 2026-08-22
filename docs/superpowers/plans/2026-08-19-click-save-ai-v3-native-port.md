# Click & Save AI V3 Native Port Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the approved Click & Save AI V3 Lovable product experience into the existing native Jetpack Compose Android application, preserve all production backend/security/truth contracts, and produce a new signed Production AAB for Block 3I.

**Architecture:** Lovable remains the visual/product reference only. The production runtime remains the existing native Android app. V3 will be implemented as a presentation-layer and information-architecture refactor on top of the existing `MainViewModel`, `FinancialSyncState`, Firebase/Auth/Gmail/App Check integrations, backend callables, and authoritative savings/provider models. New pure presentation mappers will isolate truth-sensitive display logic from composables; reusable Compose components will implement the V3 design system; primary navigation will become `בית | חיסכון | AI | פעילות | אני` while invoices remain a secondary native surface.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, AndroidX Lifecycle/SavedStateHandle, Firebase Auth/Functions/Messaging/App Check, Google AuthorizationClient, Robolectric, Compose UI Test, Roborazzi, JUnit 4, Gradle Android Plugin.

**Spec:** `docs/superpowers/specs/2026-08-19-click-save-ai-v3-native-port-design.md`

## Global Constraints

- Production package remains exactly `com.aistudio.clickandsaveai.app`.
- `versionCode=1` and `versionName="1.0"` remain unchanged during the UI port; if Google Play later proves versionCode 1 has already been consumed, STOP and return to Master Control for a source-controlled version bump and new candidate cycle.
- No WebView, Capacitor, Cordova, React runtime, or Lovable web bundle may be added to the Android production runtime.
- No `startGmailConnect`, `GOOGLE_MAIL_APP_USER_CONNECTOR_CLIENT_API_KEY`, Lovable Gmail connector, new OAuth client, new OAuth scope, frontend secret, or alternate Gmail flow may be introduced.
- Existing native Google Sign-In and Gmail readonly authorization path remains the only production auth/Gmail path.
- Existing Firebase Auth, Functions, Firestore, App Check/Play Integrity, push, backend callable names, provider action contracts, deletion/disconnect flows, production release workflow, signing, and google-services boundaries must remain unchanged.
- Realized savings and potential savings must never be conflated.
- Unknown authoritative monetary/count values must remain unknown/null and must never be displayed as zero merely because data is unavailable.
- Potential savings may be shown only when backed by the current authoritative verified/fresh/eligible offer contract.
- Realized savings may be shown only when `savingRealizationState == "REALIZED"` and a realized value is present.
- Existing source/test guards that prevent local Room/manual data from being presented as authoritative truth must remain passing.
- Hebrew RTL is the primary UI; mixed Hebrew/numbers/₪ must remain readable.
- Every implementation task follows TDD: failing test first, then minimal production change, then verification, then commit.
- No Firebase deploy is part of this implementation plan. Block 3H remains closed unless a backend/infrastructure change is independently proven unavoidable and separately authorized.

## File Structure

New focused files:

- `app/src/main/java/com/example/ui/v3/V3FinancialPresentation.kt` — pure mapping of authoritative backend models into V3 display-safe summaries and lifecycle labels.
- `app/src/main/java/com/example/ui/v3/V3Navigation.kt` — primary destination and secondary-surface constants used by `MainActivity`/`MainViewModel`.
- `app/src/main/java/com/example/ui/components/SavingsHero.kt` — realized/potential savings hero.
- `app/src/main/java/com/example/ui/components/V3StatusComponents.kt` — monitoring, verification, freshness, empty/loading/status surfaces.
- `app/src/main/java/com/example/ui/components/V3OpportunityComponents.kt` — opportunity summary/lifecycle presentation primitives.
- `app/src/main/java/com/example/ui/components/V3ActivityComponents.kt` — activity timeline primitives.
- `app/src/main/java/com/example/ui/screens/V3OnboardingContent.kt` — stateless three-step pre-auth onboarding content.
- `app/src/test/java/com/example/V3FinancialPresentationTest.kt` — truth-model unit tests.
- `app/src/test/java/com/example/V3NavigationContractTest.kt` — navigation/secondary-surface contract tests.
- `app/src/test/java/com/example/V3PrimaryScreensScreenshotTest.kt` — Roborazzi snapshots of stable preview fixtures for key V3 components/screens.
- `app/src/test/java/com/example/V3ProductionBoundaryGuardTest.kt` — source guard preventing web/Gmail-connector/runtime boundary regressions.

Existing files modified in place:

- `app/src/main/java/com/example/MainActivity.kt`
- `app/src/main/java/com/example/ui/MainViewModel.kt`
- `app/src/main/java/com/example/ui/components/BottomNavBar.kt`
- `app/src/main/java/com/example/ui/screens/DashboardScreen.kt`
- `app/src/main/java/com/example/ui/screens/ProvidersScreen.kt`
- `app/src/main/java/com/example/ui/screens/AiAssistantScreen.kt`
- `app/src/main/java/com/example/ui/screens/ActivityScreen.kt`
- `app/src/main/java/com/example/ui/screens/ProfileScreen.kt`
- `app/src/main/java/com/example/ui/screens/InvoicesScreen.kt`
- `app/src/main/java/com/example/ui/theme/Color.kt`
- `app/src/main/java/com/example/ui/theme/Theme.kt`
- `app/src/main/java/com/example/ui/theme/Type.kt`
- existing truth/navigation source guards that intentionally assert old labels/routes, especially `IntegratedNavigationContractTest.kt` and `GateCPrimarySurfacesContractTest.kt`.

---

### Task 1: Create a Truth-Safe V3 Presentation Layer

**Files:**
- Create: `app/src/main/java/com/example/ui/v3/V3FinancialPresentation.kt`
- Create: `app/src/test/java/com/example/V3FinancialPresentationTest.kt`
- Modify only if required by compile: `app/src/main/java/com/example/ui/screens/FinancialTruthFormatting.kt`

**Interfaces:**
- Consumes: `FinancialHomeResult`, `FinancialOpportunity`, `FinancialMatchedOffer` from `BackendRepository.kt`.
- Produces:
  - `data class V3SavingsSummary(val realizedMonthly: Double?, val potentialMonthly: Double?, val nextBestOpportunityId: String?)`
  - `fun FinancialHomeResult.toV3SavingsSummary(): V3SavingsSummary`
  - `fun FinancialOpportunity.hasAuthoritativeV3Offer(): Boolean`
  - `fun FinancialOpportunity.v3LifecycleLabel(): String`
  - `fun Double.asV3Money(): String`

- [ ] **Step 1: Write failing tests for realized-vs-potential truth separation**

Create `V3FinancialPresentationTest.kt` with fixtures that use the real backend model constructors. The core assertions must include:

```kotlin
package com.example

import com.example.data.repository.FinancialCategorySummary
import com.example.data.repository.FinancialHomeContext
import com.example.data.repository.FinancialHomeResult
import com.example.data.repository.FinancialMatchedOffer
import com.example.data.repository.FinancialOpportunity
import com.example.ui.v3.toV3SavingsSummary
import com.example.ui.v3.v3LifecycleLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class V3FinancialPresentationTest {
    @Test
    fun realizedAndPotentialAreNeverMerged() {
        val realized = opportunity(
            id = "realized",
            potential = 40.0,
            realized = 35.0,
            savingState = "REALIZED"
        )
        val open = opportunity(
            id = "open",
            potential = 50.0,
            realized = null,
            savingState = "UNKNOWN"
        )

        val summary = home(realized, open).toV3SavingsSummary()

        assertEquals(35.0, summary.realizedMonthly!!, 0.001)
        assertEquals(50.0, summary.potentialMonthly!!, 0.001)
        assertEquals("open", summary.nextBestOpportunityId)
    }

    @Test
    fun missingSavingsRemainUnknownInsteadOfZero() {
        val unknown = opportunity(
            id = "unknown",
            potential = null,
            realized = null,
            savingState = "UNKNOWN",
            verifiedOffer = false
        )

        val summary = home(unknown).toV3SavingsSummary()

        assertNull(summary.realizedMonthly)
        assertNull(summary.potentialMonthly)
        assertNull(summary.nextBestOpportunityId)
    }

    @Test
    fun lifecycleNeverCallsDealCompletionRealizedWithoutSavingEvidence() {
        val completedUnknown = opportunity(
            id = "completed",
            potential = 20.0,
            realized = null,
            savingState = "UNKNOWN",
            completionState = "DEAL_COMPLETED"
        )

        assertEquals("בתהליך", completedUnknown.v3LifecycleLabel())
    }

    private fun home(vararg opportunities: FinancialOpportunity) = FinancialHomeResult(
        context = FinancialHomeContext(
            observedRecurringMonthlySpend = null,
            recurringServiceCount = null,
            isCompleteHouseholdSpend = false,
            sourceCoverage = emptyList(),
            recurringServices = emptyList(),
            categories = emptyList<FinancialCategorySummary>()
        ),
        insights = emptyList(),
        opportunities = opportunities.toList()
    )

    private fun opportunity(
        id: String,
        potential: Double?,
        realized: Double?,
        savingState: String,
        verifiedOffer: Boolean = true,
        completionState: String = "UNKNOWN"
    ): FinancialOpportunity {
        val verification = if (verifiedOffer) "VERIFIED" else "UNKNOWN"
        val freshness = if (verifiedOffer) "FRESH" else "UNKNOWN"
        val eligibility = if (verifiedOffer) "ELIGIBLE" else "UNKNOWN"
        val offer = if (verifiedOffer) FinancialMatchedOffer(
            offerId = "offer-$id",
            providerName = "Provider $id",
            pricingModel = "MONTHLY",
            monthlyPrice = 80.0,
            effectiveMonthlyPrice = 80.0,
            priceGuaranteedMonths = null,
            requiredRecurringFees = null,
            requiredRecurringFeesDescription = "",
            oneTimeFees = null,
            firstYearCost = null,
            serviceType = "internet",
            verificationState = verification,
            freshnessState = freshness,
            eligibilityState = eligibility,
            verificationMethod = "OFFICIAL_SOURCE",
            officialSourceUrl = "https://example.invalid/$id",
            officialSourceName = "Official",
            verifiedAt = "2026-08-19",
            validUntil = "",
            userFitScore = null
        ) else null

        return FinancialOpportunity(
            id = id,
            type = "COMPARE_PROVIDER",
            status = "READY",
            actionMode = "IN_APP_PROVIDER_REQUEST",
            providerName = "Current $id",
            category = "אינטרנט",
            serviceType = "internet",
            currentMonthlyCost = 120.0,
            previousMonthlyCost = null,
            monthlyIncrease = null,
            percentIncrease = null,
            potentialMonthlySaving = potential,
            potentialAnnualSaving = potential?.times(12),
            realizedMonthlySaving = realized,
            realizedAnnualSaving = realized?.times(12),
            currentCostEvidenceState = "VERIFIED",
            offerVerificationState = verification,
            offerFreshnessState = freshness,
            userEligibilityState = eligibility,
            consentState = "UNKNOWN",
            requestState = "UNKNOWN",
            deliveryAttemptState = "UNKNOWN",
            submissionState = "UNKNOWN",
            deliveryState = "UNKNOWN",
            providerContactState = "UNKNOWN",
            completionState = completionState,
            savingRealizationState = savingState,
            recommendationAction = "CHECK",
            matchedOffer = offer
        )
    }
}
```

- [ ] **Step 2: Run the new test and verify it fails before production code exists**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.example.V3FinancialPresentationTest
```

Expected: FAIL because `com.example.ui.v3` presentation functions do not yet exist.

- [ ] **Step 3: Implement the minimal pure presentation mapper**

Create `V3FinancialPresentation.kt` with these exact truth rules:

```kotlin
package com.example.ui.v3

import com.example.data.repository.FinancialHomeResult
import com.example.data.repository.FinancialOpportunity
import java.util.Locale

data class V3SavingsSummary(
    val realizedMonthly: Double?,
    val potentialMonthly: Double?,
    val nextBestOpportunityId: String?
)

fun FinancialOpportunity.hasAuthoritativeV3Offer(): Boolean {
    val offer = matchedOffer ?: return false
    return offerVerificationState == "VERIFIED" &&
        offerFreshnessState == "FRESH" &&
        userEligibilityState == "ELIGIBLE" &&
        offer.verificationState == "VERIFIED" &&
        offer.freshnessState == "FRESH" &&
        offer.eligibilityState == "ELIGIBLE"
}

fun FinancialHomeResult.toV3SavingsSummary(): V3SavingsSummary {
    val realizedValues = opportunities
        .filter { it.savingRealizationState == "REALIZED" }
        .mapNotNull { it.realizedMonthlySaving?.takeIf { value -> value >= 0.0 } }

    val openOpportunities = opportunities.filter {
        it.savingRealizationState != "REALIZED" &&
            it.hasAuthoritativeV3Offer() &&
            (it.potentialMonthlySaving ?: 0.0) > 0.0
    }

    val potentialValues = openOpportunities.mapNotNull { it.potentialMonthlySaving }
    val nextBest = openOpportunities.maxByOrNull { it.potentialMonthlySaving ?: Double.NEGATIVE_INFINITY }

    return V3SavingsSummary(
        realizedMonthly = realizedValues.takeIf { it.isNotEmpty() }?.sum(),
        potentialMonthly = potentialValues.takeIf { it.isNotEmpty() }?.sum(),
        nextBestOpportunityId = nextBest?.id
    )
}

fun FinancialOpportunity.v3LifecycleLabel(): String = when {
    savingRealizationState == "REALIZED" && realizedMonthlySaving != null -> "מומש"
    completionState == "DEAL_COMPLETED" ||
        providerContactState == "CONTACTED" ||
        deliveryState == "DELIVERY_CONFIRMED" ||
        submissionState == "SUBMITTED" ||
        requestState == "REQUEST_CREATED" -> "בתהליך"
    hasAuthoritativeV3Offer() && actionMode == "IN_APP_PROVIDER_REQUEST" -> "מוכן לפעולה"
    hasAuthoritativeV3Offer() -> "נבדק"
    else -> "נמצא"
}

fun Double.asV3Money(): String = "₪${String.format(Locale.US, "%.2f", this)}"
```

Important: a `NOT_REALIZED` opportunity with an explicit realized value of zero must remain distinguishable at the opportunity level; the summary hero is allowed to show `₪0.00` as realized only when there is explicit authoritative `NOT_REALIZED` evidence for at least one completed/checked opportunity. Do not infer zero from an empty list. If implementation needs this distinction, extend `V3SavingsSummary` with `realizedKnownZero: Boolean` rather than collapsing null to zero.

- [ ] **Step 4: Run the truth mapper tests**

```bash
./gradlew :app:testDebugUnitTest --tests com.example.V3FinancialPresentationTest
```

Expected: PASS.

- [ ] **Step 5: Run existing authoritative-truth guard tests**

```bash
./gradlew :app:testDebugUnitTest --tests com.example.GateCPrimarySurfacesContractTest --tests com.example.Block4SavingsHandoffTruthGuardTest --tests com.example.FinancialAuthoritativeProjectionGuardTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/ui/v3/V3FinancialPresentation.kt app/src/test/java/com/example/V3FinancialPresentationTest.kt
git commit -m "feat: add V3 truth-safe financial presentation"
```

---

### Task 2: Build the V3 Design System and Reusable Compose Primitives

**Files:**
- Modify: `app/src/main/java/com/example/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/example/ui/theme/Type.kt`
- Modify: `app/src/main/java/com/example/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/example/ui/components/SavingsHero.kt`
- Create: `app/src/main/java/com/example/ui/components/V3StatusComponents.kt`
- Create: `app/src/main/java/com/example/ui/components/V3OpportunityComponents.kt`
- Create: `app/src/main/java/com/example/ui/components/V3ActivityComponents.kt`
- Create: `app/src/test/java/com/example/V3PrimaryScreensScreenshotTest.kt`

**Interfaces:**
- Consumes: `V3SavingsSummary`, `FinancialOpportunity`, lifecycle labels and existing Material theme.
- Produces composables:
  - `SavingsHero(realizedMonthly: Double?, potentialMonthly: Double?, realizedKnownZero: Boolean = false)`
  - `MonitoringStatus(title: String, subtitle: String?, active: Boolean)`
  - `V3EmptyState(title: String, body: String, actionLabel: String? = null, onAction: (() -> Unit)? = null)`
  - `VerificationBadge(label: String)`
  - `OpportunityLifecycleChip(label: String)`
  - `V3SectionHeader(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null)`
  - `ActivityTimelineItem(title: String, body: String?, timeLabel: String?, tone: V3ActivityTone)`

- [ ] **Step 1: Write a failing screenshot fixture for the core V3 surfaces**

Add a Roborazzi test using the existing Pixel 8 / SDK 36 pattern:

```kotlin
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
}
```

- [ ] **Step 2: Run the screenshot test and verify it fails because the component does not exist**

```bash
./gradlew :app:testDebugUnitTest --tests com.example.V3PrimaryScreensScreenshotTest
```

Expected: FAIL.

- [ ] **Step 3: Extend the existing palette without replacing the brand**

Keep the existing values unchanged and add only derived V3 surfaces such as:

```kotlin
val V3Background = Color(0xFFF7F9FC)
val V3SurfaceSoft = Color(0xFFF8FAFC)
val V3BlueSoft = Color(0xFFEFF6FF)
val V3EmeraldSoft = Color(0xFFECFDF5)
val V3AiViolet = Color(0xFF7C3AED)
val V3AmberSoft = Color(0xFFFFF7ED)
val V3ErrorSoft = Color(0xFFFEF2F2)
```

Do not remove `TechBluePrimary`, `EmeraldSavings`, `BrandNavy`, `AmberDeal`, or existing aliases used by screens/tests.

- [ ] **Step 4: Implement typography hierarchy and reusable surfaces**

Use Material 3 typography with a stronger money-number hierarchy while retaining system fonts unless a font is already licensed and bundled in the repo. Do not add font binaries.

`SavingsHero` must render realized and potential as separate labeled regions. Required test tags:

```kotlin
Modifier.testTag("v3_savings_hero")
Modifier.testTag("v3_realized_savings")
Modifier.testTag("v3_potential_savings")
```

Unknown values display `לא ידוע`; never `₪0.00` unless the caller explicitly passes a known zero.

- [ ] **Step 5: Run screenshot and compile tests**

```bash
./gradlew :app:testDebugUnitTest --tests com.example.V3PrimaryScreensScreenshotTest
./gradlew :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/ui/theme app/src/main/java/com/example/ui/components app/src/test/java/com/example/V3PrimaryScreensScreenshotTest.kt app/src/test/screenshots/v3-savings-hero.png
git commit -m "feat: add V3 native design system"
```

---

### Task 3: Replace the Primary Information Architecture Without Losing Invoices

**Files:**
- Create: `app/src/main/java/com/example/ui/v3/V3Navigation.kt`
- Create: `app/src/test/java/com/example/V3NavigationContractTest.kt`
- Modify: `app/src/test/java/com/example/IntegratedNavigationContractTest.kt`
- Modify: `app/src/main/java/com/example/ui/MainViewModel.kt`
- Modify: `app/src/main/java/com/example/ui/components/BottomNavBar.kt`
- Modify: `app/src/main/java/com/example/MainActivity.kt`

**Interfaces:**
- Produces:
  - `enum class V3PrimaryDestination(val tabIndex: Int) { HOME(0), SAVINGS(1), AI(2), ACTIVITY(3), ME(4) }`
  - `enum class V3SecondarySurface { INVOICES }`
  - `val secondarySurface: StateFlow<String?>`
  - `fun openInvoices()`
  - `fun closeSecondarySurface()`
- Preserves: `selectedTab: StateFlow<Int>` and `setTab(Int)` so existing push/navigation contracts are not needlessly broken.

- [ ] **Step 1: Update navigation tests first**

Replace the old `חשבונות` primary expectation with `AI`, and add hidden invoice-surface assertions:

```kotlin
@Test
fun primaryNavigationIsHomeSavingsAiActivityMe() {
    val nav = File("src/main/java/com/example/ui/components/BottomNavBar.kt").readText()
    assertTrue(nav.contains("Text(\"בית\")"))
    assertTrue(nav.contains("Text(\"חיסכון\")"))
    assertTrue(nav.contains("Text(\"AI\")"))
    assertTrue(nav.contains("Text(\"פעילות\")"))
    assertTrue(nav.contains("Text(\"אני\")"))
    assertFalse(nav.contains("Text(\"חשבונות\")"))
}

@Test
fun invoicesRemainReachableAsSecondaryNativeSurface() {
    val activity = File("src/main/java/com/example/MainActivity.kt").readText()
    val viewModel = File("src/main/java/com/example/ui/MainViewModel.kt").readText()
    assertTrue(activity.contains("InvoicesScreen"))
    assertTrue(viewModel.contains("openInvoices"))
    assertTrue(viewModel.contains("closeSecondarySurface"))
}
```

- [ ] **Step 2: Run navigation tests and verify failure**

```bash
./gradlew :app:testDebugUnitTest --tests com.example.V3NavigationContractTest --tests com.example.IntegratedNavigationContractTest
```

Expected: FAIL against the old nav.

- [ ] **Step 3: Add saved-state secondary-surface ownership to `MainViewModel`**

Use the existing `SavedStateHandle`. Add:

```kotlin
val secondarySurface: StateFlow<String?> =
    savedStateHandle.getStateFlow(SECONDARY_SURFACE_KEY, null)

fun openInvoices() {
    savedStateHandle[SECONDARY_SURFACE_KEY] = V3SecondarySurface.INVOICES.name
}

fun closeSecondarySurface() {
    savedStateHandle[SECONDARY_SURFACE_KEY] = null
}
```

When signing out or deleting the account, clear `SECONDARY_SURFACE_KEY` together with resetting the selected tab.

- [ ] **Step 4: Rewire bottom navigation and main surface mapping**

Bottom nav map:

- tab 0 → Home/Dashboard
- tab 1 → Savings/Providers
- tab 2 → AI Assistant
- tab 3 → Activity
- tab 4 → Me/Profile

When `secondarySurface == INVOICES`, render `InvoicesScreen` above the primary route with a native back affordance that invokes `closeSecondarySurface()`. Do not turn invoices into tab index 5.

`DashboardScreen` receives `onOpenInvoices = viewModel::openInvoices` and savings navigation now targets tab 1.

- [ ] **Step 5: Remove the large permanent top banner from the app shell**

Delete the current global `topBar` banner `Click&SaveAI עובדת ברקע...`. Monitoring status moves into Home as a compact component in Task 4. Keep status-bar inset handling/edge-to-edge behavior.

- [ ] **Step 6: Run navigation and existing push/source guards**

```bash
./gradlew :app:testDebugUnitTest --tests com.example.V3NavigationContractTest --tests com.example.IntegratedNavigationContractTest --tests com.example.PushSignOutSourceGuardTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/MainActivity.kt app/src/main/java/com/example/ui/MainViewModel.kt app/src/main/java/com/example/ui/components/BottomNavBar.kt app/src/main/java/com/example/ui/v3/V3Navigation.kt app/src/test/java/com/example/V3NavigationContractTest.kt app/src/test/java/com/example/IntegratedNavigationContractTest.kt
git commit -m "feat: adopt V3 primary navigation"
```

---

### Task 4: Rebuild Home as the V3 Financial Command Center

**Files:**
- Modify: `app/src/main/java/com/example/ui/screens/DashboardScreen.kt`
- Modify: `app/src/test/java/com/example/GateCPrimarySurfacesContractTest.kt` only where old structural wording conflicts with the approved V3 layout; never weaken its authoritative-source assertions.
- Add tests to: `app/src/test/java/com/example/V3PrimaryScreensScreenshotTest.kt`

**Interfaces:**
- Consumes: `financialSyncState`, `authoritativeFinancialHome`, `latestScanOrNull`, `userSession`, `isSyncingGmail`, `gmailSyncStep`, `toV3SavingsSummary()`.
- Callbacks: `onGoogleSignIn`, `onRequestGmailAuthorization`, `onOpenInvoices`, `onNavigateToTab`.

- [ ] **Step 1: Add failing source/UI assertions for Home hierarchy**

Add tests asserting that Home contains stable tags:

```kotlin
assertTrue(source.contains("dashboard_screen"))
assertTrue(source.contains("v3_savings_hero"))
assertTrue(source.contains("v3_next_best_action"))
assertTrue(source.contains("v3_monitoring_status"))
assertTrue(source.contains("v3_open_invoices"))
assertFalse(source.contains("viewModel.invoices.collectAsState()"))
```

Add a screenshot fixture for the stateless `SavingsHero` plus a small Home content fixture if practical without constructing a real `MainViewModel`.

- [ ] **Step 2: Run targeted tests and verify failure**

```bash
./gradlew :app:testDebugUnitTest --tests com.example.GateCPrimarySurfacesContractTest --tests com.example.V3PrimaryScreensScreenshotTest
```

Expected: FAIL on new V3 hierarchy assertions.

- [ ] **Step 3: Rebuild Home in this exact order**

1. compact greeting/orientation;
2. `SavingsHero` with realized and open-potential values from `toV3SavingsSummary()`;
3. one `הדבר הכי משתלם לעשות עכשיו` card only when `nextBestOpportunityId` resolves to an authoritative open opportunity;
4. `MonitoringStatus` based on real sync/connection state;
5. compact snapshot using nullable `observedRecurringMonthlySpend` and `recurringServiceCount`;
6. recent authoritative discoveries/invoices;
7. `כל החשבונות` secondary CTA with tag `v3_open_invoices`;
8. concise empty/partial/error handling.

For `FinancialSyncState.Partial`, continue displaying any non-null authoritative home/scan already present; show a quiet status note instead of replacing the whole screen.

For `Failed`, do not display old local cache or zeroed metrics. Keep retry action.

For unauthenticated/disconnected, delegate the pre-auth experience to Task 8's onboarding component while preserving existing callbacks.

- [ ] **Step 4: Ensure next-best action does not perform unsupported behavior**

The Home next-best CTA should navigate to Savings tab 1. It must not submit a provider lead directly from Home and must not fabricate a switch flow.

- [ ] **Step 5: Run truth and Home tests**

```bash
./gradlew :app:testDebugUnitTest --tests com.example.GateCPrimarySurfacesContractTest --tests com.example.V3FinancialPresentationTest --tests com.example.SynchronizedHomeContractTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/ui/screens/DashboardScreen.kt app/src/test/java/com/example/GateCPrimarySurfacesContractTest.kt app/src/test/java/com/example/V3PrimaryScreensScreenshotTest.kt app/src/test/screenshots
git commit -m "feat: redesign V3 financial command center"
```

---

### Task 5: Turn Savings into the Primary Opportunity Workspace

**Files:**
- Modify: `app/src/main/java/com/example/ui/screens/ProvidersScreen.kt`
- Modify: `app/src/test/java/com/example/GateCPrimarySurfacesContractTest.kt`
- Modify or add: `app/src/test/java/com/example/Block4SavingsHandoffTruthGuardTest.kt`
- Use: `app/src/main/java/com/example/ui/components/V3OpportunityComponents.kt`

**Interfaces:**
- Preserves: `OpportunityActionRepository.recordSavingsActionStarted`, `acceptSavingsOpportunity`, the existing consent/contact form, refresh via `FinancialRefreshReason.RETRY`, and all handoff/delivery/realization states.
- Uses: `hasAuthoritativeV3Offer()`, `v3LifecycleLabel()`.

- [ ] **Step 1: Write failing tests for the V3 lifecycle language and truth boundary**

Add assertions that:

```kotlin
assertTrue(source.contains("החיסכון שלך"))
assertTrue(source.contains("חיסכון פוטנציאלי"))
assertTrue(source.contains("v3LifecycleLabel"))
assertFalse(source.contains("חיסכון מאומת"))
assertFalse(source.contains("potentialMonthlySaving ?: 0.0"))
assertTrue(source.contains("recordSavingsActionStarted"))
assertTrue(source.contains("acceptSavingsOpportunity"))
```

Keep the existing requirement that offer verification/freshness/eligibility must all be authoritative before showing monetary opportunity CTAs.

- [ ] **Step 2: Run savings guards and verify the new V3 assertions fail**

```bash
./gradlew :app:testDebugUnitTest --tests com.example.GateCPrimarySurfacesContractTest --tests com.example.Block4SavingsHandoffTruthGuardTest
```

Expected: FAIL on V3-specific assertions only.

- [ ] **Step 3: Recompose Savings around the approved hierarchy**

Header:

- `החיסכון שלך`
- concise supporting line
- compact `SavingsHero` or split metrics summary.

Opportunity card contents:

- provider/category;
- current observed monthly cost;
- verified alternative only when `hasAuthoritativeV3Offer()`;
- potential monthly/annual amount only when non-null and positive;
- `OpportunityLifecycleChip(v3LifecycleLabel())`;
- source/freshness information from the matched offer;
- one action CTA when existing `IN_APP_PROVIDER_REQUEST` flow is genuinely available.

Do not hide handoff states. Rewrite them into concise human copy but preserve semantic distinctions:

`REQUEST_CREATED` ≠ `SUBMITTED` ≠ `DELIVERY_CONFIRMED` ≠ `CONTACTED` ≠ `DEAL_COMPLETED` ≠ `REALIZED`.

- [ ] **Step 4: Preserve explicit realized-zero semantics**

If `savingRealizationState == "NOT_REALIZED"` and `realizedMonthlySaving == 0.0`, showing `₪0.00` is allowed because it is authoritative evidence. Do not convert unknown to this state.

- [ ] **Step 5: Run savings and action tests**

```bash
./gradlew :app:testDebugUnitTest --tests com.example.Block4SavingsHandoffTruthGuardTest --tests com.example.GateCPrimarySurfacesContractTest --tests com.example.V3FinancialPresentationTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/ui/screens/ProvidersScreen.kt app/src/main/java/com/example/ui/components/V3OpportunityComponents.kt app/src/test/java/com/example/GateCPrimarySurfacesContractTest.kt app/src/test/java/com/example/Block4SavingsHandoffTruthGuardTest.kt
git commit -m "feat: redesign V3 savings workspace"
```

---

### Task 6: Promote the Existing Native AI Assistant into a First-Class Product Destination

**Files:**
- Modify: `app/src/main/java/com/example/ui/screens/AiAssistantScreen.kt`
- Add tests to: `app/src/test/java/com/example/V3PrimaryScreensScreenshotTest.kt`
- Create: `app/src/test/java/com/example/V3AiBoundaryGuardTest.kt`

**Interfaces:**
- Preserves `viewModel.analyzeDeal`, `viewModel.sendChatMessage`, `aiDealAnalysis`, `chatMessages`, `isAnalyzingDeal`, `isAiChatLoading`, `aiErrorMessage`, and `userSession`.
- Does not add a new AI endpoint, model, prompt transport, API key, or local LLM.

- [ ] **Step 1: Write source guard proving AI stays native/backend-grounded**

```kotlin
@Test
fun v3AiUsesExistingBackendViewModelOnly() {
    val source = File("src/main/java/com/example/ui/screens/AiAssistantScreen.kt").readText()
    assertTrue(source.contains("viewModel.analyzeDeal"))
    assertTrue(source.contains("viewModel.sendChatMessage"))
    assertTrue(source.contains("viewModel.aiDealAnalysis.collectAsState()"))
    assertFalse(source.contains("WebView"))
    assertFalse(source.contains("GOOGLE_MAIL_APP_USER_CONNECTOR_CLIENT_API_KEY"))
    assertFalse(source.contains("startGmailConnect"))
}
```

- [ ] **Step 2: Run guard before redesign**

```bash
./gradlew :app:testDebugUnitTest --tests com.example.V3AiBoundaryGuardTest
```

Expected: PASS for backend ownership, then add V3 copy assertions (`עוזר החיסכון שלך`, suggestion-chip tags) and verify those fail.

- [ ] **Step 3: Redesign AI without creating a generic ChatGPT clone**

Required hierarchy:

- title `עוזר החיסכון שלך`;
- concise trust/grounding cue;
- suggestion chips such as `איפה אני משלם יותר מדי?`, `מה אפשר לבטל?`, `מה כדאי לבדוק השבוע?`, `איפה החיסכון הגדול ביותר?`, `תסביר לי את החשבון הזה`;
- chips must call the existing `sendChatMessage`/`analyzeDeal` path only after user intent/tap; do not auto-send financial claims on screen open;
- existing chat messages remain authoritative backend output;
- loading indicator is subtle;
- errors remain human-readable.

If user is unauthenticated, show one concise sign-in guidance state; do not create a second sign-in flow inside AI.

- [ ] **Step 4: Add a screenshot of the AI idle state using a stateless extracted content composable if required**

If `AiAssistantScreen` is too coupled to `MainViewModel` for deterministic screenshot tests, extract only the rendering body into:

```kotlin
@Composable
internal fun AiAssistantContent(
    authenticated: Boolean,
    messages: List<ChatMessage>,
    loading: Boolean,
    errorMessage: String,
    onSuggestion: (String) -> Unit,
    onSend: (String) -> Unit
)
```

`AiAssistantScreen(viewModel)` remains the state-collection wrapper.

- [ ] **Step 5: Run AI tests and screenshots**

```bash
./gradlew :app:testDebugUnitTest --tests com.example.V3AiBoundaryGuardTest --tests com.example.V3PrimaryScreensScreenshotTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/ui/screens/AiAssistantScreen.kt app/src/test/java/com/example/V3AiBoundaryGuardTest.kt app/src/test/java/com/example/V3PrimaryScreensScreenshotTest.kt app/src/test/screenshots
git commit -m "feat: promote V3 savings AI assistant"
```

---

### Task 7: Redesign Activity, Me, and Secondary Invoices Without Changing Their Data Authority

**Files:**
- Modify: `app/src/main/java/com/example/ui/screens/ActivityScreen.kt`
- Modify: `app/src/main/java/com/example/ui/screens/ProfileScreen.kt`
- Modify: `app/src/main/java/com/example/ui/screens/InvoicesScreen.kt`
- Use: `app/src/main/java/com/example/ui/components/V3ActivityComponents.kt`
- Modify: `app/src/test/java/com/example/IntegratedNavigationContractTest.kt`
- Modify: `app/src/test/java/com/example/GateCPrimarySurfacesContractTest.kt`
- Preserve: `app/src/test/java/com/example/Block5PrivacyLifecycleGuardTest.kt`

**Interfaces:**
- Activity consumes only `authoritativeFinancialActivity` / `FinancialActivityEvent` and existing financial sync state.
- Profile preserves `disconnectGmail`, `deleteImportedFinancialData`, `deleteAccount`, `signOut`, native Google/Gmail callbacks, and privacy operation state.
- Invoices consumes only authoritative recovered Gmail invoices (`financialSyncState` / `latestScanOrNull`).

- [ ] **Step 1: Add failing UI/source assertions**

Activity assertions:

```kotlin
assertTrue(activity.contains("ActivityTimelineItem"))
assertFalse(activity.contains("CRM", ignoreCase = true))
assertFalse(activity.contains("lead", ignoreCase = true))
```

Profile assertions:

```kotlin
assertTrue(profile.contains("החיבורים שלי"))
assertTrue(profile.contains("פרטיות ונתונים"))
assertTrue(profile.contains("disconnectGmail"))
assertTrue(profile.contains("deleteImportedFinancialData"))
assertTrue(profile.contains("deleteAccount"))
assertTrue(profile.contains("קריאה בלבד"))
```

Invoices assertions continue to forbid local/manual fallback:

```kotlin
assertFalse(invoices.contains("viewModel.invoices.collectAsState()"))
assertFalse(invoices.contains("ManualInvoiceDialog"))
assertFalse(invoices.contains("viewModel.addManualInvoice"))
```

- [ ] **Step 2: Run relevant guards and verify only V3 copy/component assertions fail**

```bash
./gradlew :app:testDebugUnitTest --tests com.example.IntegratedNavigationContractTest --tests com.example.GateCPrimarySurfacesContractTest --tests com.example.Block5PrivacyLifecycleGuardTest
```

- [ ] **Step 3: Redesign Activity as a human timeline**

Group authoritative events by the existing event timestamps into human headings where available (`היום`, `אתמול`, `השבוע`). If timestamp parsing cannot safely determine a group, use a neutral `פעילות קודמת`; do not invent dates.

Use meaningful event labels derived from authoritative event type/payload only. Keep technical detail secondary.

- [ ] **Step 4: Redesign Me/Profile as personal control**

Required sections:

1. identity/status;
2. `החיבורים שלי` — Google/Gmail + read-only trust cue;
3. notification status if real state exists;
4. supported preferences only; local placeholder preferences must not be presented as server truth;
5. `פרטיות ונתונים` — disconnect Gmail, delete imported data, delete account;
6. app/legal/version.

Destructive controls require confirmation dialogs and remain visually separated.

- [ ] **Step 5: Redesign Invoices as a secondary financial list**

Show provider, category, amount, received date/period, verification status. Do not show fake alternative-price/savings fields from the legacy `InvoiceItem` mapping.

Add a top back action that closes the secondary surface via the callback provided by `MainActivity`/`MainAppStructure`.

- [ ] **Step 6: Run guards**

```bash
./gradlew :app:testDebugUnitTest --tests com.example.IntegratedNavigationContractTest --tests com.example.GateCPrimarySurfacesContractTest --tests com.example.Block5PrivacyLifecycleGuardTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/ui/screens/ActivityScreen.kt app/src/main/java/com/example/ui/screens/ProfileScreen.kt app/src/main/java/com/example/ui/screens/InvoicesScreen.kt app/src/main/java/com/example/ui/components/V3ActivityComponents.kt app/src/test/java/com/example/IntegratedNavigationContractTest.kt app/src/test/java/com/example/GateCPrimarySurfacesContractTest.kt
git commit -m "feat: redesign V3 activity profile and invoices"
```

---

### Task 8: Add the Privacy-First Pre-Auth Onboarding and First-Sync Experience

**Files:**
- Create: `app/src/main/java/com/example/ui/screens/V3OnboardingContent.kt`
- Modify: `app/src/main/java/com/example/ui/MainViewModel.kt`
- Modify: `app/src/main/java/com/example/ui/screens/DashboardScreen.kt`
- Create: `app/src/test/java/com/example/V3OnboardingContractTest.kt`
- Add screenshots to: `app/src/test/java/com/example/V3PrimaryScreensScreenshotTest.kt`

**Interfaces:**
- Produces `onboardingStep: StateFlow<Int>` using the existing `SavedStateHandle`.
- Produces `fun nextOnboardingStep()` and `fun resetOnboarding()`.
- Uses existing `onGoogleSignIn` then existing `onRequestGmailAuthorization`; does not create auth logic.

- [ ] **Step 1: Write failing onboarding boundary tests**

```kotlin
@Test
fun onboardingRemainsNativeAndPrivacyFirst() {
    val source = File("src/main/java/com/example/ui/screens/V3OnboardingContent.kt")
    assertTrue(source.exists())
    val text = if (source.exists()) source.readText() else ""
    assertTrue(text.contains("הכסף שלך יכול לעבוד חכם יותר"))
    assertTrue(text.contains("אנחנו מחפשים — אתה מחליט"))
    assertTrue(text.contains("מחברים Gmail בצורה מאובטחת"))
    assertTrue(text.contains("קריאה בלבד"))
    assertFalse(text.contains("startGmailConnect"))
    assertFalse(text.contains("GOOGLE_MAIL_APP_USER_CONNECTOR_CLIENT_API_KEY"))
}
```

- [ ] **Step 2: Run and verify failure**

```bash
./gradlew :app:testDebugUnitTest --tests com.example.V3OnboardingContractTest
```

Expected: FAIL because the content file does not exist.

- [ ] **Step 3: Add SavedState-backed onboarding progression**

Use an integer 0..2 in `SavedStateHandle`; this is not a new persistent backend state. When the user becomes authenticated, Home naturally exits the unauthenticated onboarding branch. Do not add DataStore/SharedPreferences solely for onboarding.

The three content stages are exactly:

1. `הכסף שלך יכול לעבוד חכם יותר`
2. `אנחנו מחפשים — אתה מחליט`
3. `מחברים Gmail בצורה מאובטחת`

Final CTA invokes the existing `onGoogleSignIn` if not authenticated; after successful auth the existing Home/Gmail connection flow invokes `onRequestGmailAuthorization` with the current explicit Gmail consent dialog.

- [ ] **Step 4: Improve first-sync messaging using only real state**

Map existing states conservatively:

- `CheckingConnection` → `מחברים את החשבון`
- `Recovering` → `מסדרים את התמונה שלך`
- `isSyncingGmail` / `gmailSyncStep` may show the actual existing repository step message when non-blank.

Do not claim `מחפשים חשבוניות` or `בודקים הזדמנויות` as completed unless the existing repository exposes that exact state. If granular truth is absent, show neutral scanning motion and `אנחנו מעדכנים את התמונה שלך`.

- [ ] **Step 5: Add screenshot fixtures for onboarding step 1 and the neutral syncing state**

Use stateless `V3OnboardingContent(step = ..., ...)` so screenshots do not require Firebase or a ViewModel.

- [ ] **Step 6: Run tests/screenshots**

```bash
./gradlew :app:testDebugUnitTest --tests com.example.V3OnboardingContractTest --tests com.example.V3PrimaryScreensScreenshotTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/ui/screens/V3OnboardingContent.kt app/src/main/java/com/example/ui/screens/DashboardScreen.kt app/src/main/java/com/example/ui/MainViewModel.kt app/src/test/java/com/example/V3OnboardingContractTest.kt app/src/test/java/com/example/V3PrimaryScreensScreenshotTest.kt app/src/test/screenshots
git commit -m "feat: add V3 onboarding and sync experience"
```

---

### Task 9: Add Explicit Production-Boundary, RTL, Accessibility, and Regression Gates

**Files:**
- Create: `app/src/test/java/com/example/V3ProductionBoundaryGuardTest.kt`
- Modify: existing screenshot test and screenshot fixtures as needed
- Do not modify production infrastructure/workflows unless a test proves a workflow bug unrelated to V3; any such change is out of scope and requires Master Control review.

**Interfaces:**
- Consumes repository source only.
- Produces a fail-closed source guard for V3 runtime boundaries.

- [ ] **Step 1: Write the production-boundary guard**

Required assertions:

```kotlin
package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V3ProductionBoundaryGuardTest {
    @Test
    fun v3DoesNotIntroduceWebOrLovableRuntime() {
        val sourceRoot = File("src/main/java")
        val source = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .joinToString("\n") { it.readText() }

        assertFalse(source.contains("android.webkit.WebView"))
        assertFalse(source.contains("addJavascriptInterface"))
        assertFalse(source.contains("startGmailConnect"))
        assertFalse(source.contains("GOOGLE_MAIL_APP_USER_CONNECTOR_CLIENT_API_KEY"))
        assertFalse(source.contains("connectAppUser"))
        assertTrue(source.contains("https://www.googleapis.com/auth/gmail.readonly"))
    }

    @Test
    fun releaseIdentityRemainsUnchanged() {
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("applicationId = \"com.aistudio.clickandsaveai.app\""))
        assertTrue(gradle.contains("versionCode = 1"))
        assertTrue(gradle.contains("versionName = \"1.0\""))
    }
}
```

- [ ] **Step 2: Run boundary guard**

```bash
./gradlew :app:testDebugUnitTest --tests com.example.V3ProductionBoundaryGuardTest
```

Expected: PASS.

- [ ] **Step 3: Validate RTL composition in screenshots**

Every V3 screenshot fixture must wrap content with:

```kotlin
CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { ... }
```

Add one compact-device qualifier fixture in addition to Pixel 8. Use an existing Robolectric qualifier if available in the installed Roborazzi version; if no named compact profile exists, use an explicit Robolectric qualifier string such as `w360dp-h640dp-xhdpi` rather than hardcoding production layout widths.

- [ ] **Step 4: Add Compose semantics to critical elements**

Verify test tags/content descriptions for:

- bottom navigation destinations;
- realized savings;
- potential savings;
- next-best action;
- Gmail connection action;
- destructive privacy actions;
- AI message input/send;
- invoices back action.

Critical meaning must be present in text/semantics, not only color.

- [ ] **Step 5: Run full Android unit/lint/build verification**

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Expected: BUILD SUCCESSFUL, all tests pass, lint has no new fatal/error findings.

- [ ] **Step 6: Run backend regression even though backend source should be untouched**

From repository root:

```bash
npm --prefix functions test
```

Expected: the governed backend suite remains fully green. If test count differs from the current governed baseline only because upstream tests changed on `main`, report the exact count; do not hide failures.

- [ ] **Step 7: Confirm no backend/infrastructure files changed**

Run:

```bash
git diff --name-only 887518646fb66b36b10345fe2187e087457395ae...HEAD
```

Expected V3 implementation changes are limited to Android UI/presentation/tests and V3 design/plan docs. If any file under `functions/`, `firestore.rules`, `firestore.indexes.json`, `.github/workflows/production-release.yml`, WIF/IAM scripts, signing config, or Firebase deployment config appears, STOP for Master Control review.

- [ ] **Step 8: Commit**

```bash
git add app/src/test/java/com/example/V3ProductionBoundaryGuardTest.kt app/src/test/java/com/example/V3PrimaryScreensScreenshotTest.kt app/src/test/screenshots
git commit -m "test: gate V3 native production boundaries"
```

---

### Task 10: PR Review, Protected-Main Integration, and New Block 3I Candidate

**Files:**
- No new product files expected.
- Use existing `.github/workflows/production-release.yml` unchanged.
- Update governance evidence in GitHub issue/PR comments only; do not commit secrets or candidate binaries to source.

**Interfaces:**
- Consumes the final V3 implementation branch and all required CI contexts.
- Produces one new protected-main source SHA and one new signed Production candidate artifact/AAB identity for Block 3I.

- [ ] **Step 1: Open/refresh a dedicated implementation PR to `main`**

The design-only PR #91 remains the approved architecture/spec carrier. Implementation should use a dedicated implementation branch/PR unless Master Control explicitly converts the branch after plan approval. The implementation PR body must state:

- V3 native UI/UX port only;
- no backend/Firebase/OAuth/App Check/signing/release-infrastructure change;
- old artifact `9354867453` is historical and must not be uploaded;
- Block 3H remains closed;
- Block 3I candidate lock will refresh after merge.

- [ ] **Step 2: Require exact-head CI PASS**

Required contexts remain:

- `android-build`
- `backend-test`
- `production-security`
- `repository-operations-readiness`

Do not merge based only on a green subset.

- [ ] **Step 3: Independently inspect PR diff before merge**

Verify no unexpected:

- `functions/` changes;
- Firebase rules/index changes;
- production release workflow changes;
- WIF/IAM changes;
- secrets/config values;
- package/version/signing changes;
- WebView/Lovable runtime additions.

- [ ] **Step 4: Merge only through protected `main` after review**

Record the exact resulting protected-main SHA.

- [ ] **Step 5: Dispatch one new signed Production candidate build with deployment disabled**

Use the existing protected release workflow with:

- `source_sha=<new exact protected-main SHA>`
- `confirm_environment=CLICKANDSAVEAI_PRODUCTION`
- `authorize_firebase_deploy=NO_DEPLOY`

Do not authorize Firebase deployment. This is a candidate refresh for V3 only.

- [ ] **Step 6: Verify candidate evidence independently**

Record:

- new artifact ID/name;
- exact source SHA;
- `app-release.aab` SHA-256;
- `app-release.apk` SHA-256 if emitted;
- package `com.aistudio.clickandsaveai.app`;
- versionCode 1 / versionName 1.0 unless Play proves a bump is required;
- upload certificate identity;
- Play app-signing SHA-1/SHA-256 metadata unchanged and distinct from upload key;
- `firebase_deployed=false` for this candidate refresh;
- `google_play_published=false` before upload.

- [ ] **Step 7: Resume Block 3I with only the new V3 AAB**

The historical pre-V3 AAB SHA-256 `facce33150606910f4b8266f9f532f8eccec42c5c2ffec5f4956c3ed72e06f7f` must not be uploaded after the V3 candidate is approved.

If Play Internal Testing is still empty and versionCode 1 is unused, upload the new V3 AAB. If Play rejects versionCode 1 as already used, STOP; do not modify the AAB out-of-band. Return to Master Control for a source-controlled version bump, CI, and a new candidate cycle.

---

## Plan Self-Review

### Spec coverage

- Native-only production runtime: Tasks 3, 6, 9.
- V3 navigation: Task 3.
- Home financial command center: Task 4.
- Savings opportunity workspace and truthful lifecycle: Tasks 1, 5.
- AI primary destination: Task 6.
- Activity timeline: Task 7.
- Me/Profile privacy/connection control: Task 7.
- Secondary invoices: Tasks 3, 7.
- Onboarding and first sync: Task 8.
- Brand/design system/motion-ready primitives: Task 2.
- RTL/accessibility/compact devices: Task 9.
- Gmail/Firebase/backend/security preservation: Tasks 6, 7, 8, 9.
- New signed AAB and Block 3I handoff: Task 10.

### Placeholder scan

No `TBD`, `TODO`, fake feature, or open-ended implementation placeholder is permitted by this plan. Unsupported UI behavior must be omitted or routed to an existing native callback rather than mocked.

### Type consistency

- `V3SavingsSummary`, `hasAuthoritativeV3Offer`, `v3LifecycleLabel`, and `asV3Money` are defined in Task 1 and consumed by later tasks.
- Primary destination indexes remain 0..4 so existing `selectedTab`/SavedState semantics stay stable.
- Invoices use a separate `V3SecondarySurface`, not a hidden sixth tab.
- Existing native Google/Gmail callbacks remain owned by `MainActivity`/`MainViewModel`; no new connector interface is introduced.
