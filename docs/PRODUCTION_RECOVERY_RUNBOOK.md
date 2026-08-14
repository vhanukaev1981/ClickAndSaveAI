# ClickAndSaveAI Production Recovery Runbook

## Purpose

This runbook defines controlled recovery from a verified known-good release identity. It does not execute deployment. **ROLLBACK PLAN EXISTS does not mean ROLLBACK VERIFIED.** Every environment action requires the relevant protected production identity and owner authorization.

A staging recovery result is not production evidence; **staging evidence must never be reported as production evidence**.

## Known-Good Release Identity

Use `operations/release/known-good-manifest.schema.json` for the canonical release identity contract. The repository example is intentionally `EXAMPLE_NOT_PRODUCTION` and cannot be treated as a deployed release record.

A production known-good record must bind, at minimum:

- immutable source SHA;
- Functions source SHA;
- Firestore Rules and index source SHA;
- non-secret production configuration version plus secret-version references;
- Android versionCode/versionName, production signing identity evidence, and artifact digest;
- schema compatibility epoch and supported release window.

Never infer a missing field. If production binding is incomplete, state `PRODUCTION_VERIFICATION_REQUIRED` or `OWNER_ACTION_REQUIRED` and stop.

## Firebase Functions Recovery

Canonical repository strategy: **known-good source redeploy**, not an invented generic rollback command.

1. Identify the exact deployed Functions revisions and current source/release identity.
2. Select a production-verified known-good manifest.
3. Check out its immutable source SHA and verify it locally/CI.
4. Review the Functions delta and data/schema compatibility with writes made by the affected release.
5. Use the governed production deployment workflow to redeploy only the approved Functions surface.
6. Capture new deployed revision identities.
7. Verify health, auth/App Check behavior, Gmail/OAuth, push, provider handoff, and privacy operations as applicable.
8. Do not mark recovery `VERIFIED` until real production evidence is captured.

## Firestore Rules Recovery

Two controlled recovery paths may exist, subject to owner authorization:

- restore a previously verified ruleset from Firebase Rules release history; or
- redeploy the source-controlled known-good `firestore.rules` from the exact immutable release SHA.

Procedure:

1. Capture the current ruleset identity and incident evidence.
2. Resolve the last production-verified ruleset/source SHA.
3. Compare access effects; recovery must not silently broaden direct client access.
4. Apply the approved ruleset through the authorized production path.
5. Record the resulting ruleset identity.
6. Verify expected allow/deny behavior before reporting recovery verified.

The repository currently maintains deny-by-default direct Firestore client access; any recovery that changes that posture requires explicit security review.

## Configuration Recovery

Configuration recovery is identifier-based and must never store secret values in git.

1. Resolve the production-verified known-good configuration version from authorized records.
2. Resolve secret-version references through the production secret manager/control plane; do not print values.
3. Compare current and known-good non-secret configuration identifiers.
4. Apply only the approved configuration binding.
5. Restart/redeploy only components that require the restored binding.
6. Verify Firebase project, OAuth client, Gmail scope, App Check/Play Integrity, and environment isolation before declaring success.

Missing production configuration records are an external blocker, not a reason to substitute staging configuration.

## Android Recovery

Google Play recovery has two distinct effects and must not be overstated.

1. If a staged rollout is active, halt it when appropriate to stop additional rollout exposure. Devices that already installed that build remain affected until a corrective update is available.
2. If platform controls permit halting a fully rolled-out release, use that control only after owner review; it can make the prior version available to eligible users but must not be described as downgrading devices already on the affected build.
3. Prepare a corrective signed production build from reviewed source using a **higher versionCode** than the affected release.
4. Verify production upload signing identity, production Firebase config, production OAuth client, App Check/Play Integrity readiness, artifact digest, and release notes.
5. Publish only through the protected production release process after explicit owner authorization.
6. Observe rollout health before binding the corrective build as a new known-good release.

Never reuse the affected versionCode, staging signing identity, staging Firebase config, or staging OAuth client in production recovery.

## Data Compatibility Verification

Before any server or Android recovery, verify the candidate recovery release can safely coexist with data written by the affected release.

Required checks:

- additive-first schema contract is still satisfied;
- no field meaning has been reused;
- older supported readers tolerate unknown fields;
- required backfills are idempotent and completed before destructive cleanup;
- new-release writes do not require a destructive downgrade;
- at least the current and immediately previous supported client release remain compatible unless a reviewed migration explicitly says otherwise.

If any item is unknown, stop and classify `PRODUCTION_VERIFICATION_REQUIRED`.

## Recovery Planner

The repository planner is plan-only:

```text
node scripts/production-recovery-plan.mjs \
  --manifest <production-known-good-manifest.json> \
  --target <functions|firestore-rules|configuration|android> \
  --mode plan
```

It validates recovery metadata and outputs steps. It intentionally does not invoke Firebase, Google Cloud, Google Play, or deployment commands. A successful planner run proves only repository plan generation.

## Verification Matrix

After recovery, capture real evidence for the changed surface and relevant dependencies:

- Functions: deployed revision identity, invocation/error health, auth/App Check enforcement, affected subsystem checks.
- Firestore Rules: ruleset identity plus expected deny/allow tests.
- Configuration: version identifiers, environment identity, secret reference identity without values.
- Android: versionCode/versionName, signing certificate identity, artifact digest, Play release status, crash/auth/push health.
- Data: compatibility checks and absence of unintended destructive transformation.

## Stop Conditions

Stop immediately when:

- known-good identity is not production-verified;
- the source SHA is not an exact immutable 40-character Git SHA;
- required production signing/Firebase/OAuth/IAM/WIF/GitHub-environment resources are unavailable;
- recovery metadata includes secret values;
- the candidate recovery release cannot safely read data written by the affected release;
- recovery would broaden Firestore access unexpectedly;
- Android versionCode is not strictly higher for the corrective build;
- provider cleanup or account-deletion completion is unconfirmed;
- any operator proposes to use staging identity/configuration as production evidence.

## Evidence State After Repository Verification

Passing repository tests for this runbook, manifest, planner, retention policy, monitoring spec, and schema guard establishes `REPOSITORY_READY` only. Actual Functions/Rules/config/Android recovery remains `PRODUCTION_VERIFICATION_REQUIRED` until exercised under the real production control plane.
