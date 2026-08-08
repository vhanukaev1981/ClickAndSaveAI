"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { applyVerifiedSettlementToCommission } = require("../src/providerPaidTransitionGate");

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

test("exact verified settlement advances confirmed commission to paid", () => {
  const result = applyVerifiedSettlementToCommission(commission(), settlement());
  assert.equal(result.applied, true);
  assert.equal(result.commission.state, "PAID");
  assert.equal(result.commission.evidenceSource, "SETTLEMENT_EVIDENCE");
  assert.equal(result.settlementEvidenceId.length, 64);
});

test("partial settlement cannot advance to paid", () => {
  const result = applyVerifiedSettlementToCommission(commission(), settlement({ amount: 50 }));
  assert.equal(result.applied, false);
  assert.equal(result.commission.state, "CONFIRMED");
});

test("expected commission cannot bypass confirmation", () => {
  assert.throws(() => applyVerifiedSettlementToCommission(commission({ state: "EXPECTED", confirmedAmount: null, evidenceSource: "", evidenceObservedAt: "" }), settlement()), /confirmed commission/);
});

test("already paid commission remains idempotent", () => {
  const result = applyVerifiedSettlementToCommission(commission({ state: "PAID" }), settlement());
  assert.equal(result.applied, false);
  assert.equal(result.commission.state, "PAID");
});
