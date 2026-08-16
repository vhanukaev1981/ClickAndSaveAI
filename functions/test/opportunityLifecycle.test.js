"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  isOpportunityLifecycleLocked,
  engineOpportunityPayload,
  shouldRefreshCommerceMatch,
} = require("../src/opportunityLifecycle");

const newRecommendation = {
  id: "opp-1",
  matchedOffer: { offerId: "offer-new", providerName: "Provider B", monthlyPrice: 79 },
  potentialMonthlySaving: 50,
};

test("accepted and fulfillment opportunities are locked against agent rewrites", () => {
  for (const status of ["USER_ACCEPTED", "PROVIDER_PROCESSING", "ACTIVATED", "DEAL_COMPLETED", "COMPLETED"]) {
    const existing = {
      status,
      matchedOffer: { offerId: "offer-approved", providerName: "Provider A", monthlyPrice: 89 },
      potentialMonthlySaving: 40,
    };
    assert.equal(isOpportunityLifecycleLocked(existing), true);
    assert.deepEqual(engineOpportunityPayload(newRecommendation, existing), {});
    assert.equal(shouldRefreshCommerceMatch(existing), false);
  }
});

test("open and rejected opportunities may be re-evaluated by the agent", () => {
  for (const status of ["OPEN", "PROVIDER_REJECTED"]) {
    const existing = { status };
    assert.equal(isOpportunityLifecycleLocked(existing), false);
    assert.equal(engineOpportunityPayload(newRecommendation, existing).matchedOffer.offerId, "offer-new");
    assert.equal(shouldRefreshCommerceMatch(existing), true);
  }
});
