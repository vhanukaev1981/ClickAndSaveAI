# Stream B — P0 North Star gap map

Source: product/UX North Star in issue #29.

This map separates customer-presentation work owned by Stream B from Core/evidence dependencies owned by Stream A. Stream B must not simulate missing backend capabilities.

| P0 area | Stream B status | Dependency / next integration rule |
|---|---|---|
| Trust-first copy / no lead terminology | IMPLEMENTED / GUARDED | Keep customer copy behind `CustomerPresentationPolicy` and source guards. |
| Verified savings truthfulness | IMPLEMENTED / GUARDED | Preserve Core economics; never synthesize annual or zero savings. |
| Semantic colors | PARTIAL | Palette exists; verified savings wiring on overlapping Dashboard/Savings screens is completed during post-E2E rebase while preserving Core action semantics. |
| Truthful progress language | IMPLEMENTED AS PRESENTATION POLICY | `TruthfulProgressPresentationPolicy` renders only Core-supplied states; no timers/percentages. |
| Functional motion | SPECIFIED, NOT SIMULATED | `STREAM_B_MOTION_CONTRACT.md`; wait for explicit Core scan/import/analyze/compare/verify states before live animation. |
| First-run 60-second onboarding | BLOCKED ON CORE STATE CONTRACT + E2E BASELINE | Stream B may define copy/flow after Core exposes truthful first-scan stages. Do not create fake onboarding progress. |
| Dashboard WOW/value hierarchy | IMPLEMENTED / REBASE REQUIRED | Preserve Stream A financial-home truth semantics during conflict resolution. |
| Bills as active utility | PARTIAL | Spend-first UI exists. Due date/status/line items/history require reliable Core metadata. |
| Verified provider payment handoff | BLOCKED ON CORE TRUSTED PAYMENT METADATA | Do not show a Pay CTA until Core supplies a payment candidate verified against trusted provider configuration. Click&SaveAI never stores card details or processes payment in this phase. |
| Savings recommend/connect/refer wording | IMPLEMENTED / GUARDED | Preserve exact offer + ACTION_STARTED + explicit consent; never imply Click&SaveAI executes switching. |
| Smart provider handoff visual | PARTIAL / REBASE REQUIRED | Customer handoff presentation may be polished only around the Core-owned exact-offer/action flow. Do not claim activation after browser/open handoff without evidence. |
| Activity primary area | QUEUED | Issue #29 allows IA evolution; do not add a fifth tab until meaningful Core activity states/data exist and project manager coordinates navigation change. |
| Profile / permissions / privacy | IMPLEMENTED / GUARDED | Revocation under Privacy & Connections; destructive actions confirmed. |
| Reduced-motion accessibility | SPECIFIED | Must be implemented with actual motion work; animation can never be the only status signal. |
| Admin / monetization separation | GUARDED | Operational attribution/commission remains outside consumer UX. |

## Hard blockers before Stream B integration

1. Stream A same-tree staging deployment + real-device E2E correction cycle #2.
2. Rebase Stream B onto the validated Stream A baseline using `STREAM_B_INTEGRATION_PLAN.md`.
3. Resolve overlapping Dashboard/Bills/Profile/Savings files by ownership, preserving Core semantics.
4. Fresh full CI on the exact integrated HEAD.
5. Same-SHA staging APK + real-device E2E + `STREAM_B_DEVICE_EVIDENCE_TEMPLATE.md`.

## Do not build around missing data

Until Core exposes evidence-backed fields, Stream B must not invent:

- scan/analyze/verify percentages;
- due/payment status certainty;
- provider payment URLs;
- realized savings;
- provider activation/conversion;
- fifth-tab activity content;
- payment processing or card storage;
- completed provider switching.
