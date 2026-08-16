"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  offerAvailabilityEligible,
  normalizeConsumerPricingEvidence,
} = require("../src/offerEligibilityPolicy");

test("nationwide offers can be evaluated without user-specific eligibility", () => {
  assert.equal(offerAvailabilityEligible({}, {
    offerId: "mobile-nationwide",
    availabilityMode: "NATIONWIDE",
  }), true);
});

test("address or infrastructure-dependent offer is blocked until eligibility is verified", () => {
  const offer = {
    offerId: "fiber-1g",
    availabilityMode: "USER_VERIFIED",
  };
  assert.equal(offerAvailabilityEligible({}, offer), false);
  assert.equal(offerAvailabilityEligible({
    availabilityVerifiedOfferIds: ["fiber-1g"],
  }, offer), true);
});

test("eligibility-required offers never create an automatic verified saving before verification", () => {
  assert.equal(offerAvailabilityEligible({
    availabilityVerifiedOfferIds: ["fiber-1g"],
  }, {
    offerId: "fiber-1g",
    availabilityMode: "ELIGIBILITY_REQUIRED",
  }), false);
});

test("consumer price evidence requires VAT-inclusive price and explicit mandatory recurring fees", () => {
  assert.equal(normalizeConsumerPricingEvidence({
    consumerPriceIncludesVat: false,
    requiredRecurringFees: 0,
  }), null);

  assert.deepEqual(normalizeConsumerPricingEvidence({
    consumerPriceIncludesVat: true,
    requiredRecurringFees: 0,
    requiredRecurringFeesDescription: "",
  }), {
    consumerPriceIncludesVat: true,
    requiredRecurringFees: 0,
    requiredRecurringFeesDescription: "",
  });

  assert.equal(normalizeConsumerPricingEvidence({
    consumerPriceIncludesVat: true,
    requiredRecurringFees: 20,
    requiredRecurringFeesDescription: "",
  }), null);

  assert.deepEqual(normalizeConsumerPricingEvidence({
    consumerPriceIncludesVat: true,
    requiredRecurringFees: 20,
    requiredRecurringFeesDescription: "required router rental",
  }), {
    consumerPriceIncludesVat: true,
    requiredRecurringFees: 20,
    requiredRecurringFeesDescription: "required router rental",
  });
});
