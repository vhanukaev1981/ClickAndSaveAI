"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { verifyProviderDispatchIntent } = require("../src/providerConsentIntentGate");

function intent(overrides = {}) {
  return {
    accepted: true,
    action: "CONTINUE_TO_OFFER",
    offerId: "offer-1",
    providerId: "provider-a",
    consentVersion: "v1",
    acceptedAt: "2026-08-08T20:00:00Z",
    ...overrides,
  };
}

test("accepts explicit continue-to-offer intent", () => {
  const result = verifyProviderDispatchIntent(intent());
  assert.equal(result.accepted, true);
  assert.equal(result.action, "CONTINUE_TO_OFFER");
});

test("rejects missing explicit acceptance", () => {
  assert.throws(() => verifyProviderDispatchIntent(intent({ accepted: false })), /explicit user acceptance/);
});

test("rejects unrelated action", () => {
  assert.throws(() => verifyProviderDispatchIntent(intent({ action: "OPEN_SCREEN" })), /continue-to-offer intent/);
});

test("requires valid acceptance timestamp", () => {
  assert.throws(() => verifyProviderDispatchIntent(intent({ acceptedAt: "invalid" })), /valid timestamp/);
});
