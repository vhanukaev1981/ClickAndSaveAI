# ClickAndSaveAI Production Schema Compatibility

## Canonical Policy

Machine-readable policy: `operations/schema/schema-compatibility.json`

Compatibility epoch: `v1`

Minimum supported release window: current release plus the immediately previous supported release (`2` releases minimum).

## Rules

1. Schema evolution is additive-first.
2. An existing field must never be reused with a new semantic meaning.
3. Readers must tolerate unknown additive fields where the storage/API format permits it.
4. Destructive removal is fail-closed by default.
5. A destructive change requires a documented deprecation window, explicit compatibility review, a migration/backfill plan, and a known-good recovery assessment.
6. Backfill/migration must occur before destructive cleanup.
7. Recovery code must be able to read data written by the candidate release; recovery must not depend on destructive data downgrade.

## Required Migration Order

1. deploy backward-compatible readers;
2. add fields/collections or new optional representations;
3. perform idempotent backfill if required;
4. verify current and previous supported clients/functions;
5. stop deprecated writes only after the supported-release window;
6. remove deprecated data only after explicit review and recovery analysis.

## Recovery Constraint

A server or Android release must not be selected as known-good recovery merely because its source compiled previously. If the affected release wrote data that the recovery candidate cannot safely read, recovery stops until a compatible forward-fix or migration path is available.

## Verification State

The policy and repository guard can be `REPOSITORY_READY`. Real production compatibility for a particular release remains `PRODUCTION_VERIFICATION_REQUIRED` until the release manifest, deployed data behavior, and supported clients are verified against the real environment.
