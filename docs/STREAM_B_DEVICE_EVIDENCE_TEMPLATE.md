# Stream B — device evidence template

Use this template only after Stream B is rebased onto the validated Stream A baseline and the exact integrated HEAD has a fresh green CI run.

## Build identity

- Stream A baseline SHA:
- Rebasing/integrated Stream B SHA:
- CI run ID / URL:
- CI conclusion:
- Staging APK artifact name:
- APK source SHA:
- APK signing verification result:
- Firebase project verified as `clickandsaveai-staging`:

## Device identity

- Device model:
- Android version:
- App package:
- Install timestamp:

## Same-SHA gate

Before device acceptance, verify all three values are identical:

- CI green SHA:
- APK source SHA:
- Device-tested SHA:

If they differ, stop. Historical CI or an APK built from another commit does not satisfy the release gate.

## Customer journey evidence

Record PASS / FAIL / NOT REACHED and attach a screenshot for every failure.

| Surface | Result | Evidence / notes |
|---|---|---|
| Initial connection / Google authorization | | |
| Dashboard / verified savings hero | | |
| Dashboard retry / under-review states | | |
| Bills list / manual add / category selection | | |
| Bills delete confirmation / feedback | | |
| Savings verified offer presentation | | |
| ACTION_STARTED → explicit consent → exact offer | | |
| Savings submitting / double-submit prevention | | |
| Savings success / error / retry | | |
| Profile sign-out confirmation | | |
| Privacy & Connections / disconnect confirmation | | |
| Savings preferences / save / unsaved-changes protection | | |
| RTL bottom navigation / accessibility | | |

## Truthfulness and privacy checks

- No `₪0` is presented as verified savings.
- No annual savings value is synthesized from monthly savings in Android.
- Verified savings values/icons use the dedicated savings-success green semantic; brand/action controls remain blue.
- No Firebase/App Check/backend/CRM/lead/commission/attribution/internal IDs are visible to the customer.
- Authorization failures do not expose client IDs, OAuth scopes, tokens, server-auth codes, exception classes or raw platform/API error text.
- No automatic provider switch is implied or executed.
- Provider contact happens only after explicit consent for the exact displayed offer.
- Gmail content and the full internal spending picture are not represented as data sent to the provider.

## Final acceptance

- All mandatory Stream B device checks passed: YES / NO
- Open E2E findings:
- Screenshots / recordings attached:
- Accepted by:
- Acceptance timestamp:

Do not mark Stream B READY TO MERGE / DEVICE ACCEPTED until this evidence is complete and matches `docs/STREAM_B_RELEASE_GATE.md` and `docs/STREAM_B_DEVICE_E2E.md`.
