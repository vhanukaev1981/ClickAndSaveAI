# Stream B — P0 onboarding contract

Source: issue #29 trust-first product North Star.

## Goal

The first 60 seconds should build trust before asking for sensitive access and should reveal value only from real data. Stream B owns the customer flow/copy; Stream A owns authentication, Gmail permissions, scan/import/analyze/verify state and financial evidence.

## Required sequence

1. Premium Click&SaveAI splash/brand moment — no robot/mascot or AI theatre.
2. Clear promise: Click&SaveAI looks for recurring household savings opportunities.
3. Short explanation of how it works: connect once, identify bills/services, keep checking, show savings only when verified.
4. Privacy explanation before permission: Gmail is read-only; explain purpose and what is not shared with providers.
5. Google sign-in.
6. Explain Gmail read-only purpose before opening Google authorization.
7. First scan/import experience driven only by Core-provided states.
8. First result reveal: services/bills/spend identified only where evidence supports them.
9. If a verified saving exists, reveal it with Verified Green. If not, show continued monitoring — never ₪0 as failure.
10. Land on a useful dashboard populated by the same verified/observed data.

## Truthful progress

During step 7, Stream B may render only explicit Core states through `TruthfulProgressPresentationPolicy`:

- `DETECTED` → `זיהינו`
- `CHECKED` → `בדקנו`
- `VERIFIED` → `אימתנו`
- `STILL_CHECKING` → `אנחנו עדיין בודקים`

No percentage, countdown or timer may advance the state. Until Core exposes these states, the onboarding UI must not simulate a staged live scan.

## Permission/trust copy

Before Gmail authorization, customer language must make clear:

- access is read-only;
- the purpose is identifying bills/services and savings opportunities;
- Click&SaveAI does not send mailbox content or the full spending picture to providers;
- connecting does not authorize a provider switch, payment, contract or cancellation;
- technical OAuth/client/scope details remain hidden from customer UI.

## Completion rules

Onboarding is complete only when the actual connection/authentication state says so. Cancelling or failing authorization must not visually advance the journey.

If first-scan data is still processing, land on a useful dashboard with truthful `still checking`/under-review language rather than blocking the customer behind a fake progress screen.

## Accessibility

- Motion is optional and never the only status signal.
- Respect reduced-motion preferences.
- Permission explanations and consent actions remain readable and actionable without animation.

## Blocked until Core/E2E integration

Actual live onboarding stage wiring waits for:

1. Stream A E2E correction cycle #2 on the locked same-tree staging build.
2. Core exposure of explicit scan/import/analyze/compare/verify evidence states.
3. Stream B rebase onto the validated Stream A baseline.

The contract may be implemented visually only after those dependencies are real; do not fake them in Stream B.
