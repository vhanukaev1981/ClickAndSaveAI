# Provider / Commerce Integration Framework

## Purpose

This framework is the canonical boundary between Click&SaveAI's verified commerce lifecycle and future real provider/CRM transports.

It deliberately does **not** claim any provider is connected merely because configuration exists.

## Truth states

- `DISABLED` — provider integration configuration exists but is disabled.
- `READY_FOR_ADAPTER` — configuration is valid, but there is no authorized runtime adapter registered in source.
- `DEGRADED` — an adapter is registered, but current provider health/authority is not verified.
- `ACTUALLY_CONNECTED` — an authorized runtime adapter is registered and its health check returned authoritative external evidence.

No operator/config field can directly set `ACTUALLY_CONNECTED`.

## Configuration

Provider integration configuration is stored without secret values. It may reference a credential binding name, but API keys, access tokens, passwords, client secrets and other secret values are rejected.

Configuration includes:
- provider ID/name;
- adapter key and contract version;
- optional credential binding name;
- delivery-receipt and commission-reconciliation capabilities;
- bounded exponential retry policy.

## Adapter contract

A runtime adapter must implement:

```js
{
  dispatch(request) => Promise<{
    submissionAccepted: boolean,
    deliveryConfirmed: boolean,
    externalReceiptReference?: string,
    failureCode?: string,
    retryable?: boolean
  }>,
  healthCheck?() => Promise<{
    healthy: boolean,
    externalReference?: string
  }>,
  reconcileCommission?(request) => Promise<{
    leadId: string,
    actualCommissionAmount: number,
    externalReference: string,
    currency: string
  }>
}
```

A confirmed delivery requires authoritative provider receipt evidence.

Commission reconciliation requires a positive actual amount and external settlement reference.

## Dispatch retry / dead-letter

The dispatch worker contract uses bounded exponential retry. A retryable failed attempt becomes `RETRY_SCHEDULED` while attempts remain. When the attempt ceiling is reached, it becomes `DEAD_LETTER`. A successful delivery becomes `DELIVERED` only with explicit delivery evidence.

No retry path may fabricate provider acknowledgement.

## Privacy boundary

Provider payloads are checked before adapter dispatch. Raw Gmail content, raw PDF content, current-spend context and commission fields are forbidden from provider-facing payloads.

Existing `providerDispatchQueue`, lead truth states and commerce events remain the source of internal lifecycle evidence.

## Current runtime state

The production registry intentionally ships with **zero live provider adapters**. Real connectivity must be introduced explicitly in source for a named provider, with authorized credentials outside source control, health evidence, tests and normal protected-branch review.

Until then, provider integrations remain `READY_FOR_ADAPTER`, not `ACTUALLY_CONNECTED`.
