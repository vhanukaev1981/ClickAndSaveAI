"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  COMMISSION_STATES,
  normalizeCommissionRecord,
  reconcileCommission,
  buildPartnerMetrics,
} = require("../src/providerReconciliation");

function expected(overrides = {}) {
  return {
    commissionId: "c-1",
    partnerId: "partner-a",
    providerReference: "provider-ref-1",
    state: COMMISSION_STATES.EXPECTED,
    expectedAmount: 100,
    currency: "ILS",
    ...overrides,
  };
}

test("expected commission does not require fabricated provider evidence", () => {
  const record = normalizeCommissionRecord(expected());
  assert.equal(record.state, COMMISSION_STATES.EXPECTED);
  assert.equal(record.confirmedAmount, null);
});

test("confirmed commission requires provider evidence and confirmed amount", () => {
  assert.throws(() => normalizeCommissionRecord(expected({
    state: COMMISSION_STATES.CONFIRMED,
  })), /evidence/);

  assert.throws(() => normalizeCommissionRecord(expected({
    state: COMMISSION_STATES.CONFIRMED,
    evidenceSource: "PARTNER_REPORT",
    evidenceObservedAt: "2026-08-08T20:00:00Z",
  })), /confirmedAmount/);
});

test("reconciliation upgrades expected commission only from explicit evidence", () => {
  const result = reconcileCommission(expected(), {
    state: COMMISSION_STATES.CONFIRMED,
    confirmedAmount: 85.5,
    evidenceSource: "PARTNER_REPORT",
    evidenceObservedAt: "2026-08-08T20:00:00Z",
  });
  assert.equal(result.state, COMMISSION_STATES.CONFIRMED);
  assert.equal(result.confirmedAmount, 85.5);
});

test("paid commission cannot be downgraded", () => {
  const paid = expected({
    state: COMMISSION_STATES.PAID,
    confirmedAmount: 90,
    evidenceSource: "BANK_SETTLEMENT_REPORT",
    evidenceObservedAt: "2026-08-08T20:00:00Z",
  });
  assert.throws(() => reconcileCommission(paid, {
    state: COMMISSION_STATES.REJECTED,
    evidenceSource: "PARTNER_REPORT",
    evidenceObservedAt: "2026-08-09T20:00:00Z",
  }), /cannot be downgraded/);
});

test("partner metrics contain aggregate commercial numbers only", () => {
  const metrics = buildPartnerMetrics([
    expected({ commissionId: "1", expectedAmount: 100 }),
    expected({
      commissionId: "2",
      state: COMMISSION_STATES.CONFIRMED,
      expectedAmount: 120,
      confirmedAmount: 110,
      evidenceSource: "PARTNER_REPORT",
      evidenceObservedAt: "2026-08-08T20:00:00Z",
    }),
    expected({
      commissionId: "3",
      state: COMMISSION_STATES.PAID,
      expectedAmount: 80,
      confirmedAmount: 80,
      evidenceSource: "BANK_SETTLEMENT_REPORT",
      evidenceObservedAt: "2026-08-08T20:00:00Z",
    }),
    expected({
      commissionId: "4",
      state: COMMISSION_STATES.REJECTED,
      expectedAmount: 50,
      evidenceSource: "PARTNER_REPORT",
      evidenceObservedAt: "2026-08-08T20:00:00Z",
    }),
  ]);
  assert.deepEqual(metrics, {
    records: 4,
    expectedAmount: 350,
    confirmedAmount: 190,
    paidAmount: 80,
    rejectedCount: 1,
  });
});
