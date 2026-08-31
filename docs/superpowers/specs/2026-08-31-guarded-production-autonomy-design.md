# Guarded Production Autonomy Design

## Objective

Separate production automation into independent, source-controlled controllers so Firebase production deployment, Firebase post-deploy health handling, Google Play Internal Testing, and Google Play Production staged rollout cannot implicitly authorize one another and no workflow needs to rewrite another workflow.

## Architecture

1. `production-release.yml` remains the exact-SHA release gate and retains existing bounded Firebase and Internal Testing paths.
2. A dedicated Firebase post-deploy health controller validates durable release evidence, evaluates fail-closed health policy, and records rollback/stop intent without authorizing Google Play publication.
3. A dedicated Google Play Production controller owns staged rollout only. It requires an authorization distinct from Internal Testing, consumes exact-SHA release evidence, and promotes only through `5 -> 20 -> 50 -> 100` after health gates pass.
4. A dedicated owner-only dispatcher may request bounded production operations, but each downstream controller independently verifies repository owner, exact `main` SHA, authorization phrase, and target.
5. No controller self-modifies GitHub Actions files. Workflow files are immutable runtime inputs changed only through normal Git commits/PR review.

## Identity and Safety Boundaries

- GitHub Actions authenticates to Google Cloud with OIDC/WIF and short-lived credentials.
- Production controllers pin repository, repository ID, owner ID, `refs/heads/main`, expected workflow identity, project/package identifiers, and exact 40-character source SHA.
- Firebase production authorization cannot authorize any Google Play track.
- Internal Testing authorization cannot authorize Google Play Production.
- Google Play Production uses a distinct phrase: `PUBLISH_GOOGLE_PLAY_PRODUCTION_STAGED`.
- Production rollout starts at 5% and can only advance to 20%, 50%, then 100% through explicit policy evaluation.
- Missing required telemetry or evidence fails closed.
- Unhealthy staged rollout halts promotion; if provider halt is available the controller halts it, otherwise it records a blocked state and does not advance.
- No workflow may reduce Android `versionCode` as a rollback mechanism.

## Evidence Contract

Every privileged controller preserves or validates:

- exact source SHA
- workflow run ID
- package/project/environment target
- version code/version name where applicable
- artifact hashes where available
- operation type
- rollout percentage for Play Production
- health gate inputs and decision
- final action/outcome

Evidence is retained as GitHub Actions artifacts where practical.

## Controller Boundaries

### Firebase Post-Deploy Health Controller

Consumes Firebase deployment evidence for the exact source SHA. It validates target identity and post-deploy smoke/health output. If health fails, it produces deterministic rollback/last-known-good remediation evidence. It contains no Play Production authorization phrase or production-track API call.

### Google Play Production Controller

Consumes exact-SHA release evidence and requires prior Internal Testing lineage or an explicitly equivalent pre-production proof. It authenticates with the dedicated Play publisher identity and can operate only on `tracks/production`. It enforces the staged sequence `5 -> 20 -> 50 -> 100`, fail-closed health checks, and halt-on-regression semantics.

### Owner-Only Dispatcher

Accepts an issue/label or equivalent repository-native request created by the repository owner, verifies the current exact `main` SHA, and dispatches only the requested bounded controller. Internal Testing dispatch remains unchanged and cannot contain the Production authorization phrase.

## Success Criteria

- PR CI is green with exact tests for controller isolation.
- Existing Internal Testing bridge remains unable to authorize Production.
- Firebase health automation does not reference Play Production authorization or production-track publication.
- Play Production controller cannot run without its distinct authorization phrase and exact-main checks.
- Rollout cannot jump from 0% directly to 100% through the ordinary autonomous path.
- Missing health evidence blocks promotion.
- Temporary/self-modifying helper workflows are absent from the final branch.
