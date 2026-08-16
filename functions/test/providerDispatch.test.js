"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { buildProviderDispatchPayload } = require("../src/providerDispatch");

test("provider payload includes only contact and selected-offer fields", () => {
  const payload = buildProviderDispatchPayload({
    id: "lead-1",
    contactName: "Test User",
    phone: "0501234567",
    contactEmail: "TEST@example.com",
    requestedProvider: "Provider A",
    category: "אינטרנט",
    offerId: "offer-1",
    consentVersion: "opportunity-action-v1",
    currentProvider: "Partner",
    currentMonthlyCost: 129,
    potentialMonthlySaving: 40,
    potentialAnnualSaving: 480,
    authenticatedEmail: "private@example.com",
    opportunityId: "opp-1",
    uid: "secret-user-id",
  });

  assert.deepEqual(payload, {
    leadId: "lead-1",
    contactName: "Test User",
    phone: "0501234567",
    contactEmail: "test@example.com",
    requestedProvider: "Provider A",
    category: "אינטרנט",
    offerId: "offer-1",
    consentVersion: "opportunity-action-v1",
    source: "CLICKANDSAVE_VERIFIED_OPPORTUNITY",
  });
  assert.equal(Object.hasOwn(payload, "currentProvider"), false);
  assert.equal(Object.hasOwn(payload, "currentMonthlyCost"), false);
  assert.equal(Object.hasOwn(payload, "potentialMonthlySaving"), false);
  assert.equal(Object.hasOwn(payload, "uid"), false);
});

test("provider payload is not generated from an incomplete lead", () => {
  assert.equal(buildProviderDispatchPayload({ id: "lead-1" }), null);
});
