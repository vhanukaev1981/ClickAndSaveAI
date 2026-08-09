# Stream B — real-device E2E acceptance

Run this checklist only after Stream A promotes a validated staging baseline and Stream B is rebased/integrated onto it.

## Home
- `dashboard_screen` renders without technical/backend wording.
- Initial source connection appears only when the account/source is not connected (`dashboard_initial_connection`).
- After connection, the permanent technical connection card is absent.
- `dashboard_savings_hero` never shows `₪0` as verified savings.
- A positive verified monthly saving has a positive annual display.
- Loading, error and under-review states are mutually clear and customer-facing.
- `dashboard_manage_bills`, `dashboard_manage_savings`, `dashboard_manage_profile`, recent bills and opportunity cards navigate to the intended destination.

## Bills
- `invoices_screen` is a spend/bills surface, not a provider-lead surface.
- No customer-facing `lead`, CRM, Firebase, App Check, Gmail scope code, internal ID or backend exception is visible.
- `bills_monthly_overview` shows identified spend only; it does not claim unverified savings.
- Manual add (`add_manual_bill`) is secondary and functional.
- Delete requires explicit confirmation (`confirm_delete_bill` / `cancel_delete_bill`).
- Category filters remain usable in RTL.

## Savings
- `providers_screen` presents opportunities in customer financial language.
- Under-review opportunities show no fabricated saving amount.
- Verified opportunities show the exact verified monthly/annual saving.
- VIEW_ONLY offers remain visible without implying a direct provider action is available.
- In-app action appears only where the backend provides the supported action mode.
- `accept_savings_*` opens the explicit consent form.
- `submit_savings_request` is disabled until required contact data and consent are present.
- The consent copy states that mailbox content and the full spending picture are not sent.
- Raw backend errors never appear; `savings_error_state` uses generic customer-safe wording.

## Profile / privacy / preferences
- `profile_screen` contains account, savings goals/preferences and privacy — no permanent technical Gmail card.
- `open_savings_preferences` opens the preferences screen and saving is functional.
- `open_privacy_connections` is the only normal customer route to connection revocation.
- `disconnect_document_source` appears only when a source is connected.
- Preferences do not imply an automatic provider switch.

## Navigation / accessibility
- Bottom navigation order and layout are RTL-aware.
- Home/Bills/Savings/Profile are all visible and clickable.
- Selected state matches the active destination.
- Accessibility descriptions exist for every bottom navigation destination.

## Acceptance rule
A Stream B build is accepted only if the app feels like one coherent financial product: spend is shown as spend, verified savings as verified savings, under-review states do not invent money, every visible CTA works, and implementation/business terminology remains behind the scenes.
