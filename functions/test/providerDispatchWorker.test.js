"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");

const {
  executeProviderDispatchAttempt,
  executeCommissionReconciliation,
} = require("../src/providerDispatchWorker");

function config(overrides = {}) {
  return {
    enabled: true,
    adapterKey: "provider-a-v1",
    lastHealthVerified: true,
    supportsCommissionReconciliation: true,
    retryPolicy: { maxAttempts: 3, baseDelaySeconds: 60, maxDelaySeconds: 600 },
    ...overrides,
  };
}

function queue(overrides = {}) {
  return {
    leadId: "lead-1",
    opportunityId: "opp-1",
    offerId: "offer-1",
    providerName: "Provider A",
    attempts: 0,
    payload: {
      contactName: "Test User",
      phone: "0501234567",
      contactEmail: "test@example.com",
      offerId: "offer-1",
    },
    ...overrides,
  };
}

test("dispatch executes only when adapter exists and health is verified", async () => {
  let calls = 0;
  const adapter = {
    dispatch: async () => {
      calls += 1;
      return {
        submissionAccepted: true,
        deliveryConfirmed: true,
        externalReceiptReference: "receipt-1",
        retryable: false,
      };
    },
  };
  const blocked = await executeProviderDispatchAttempt({
    queueRecord: queue(),
    integrationConfig: config({ lastHealthVerified: false }),
    adapter,
    nowMs: 1_000,
  });
  assert.equal(blocked.executed, false);
  assert.equal(calls, 0);

  const delivered = await executeProviderDispatchAttempt({
    queueRecord: queue(),
    integrationConfig: config(),
    adapter,
    nowMs: 1_000,
  });
  assert.equal(delivered.executed, true);
  assert.equal(delivered.transition.status, "DELIVERED");
  assert.equal(calls, 1);
});

test("retryable adapter failure becomes scheduled retry without fabricated success", async () => {
  const result = await executeProviderDispatchAttempt({
    queueRecord: queue(),
    integrationConfig: config(),
    adapter: {
      dispatch: async () => ({
        submissionAccepted: false,
        deliveryConfirmed: false,
        failureCode: "PROVIDER_TIMEOUT",
        retryable: true,
      }),
    },
    nowMs: 10_000,
  });
  assert.equal(result.transition.status, "RETRY_SCHEDULED");
  assert.equal(result.transition.evidence.deliveryConfirmed, false);
});

test("worker rejects payloads containing forbidden financial or Gmail context", async () => {
  await assert.rejects(
    () => executeProviderDispatchAttempt({
      queueRecord: queue({ payload: { offerId: "offer-1", currentMonthlyCost: 129 } }),
      integrationConfig: config(),
      adapter: { dispatch: async () => ({ submissionAccepted: false, deliveryConfirmed: false }) },
    }),
    /forbidden/i
  );
});

test("commission reconciliation requires adapter capability and authoritative evidence", async () => {
  const missing = await executeCommissionReconciliation({
    lead: { leadId: "lead-1", requestedProvider: "Provider A" },
    integrationConfig: config({ supportsCommissionReconciliation: false }),
    adapter: { dispatch: async () => ({}) },
  });
  assert.equal(missing.executed, false);

  const verified = await executeCommissionReconciliation({
    lead: { leadId: "lead-1", requestedProvider: "Provider A" },
    integrationConfig: config(),
    adapter: {
      dispatch: async () => ({}),
      reconcileCommission: async () => ({
        leadId: "lead-1",
        actualCommissionAmount: 180,
        externalReference: "settlement-1",
        currency: "ILS",
      }),
    },
  });
  assert.equal(verified.executed, true);
  assert.equal(verified.evidence.actualCommissionAmount, 180);
});
