# Stream B — provider handoff contract

Source: issue #29 and the locked Stream A commercial-action invariants.

## Customer promise

A provider handoff should feel intentional and trustworthy. Click&SaveAI helps the customer understand and choose a verified saving opportunity, then connects/refers the customer to the provider when an attributable path exists. Click&SaveAI does not perform the provider switch itself.

## Required handoff sequence

1. Show the current service/cost context only where evidence supports it.
2. Show the exact verified alternative the customer is choosing.
3. Show verified monthly saving and verified annual/first-year economics exactly as returned by Core; never synthesize annual savings in Android.
4. Show a short verification summary: what was checked and what is still unknown.
5. Record customer intent for the exact displayed offer (`ACTION_STARTED`) before opening the consent step.
6. Ask explicit consent for the provider-contact handoff and show what customer data will be shared.
7. Revalidate the exact offer through the Core-owned acceptance flow.
8. Only after successful intent/consent/revalidation may the customer be handed off/referred to the provider destination or provider process supported by Core.

## Customer-facing language

Prefer value/intent language such as:

- `אני רוצה לחסוך`
- `בחרתי בהצעה הזו`
- `העבר את פרטי הקשר שלי לספק`
- `המשך לספק`

Do not expose CRM/lead/commission/attribution terminology to the customer.

Do not say that Click&SaveAI switched, activated, enrolled, cancelled or completed a contract unless that outcome is actually proven and the product phase supports it.

## Data transparency before handoff

Before explicit consent, show:

- provider identity;
- exact offer/economics being acted on;
- contact details that will be shared;
- a clear statement that mailbox content and the full internal spending picture are not sent;
- a clear statement that viewing/expressing interest alone does not execute a service/payment change.

## Return / outcome states

After the customer leaves/reaches a provider destination:

- if only an open/reach event is known, say only that the provider destination was opened/reached;
- do not claim activation/conversion/sale without provider evidence;
- do not claim commission success in consumer UI;
- continue monitoring the household expense independently of commercial outcome.

## Visual language

- current state: neutral/deep navy;
- active handoff/intelligence: Click Blue;
- verified saving: Verified Green;
- attention/eligibility still checking: Warm Amber;
- failure/action required: Red only when genuine.

Any handoff motion is functional: it may visually move from Click&SaveAI to provider only after explicit intent/consent. Motion never substitutes for the textual status.

## Core ownership preserved

Stream A remains authoritative for:

- exact offer ID;
- action mode / commercial eligibility;
- `ACTION_STARTED` recording;
- acceptance-time revalidation;
- provider dispatch/referral mechanics;
- attribution/lifecycle/evidence.

Stream B may polish presentation around those contracts but must not bypass or reimplement them.
