"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  EVIDENCE_KINDS,
  MAX_FUTURE_SKEW_MS,
  normalizeProviderEvidence,
  dedupeProviderEvidence,
} = require("../src/providerEvidenceIngestion");

const NOW_MS = Date.parse("2026-08-08T20:10:00Z");

function evidence(overrides = {}) {
  return {
    providerId: "provider-a",
    contractId: "contract-1",
    providerReference: "crm-123",
    externalEventId: "evt-1",
    kind: EVIDENCE_KINDS.ACTIVATION,
    source: "WEBHOOK",
    observedAt: "2026-08-08T20:00:00Z",
    ...overrides,
  };
}

function normalize(input) {
  return normalizeProviderEvidence(input, { nowMs: NOW_MS });
}

test("normalizes externally attributable provider evidence", () => {
  const normalized = normalize(evidence());
  assert.equal(normalized.providerReference, "crm-123");
  assert.equal(normalized.observedAt, "2026-08-08T20:00:00.000Z");
  assert.equal(normalized.evidenceEventId.length, 64);
});

test("same external provider event derives the same evidence id", () => {
  const first = normalize(evidence());
  const second = normalize(evidence());
  assert.equal(first.evidenceEventId, second.evidenceEventId);
});

test("different external event id produces a different evidence id", () => {
  const first = normalize(evidence());
  const second = normalize(evidence({ externalEventId: "evt-2" }));
  assert.notEqual(first.evidenceEventId, second.evidenceEventId);
});

test("unsupported source cannot masquerade as verified provider evidence", () => {
  assert.throws(() => normalize(evidence({ source: "INTERNAL_GUESS" })), /unsupported evidence source/);
});

test("invalid or missing provider reference is rejected", () => {
  assert.throws(() => normalize(evidence({ providerReference: "" })), /providerReference is required/);
});

test("commission evidence may carry non-negative monetary evidence", () => {
  const normalized = normalize(evidence({
    kind: EVIDENCE_KINDS.COMMISSION,
    amount: 75.5,
    currency: "ils",
  }));
  assert.equal(normalized.amount, 75.5);
  assert.equal(normalized.currency, "ILS");
});

test("negative monetary evidence is rejected", () => {
  assert.throws(() => normalize(evidence({ amount: -1 })), /non-negative/);
});

test("evidence timestamp allows small provider clock skew but rejects implausible future time", () => {
  const withinSkew = normalizeProviderEvidence(evidence({
    observedAt: new Date(NOW_MS + MAX_FUTURE_SKEW_MS).toISOString(),
  }), { nowMs: NOW_MS });
  assert.equal(withinSkew.observedAt, new Date(NOW_MS + MAX_FUTURE_SKEW_MS).toISOString());

  assert.throws(() => normalizeProviderEvidence(evidence({
    observedAt: new Date(NOW_MS + MAX_FUTURE_SKEW_MS + 1).toISOString(),
  }), { nowMs: NOW_MS }), /future/);
});

test("evidence import is idempotent by evidenceEventId", () => {
  const unique = dedupeProviderEvidence([
    evidence(),
    evidence(),
    evidence({ externalEventId: "evt-2" }),
  ], { nowMs: NOW_MS });
  assert.equal(unique.length, 2);
});
