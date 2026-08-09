# Stream B Release Gate

Stream B is not accepted by appearance alone. The exact integrated commit must pass the same evidence chain from source to device.

## Gate order

1. Stream A promotes a validated staging baseline.
2. Rebase `agent/ui-myfinanda-polish` onto that exact Stream A baseline using `docs/STREAM_B_INTEGRATION_PLAN.md`.
3. Resolve overlap by ownership: Stream A keeps backend/data semantics; Stream B keeps customer presentation and interaction contracts.
4. Run full Android + backend CI on the rebased Stream B HEAD. The Android unit-test gate must include `StreamBDeviceE2EContractTest` and the other Stream B product/manager contracts before any APK is accepted for device testing.
5. Record the green commit SHA.
6. Build/download the staging APK from that exact green SHA only.
7. Verify the APK targets `clickandsaveai-staging` and uses the registered staging signing certificate when OAuth E2E credentials are configured.
8. Install that exact APK on the real Android device.
9. Execute every item in `docs/STREAM_B_DEVICE_E2E.md`.
10. Record failures as E2E findings; do not silently relax truthfulness, privacy, consent, accessibility or ranking/commercial boundaries.

## Hard acceptance rules

- Historical green CI does not validate a newer HEAD.
- A locally rebuilt APK from a different commit does not satisfy the gate.
- No verified-savings amount may be synthesized in UI.
- Verified savings values/icons must use the dedicated savings-success green semantic; brand/action controls remain on the blue brand palette.
- No provider action may bypass explicit user intent/consent or the exact displayed-offer binding.
- No customer screen may expose implementation/commercial internals.
- Authorization failures must remain customer-safe and must not expose client IDs, scopes, tokens, exception classes or raw platform/API error text.
- No destructive account/privacy/bill action may become single-tap.
- All visible primary CTAs must remain functional and testable.
- Accessibility contracts and RTL navigation must remain intact.

## Evidence to keep with the accepted build

- Stream A baseline SHA
- rebased Stream B SHA
- CI run URL / run ID
- staging APK artifact name
- APK signing verification result
- device model / Android version
- E2E checklist result and screenshots for failures

Only after this evidence chain is complete should Stream B be marked READY TO MERGE / DEVICE ACCEPTED.
