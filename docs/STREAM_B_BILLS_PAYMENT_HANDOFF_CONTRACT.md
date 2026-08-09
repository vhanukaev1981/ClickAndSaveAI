# Stream B — bills payment handoff contract

Source: issue #29 payment boundary.

## Product boundary

Click&SaveAI does not process customer payments and does not store card details in the current product phase.

A `Pay` action means a trusted handoff to the provider's official payment channel. It is not an in-app payment flow.

## Eligibility for a payment CTA

Stream B may render a payment CTA only when Core supplies a payment-handoff candidate that is explicitly verified against trusted provider configuration.

Required evidence from Core before presentation:

- provider identity;
- official payment URL;
- URL/domain verification result against trusted provider configuration;
- bill/source association where reliable;
- customer-safe validity/status metadata if available.

If verification is absent, stale, failed or ambiguous, do not present the destination as an official payment CTA.

## Customer presentation

When eligible, payment handoff copy must state clearly:

- payment is completed directly with the provider;
- Click&SaveAI does not receive/store card details;
- opening the provider destination does not prove that payment completed;
- the customer should verify amount/provider before completing payment at the provider.

Suggested value language:

- `שלם באתר הספק`
- `המשך לתשלום אצל הספק`

Do not use wording that implies Click&SaveAI charges the customer.

## Fallback

If no trusted payment destination exists:

- keep the bill/detail utility available;
- where safe and supported, allow access to the original bill/document source;
- do not fabricate or guess a provider URL from provider name, email body or search result.

## Return state

After opening the provider payment destination:

- at most record/display that the provider payment page was opened if that event is known;
- never mark the bill paid solely because the URL was opened;
- payment/completion status may change only when reliable downstream evidence exists.

## Security and privacy

- Use HTTPS provider destinations only when Core marks them verified.
- Do not append Gmail content, full spending context, internal IDs, commission data or credentials to the customer-facing payment URL.
- No card number, CVV, expiry, wallet token or payment credential is collected/stored by Click&SaveAI in this phase.

## Ownership

Stream A/Core owns trusted provider configuration, URL/domain verification, bill/payment metadata and any downstream evidence.

Stream B owns only the customer-facing bill/payment-handoff presentation and may not create its own provider/payment URLs.
