"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { reconcileSettlement, SETTLEMENT_RESULTS } = require("../src/providerSettlementReconciliation");

function commission(overrides = {}) {
  return {
    commissionId: "commission-1",
    partnerId: "partner-a",
    providerReference: "crm-123",
    state: "CONFIRMED",
    expectedAmount: 75.5,
    confirmedAmount: 75.5,
    currency: "ILS",
    evidenceSource: "PROVIDER_REPORT",
    evidenceObservedAt: "2026-08-08T19:00:00Z",
    ...overrides,
  };
}

function settlement(overrides = {}) {
  return {
    partnerId: "partner-a",
    providerReference: "crm-123",
    externalPaymentId: "payment-1",
    amount: 75.5,
    currency: "ILS",
    source: "BANK_REPORT",
    paidAt: "2026-08-08T20:00:00Z",
    ...overrides,
  };
}

test("exact settlement can mark confirmed commission paid", () => {
  const result = reconcileSettlement(commission(), settlement());
  assert.equal(result.result, SETTLEMENT_RESULTS.MATCHED);
  assert.equal(result.canMarkPaid, true);
  assert.equal(result.delta, 0);
});

test("partial settlement cannot mark paid", () => {
  const result = reconcileSettlement(commission(), settlement({ amount: 50 }));
  assert.equal(result.result, SETTLEMENT_RESULTS.PARTIAL);
  assert.equal(result.canMarkPaid, false);
  assert.equal(result.delta, -25.5);
});

test("overpayment is surfaced instead of silently accepted", () => {
  const result = reconcileSettlement(commission(), settlement({ amount: 80 }));
  assert.equal(result.result, SETTLEMENT_RESULTS.OVERPAID);
  assert.equal(result.canMarkPaid, false);
});

test("partner or currency mismatch never marks paid", () => {
  assert.equal(reconcileSettlement(commission(), settlement({ partnerId: "partner-b" })).result, SETTLEMENT_RESULTS.MISMATCH);
  assert.equal(reconcileSettlement(commission(), settlement({ currency: "USD" })).result, SETTLEMENT_RESULTS.MISMATCH);
});

test("expected commission cannot be settled before confirmation", () => {
  assert.throws(() => reconcileSettlement(commission({ state: "EXPECTED", confirmedAmount: null, evidenceSource: "", evidenceObservedAt: "" }), settlement()), /confirmed commission/);
});
