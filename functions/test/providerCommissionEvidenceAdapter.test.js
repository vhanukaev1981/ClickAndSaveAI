"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { COMMISSION_STATES } = require("../src/providerReconciliation");
const { reconcileCommissionFromProviderEvidence } = require("../src/providerCommissionEvidenceAdapter");

function current(overrides = {}) {
  return {
    commissionId: "commission-1",
    partnerId: "provider-a",
    providerReference: "crm-123",
    state: COMMISSION_STATES.EXPECTED,
    expectedAmount: 75.5,
    confirmedAmount: null,
    currency: "ILS",
    ...overrides,
  };
}

function evidence(overrides = {}) {
  return {
    evidenceEventId: "evt-hash-1",
    providerId: "provider-a",
    contractId: "contract-1",
    providerReference: "crm-123",
    kind: "COMMISSION",
    source: "WEBHOOK",
    observedAt: "2026-08-08T20:00:00.000Z",
    amount: 75.5,
    currency: "ILS",
    ...overrides,
  };
}

function correlation(overrides = {}) {
  return {
    matched: true,
    dispatchId: "dispatch-1",
    providerId: "provider-a",
    contractId: "contract-1",
    providerReference: "crm-123",
    evidenceEventId: "evt-hash-1",
    kind: "COMMISSION",
    observedAt: "2026-08-08T20:00:00.000Z",
    ...overrides,
  };
}

test("correlated provider commission evidence confirms expected commission", () => {
  const reconciled = reconcileCommissionFromProviderEvidence(current(), evidence(), correlation());
  assert.equal(reconciled.state, COMMISSION_STATES.CONFIRMED);
  assert.equal(reconciled.confirmedAmount, 75.5);
  assert.equal(reconciled.currency, "ILS");
  assert.equal(reconciled.evidenceSource, "PROVIDER_WEBHOOK");
  assert.equal(reconciled.evidenceObservedAt, "2026-08-08T20:00:00.000Z");
});

test("adapter never marks a commission paid from generic commission evidence", () => {
  const reconciled = reconcileCommissionFromProviderEvidence(current(), evidence(), correlation());
  assert.notEqual(reconciled.state, COMMISSION_STATES.PAID);
});

test("uncorrelated evidence cannot confirm commission", () => {
  assert.throws(() => reconcileCommissionFromProviderEvidence(
    current(),
    evidence(),
    correlation({ matched: false }),
  ), /correlated/);
});

test("non-commission provider evidence cannot confirm commission", () => {
  assert.throws(() => reconcileCommissionFromProviderEvidence(
    current(),
    evidence({ kind: "ACTIVATION" }),
    correlation({ kind: "ACTIVATION" }),
  ), /only COMMISSION/);
});

test("provider reference mismatch cannot cross-attribute commission", () => {
  assert.throws(() => reconcileCommissionFromProviderEvidence(
    current(),
    evidence({ providerReference: "crm-other" }),
    correlation(),
  ), /provider reference/);
});

test("evidence event id must match the correlated event", () => {
  assert.throws(() => reconcileCommissionFromProviderEvidence(
    current(),
    evidence({ evidenceEventId: "evt-other" }),
    correlation(),
  ), /event does not match/);
});

test("currency mismatch cannot silently change commercial accounting", () => {
  assert.throws(() => reconcileCommissionFromProviderEvidence(
    current(),
    evidence({ currency: "USD" }),
    correlation(),
  ), /currency/);
});

test("zero or negative provider commission evidence is rejected", () => {
  assert.throws(() => reconcileCommissionFromProviderEvidence(current(), evidence({ amount: 0 }), correlation()), /positive amount/);
  assert.throws(() => reconcileCommissionFromProviderEvidence(current(), evidence({ amount: -1 }), correlation()), /positive amount/);
});
