# V3 Visual Fidelity Remediation V2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the native Jetpack Compose V3 screens structurally closer to the approved Click & Save AI premium reference by reducing oversized card treatment, increasing information density, flattening elevation, tightening typography, and integrating the bottom navigation into the screen while preserving all product truth and behavior.

**Architecture:** Keep existing screen data/state logic untouched. Perform the remediation primarily through shared visual primitives (`V3ReferenceVisualComponents.kt`, `SavingsHero.kt`, `BottomNavBar.kt`) so Home, Savings, AI, Bills, and Profile move together as one design system. Lock the approved density/proportion changes with source-level regression guards before implementation.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Robolectric/source guard tests, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-22-v3-visual-fidelity-remediation-design.md`

## Global Constraints

- Work only on existing PR #92 / branch `agent/v3-native-port`.
- Keep PR OPEN, DRAFT, NOT MERGED.
- Do not modify protected `main`.
- Do not modify backend, Firebase, OAuth, App Check, signing, release infrastructure, or production behavior.
- Keep exact primary navigation labels/order: `בית | חיסכון | AI | לתשלום | פרופיל`.
- Preserve RTL Hebrew and mixed-direction isolation.
- Preserve UNKNOWN != ZERO and potential savings != realized savings.
- Do not add fake providers, amounts, dates, savings, or recommendations.
- Do not use Lovable runtime or spend Lovable credits.

---

### Task 1: Lock V2 visual-density contracts

**Files:**
- Create: `app/src/test/java/com/example/V3VisualFidelityV2GuardTest.kt`

**Interfaces:**
- Consumes: native source files under `app/src/main/java/com/example/ui/components/`.
- Produces: regression guards for compact hero geometry, restrained elevation, compact savings summary, and integrated bottom navigation.

- [ ] **Step 1: Write the failing test**

Create source guards that require: hero radius <= 22dp, shared card radius <= 18dp, no 10/12dp hero shadows, AI orb <= 68dp, profile avatar <= 58dp, compact Home savings strip, and edge-integrated bottom navigation with 2dp elevation.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests com.example.V3VisualFidelityV2GuardTest`
Expected: FAIL against the current oversized visual system.

- [ ] **Step 3: Commit the failing guard**

Commit: `test(v3): lock structural visual fidelity V2`

### Task 2: Rebuild shared premium primitives for density and hierarchy

**Files:**
- Modify: `app/src/main/java/com/example/ui/components/V3ReferenceVisualComponents.kt`

**Interfaces:**
- Consumes: existing theme colors and truthful numeric formatting.
- Produces: the same public composable signatures (`V3GradientHeader`, `V3FinancialOverviewCard`, `V3SavingsDashboardHero`, `V3AiExperienceHero`, `V3BillVisualCard`, `V3ProfileHero`) with tighter reference-like proportions.

- [ ] **Step 1: Reduce geometry and elevation**

Use 22dp hero radius, 18dp card radius, 0–4dp restrained elevation, smaller vertical padding, smaller decorative circles, and compact typography.

- [ ] **Step 2: Increase dashboard density**

Make financial metrics shorter, reduce tile padding, reduce AI orb/rings, compress bill cards, and compress profile identity hero without removing any truthful state.

- [ ] **Step 3: Run V2 guard and full unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: PASS.

- [ ] **Step 4: Commit**

Commit: `fix(v3): tighten premium reference proportions`

### Task 3: Remove the second oversized Home hero and floating-nav heaviness

**Files:**
- Modify: `app/src/main/java/com/example/ui/components/SavingsHero.kt`
- Modify: `app/src/main/java/com/example/ui/components/BottomNavBar.kt`

**Interfaces:**
- Consumes: existing SavingsHero inputs and frozen navigation callbacks/tags.
- Produces: compact Home savings strip and a thinner integrated navigation surface while preserving test tags and navigation semantics.

- [ ] **Step 1: Compact SavingsHero**

Replace the large nested card treatment with a low-height two-metric savings strip. Keep `v3_realized_savings`, `v3_potential_savings`, and `v3_savings_hero` tags and all UNKNOWN/realized semantics.

- [ ] **Step 2: Integrate bottom navigation**

Remove 16dp floating side gutters, reduce dock radius/elevation, keep 52dp+ touch targets, preserve exact labels/order and all `nav_*` tags.

- [ ] **Step 3: Run unit tests and lint**

Run: `./gradlew testDebugUnitTest lintDebug`
Expected: PASS.

- [ ] **Step 4: Commit**

Commit: `fix(v3): compact savings and navigation surfaces`

### Task 4: Exact-head verification

**Files:** none unless verification exposes a regression.

**Interfaces:**
- Consumes: final branch HEAD.
- Produces: exact-head CI evidence and Android staging APK.

- [ ] **Step 1: Verify Android unit tests, lint, debug APK, release audit**

Expected: all PASS on exact HEAD.

- [ ] **Step 2: Verify Production Operations and Production Enablement Security**

Expected: PASS without production mutation.

- [ ] **Step 3: Re-check governance**

Verify PR #92 remains OPEN + DRAFT + NOT MERGED and protected `main` remains `887518646fb66b36b10345fe2187e087457395ae`.

- [ ] **Step 4: Build handoff**

Return the exact-head APK for device visual acceptance. Do not merge.