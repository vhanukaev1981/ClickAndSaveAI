"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  QUEUE_STATES,
  dispatchId,
  buildDispatchEnvelope,
  beginAttempt,
  applyAcknowledgement,
  applyFailure,
} = require("../src/providerDispatchEnvelope");

function input(overrides = {}) {
  return {
    leadId: "lead-1",
    providerId: "provider-a",
    offerId: "offer-1",
    contractId: "contract-1",
    adapterKey: "provider-a-v1",
    payload: {
      leadId: "lead-1",
      contactName: "Test User",
      phone: "0501234567",
      contactEmail: "test@example.com",
      requestedProvider: "Provider A",
      category: "אינטרנט",
      offerId: "offer-1",
      consentVersion: "v1",
    },
    ...overrides,
  };
}

test("dispatch identity is deterministic for the same lead/provider/offer/contract", () => {
  const first = dispatchId(input());
  const second = dispatchId(input());
  assert.equal(first, second);
  assert.equal(first.length, 64);
});

test("dispatch envelope starts READY with zero attempts and normalized minimal payload", () => {
  const envelope = buildDispatchEnvelope(input());
  assert.equal(envelope.state, QUEUE_STATES.READY);
  assert.equal(envelope.attempt, 0);
  assert.deepEqual(envelope.payload, {
    leadId: "lead-1",
    contactName: "Test User",
    phone: "0501234567",
    contactEmail: "test@example.com",
    requestedProvider: "Provider A",
    category: "אינטרנט",
    offerId: "offer-1",
    consentVersion: "v1",
    source: "CLICKANDSAVE_VERIFIED_OPPORTUNITY",
  });
});

test("provider payload rejects every field outside the minimum-data allowlist", () => {
  for (const forbidden of ["uid", "gmailContent", "currentMonthlyCost", "potentialMonthlySaving", "commissionAmount", "metadata"]) {
    const value = forbidden === "metadata" ? { gmailContent: "private" } : "private";
    assert.throws(() => buildDispatchEnvelope(input({
      payload: { ...input().payload, [forbidden]: value },
    })), /unsupported field/);
  }
});

test("provider payload identifiers must match the dispatch envelope", () => {
  assert.throws(() => buildDispatchEnvelope(input({
    payload: { ...input().payload, leadId: "other-lead" },
  })), /leadId does not match/);

  assert.throws(() => buildDispatchEnvelope(input({
    payload: { ...input().payload, offerId: "other-offer" },
  })), /offerId does not match/);
});

test("provider payload cannot override the verified source marker", () => {
  assert.throws(() => buildDispatchEnvelope(input({
    payload: { ...input().payload, source: "MANUAL" },
  })), /source is unsupported/);
});

test("attempt transitions READY to IN_FLIGHT and increments exactly once", () => {
  const inFlight = beginAttempt(buildDispatchEnvelope(input()));
  assert.equal(inFlight.state, QUEUE_STATES.IN_FLIGHT);
  assert.equal(inFlight.attempt, 1);
  assert.throws(() => beginAttempt(inFlight), /not ready/);
});

test("acknowledgement requires provider reference and makes dispatch terminal", () => {
  const inFlight = beginAttempt(buildDispatchEnvelope(input()));
  assert.throws(() => applyAcknowledgement(inFlight, ""), /providerReference/);
  const acked = applyAcknowledgement(inFlight, "crm-123");
  assert.equal(acked.state, QUEUE_STATES.ACKNOWLEDGED);
  assert.equal(acked.providerReference, "crm-123");
  assert.throws(() => beginAttempt(acked), /terminal/);
});

test("retryable failure enters RETRY_WAIT while permanent failure dead-letters", () => {
  const inFlight = beginAttempt(buildDispatchEnvelope(input()));
  const retry = applyFailure(inFlight, { retryable: true, errorCode: "HTTP_503" });
  assert.equal(retry.state, QUEUE_STATES.RETRY_WAIT);
  assert.equal(beginAttempt(retry).attempt, 2);

  const inFlightAgain = beginAttempt(buildDispatchEnvelope(input()));
  const dead = applyFailure(inFlightAgain, { retryable: false, errorCode: "INVALID_REQUEST" });
  assert.equal(dead.state, QUEUE_STATES.DEAD_LETTER);
  assert.throws(() => beginAttempt(dead), /terminal/);
});
