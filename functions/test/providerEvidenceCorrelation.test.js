"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { QUEUE_STATES } = require("../src/providerDispatchEnvelope");
const { correlateEvidenceToDispatch } = require("../src/providerEvidenceCorrelation");

function dispatch(overrides = {}) {
  return {
    dispatchId: "dispatch-1",
    providerId: "provider-a",
    contractId: "contract-1",
    providerReference: "crm-123",
    state: QUEUE_STATES.ACKNOWLEDGED,
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
    observedAt: "2026-08-08T20:00:00.000Z",
    ...overrides,
  };
}

test("correlates verified evidence only to the acknowledged dispatch identity", () => {
  assert.deepEqual(correlateEvidenceToDispatch({ evidence: evidence(), dispatch: dispatch() }), {
    matched: true,
    dispatchId: "dispatch-1",
    providerId: "provider-a",
    contractId: "contract-1",
    providerReference: "crm-123",
    evidenceEventId: "evt-hash-1",
    kind: "ACTIVATION",
    observedAt: "2026-08-08T20:00:00.000Z",
  });
});

test("unacknowledged dispatch cannot receive downstream provider evidence", () => {
  assert.throws(() => correlateEvidenceToDispatch({
    evidence: evidence(),
    dispatch: dispatch({ state: QUEUE_STATES.IN_FLIGHT }),
  }), /acknowledged dispatch/);
});

test("provider mismatch is rejected", () => {
  assert.throws(() => correlateEvidenceToDispatch({
    evidence: evidence({ providerId: "provider-b" }),
    dispatch: dispatch(),
  }), /dispatch provider/);
});

test("contract mismatch is rejected", () => {
  assert.throws(() => correlateEvidenceToDispatch({
    evidence: evidence({ contractId: "contract-2" }),
    dispatch: dispatch(),
  }), /dispatch contract/);
});

test("provider reference mismatch is rejected to prevent cross-customer attribution", () => {
  assert.throws(() => correlateEvidenceToDispatch({
    evidence: evidence({ providerReference: "crm-other" }),
    dispatch: dispatch(),
  }), /acknowledged provider reference/);
});

test("correlation result contains no provider payload or personal contact fields", () => {
  const result = correlateEvidenceToDispatch({
    evidence: { ...evidence(), phone: "0500000000", contactEmail: "private@example.com" },
    dispatch: { ...dispatch(), payload: { phone: "0500000000" } },
  });
  assert.equal(Object.hasOwn(result, "phone"), false);
  assert.equal(Object.hasOwn(result, "contactEmail"), false);
  assert.equal(Object.hasOwn(result, "payload"), false);
});
