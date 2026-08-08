"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { buildLifecycleEvidenceFromProviderEvidence } = require("../src/providerLifecycleEvidenceAdapter");

function correlation(overrides = {}) {
  return {
    matched: true,
    dispatchId: "dispatch-1",
    providerId: "provider-a",
    contractId: "contract-1",
    providerReference: "crm-123",
    evidenceEventId: "evt-hash-1",
    kind: "ACTIVATION",
    observedAt: "2026-08-08T20:00:00.000Z",
    ...overrides,
  };
}

function evidence(overrides = {}) {
  return {
    evidenceEventId: "evt-hash-1",
    providerId: "provider-a",
    contractId: "contract-1",
    providerReference: "crm-123",
    kind: "ACTIVATION",
    source: "WEBHOOK",
    observedAt: "2026-08-08T20:00:00.000Z",
    ...overrides,
  };
}

test("activation evidence maps deterministically to ACTIVATED", () => {
  const lifecycle = buildLifecycleEvidenceFromProviderEvidence(evidence(), correlation());
  assert.equal(lifecycle.stage, "ACTIVATED");
  assert.equal(lifecycle.providerReference, "crm-123");
  assert.equal(lifecycle.evidenceSource, "PROVIDER_WEBHOOK");
});

test("commission evidence maps to COMMISSION_CONFIRMED with amount evidence", () => {
  const lifecycle = buildLifecycleEvidenceFromProviderEvidence(
    evidence({ kind: "COMMISSION", source: "REPORT_IMPORT", amount: 81.25, currency: "ils" }),
    correlation({ kind: "COMMISSION" }),
  );
  assert.equal(lifecycle.stage, "COMMISSION_CONFIRMED");
  assert.equal(lifecycle.amount, 81.25);
  assert.equal(lifecycle.currency, "ILS");
});

test("uncorrelated evidence cannot advance lifecycle", () => {
  assert.throws(() => buildLifecycleEvidenceFromProviderEvidence(
    evidence(),
    correlation({ matched: false }),
  ), /correlated/);
});

test("evidence event must match correlation event", () => {
  assert.throws(() => buildLifecycleEvidenceFromProviderEvidence(
    evidence({ evidenceEventId: "evt-other" }),
    correlation(),
  ), /event does not match/);
});

test("provider reference must match correlation reference", () => {
  assert.throws(() => buildLifecycleEvidenceFromProviderEvidence(
    evidence({ providerReference: "crm-other" }),
    correlation(),
  ), /reference does not match/);
});

test("ambiguous STATUS or CONVERSION evidence is never guessed into a lifecycle stage", () => {
  assert.throws(() => buildLifecycleEvidenceFromProviderEvidence(
    evidence({ kind: "STATUS" }),
    correlation({ kind: "STATUS" }),
  ), /does not map unambiguously/);

  assert.throws(() => buildLifecycleEvidenceFromProviderEvidence(
    evidence({ kind: "CONVERSION" }),
    correlation({ kind: "CONVERSION" }),
  ), /does not map unambiguously/);
});

test("commission lifecycle confirmation requires positive amount", () => {
  assert.throws(() => buildLifecycleEvidenceFromProviderEvidence(
    evidence({ kind: "COMMISSION", amount: 0, currency: "ILS" }),
    correlation({ kind: "COMMISSION" }),
  ), /positive amount/);
});
