"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  normalizeSettlementEvidence,
  dedupeSettlementEvidence,
} = require("../src/providerSettlementEvidence");

function sample(overrides = {}) {
  return {
    partnerId: "partner-a",
    providerReference: "crm-123",
    externalPaymentId: "payment-1",
    amount: 75.5,
    currency: "ils",
    source: "BANK_REPORT",
    paidAt: "2026-08-08T20:00:00Z",
    ...overrides,
  };
}

test("normalizes settlement evidence and derives stable id", () => {
  const first = normalizeSettlementEvidence(sample());
  const second = normalizeSettlementEvidence(sample());
  assert.equal(first.currency, "ILS");
  assert.equal(first.settlementEvidenceId, second.settlementEvidenceId);
  assert.equal(first.settlementEvidenceId.length, 64);
});

test("rejects unsupported settlement source", () => {
  assert.throws(() => normalizeSettlementEvidence(sample({ source: "INTERNAL_GUESS" })), /unsupported settlement source/);
});

test("requires positive paid amount", () => {
  assert.throws(() => normalizeSettlementEvidence(sample({ amount: 0 })), /positive number/);
});

test("dedupes repeated payment evidence", () => {
  const unique = dedupeSettlementEvidence([
    sample(),
    sample(),
    sample({ externalPaymentId: "payment-2" }),
  ]);
  assert.equal(unique.length, 2);
});
