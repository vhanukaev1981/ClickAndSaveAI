# ClickAndSaveAI Production Operations Runbook

## Purpose

This runbook defines the repository-side operating model for production incidents. It does not authorize production access, create production monitoring resources, or prove production recovery.

## Operational Truth States

Use the narrowest evidence state supported by facts:

- `REPOSITORY_READY`: source-controlled control exists and repository verification passed.
- `SPECIFIED_NOT_ACTIVE`: metric, alert, dashboard, or policy is defined but not instantiated in production.
- `OWNER_ACTION_REQUIRED`: an owner-controlled identity, credential, environment, legal decision, approval, or platform action is missing.
- `PRODUCTION_VERIFICATION_REQUIRED`: the repository control exists but has not been exercised and evidenced in the real production environment.
- `VERIFIED`: a real production action has been executed, observed, and preserved as evidence.

Truth rules are non-negotiable: **LOG EXISTS does not mean MONITORING READY. METRIC SPECIFIED does not mean ALERT ACTIVE.** Repository CI is not production monitoring evidence.

## Incident Severity

- `CRITICAL`: user deletion/privacy integrity risk, production credential/signing compromise, widespread auth failure, confirmed data corruption, or unsafe release behavior.
- `ERROR`: material subsystem outage or repeated failed provider/Gmail/push/recovery operation requiring operator action.
- `WARNING`: degraded behavior, elevated retry rate, partial notification delivery, or recovery indicator that has not yet caused material loss.

Severity assignment is operational triage, not a legal determination.

## Evidence Capture

Before changing production state, preserve where available:

1. incident start time and reporter;
2. exact deployed source SHA and known-good manifest identity;
3. Android versionCode/versionName and signed artifact digest if already verified;
4. Firebase Functions revisions and Firestore Rules ruleset identity;
5. configuration version identifiers and secret-version references without secret values;
6. sanitized structured operational events and relevant control-plane workflow results;
7. user-impact scope using aggregate counts, not raw user content;
8. operator approvals and exact recovery action taken.

Never paste OAuth tokens, authorization codes, refresh/access tokens, private keys, passwords, Gmail message bodies, raw email addresses, or production secrets into incident notes.

## Containment

Choose the smallest reversible containment appropriate to the failing surface:

- Android: halt a staged or fully rolled-out release when the platform permits and exposure reduction is needed; devices already on the affected build may still require a forward-fix release.
- Firebase Functions: stop further deployment, identify affected functions/revisions, and prepare known-good source redeployment.
- Firestore Rules: stop rules changes and identify the last verified ruleset/source SHA.
- Gmail/OAuth: preserve disconnect/retry-required safety semantics; never clear retained cleanup state merely to make an error disappear.
- Push/provider handoff: disable or constrain the failing operational path only through an existing authorized control; never invent delivery/provider receipt evidence.
- Privacy/deletion: fail closed. Do not report deletion complete when external cleanup or destructive data removal is unconfirmed.

## Monitoring and Triage

The canonical monitoring specification is `operations/monitoring/monitoring-spec.json`. It describes intended log-based/control-plane metrics and alert conditions. Until production log metrics, alert policies, notification channels, IAM, and routing are actually created and tested, all entries remain `SPECIFIED_NOT_ACTIVE`.

Primary coverage domains:

- Gmail OAuth connect/disconnect;
- Gmail watch and reconciliation;
- push delivery;
- provider handoff queue;
- privacy/imported-data/account deletion;
- production deployment/recovery control plane.

## Initial Response Sequence

1. Confirm the incident against authoritative evidence.
2. Identify the exact deployed release identity; if unknown, do not guess.
3. Classify affected surfaces and severity.
4. Stop further release activity while identity or compatibility is uncertain.
5. Apply containment without bypassing auth, App Check, provider cleanup, signing, IAM, or deletion safeguards.
6. Select a known-good recovery candidate only from immutable evidence.
7. Use `docs/PRODUCTION_RECOVERY_RUNBOOK.md` for surface-specific recovery.
8. Verify recovery against production evidence before declaring the incident resolved.
9. Preserve an incident record containing decisions, exact SHAs/revisions/rulesets/artifact digests, and residual risks.

## Stop Conditions

Stop recovery/release activity and escalate when any of the following is true:

- deployed source SHA, signing identity, Firebase project, OAuth client, or configuration identity cannot be established;
- the candidate known-good manifest is malformed or not bound to verified production evidence;
- recovery would require secret values from repository/log output;
- schema/data written by the candidate release is not known to be readable by the recovery release;
- Firestore Rules recovery would broaden access without explicit review;
- Gmail provider cleanup is unconfirmed for an account deletion;
- Android corrective artifact does not use the required production signing identity or a valid higher versionCode;
- monitoring evidence is absent but someone proposes reporting `MONITORING READY` or `ALERT ACTIVE`;
- required owner approval is unavailable.

## Post-Recovery Verification

Verify the smallest authoritative checks for the affected surface, then verify cross-surface compatibility. Record evidence state as `PRODUCTION_VERIFICATION_REQUIRED` until the real environment checks complete successfully. A repository test, dry-run plan, staging result, or successful build cannot upgrade the state to `VERIFIED` by itself.

## External Owner Actions

Block 2 does not provide or fabricate:

- Play App Signing/upload signing identity;
- signed production artifact;
- separate production Firebase project/config;
- production OAuth clients or runtime secrets;
- Google restricted-scope verification/security assessment;
- production App Check/Play Integrity enforcement;
- production GCP IAM/WIF identities;
- GitHub production environment;
- real production secrets/variables;
- active Cloud Logging metrics, alert policies, dashboards, or notification channels;
- legal/privacy approval of retention periods.

These remain `OWNER_ACTION_REQUIRED` or `PRODUCTION_VERIFICATION_REQUIRED` until real evidence exists.
