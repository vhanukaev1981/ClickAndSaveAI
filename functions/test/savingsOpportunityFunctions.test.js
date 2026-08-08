"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { getApps, initializeApp } = require("firebase-admin/app");

if (getApps().length === 0) initializeApp({ projectId: "clickandsaveai-test" });

const {
  _userFacingOffer: userFacingOffer,
  _userFacingOpportunity: userFacingOpportunity,
} = require("../src/savingsOpportunityFunctions");

test("user-facing offer exposes complete first-year economics but never commission metadata", () => {
  const result = userFacingOffer({
    offerId: "offer-1",
    providerName: "Provider A",
    pricingModel: "FIXED_MONTHLY",
    monthlyPrice: 89,
    effectiveMonthlyPrice: 99,
    priceGuaranteedMonths: 12,
    requiredRecurringFees: 10,
    requiredRecurringFeesDescription: "required router rental",
    oneTimeFees: 120,
    firstYearCost: 1308,
    serviceType: "INTERNET_1000_MBPS",
    verifiedAt: "2026-08-08T10:00:00Z",
    validUntil: "2026-09-08T10:00:00Z",
    commercial: {
      agreementActive: true,
      commissionType: "CPA",
      commissionValue: 500,
    },
    commissionValue: 500,
  });

  assert.equal(result.monthlyPrice, 89);
  assert.equal(result.effectiveMonthlyPrice, 99);
  assert.equal(result.requiredRecurringFees, 10);
  assert.equal(result.requiredRecurringFeesDescription, "required router rental");
  assert.equal(result.priceGuaranteedMonths, 12);
  assert.equal(result.oneTimeFees, 120);
  assert.equal(result.firstYearCost, 1308);
  assert.equal(Object.hasOwn(result, "commercial"), false);
  assert.equal(Object.hasOwn(result, "commissionValue"), false);
  assert.equal(Object.hasOwn(result, "commissionType"), false);
});

test("user-facing opportunity preserves safe action mode and strips internal commercial state", () => {
  const result = userFacingOpportunity("opp-1", {
    type: "OPTIMIZE_RECURRING_SERVICE",
    status: "OPEN",
    actionMode: "IN_APP_PROVIDER_REQUEST",
    providerName: "Partner",
    category: "אינטרנט",
    serviceType: "INTERNET_1000_MBPS",
    currentMonthlyCost: 129,
    potentialMonthlySaving: 30,
    potentialAnnualSaving: 360,
    commercial: {
      partnerMatchStatus: "ACTIVE_PARTNER_MATCH",
      commissionStatus: "TRACKABLE",
    },
    matchedOffer: {
      offerId: "offer-1",
      providerName: "Provider A",
      pricingModel: "FIXED_MONTHLY",
      monthlyPrice: 99,
      effectiveMonthlyPrice: 99,
      priceGuaranteedMonths: 12,
      requiredRecurringFees: 0,
      requiredRecurringFeesDescription: "",
      oneTimeFees: 0,
      firstYearCost: 1188,
      serviceType: "INTERNET_1000_MBPS",
    },
  });

  assert.equal(result.actionMode, "IN_APP_PROVIDER_REQUEST");
  assert.equal(result.potentialAnnualSaving, 360);
  assert.equal(result.matchedOffer.firstYearCost, 1188);
  assert.equal(Object.hasOwn(result, "commercial"), false);
});

test("legacy opportunity without a commercial action mode is view-only", () => {
  const result = userFacingOpportunity("opp-legacy", {
    type: "OPTIMIZE_RECURRING_SERVICE",
    status: "OPEN",
    providerName: "Partner",
    category: "אינטרנט",
  });
  assert.equal(result.actionMode, "VIEW_ONLY");
});
