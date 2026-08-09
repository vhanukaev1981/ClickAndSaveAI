# Stream B Acceptance Matrix

This matrix is the execution checklist for the MyFinanda-style customer UX workstream. It intentionally covers presentation and interaction only; Stream A remains the source of truth for backend/data semantics.

| Area | Customer requirement | Stable hook / contract | Automated guard |
|---|---|---|---|
| Dashboard | Primary KPI is verified savings; never fabricate ₪0 savings | `dashboard_savings_hero` | `DashboardProductContractTest`, `CustomerPresentationPolicyTest` |
| Dashboard | Initial source connection appears only while account/source is not connected | `dashboard_initial_connection`, `dashboard_connect_account` | `DashboardProductContractTest` |
| Dashboard | Temporary financial-home failure is recoverable | `dashboard_error_state`, `dashboard_retry_financial_home` | `DashboardProductContractTest`, `CustomerUiSourceGuardTest` |
| Dashboard | Main navigation actions are functional | `dashboard_manage_bills`, `dashboard_manage_savings`, `dashboard_manage_profile` | `CustomerUiSourceGuardTest` |
| Bills | Surface describes spend, not provider/commercial internals | `invoices_screen`, `bills_monthly_overview` | `StreamBFinancialSurfaceContractTest`, `CustomerVisibleCopyGuardTest` |
| Bills | Manual bill add is secondary, explicit and testable | `add_manual_bill`, `manual_bill_provider`, `manual_bill_amount`, `save_manual_bill` | `StreamBFinancialSurfaceContractTest` |
| Bills | Manual categories are deterministic for E2E | `manual_bill_category_*` | `StreamBFinancialSurfaceContractTest` |
| Bills | Delete requires confirmation and visible completion feedback | `delete_bill_*`, `confirm_delete_bill`, `cancel_delete_bill`, `bills_action_feedback` | `StreamBAccessibilityContractTest`, `CustomerUiSourceGuardTest` |
| Savings | Verified offer shows only backend-verified economics | `providers_screen`, `savings_opportunity_*` | `StreamBFinancialSurfaceContractTest`, `CustomerPresentationPolicyTest` |
| Savings | Action starts only for supported in-app provider request | `accept_savings_*`, `savings_action_starting` | `StreamBFinancialSurfaceContractTest` |
| Savings | Exact displayed offer is bound before consent/acceptance | internal `displayedOfferId` / `expectedOfferId` contract | `StreamBConsentPrivacyContractTest` |
| Savings | Contact details require explicit consent | `savings_contact_*`, `savings_contact_consent`, `submit_savings_request` | `StreamBConsentPrivacyContractTest` |
| Savings | Repeated taps cannot create duplicate in-flight UI submissions | `savings_action_submitting` | `StreamBFinancialSurfaceContractTest`, `CustomerUiSourceGuardTest` |
| Savings | Temporary load/action error has customer-safe recovery | `savings_error_state`, `savings_retry_refresh` | `StreamBFinancialSurfaceContractTest` |
| Profile | Account action is explicit and sign-out requires confirmation | `profile_sign_out`, `confirm_profile_sign_out`, `cancel_profile_sign_out` | `ProfilePrivacyProductContractTest`, `StreamBAccessibilityContractTest` |
| Profile | Preferences and privacy are first-class destinations | `open_savings_preferences`, `open_privacy_connections` | `ProfilePrivacyProductContractTest` |
| Privacy | Source revocation lives only under Privacy & Connections | `privacy_connections_screen`, `disconnect_document_source` | `ProfilePrivacyProductContractTest` |
| Privacy | Disconnect requires confirmation | `confirm_disconnect_document_source`, `cancel_disconnect_document_source` | `ProfilePrivacyProductContractTest`, `StreamBAccessibilityContractTest` |
| Preferences | Numeric fields are bounded and machine-testable | `monthly_savings_goal`, `minimum_savings_threshold` | `SettingsProductContractTest` |
| Preferences | Five service preference pickers retain stable hooks | `preference_*` | `SettingsProductContractTest` |
| Preferences | Save gives in-app feedback and does not authorize switching | `save_savings_preferences`, `preferences_saved_confirmation` | `SettingsProductContractTest` |
| Preferences | Unsaved changes cannot be lost silently | `settings_back`, `discard_preferences_changes`, `keep_editing_preferences` | `SettingsProductContractTest` |
| Navigation | Home/Bills/Savings/Profile are RTL-aware and clickable | `nav_dashboard`, `nav_invoices`, `nav_savings`, `nav_profile` | `BottomNavBarTest`, `StreamBAccessibilityContractTest` |
| Accessibility | Bottom nav retains semantic tabs, selected state and 48dp target | navigation hooks above | `BottomNavBarTest`, `FinancialDesignTokensTest`, `StreamBAccessibilityContractTest` |
| Customer copy | No Firebase/App Check/backend/CRM/lead/commission/attribution language | all customer screens | `CustomerVisibleCopyGuardTest`, `CustomerUiSourceGuardTest` |
| Consent | App never claims an automatic provider switch | Savings/Profile/Settings copy | `StreamBConsentPrivacyContractTest` |

## Integration acceptance

Stream B is integration-ready only after:

1. Stream A promotes a validated staging baseline.
2. Stream B is rebased using `docs/STREAM_B_INTEGRATION_PLAN.md` with Stream A semantics preserved in overlapping screens.
3. Full Android and backend CI is green on the rebased HEAD.
4. A staging APK is built from that exact green HEAD.
5. `docs/STREAM_B_DEVICE_E2E.md` passes on a real device.
6. No customer-facing copy exposes implementation or commercial internals and no UI fabricates savings/economic data.
