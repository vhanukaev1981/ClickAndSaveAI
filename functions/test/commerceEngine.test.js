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

test("unverified, expired and incompatible offers are rejected", () => {
  const matches = matchVerifiedOffers(opportunity, [
    offer({ offerId: "unverified", officialSourceVerified: false }),
    offer({ offerId: "expired", validUntil: "2026-08-01T08:00:00Z" }),
    offer({ offerId: "wrong-service", serviceType: "100Mbps" }),
    offer({ offerId: "wrong-country", country: "US" }),
    offer({ offerId: "valid", monthlyPrice: 95 }),
  ], { nowMs });

  assert.deepEqual(matches.map((item) => item.offerId), ["valid"]);
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
