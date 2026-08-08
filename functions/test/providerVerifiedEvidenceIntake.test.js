"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { EVIDENCE_KINDS } = require("../src/providerEvidenceIngestion");
const { ingestVerifiedProviderEvidence } = require("../src/providerVerifiedEvidenceIntake");

const NOW_MS = Date.parse("2026-08-08T20:55:00Z");

function evidence(overrides = {}) {
  return {
    providerId: "provider-a",
    contractId: "contract-1",
    providerReference: "crm-123",
    externalEventId: "evt-1",
    kind: EVIDENCE_KINDS.ACTIVATION,
    source: "WEBHOOK",
    observedAt: "2026-08-08T20:54:00Z",
    ...overrides,
  };
}

test("verified webhook may enter the provider evidence pipeline", () => {
  const normalized = ingestVerifiedProviderEvidence({
    verification: { verified: true, timestampMs: NOW_MS - 60_000 },
    evidence: evidence(),
  }, { nowMs: NOW_MS });
  assert.equal(normalized.kind, EVIDENCE_KINDS.ACTIVATION);
  assert.equal(normalized.source, "WEBHOOK");
});

test("unverified webhook cannot become activation evidence", () => {
  assert.throws(() => ingestVerifiedProviderEvidence({
    verification: { verified: false, timestampMs: NOW_MS - 60_000 },
    evidence: evidence(),
  }, { nowMs: NOW_MS }), /verified webhook authenticity/);
});

test("missing webhook verification cannot be bypassed", () => {
  assert.throws(() => ingestVerifiedProviderEvidence({
    evidence: evidence(),
  }, { nowMs: NOW_MS }), /verification must be an object/);
});

test("verified signed evidence requires attributable verification timestamp", () => {
  assert.throws(() => ingestVerifiedProviderEvidence({
    verification: { verified: true },
    evidence: evidence({ source: "POSTBACK" }),
  }, { nowMs: NOW_MS }), /verification timestamp/);
});

test("report import remains evidence-source validated without webhook signature semantics", () => {
  const normalized = ingestVerifiedProviderEvidence({
    evidence: evidence({ source: "REPORT_IMPORT" }),
  }, { nowMs: NOW_MS });
  assert.equal(normalized.source, "REPORT_IMPORT");
});
