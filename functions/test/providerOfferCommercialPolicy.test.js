"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { getApps, initializeApp } = require("firebase-admin/app");

if (getApps().length === 0) initializeApp({ projectId: "clickandsaveai-test" });

const {
  _validateProviderOfferInput: validateProviderOfferInput,
} = require("../src/providerOfferCatalogFunctions");

const nowMs = Date.parse("2026-08-08T18:00:00Z");

function offer(overrides = {}) {
  return {
    offerId: "partner-trackable-1g",
    providerName: "Provider A",
    category: "אינטרנט",
    pricingModel: "FIXED_MONTHLY",
    country: "IL",
    serviceType: "1 Gbps",
    monthlyPrice: 89,
    priceGuaranteedMonths: 12,
    oneTimeFees: 0,
    consumerPriceIncludesVat: true,
    requiredRecurringFees: 0,
    requiredRecurringFeesDescription: "",
    availabilityMode: "NATIONWIDE",
    availabilityStatus: "AVAILABLE",
    officialSourceUrl: "https://provider.example.co.il/offer",
    officialSourceName: "Provider official offer",
    verifiedAt: "2026-08-08T17:00:00Z",
    validUntil: "2026-09-08T17:00:00Z",
    commercialAgreementActive: true,
    commissionType: "CPA",
    commissionValue: 180,
    ...overrides,
  };
}

test("active commercial offer requires a real positive commission", () => {
  assert.equal(validateProviderOfferInput(offer(), nowMs).commissionValue, 180);
  assert.throws(
    () => validateProviderOfferInput(offer({ commissionValue: 0 }), nowMs),
    /positive commissionValue/i
  );
  assert.throws(
    () => validateProviderOfferInput(offer({ commissionValue: null }), nowMs),
    /positive commissionValue/i
  );
});

test("non-partner offer is valid for unbiased recommendation when commission terms are absent", () => {
  const result = validateProviderOfferInput(offer({
    commercialAgreementActive: false,
    commissionType: "NONE",
    commissionValue: null,
  }), nowMs);
  assert.equal(result.commercialAgreementActive, false);
  assert.equal(result.commissionType, "NONE");
  assert.equal(result.commissionValue, null);
});
