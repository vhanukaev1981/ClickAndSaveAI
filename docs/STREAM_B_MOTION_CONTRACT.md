# Stream B — truthful motion contract

This contract implements the product direction from issue #29 without inventing backend progress.

## Principle

Motion communicates a real state change. It never manufactures progress, certainty, savings or conversion.

The UI may render only a stage/evidence state supplied by Core. Stream B does not calculate a completion percentage and does not advance a stage on a timer.

## State language

When Core supplies the corresponding state/evidence, customer copy may use:

- `DETECTED` → `זיהינו`
- `CHECKED` → `בדקנו`
- `VERIFIED` → `אימתנו`
- `STILL_CHECKING` → `אנחנו עדיין בודקים`

The shared implementation is `TruthfulProgressPresentationPolicy`.

## Motion semantics

- Scan/import: restrained Click Blue pulse only while Core reports active scan/import work.
- Detection: an item may appear when the detected result actually arrives.
- Analysis/comparison: active motion only while Core reports analysis/comparison in progress.
- Verification: Blue → Verified Green transition only after verified evidence exists.
- Verified money reveal: may animate after verified savings data arrives; never animate a fabricated zero or inferred amount.
- Provider handoff: visual movement may represent the customer leaving Click&SaveAI for the provider only after explicit customer intent/consent.
- Waiting: pulse only the currently active stage.
- Success: restrained check/reveal only for a state we can prove.

## Timing guidance

Timing is visual guidance, never business-state timing:

- interaction feedback: 150–250ms
- state transition: 300–450ms
- verified money/success reveal: 600–900ms
- subtle active pulse loop: 1.5–2.5s

A timer must never cause a stage transition.

## Forbidden

- fake percentages;
- timer-driven progress advancement;
- robot/mascot AI theatre;
- purple/neon AI visual clichés;
- spinner-only waiting when a truthful named stage is available;
- green success before verified evidence;
- animation as the only status signal;
- claiming activation/conversion because a provider page was merely opened.

## Accessibility

- Respect reduced-motion preferences.
- Every animated state also has visible text/state semantics.
- Removing/reducing animation must not remove status information or actions.

## Integration rule

Until Stream A exposes explicit backend-driven progress/evidence states, Stream B may define presentation contracts and static state copy but must not simulate live progress in onboarding/dashboard.
