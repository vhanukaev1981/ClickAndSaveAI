"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  matchVerifiedOffers,
  enrichOpportunityWithBestOffer,
} = require("../src/commerceEngine");

const nowMs = Date.parse("2026-08-08T12:00:00Z");
const opportunity = {
  category: "אינטרנט",
  currentMonthlyCost: 129,
  serviceType: "1Gbps fiber",
};

function offer(overrides) {
  return {
    offerId: "offer-default",
    providerName: "Provider",
    category: "אינטרנט",
    pricingModel: "FIXED_MONTHLY",
    country: "IL",
    monthlyPrice: 99,
    serviceType: "1Gbps fiber",
    verifiedAt: "2026-08-08T08:00:00Z",
    validUntil: "2026-09-08T08:00:00Z",
    officialSourceVerified: true,
    availabilityStatus: "AVAILABLE",
    userFitScore: 0.9,
    commercialAgreementActive: true,
    commissionType: "CPA",
    commissionValue: 100,
    ...overrides,
  };
}

test("ranking chooses the highest user saving even when another provider pays a larger commission", () => {
  const matches = matchVerifiedOffers(opportunity, [
    offer({ offerId: "best-user", monthlyPrice: 89, commissionValue: 50 }),
    offer({ offerId: "best-commission", monthlyPrice: 99, commissionValue: 500 }),
  ], { nowMs });

  assert.equal(matches[0].offerId, "best-user");
  assert.equal(matches[0].monthlySaving, 40);
  assert.equal(matches[1].offerId, "best-commission");
});

test("unverified, expired, wrong-model and incompatible offers are rejected", () => {
  const matches = matchVerifiedOffers(opportunity, [
    offer({ offerId: "unverified", officialSourceVerified: false }),
    offer({ offerId: "expired", validUntil: "2026-08-01T08:00:00Z" }),
    offer({ offerId: "wrong-service", serviceType: "100Mbps" }),
    offer({ offerId: "wrong-country", country: "US" }),
    offer({ offerId: "wrong-model", pricingModel: "PERCENT_DISCOUNT" }),
    offer({ offerId: "valid", monthlyPrice: 95 }),
  ], { nowMs });

  assert.deepEqual(matches.map((item) => item.offerId), ["valid"]);
});

test("specific service offer is rejected when user service type is unknown", () => {
  const unknownServiceOpportunity = {
    category: "אינטרנט",
    currentMonthlyCost: 129,
  };
  const matches = matchVerifiedOffers(unknownServiceOpportunity, [
    offer({ offerId: "specific", serviceType: "1Gbps fiber", monthlyPrice: 89 }),
    offer({ offerId: "universal", serviceType: "ANY", monthlyPrice: 99 }),
  ], { nowMs });

  assert.deepEqual(matches.map((item) => item.offerId), ["universal"]);
});

test("offer without a declared service type is rejected", () => {
  const matches = matchVerifiedOffers(opportunity, [
    offer({ offerId: "missing-service", serviceType: "" }),
  ], { nowMs });
  assert.equal(matches.length, 0);
});

test("insurance and electricity never receive a fixed-monthly savings claim", () => {
  const insurance = enrichOpportunityWithBestOffer({
    category: "ביטוח",
    currentMonthlyCost: 350,
    serviceType: "INSURANCE_CAR",
  }, [offer({
    offerId: "fake-insurance-fixed",
    category: "ביטוח",
    serviceType: "INSURANCE_CAR",
    monthlyPrice: 250,
  })], { nowMs });
  assert.equal(insurance.matchedOffer, null);
  assert.equal(insurance.potentialMonthlySaving, null);
  assert.equal(insurance.truthfulness.savingsClaimAvailable, false);
  assert.match(insurance.truthfulness.reason, /category-specific pricing model/i);

  const electricity = enrichOpportunityWithBestOffer({
    category: "חשמל",
    currentMonthlyCost: 500,
  }, [offer({
    offerId: "fake-electricity-fixed",
    category: "חשמל",
    serviceType: "ANY",
    monthlyPrice: 400,
  })], { nowMs });
  assert.equal(electricity.matchedOffer, null);
  assert.equal(electricity.potentialAnnualSaving, null);
});

test("savings claim appears only after a verified compatible offer is matched", () => {
  const noMatch = enrichOpportunityWithBestOffer(opportunity, [], { nowMs });
  assert.equal(noMatch.potentialMonthlySaving, null);
  assert.equal(noMatch.truthfulness.savingsClaimAvailable, false);

  const matched = enrichOpportunityWithBestOffer(opportunity, [
    offer({ offerId: "verified", monthlyPrice: 89 }),
  ], { nowMs });
  assert.equal(matched.potentialMonthlySaving, 40);
  assert.equal(matched.potentialAnnualSaving, 480);
  assert.equal(matched.truthfulness.savingsClaimAvailable, true);
});

test("Firestore Timestamp-like dates are accepted for verified provider offers", () => {
  const verifiedAtSeconds = Math.floor(Date.parse("2026-08-08T08:00:00Z") / 1000);
  const validUntilSeconds = Math.floor(Date.parse("2026-09-08T08:00:00Z") / 1000);
  const matches = matchVerifiedOffers(opportunity, [
    offer({
      offerId: "firestore-timestamp",
      monthlyPrice: 89,
      verifiedAt: { seconds: verifiedAtSeconds, nanoseconds: 0 },
      validUntil: { seconds: validUntilSeconds, nanoseconds: 0 },
    }),
  ], { nowMs });

  assert.equal(matches.length, 1);
  assert.equal(matches[0].offerId, "firestore-timestamp");
  assert.equal(matches[0].monthlySaving, 40);
});
