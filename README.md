# Click & Save AI

Android prototype for organizing household bills and exploring future savings workflows.

## Current status

This repository is **not production-ready**. The current hardened prototype intentionally does not claim that unavailable integrations are working:

- Gmail OAuth is not configured end to end.
- AI requests are not sent from the Android client.
- Provider-switch requests are not sent to providers.
- Price monitoring and push delivery are not active.
- No sample invoices or savings are inserted automatically.

## Security direction

AI provider keys must never be bundled in the APK. A production implementation should use an authenticated backend that handles authorization, redaction, quotas, audit logging and provider credentials.

Local financial data is currently stored in Room. Android backup is disabled while a complete encrypted, user-scoped data design is being implemented.

## Build notes

The repository still needs a committed Gradle Wrapper and a CI workflow before builds are reproducible. Do not ship a release until the remaining audit blockers are resolved.
