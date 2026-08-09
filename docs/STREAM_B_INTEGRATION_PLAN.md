# Stream B integration plan

Use this plan only after Stream A promotes a validated staging/E2E baseline.

## Integration rule

Stream A remains authoritative for backend contracts, Gmail/Auth behavior, financial calculations, opportunity/ranking semantics, attribution/lifecycle, staging and E2E blockers. Stream B remains authoritative for customer-facing hierarchy, copy, states, RTL/accessibility and visual design.

Do not resolve a conflict by weakening truthfulness, consent, security, privacy or commercial-action gates.

## Known overlapping files

### DashboardScreen.kt
Preserve from Stream A:
- current backend/financial-home contract
- authoritative recurring-spend/count semantics
- connection/sync behavior
- any E2E blocker fixes promoted by Stream A

Reapply from Stream B:
- savings-first hierarchy
- verified-savings-only presentation
- under-review/loading/error/retry states
- initial connection only when not connected
- customer-safe copy
- stable E2E hooks
- FinancialDesignTokens and financial typography

### InvoicesScreen.kt
Preserve from Stream A:
- invoice merge/data behavior
- source/verification semantics
- any parser/sync-driven state required by the current Android contract

Reapply from Stream B:
- bills/spend-first presentation
- no provider-action/lead language
- manual add as secondary action
- add/delete confirmations and visible feedback
- category filters and stable manual-category E2E hooks
- FinancialDesignTokens

### ProfileScreen.kt
Preserve from Stream A:
- real auth/session state
- disconnect mechanics and backend status

Reapply from Stream B:
- no permanent technical source card
- source revocation only under Privacy & Connections
- explicit sign-out/disconnect confirmation
- customer-safe state wording
- E2E hooks and Design Tokens

### ProvidersScreen.kt
Preserve from Stream A:
- exact offer/actionMode semantics
- ACTION_STARTED/accept contract
- lifecycle statuses and backend-returned economics
- commercial-action eligibility

Reapply from Stream B:
- verified offer vs under-review presentation
- VIEW_ONLY honesty
- explicit contact consent
- minimum customer data wording
- starting/submitting/retry/success states
- duplicate UI submission prevention
- stable E2E hooks and Design Tokens

### WORKSTREAMS.md
Do not merge mechanically. Keep the newest project-manager/source-of-truth version from Stream A, then record Stream B integration status there only after integration.

## Low-conflict Stream B files

Prefer taking Stream B versions unless Stream A has intentionally introduced a replacement:
- ui/CustomerPresentationPolicy.kt
- ui/FinancialUiStatePolicy.kt
- ui/components/BottomNavBar.kt
- ui/theme/FinancialDesignTokens.kt
- ui/theme/Color.kt
- ui/theme/Theme.kt
- ui/theme/Type.kt
- SettingsScreen.kt
- Stream B UI/unit/source tests
- docs/STREAM_B_DEVICE_E2E.md

## Required verification after rebase

1. Confirm no protected Stream A backend/data files were changed by conflict resolution.
2. Run backend tests even though Stream B should not change Functions.
3. Run Android unit tests and lint.
4. Assemble staging debug APK with the validated Firebase/signing configuration.
5. Execute `docs/STREAM_B_DEVICE_E2E.md` on the real device.
6. Reject integration if any verified saving becomes inferred/fabricated, a technical/internal term reaches customer UI, a visible CTA is dead, consent disappears, or provider action bypasses the Stream A commercial-action gate.

## Current pre-integration state

The branches are intentionally diverged while Stream A remains on the critical E2E path. Do not rebase early merely to reduce the ahead/behind count; rebase only onto the promoted validated Stream A baseline.
