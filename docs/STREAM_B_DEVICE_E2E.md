# Stream B — real-device E2E acceptance

Run this checklist only after Stream A promotes a validated staging baseline and Stream B is rebased/integrated onto it.

## First-run onboarding / trust
- The first-run journey explains the savings promise before asking for sensitive access.
- Privacy/read-only purpose is explained before Gmail authorization.
- No robot/mascot or AI-theatre screen is used as a substitute for financial value.
- Cancelling or failing Google/Gmail authorization does not visually advance onboarding.
- A live first scan is shown only when Core exposes real scan/import/analyze/compare/verify state; no percentage/countdown/timer simulates progress.
- If Core exposes a progress stage, customer copy uses only truthful state language such as `זיהינו`, `בדקנו`, `אימתנו`, `אנחנו עדיין בודקים` for the corresponding evidence state.
- If first-scan work is not complete, the user can land on a useful dashboard with truthful under-review/still-checking language rather than a fake blocking progress screen.
- First result reveal shows services/bills/spend only where evidence supports them.
- Verified saving is revealed only when verified; absence of verified saving is not presented as `₪0` failure.

## Home
- `dashboard_screen` renders without technical/backend wording.
- Initial source connection appears only when the account/source is not connected (`dashboard_initial_connection`).
- After connection, the permanent technical connection card is absent.
- `dashboard_savings_hero` never shows `₪0` as verified savings.
- A positive verified monthly saving has a positive annual display only when that annual value is returned/verified by the backend; Android never multiplies monthly savings by 12.
- Loading, error and under-review states are mutually clear and customer-facing.
- If `dashboard_error_state` appears, `dashboard_retry_financial_home` is visible and tapping it clears the stale error, shows the loading transition and retries the existing financial-home request.
- `dashboard_manage_bills`, `dashboard_manage_savings`, `dashboard_manage_profile`, recent bills and opportunity cards navigate to the intended destination.

## Truthful progress / motion
- No visible progress percentage is fabricated by Android.
- No timer advances a scan/analyze/verify stage before Core state advances.
- Click Blue motion/pulse appears only while a real active stage is reported.
- Blue → Verified Green transition happens only after verified evidence exists.
- A money/success reveal never animates a fabricated zero or inferred amount.
- Reduced-motion mode preserves the same readable state information without relying on animation.
- A spinner-only waiting state is not used when a meaningful real state label is available.
- Provider/open-page motion never implies activation/conversion/payment completion by itself.

## Initial connection / authorization errors
- Initial sign-in / source authorization remains a customer flow, not a developer/configuration screen.
- If sign-in or document-source authorization fails, the visible message is plain Hebrew product language.
- No visible authorization failure exposes `google_web_client_id`, Firebase/OAuth implementation terminology, `gmail.readonly`, OAuth scopes, server-auth codes, exception class names, tokens or raw Google/API error text.
- Debug/configuration detail may exist in logs only; it is not shown in the product UI.
- Cancelling authorization does not imply that any data was saved or that the source is connected.

## Bills
- `invoices_screen` is a spend/bills surface, not a provider/commercial-internals surface.
- No customer-facing CRM, Firebase, App Check, mailbox scope code, internal ID, attribution/commission term or backend exception is visible.
- `bills_monthly_overview` shows identified spend only; it does not claim unverified savings.
- Manual add (`add_manual_bill`) is secondary and functional.
- `manual_bill_provider` and `manual_bill_amount` accept input, and every manual category hook (`manual_bill_category_electricity`, `manual_bill_category_cellular`, `manual_bill_category_internet`, `manual_bill_category_communications`, `manual_bill_category_insurance`, `manual_bill_category_television`) selects the intended category.
- Saving a valid manual bill closes the dialog and `bills_action_feedback` confirms the bill was added.
- Delete requires explicit confirmation (`confirm_delete_bill` / `cancel_delete_bill`).
- `delete_bill_*` has an accessible delete description.
- Confirming delete removes the bill from the visible list and `bills_action_feedback` confirms removal.
- Cancelling delete leaves the bill unchanged.
- Category filters remain usable in RTL.
- Due date/payment-status/history/line-item fields are shown only when Core supplies reliable metadata; absence is not converted into certainty.

## Bills / provider payment handoff
- A payment CTA is absent unless Core supplies a payment-handoff candidate verified against trusted provider configuration.
- When eligible, the CTA clearly says payment continues at the provider (for example `שלם באתר הספק` / `המשך לתשלום אצל הספק`).
- The screen clearly states that Click&SaveAI does not receive/store card details and does not process the payment.
- The destination is HTTPS and corresponds to the Core-verified official provider URL/domain.
- The app never guesses a payment URL from provider name, email body or search results.
- Opening the provider payment page does not mark the bill paid.
- Payment/completion status changes only when reliable downstream evidence exists.
- If no trusted payment destination exists, no official-payment CTA is fabricated; bill/detail/original-document utility remains available where safe.

## Savings
- `providers_screen` presents opportunities in customer financial language.
- Under-review opportunities show no fabricated saving amount.
- Verified opportunities show the exact verified monthly/annual saving returned for the verified offer.
- Verified savings values/icons use the dedicated savings-success semantic green; brand/action controls remain on the blue brand palette.
- Offers without a supported in-app action remain visible without implying that a direct provider action is available.
- In-app action appears only where the backend provides the supported action mode.
- `accept_savings_*` first enters `savings_action_starting` while the displayed offer is revalidated.
- The action remains bound to the displayed offer before consent and final acceptance.
- The explicit contact form exposes `savings_contact_name`, `savings_contact_phone`, `savings_contact_email` and `savings_contact_consent`.
- `submit_savings_request` is disabled until required contact data and consent are present.
- The consent copy states that mailbox content and the full spending picture are not sent.
- After submission starts, `savings_action_submitting` is visible and all opportunity action buttons are disabled until the request finishes.
- Repeated taps cannot create a second in-flight submission from the same UI state.
- Success shows `savings_action_success`.
- Failure shows customer-safe `savings_error_state` without raw backend text; `savings_retry_refresh` returns the screen to a loading/retry path.

## Provider handoff
- Before handoff, the screen shows the exact provider/offer being acted on and the verified savings/economics returned by Core.
- `ACTION_STARTED` remains before consent; explicit consent remains before final provider request/revalidation.
- The customer can see which contact details will be shared.
- Copy explains that mailbox content and the full internal spending picture are not shared with the provider.
- Copy uses customer intent/value language such as `אני רוצה לחסוך`, `המשך לספק` or equivalent — never internal CRM/lead terminology.
- Viewing/expressing interest does not imply that Click&SaveAI performed a switch, cancellation, contract or payment.
- If only an external provider destination/open event is known after return, UI says only what can be proven; it does not claim activation, conversion or sale.
- Provider handoff motion occurs only after explicit customer intent/consent and is never the only status signal.

## Profile / privacy / preferences
- `profile_screen` contains account, savings goals/preferences and privacy — no permanent technical source-connection card.
- `profile_sign_out` does not sign out immediately; it opens a confirmation dialog.
- `cancel_profile_sign_out` keeps the session active.
- `confirm_profile_sign_out` invokes the existing sign-out action only after explicit confirmation.
- `open_savings_preferences` opens the preferences screen.
- `settings_back` has an accessible back description and returns directly only when there are no unsaved changes.
- After editing any preference without saving, `settings_back` opens the unsaved-changes dialog.
- `keep_editing_preferences` closes the dialog and preserves edits.
- `discard_preferences_changes` exits without saving the edited values.
- `monthly_savings_goal` and `minimum_savings_threshold` accept only bounded numeric input.
- `preference_electricity`, `preference_cellular`, `preference_internet`, `preference_insurance` and `preference_streaming` each open and select a value.
- `save_savings_preferences` persists the current choices without authorizing any provider switch.
- `preferences_saved_confirmation` appears after save and disappears logically once the edited values no longer match the saved snapshot.
- `open_privacy_connections` is the only normal customer route to connection revocation.
- `privacy_back` has an accessible back description.
- `disconnect_document_source` appears only when a source is connected.
- Tapping `disconnect_document_source` does not disconnect immediately; it opens a clear confirmation dialog.
- `cancel_disconnect_document_source` leaves the source connected and closes the dialog.
- `confirm_disconnect_document_source` starts the existing disconnect action and the UI returns to a non-connected state when it completes.
- The disconnect dialog clearly explains that new documents stop syncing until reconnection while already identified bills remain available.
- Preferences do not imply an automatic provider switch.

## Navigation / accessibility
- Bottom navigation order and layout are RTL-aware.
- Home/Bills/Savings/Profile are all visible and clickable.
- Selected state matches the active destination.
- Every destination exposes a customer-facing accessibility description.
- Every bottom navigation destination keeps at least the shared 48dp minimum touch target.
- Bottom navigation items retain tab semantics and selected-state semantics.
- Destructive actions remain confirmable/cancellable and expose stable E2E hooks.
- Do not add a fifth Activity tab until meaningful Core activity states/data exist and the navigation change is coordinated.

## Acceptance rule
A Stream B build is accepted only if the app feels like one coherent trust-first financial product: spend is shown as spend, verified savings as verified savings, under-review states do not invent money, progress/motion reflects only real Core state, every visible CTA works, retry paths recover from temporary UI errors, destructive account/privacy/bill actions require confirmation, in-flight savings actions cannot be double-submitted, unsaved edits are protected, provider/payment handoffs state their boundaries clearly, accessibility contracts remain intact, authorization failures remain customer-safe, and implementation/commercial terminology remains behind the scenes.

See `docs/STREAM_B_ACCEPTANCE_MATRIX.md` for the requirement → hook → automated-guard mapping, `docs/STREAM_B_INTEGRATION_PLAN.md` for rebase/integration rules, `docs/STREAM_B_RELEASE_GATE.md` for same-SHA CI/APK/device acceptance, and the P0 onboarding/handoff/payment contracts for issue #29 boundaries.
