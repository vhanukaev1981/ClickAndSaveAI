"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");

const {
  PROVIDER_INTEGRATION_STATES,
  DISPATCH_STATES,
  assertPrivacySafeProviderPayload,
  validateProviderIntegrationConfig,
  integrationStatus,
  retryDelaySeconds,
  nextDispatchState,
  validateCommissionReconciliationEvidence,
  validateAdapterContract,
} = require("../src/providerIntegrationFramework");

function config(overrides = {}) {
  return {
    providerId: "provider-a",
    providerName: "Provider A",
    adapterKey: "provider-a-v1",
    contractVersion: "v1",
    enabled: true,
    supportsDeliveryReceipts: true,
    supportsCommissionReconciliation: true,
    retryPolicy: { maxAttempts: 3, baseDelaySeconds: 60, maxDelaySeconds: 600 },
    ...overrides,
  };
}

test("integration config is secret-free and bounded", () => {
  const result = validateProviderIntegrationConfig(config({
    credentialBindingName: "PROVIDER_A_API_KEY",
  }));
  assert.equal(result.providerId, "provider-a");
  assert.equal(result.credentialBindingName, "PROVIDER_A_API_KEY");
  assert.throws(() => validateProviderIntegrationConfig(config({ apiKey: "secret-value" })), /must not be stored/i);
  assert.throws(() => validateProviderIntegrationConfig(config({
    retryPolicy: { maxAttempts: 100, baseDelaySeconds: 60, maxDelaySeconds: 600 },
  })), /maxAttempts/i);
});

test("operator status distinguishes ready configuration from real connectivity", () => {
  const validated = validateProviderIntegrationConfig(config());
  assert.equal(
    integrationStatus(validated, { adapterRegistered: false, healthVerified: false }).state,
    PROVIDER_INTEGRATION_STATES.READY_FOR_ADAPTER
  );
  assert.equal(
    integrationStatus(validated, { adapterRegistered: true, healthVerified: false }).state,
    PROVIDER_INTEGRATION_STATES.DEGRADED
  );
  assert.equal(
    integrationStatus(validated, { adapterRegistered: true, healthVerified: true }).state,
    PROVIDER_INTEGRATION_STATES.ACTUALLY_CONNECTED
  );
});

test("dispatch retry policy is bounded exponential and ends in dead letter", () => {
  assert.equal(retryDelaySeconds(1, config().retryPolicy), 60);
  assert.equal(retryDelaySeconds(2, config().retryPolicy), 120);
  const retry = nextDispatchState(
    { attempts: 0, retryPolicy: config().retryPolicy },
    { submissionAccepted: false, deliveryConfirmed: false, failureCode: "TIMEOUT", retryable: true },
    1_000
  );
  assert.equal(retry.status, DISPATCH_STATES.RETRY_SCHEDULED);
  assert.equal(retry.nextAttemptAtMs, 61_000);

  const dead = nextDispatchState(
    { attempts: 2, retryPolicy: config().retryPolicy },
    { submissionAccepted: false, deliveryConfirmed: false, failureCode: "TIMEOUT", retryable: true },
    1_000
  );
  assert.equal(dead.status, DISPATCH_STATES.DEAD_LETTER);
  assert.equal(dead.nextAttemptAtMs, null);
});

test("delivery confirmation requires explicit provider receipt evidence", () => {
  assert.throws(
    () => nextDispatchState(
      { attempts: 0, retryPolicy: config().retryPolicy },
      { submissionAccepted: true, deliveryConfirmed: true, retryable: false },
      1_000
    ),
    /receipt/i
  );
  const delivered = nextDispatchState(
    { attempts: 0, retryPolicy: config().retryPolicy },
    {
      submissionAccepted: true,
      deliveryConfirmed: true,
      externalReceiptReference: "provider-receipt-1",
      retryable: false,
    },
    1_000
  );
  assert.equal(delivered.status, DISPATCH_STATES.DELIVERED);
});

test("provider payload guard blocks Gmail, spend and commission context", () => {
  assert.equal(assertPrivacySafeProviderPayload({
    contactName: "Test User",
    phone: "0501234567",
    offerId: "offer-1",
  }).offerId, "offer-1");
  for (const key of ["gmailContent", "currentMonthlyCost", "commissionValue"]) {
    assert.throws(() => assertPrivacySafeProviderPayload({ [key]: "forbidden" }), /forbidden/i);
  }
});

test("commission reconciliation accepts only positive externally referenced evidence", () => {
  assert.throws(() => validateCommissionReconciliationEvidence({
    leadId: "lead-1",
    actualCommissionAmount: 180,
  }), /externalReference/i);
  const evidence = validateCommissionReconciliationEvidence({
    leadId: "lead-1",
    actualCommissionAmount: 180.129,
    externalReference: "settlement-123",
    currency: "ils",
  });
  assert.equal(evidence.actualCommissionAmount, 180.13);
  assert.equal(evidence.currency, "ILS");
});

test("adapter contract requires dispatch and validates optional capabilities", () => {
  assert.throws(() => validateAdapterContract({}), /dispatch/i);
  const adapter = validateAdapterContract({
    dispatch: async () => ({ submissionAccepted: false, deliveryConfirmed: false }),
    healthCheck: async () => ({ healthy: true, externalReference: "health-1" }),
  });
  assert.equal(typeof adapter.dispatch, "function");
});
