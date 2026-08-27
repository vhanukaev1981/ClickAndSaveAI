# Click & Save AI — V3 Visual Fidelity Remediation Design

## Status

Owner-approved design remediation for PR #92. This document corrects the visual-fidelity gap between the current native Android V3 implementation and the approved Lovable V3 product/design source.

This is a presentation-layer correction only. It does not authorize a backend, Firebase, OAuth, App Check, signing, deployment, Google Play, or Production architecture change.

## Canonical Design Source

The authoritative Lovable source is the separate two-way-synced repository:

- Repository: `vhanukaev1981/click-save-smile`
- Frozen design-source branch: `main`
- Frozen design-source SHA: `a32abfe5ad2bb6f03a64f65d2ed48327f6c8c65b`

Native implementation must be compared against that exact SHA, not against a newly generated Lovable iteration. No further Lovable credits are required for this remediation.

Primary source files include:

- `src/styles.css`
- `src/components/csa/MobileShell.tsx`
- `src/components/csa/score.tsx`
- `src/components/csa/v6.tsx`
- `src/routes/_authenticated/home.tsx`
- `src/routes/_authenticated/savings.tsx`
- `src/routes/_authenticated/assistant.tsx`
- `src/routes/_authenticated/bills.tsx`
- `src/routes/_authenticated/me.tsx`
- `src/routes/_authenticated/activity.tsx`
- `src/routes/onboarding.tsx`
- `src/routes/auth.tsx`
- `src/routes/privacy.tsx`
- `src/routes/_authenticated/connect.tsx`

The Lovable plan `.lovable/plan/v3-final-design-pass-click-save-ai-2026-08-19.md` is supporting product intent. Actual rendered/component source at the frozen SHA is the higher-fidelity visual reference.

## Native Target

Repository: `vhanukaev1981/ClickAndSaveAI`

Continue only PR #92 / branch `agent/v3-native-port`.

The Android application remains native Jetpack Compose. No WebView, React runtime, Capacitor, Cordova, Lovable runtime, Lovable Gmail connector, or alternate authentication stack may be introduced.

## Frozen Product Navigation

Owner approved preserving the current product navigation while correcting the visuals:

1. `בית`
2. `חיסכון`
3. `AI`
4. `לתשלום`
5. `פרופיל`

`פעילות` remains a secondary surface reachable from Home/Profile. This visual-remediation pass must not redesign the information architecture again.

## Problem Statement

The current native build is functionally close to the approved V3 product flow but visually diverges from the Lovable reference. The principal defects are:

- Material 3 defaults dominate the visual language.
- Large gray/lavender `surfaceVariant` blocks make the product look like an internal/debug application.
- Screen density is too loose in some places and too heavy in others.
- Headers are oversized and inconsistent with Lovable's compact hierarchy.
- Cards are too visually heavy, frequently nested, and lack the consistent hairline-border/shadow grammar.
- Bottom navigation is a full-width Material bar rather than the compact floating dock in the Lovable source.
- Primary blue is used too broadly instead of as a restrained action/accent color.
- AI currently resembles a technical form/chat surface instead of the proactive savings-assistant design.
- Profile currently exposes technical/debug concepts too prominently.
- Staging diagnostics such as `INTERNAL` and `Recovery diagnostic ...` appear in consumer-visible hierarchy.
- The existing screenshot suite validates components/states but does not prove screen-level fidelity to the approved design source.

## Visual System — Exact Source Tokens

The native palette must map the frozen Lovable tokens from `src/styles.css`:

| Role | Lovable token | Native target |
|---|---|---|
| App background | `#F8FAFC` | `V3Background` |
| Surface/card | `#FFFFFF` | `V3Surface` |
| Primary text/navy | `#0F172A` | `V3Navy` |
| Primary action | `#2563EB` | `V3Primary` |
| Teal signal/accent | `#14B8A6` | `V3Teal` |
| Savings/success | `#00B879` | `V3Success` |
| Success soft | `#ECFDF5` | `V3SuccessSoft` |
| Muted surface | `#F1F5F9` | `V3Muted` |
| Muted text | `#64748B` | `V3MutedForeground` |
| Primary soft/accent | `#EFF6FF` | `V3PrimarySoft` |
| Border/input | `#E2E8F0` | `V3Border` |
| Warning | `#F59E0B` | `V3Warning` |
| Warning soft | `#FFFBEB` | `V3WarningSoft` |
| Destructive | `#EF4444` | `V3Destructive` |
| Aurora dark 1 | `#071638` | `V3Aurora1` |
| Aurora dark 2 | `#17419E` | `V3Aurora2` |
| Aurora dark 3 | `#0E7490` | `V3Aurora3` |

The current `#7C3AED` violet must not be a generic secondary brand color. Blue→violet treatment is allowed only as a restrained AI accent where the Lovable reference uses it; generic cards, navigation and status surfaces must use the frozen palette above.

## Typography

Lovable uses `Heebo`, with `Assistant` as fallback. Android should use Heebo if an existing bundled/project-safe font route is available; otherwise use a native/system fallback only if bundling Heebo would expand scope beyond presentation assets.

Required visual hierarchy:

- Screen title: approximately 22sp, very bold/black, compact line height.
- Eyebrow: approximately 10.5–11sp, bold, restrained tracking, primary color.
- Section title: approximately 15.5–16sp, extra-bold.
- Card title: approximately 14–15sp, bold/extra-bold.
- Body: approximately 12.5–14sp depending on role.
- Supporting/meta: approximately 11–12sp, muted foreground.
- Money/savings: 22–27sp for primary figures, tabular-style number treatment where practical.

Do not use 24–40sp headings by default on ordinary phone screens. Large typography is reserved for rare hero/value moments.

## Spacing, Radius and Surface Grammar

Native equivalents should follow the frozen source:

- Screen horizontal padding: 20dp.
- Main top padding: approximately 20dp.
- Page-level vertical rhythm: approximately 28dp between major sections.
- Canonical panel radius: 20dp (`1.25rem` in Lovable).
- Standard CTA radius: 16dp.
- Small control/icon radius: 12–16dp.
- Card padding: 16dp.
- Minimum interactive height: 44–48dp.
- Borders: 1dp `V3Border`, often at reduced alpha for dock/quiet surfaces.
- Shadows: low, soft, short-range; no large Material tonal/elevation blocks.

Canonical panel tones:

- Plain: white card + subtle border + soft shadow.
- Primary: `V3PrimarySoft`/white blend + primary border at low alpha.
- Success: `V3SuccessSoft`/white blend + success border at low alpha.
- Attention: `V3WarningSoft`/white blend + warning border at low alpha.

Avoid using `surfaceVariant` as a large generic gray/lavender panel background.

## Bottom Navigation

Match `MobileShell.tsx` behavior and proportions:

- Floating dock, not full-width Material navigation slab.
- Outer horizontal margin approximately 16dp.
- Bottom safe-area aware.
- White/translucent surface feel with subtle border and soft shadow.
- Outer radius approximately 20dp.
- Internal padding approximately 4dp.
- Each item has compact icon + label spacing.
- Icon approximately 20dp.
- Active item uses `V3PrimarySoft` background and `V3Primary` icon/text.
- Inactive item uses muted foreground.
- Labels are compact; the dock must not visually dominate the screen.

Required labels remain exactly `בית | חיסכון | AI | לתשלום | פרופיל`.

## Shared Native Primitives

Create or consolidate native equivalents of Lovable's frozen V3 primitives rather than styling every screen independently:

- `V3ScreenHeader`
- `V3SectionHead`
- `V3Panel`
- `V3HeaderIconButton`
- `V3PrimaryButton`
- `V3SecondaryButton`
- `V3QuietEmpty`
- `V3SummaryStrip`
- `V3Note`
- `V3SettingsGroup`
- `V3SettingsRow`
- `V3IncreaseBadge`
- `V3IncreaseLine`
- `SavingsHero`
- `SavingsFigure`
- `NoVerifiedSaving`

Existing component names may be retained where they already represent the same responsibility. The goal is one consistent visual grammar, not abstraction churn.

## Home

Visual order follows the frozen `home.tsx` reference:

1. Compact header with eyebrow `Click & Save AI`, greeting, short support line and optional notification action.
2. Quiet one-line monitoring status. It must never dominate the screen.
3. Savings hero with potential and realized structurally separated.
4. One priority/next-best-action card.
5. `לתשלום` section with recent invoices and a compact link to the full bills screen.
6. Bill-increase card only when authoritative comparison exists.
7. Recent activity section.
8. Quiet truth/legal note.

No permanent giant error/status banner. Failure states should be compact, actionable and visually secondary to preserved verified content.

## Savings

Follow frozen `savings.tsx`:

- Compact header `החיסכון שלך`.
- Savings hero first.
- Compact summary strip.
- `אפשר לחסוך` opportunity cards.
- `בתהליך` section.
- `נחסך בפועל` section.
- Strong separation between potential and realized savings.
- Unknown savings display uses `NoVerifiedSaving`; never `₪0` fallback.

Cards should be white/soft, bordered and compact rather than large gray containers.

## AI

Follow frozen `assistant.tsx` product treatment:

- Header `עוזר החיסכון שלך`.
- Proactive savings framing before chat input.
- Top insight/priority card only from authoritative signals.
- 4–6 primary suggestion actions, with additional suggestions grouped separately.
- Compact chat bubbles/results only after interaction.
- Input/composer is a clean consumer control, not a gray technical form.
- No generic ChatGPT clone styling.
- AI accent may use restrained blue/AI accent, but navy/white/primary-blue remain dominant.
- Insufficient-data answers must remain explicit and visually calm.

## Bills / `לתשלום`

Follow frozen `bills.tsx`:

- Header `לתשלום` with compact support copy.
- Summary strip for real counts/known totals only.
- Compact trust panel explaining payment boundary.
- Horizontal filter chips/pills.
- Invoice action cards with provider, amount, date/category, truth-safe state, increase signal and savings/payment action slots.
- Do not render large empty gray blocks for missing information.
- Unknown due date/payment target remains an explicit muted unknown state.

## Profile

Follow frozen `me.tsx` visual hierarchy:

- Identity header first.
- Grouped settings, not a sequence of large explanatory cards.
- Groups exactly: `חיבור ונתונים`, `פרטיות והרשאות`, `פעילות והתראות`, `חשבון ואבטחה`.
- Rows use compact icons, title, optional status and short hint.
- Gmail connection truth stays explicit.
- Disconnect Gmail, delete imported data, delete account and sign-out remain semantically distinct.
- Destructive actions are visually contained and confirmation-gated.
- Version/about information stays quiet and low-priority.

## Activity and Secondary Surfaces

Activity keeps real events only and adopts the same white-card / compact-section grammar. No technical ledger/debug presentation.

Onboarding/auth/connect/privacy/scanning must use the same palette, typography, cards and CTA system. Excess top whitespace should be removed; CTA placement must remain above navigation/system safe areas.

## Staging / Diagnostic Presentation Rule

Staging may retain diagnostics internally for engineering verification, but technical diagnostics must not occupy normal consumer hierarchy.

Specifically:

- `INTERNAL` must not appear as a prominent raw status label in normal consumer UI.
- `Recovery diagnostic ...`, parser/import counters, storage counters, duplicate counts, raw backend stage names and similar strings must not be rendered as normal profile/home content.
- If diagnostic exposure is still required for Staging acceptance, it must be behind an explicit debug-only disclosure surface or test-tagged diagnostic section that is visually separated from the consumer experience and excluded from Production builds.
- Customer-facing failure copy remains short and safe; detailed diagnostics stay available to tests/logs/debug surfaces.

## Truth and Safety Contracts

Visual remediation must not weaken any existing truth contract:

- UNKNOWN != ZERO.
- Potential != realized.
- Verified/fresh/eligible offer gating remains required.
- Request != submission != completion != realization.
- No fabricated payment target, due date, provider offer, savings amount or completion state.
- Existing native Gmail readonly authorization remains the only Gmail path.
- Previously verified truth remains visible during partial/failure states where current state allows it.

## Accessibility and RTL

- Hebrew RTL is primary.
- Mixed Hebrew, numbers, currency and email addresses must remain readable.
- Directional icons use AutoMirrored where applicable.
- Minimum touch targets 44–48dp.
- Color is never the only carrier of state.
- Every actionable icon has a content description or semantic label.
- Compact-phone and large-font layouts must not clip or overlap bottom navigation.
- Decorative Lovable motifs are decorative in the accessibility tree unless they are the sole carrier of meaning.

## Visual Verification Strategy

The remediation is not complete merely because unit tests and CI are green.

Required evidence:

1. Screen-level Roborazzi fixtures for Home, Savings, AI, Bills and Profile.
2. At least one compact 360dp RTL fixture for each primary screen or a deterministic representative state proving shared shell/layout behavior.
3. Onboarding, Activity and key empty/error/loading states captured.
4. A source-level design-token guard validating exact palette constants, card radius, screen padding and bottom-nav labels.
5. A diagnostic-visibility guard preventing raw `INTERNAL` / `Recovery diagnostic` strings from ordinary consumer composables.
6. Manual/visual comparison against the frozen Lovable source at `a32abfe5ad2bb6f03a64f65d2ed48327f6c8c65b` before declaring Design Acceptance.

Pixel-identical rendering is not required across web and Android font/rendering engines, but composition, hierarchy, color roles, density, radii, navigation treatment and component relationships must be recognizably the same design system.

## Scope Boundary

Allowed:

- Android Compose UI/layout/theme/components.
- Android visual assets needed for the V3 presentation.
- Presentation-only debug gating for raw diagnostics.
- Screenshot/contract tests and visual fixtures.
- Documentation for the design-remediation pass.

Forbidden:

- backend behavior changes;
- Firebase/Firestore/Functions changes;
- OAuth architecture/scope changes;
- App Check/Play Integrity changes;
- Production IAM/secrets changes;
- signing/release workflow changes;
- Production deployment;
- Google Play actions;
- WebView/React/Capacitor/Lovable runtime integration.

## Acceptance Criteria

The pass is ready for Master Control design review only when:

1. The native five-tab dock visually follows the frozen Lovable shell and labels remain `בית | חיסכון | AI | לתשלום | פרופיל`.
2. Exact frozen palette roles are implemented consistently.
3. Screens no longer read as default Material 3 or debug tooling.
4. Home, Savings, AI, Bills and Profile match Lovable hierarchy/density/component grammar.
5. Raw Staging diagnostics are removed from ordinary consumer hierarchy.
6. Potential/realized/unknown truth contracts remain intact.
7. All primary screen-level visual fixtures exist and pass.
8. RTL, touch targets and compact-device behavior pass.
9. Android unit tests, lint and build pass.
10. Backend/security/operations CI remains green because no non-UI contract was changed.
11. PR #92 remains Draft/Open/Unmerged until Master Control approves design acceptance.
12. Protected main remains unchanged.
