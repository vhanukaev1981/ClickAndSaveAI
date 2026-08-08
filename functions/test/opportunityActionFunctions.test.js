"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  _validateOpportunityActionInput: validateOpportunityActionInput,
  _verifiedActionSnapshot: verifiedActionSnapshot,
  OPPORTUNITY_ACTION_CONSENT_VERSION,
} = require("../src/opportunityActionFunctions");

function opportunity(overrides = {}) {
  return {
    id: "opp-1",
    providerName: "Partner",
    category: "אינטרנט",
    currentMonthlyCost: 129,
    potentialMonthlySaving: 40,
    potentialAnnualSaving: 480,
    matchedOffer: {
      offerId: "offer-1",
      providerName: "Provider A",
      monthlyPrice: 89,
    },
    ...overrides,
  };
}

function providerOffer(overrides = {}) {
  return {
    id: "offer-1",
    providerName: "Provider A",
    category: "אינטרנט",
    country: "IL",
    monthlyPrice: 89,
    serviceType: "",
    verifiedAt: "2026-08-08T08:00:00Z",
    validUntil: "2027-09-08T08:00:00Z",
    officialSourceVerified: true,
    availabilityStatus: "AVAILABLE",
    userFitScore: 0.9,
    commercialAgreementActive: true,
    commissionType: "CPA",
    commissionValue: 180,
    ...overrides,
  };
}

test("opportunity action requires explicit versioned consent and exact offer id", () => {
  assert.throws(() => validateOpportunityActionInput({
    opportunityId: "opp-1",
    expectedOfferId: "offer-1",
    contactName: "Test User",
    phone: "0501234567",
    contactEmail: "test@example.com",
    consentAccepted: false,
    consentVersion: OPPORTUNITY_ACTION_CONSENT_VERSION,
  }), /consent/i);

  const validated = validateOpportunityActionInput({
    opportunityId: "opp-1",
    expectedOfferId: "offer-1",
    contactName: "Test User",
    phone: "0501234567",
    contactEmail: "test@example.com",
    consentAccepted: true,
    consentVersion: OPPORTUNITY_ACTION_CONSENT_VERSION,
  });
  assert.equal(validated.opportunityId, "opp-1");
  assert.equal(validated.expectedOfferId, "offer-1");
  assert.equal(validated.contactEmail, "test@example.com");
});

test("verified action snapshot carries user saving and commercial attribution separately", () => {
  const snapshot = verifiedActionSnapshot(opportunity(), providerOffer(), "offer-1");
  assert.ok(snapshot);
  assert.equal(snapshot.requestedProvider, "Provider A");
  assert.equal(snapshot.potentialMonthlySaving, 40);
  assert.equal(snapshot.commissionType, "CPA");
  assert.equal(snapshot.commissionValue, 180);
  assert.equal(snapshot.commercialAgreementActive, true);
});

test("action is rejected when the opportunity no longer points to the offer the user saw", () => {
  const snapshot = verifiedActionSnapshot(opportunity(), providerOffer(), "offer-old");
  assert.equal(snapshot, null);
});

test("opportunity cannot be accepted when the official offer changed price", () => {
  const snapshot = verifiedActionSnapshot(
    opportunity(),
    providerOffer({ monthlyPrice: 99 }),
    "offer-1"
  );
  assert.equal(snapshot, null);
});

test("opportunity cannot be accepted against expired or unverified offer", () => {
  assert.equal(
    verifiedActionSnapshot(
      opportunity(),
      providerOffer({ officialSourceVerified: false }),
      "offer-1"
    ),
    null
  );
  assert.equal(
    verifiedActionSnapshot(
      opportunity(),
      providerOffer({ validUntil: "2026-01-01T00:00:00Z" }),
      "offer-1"
    ),
    null
  );
});
