"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { buildPartnerFinanceSummary } = require("../src/providerPartnerFinanceSummary");

function record(overrides = {}) {
  return {
    commissionId: "commission-1",
    partnerId: "partner-a",
    providerReference: "crm-123",
    state: "EXPECTED",
    expectedAmount: 100,
    confirmedAmount: null,
    currency: "ILS",
    evidenceSource: "",
    evidenceObservedAt: "",
    ...overrides,
  };
}

test("summarizes expected confirmed paid and outstanding amounts", () => {
  const summary = buildPartnerFinanceSummary([
    record(),
    record({ commissionId: "commission-2", providerReference: "crm-124", state: "CONFIRMED", confirmedAmount: 80, evidenceSource: "REPORT", evidenceObservedAt: "2026-08-08T20:00:00Z" }),
    record({ commissionId: "commission-3", providerReference: "crm-125", state: "PAID", confirmedAmount: 70, evidenceSource: "SETTLEMENT", evidenceObservedAt: "2026-08-08T21:00:00Z" }),
  ]);
  assert.equal(summary.expectedAmount, 300);
  assert.equal(summary.confirmedAmount, 150);
  assert.equal(summary.paidAmount, 70);
  assert.equal(summary.outstandingConfirmed, 80);
  assert.equal(summary.unconfirmedExpected, 150);
});

test("empty summary is stable", () => {
  assert.deepEqual(buildPartnerFinanceSummary([]), {
    records: 0,
    expectedAmount: 0,
    confirmedAmount: 0,
    paidAmount: 0,
    outstandingConfirmed: 0,
    unconfirmedExpected: 0,
    rejectedCount: 0,
    collectionRate: 0,
  });
});
