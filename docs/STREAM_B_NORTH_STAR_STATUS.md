# Stream B — Issue #29 North Star status

This file is the shared implementation checkpoint for the customer-experience portion of issue #29.

## Implemented / guarded in Stream B

- Trust-first financial-product presentation.
- No consumer-facing lead/CRM/commission/attribution terminology.
- No robot/mascot AI theatre.
- Verified savings are never synthesized from missing evidence and ₪0 is never presented as a verified saving.
- Truthful progress presentation vocabulary exists for Core-supplied states only: detected / checked / verified / still checking.
- Android presentation does not calculate fake progress percentages or auto-advance stages on timers.
- Functional-motion contract and reduced-motion requirements are documented.
- Dedicated verified-savings green semantic exists; brand/action identity remains blue.
- Privacy is visible at the moment of provider consent: only approved contact data + exact offer are sent; mailbox content and the full spending picture are not sent.
- Privacy screen explicitly states that Click&SaveAI does not itself execute provider switching, payment or service cancellation.
- Provider handoff contract preserves exact displayed offer, ACTION_STARTED, explicit consent and revalidation before handoff.
- Provider outcome copy must not claim activation/conversion/sale without downstream evidence.
- Bills payment-handoff contract permits a CTA only for a Core-verified official provider destination; Click&SaveAI does not store card data or process payment.
- First-run trust/onboarding contract is defined without simulating scan stages that Core has not exposed.
- Same-SHA CI -> APK -> real-device evidence chain is mandatory.

## Waiting on Stream A / Core evidence

- Same-tree staging deployment and device E2E correction cycle #2 for locked Core baseline `ac2105098d698df06159f929f41595f91505c855`.
- Explicit evidence-backed scan/import/analyze/compare/verify state contract for live onboarding/dashboard motion.
- Reliable bill due/status/history/line-item metadata before those states are presented as facts.
- Trusted provider payment URL/domain verification metadata before any Pay CTA is rendered.
- Meaningful activity-state data before adding a fifth Activity primary tab.
- Core-owned customer-safe authorization copy in `MainActivity.kt`.

## Integration trigger

Do not rebase Stream B merely because Core CI is green. Rebase only after the locked Core tree is deployed to staging and the corresponding signed artifact completes device E2E correction cycle #2. During conflict resolution, preserve Core financial/action semantics and Stream B customer presentation contracts.
