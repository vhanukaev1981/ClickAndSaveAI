"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { buildMinimizedProviderPayload } = require("../src/providerDataMinimization");

function payload(overrides = {}) {
  return {
    contactName: "Test User",
    phone: "0500000000",
    contactEmail: "test@example.com",
    requestedProvider: "provider-a",
    category: "internet",
    offerId: "offer-1",
    consentVersion: "v1",
    source: "anything",
    ...overrides,
  };
}

test("keeps only allowed external fields and canonical source", () => {
  const result = buildMinimizedProviderPayload(payload());
  assert.equal(result.source, "CLICKANDSAVE_VERIFIED_OPPORTUNITY");
  assert.deepEqual(Object.keys(result).sort(), ["category", "consentVersion", "contactEmail", "contactName", "offerId", "phone", "requestedProvider", "source"].sort());
});

test("forbids internal financial and Gmail context", () => {
  assert.throws(() => buildMinimizedProviderPayload(payload({ gmailRaw: "raw" })), /forbidden internal field/);
  assert.throws(() => buildMinimizedProviderPayload(payload({ currentSpend: 300 })), /forbidden internal field/);
  assert.throws(() => buildMinimizedProviderPayload(payload({ commissionAmount: 50 })), /forbidden internal field/);
});

test("requires minimum contact and offer fields", () => {
  assert.throws(() => buildMinimizedProviderPayload(payload({ offerId: "" })), /offerId is required/);
});
