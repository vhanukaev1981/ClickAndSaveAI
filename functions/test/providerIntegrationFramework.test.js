"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  CONNECTION_STATES,
  DISPATCH_OUTCOMES,
  normalizeProviderConfig,
  assertDispatchAllowed,
  normalizeAdapterResult,
  nextRetry,
  normalizeLifecycleEvidence,
} = require("../src/providerIntegrationFramework");

function config(overrides = {}) {
  return {
    providerId: "provider-a",
    adapterKey: "provider-a-v1",
    enabled: true,
    adapterImplemented: false,
    credentialsConfigured: false,
    connectionVerified: false,
    ...overrides,
  };
}

test("provider remains READY_FOR_ADAPTER until adapter, credentials and connection are all verified", () => {
  assert.equal(normalizeProviderConfig(config()).integrationState, CONNECTION_STATES.READY_FOR_ADAPTER);
  assert.equal(normalizeProviderConfig(config({ adapterImplemented: true })).integrationState, CONNECTION_STATES.READY_FOR_ADAPTER);
  assert.equal(normalizeProviderConfig(config({ adapterImplemented: true, credentialsConfigured: true })).integrationState, CONNECTION_STATES.READY_FOR_ADAPTER);
  assert.equal(normalizeProviderConfig(config({
    adapterImplemented: true,
    credentialsConfigured: true,
    connectionVerified: true,
  })).integrationState, CONNECTION_STATES.ACTUALLY_CONNECTED);
});

test("disabled provider cannot be treated as connected", () => {
  const normalized = normalizeProviderConfig(config({
    enabled: false,
    adapterImplemented: true,
    credentialsConfigured: true,
    connectionVerified: true,
  }));
  assert.equal(normalized.integrationState, CONNECTION_STATES.DISABLED);
});

test("dispatch is rejected unless provider is ACTUALLY_CONNECTED", () => {
  assert.throws(() => assertDispatchAllowed(config()), /not actually connected/);
  assert.doesNotThrow(() => assertDispatchAllowed(config({
    adapterImplemented: true,
    credentialsConfigured: true,
    connectionVerified: true,
  })));
});

test("acknowledged dispatch requires provider reference evidence", () => {
  assert.throws(() => normalizeAdapterResult({ outcome: DISPATCH_OUTCOMES.ACKNOWLEDGED }), /providerReference/);
  assert.deepEqual(normalizeAdapterResult({
    outcome: DISPATCH_OUTCOMES.ACKNOWLEDGED,
    providerReference: "crm-123",
    evidenceType: "CRM_ACK",
  }), {
    outcome: DISPATCH_OUTCOMES.ACKNOWLEDGED,
    providerReference: "crm-123",
    retryAfterMs: 0,
    evidenceType: "CRM_ACK",
  });
});

test("retry policy uses bounded exponential backoff and dead letters at the limit", () => {
  assert.deepEqual(nextRetry(1), { shouldRetry: true, deadLetter: false, delayMs: 30_000 });
  assert.deepEqual(nextRetry(2, 120_000), { shouldRetry: true, deadLetter: false, delayMs: 120_000 });
  assert.deepEqual(nextRetry(5), { shouldRetry: false, deadLetter: true, delayMs: 0 });
});

test("provider lifecycle advancement requires attributable external evidence", () => {
  const evidence = normalizeLifecycleEvidence({
    stage: "ACTIVATED",
    providerReference: "activation-789",
    evidenceSource: "PROVIDER_POSTBACK",
    observedAt: "2026-08-08T19:00:00Z",
  });
  assert.equal(evidence.stage, "ACTIVATED");
  assert.equal(evidence.providerReference, "activation-789");
  assert.equal(evidence.evidenceSource, "PROVIDER_POSTBACK");
});

test("lifecycle evidence requires a valid observedAt timestamp", () => {
  assert.throws(() => normalizeLifecycleEvidence({
    stage: "ACTIVATED",
    providerReference: "activation-789",
    evidenceSource: "PROVIDER_POSTBACK",
    observedAt: "not-a-timestamp",
  }), /valid timestamp/);
});

test("commission confirmation requires explicit positive amount evidence", () => {
  assert.throws(() => normalizeLifecycleEvidence({
    stage: "COMMISSION_CONFIRMED",
    providerReference: "commission-1",
    evidenceSource: "PROVIDER_REPORT",
    observedAt: "2026-08-08T19:00:00Z",
  }), /positive amount/);

  assert.throws(() => normalizeLifecycleEvidence({
    stage: "COMMISSION_CONFIRMED",
    providerReference: "commission-1",
    evidenceSource: "PROVIDER_REPORT",
    observedAt: "2026-08-08T19:00:00Z",
    amount: 0,
  }), /positive amount/);

  const evidence = normalizeLifecycleEvidence({
    stage: "COMMISSION_CONFIRMED",
    providerReference: "commission-1",
    evidenceSource: "PROVIDER_REPORT",
    observedAt: "2026-08-08T19:00:00Z",
    amount: 75.5,
    currency: "ils",
  });
  assert.equal(evidence.amount, 75.5);
  assert.equal(evidence.currency, "ILS");
});
