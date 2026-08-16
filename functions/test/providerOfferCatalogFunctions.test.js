"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { getApps, initializeApp } = require("firebase-admin/app");

if (getApps().length === 0) initializeApp({ projectId: "clickandsaveai-test" });

const {
  _validateProviderOfferInput: validateProviderOfferInput,
  _parseHttpsUrl: parseHttpsUrl,
} = require("../src/providerOfferCatalogFunctions");

const nowMs = Date.parse("2026-08-08T17:00:00Z");

function validOffer(overrides = {}) {
  return {
    offerId: "internet-provider-a-1g-202608",
    providerName: "Provider A",
    category: "אינטרנט",
    pricingModel: "FIXED_MONTHLY",
    country: "IL",
    serviceType: "1Gbps fiber",
    monthlyPrice: 89,
    priceGuaranteedMonths: 12,
    oneTimeFees: 0,
    consumerPriceIncludesVat: true,
    requiredRecurringFees: 0,
    requiredRecurringFeesDescription: "",
    availabilityMode: "NATIONWIDE",
    availabilityStatus: "AVAILABLE",
    officialSourceUrl: "https://provider.example.co.il/fiber/1g",
    officialSourceName: "Provider A official website",
    sourceEvidenceNote: "Public 1Gbps monthly price and first-year fees",
    verifiedAt: "2026-08-08T16:30:00Z",
    validUntil: "2026-09-08T16:30:00Z",
    commercialAgreementActive: true,
    commissionType: "CPA",
    commissionValue: 180,
    ...overrides,
  };
}

test("catalog accepts a current Israeli fixed-monthly offer with complete first-year pricing evidence", () => {
  const result = validateProviderOfferInput(validOffer(), nowMs);
  assert.equal(result.offerId, "internet-provider-a-1g-202608");
  assert.equal(result.monthlyPrice, 89);
  assert.equal(result.category, "אינטרנט");
  assert.equal(result.pricingModel, "FIXED_MONTHLY");
  assert.equal(result.serviceType, "INTERNET_1000_MBPS");
  assert.equal(result.priceGuaranteedMonths, 12);
  assert.equal(result.oneTimeFees, 0);
  assert.equal(result.consumerPriceIncludesVat, true);
  assert.equal(result.requiredRecurringFees, 0);
  assert.equal(result.availabilityMode, "NATIONWIDE");
  assert.equal(result.commissionType, "CPA");
});

test("headline promo prices shorter than twelve months are rejected", () => {
  assert.throws(
    () => validateProviderOfferInput(validOffer({ priceGuaranteedMonths: 3 }), nowMs),
    /priceGuaranteedMonths/i
  );
});

test("first-year one-time fees must be stated explicitly, including zero", () => {
  const missing = validOffer();
  delete missing.oneTimeFees;
  assert.throws(
    () => validateProviderOfferInput(missing, nowMs),
    /oneTimeFees must be stated explicitly/i
  );

  const withFee = validateProviderOfferInput(validOffer({ oneTimeFees: 240 }), nowMs);
  assert.equal(withFee.oneTimeFees, 240);
});

test("consumer pricing must include VAT and disclose mandatory recurring fees", () => {
  assert.throws(
    () => validateProviderOfferInput(validOffer({ consumerPriceIncludesVat: false }), nowMs),
    /VAT-inclusive/i
  );
  assert.throws(
    () => validateProviderOfferInput(validOffer({
      requiredRecurringFees: 20,
      requiredRecurringFeesDescription: "",
    }), nowMs),
    /mandatory recurring fees/i
  );

  const withRouter = validateProviderOfferInput(validOffer({
    requiredRecurringFees: 20,
    requiredRecurringFeesDescription: "required router rental",
  }), nowMs);
  assert.equal(withRouter.requiredRecurringFees, 20);
  assert.equal(withRouter.requiredRecurringFeesDescription, "required router rental");
});

test("catalog requires an explicit supported availability mode", () => {
  const missing = validOffer();
  delete missing.availabilityMode;
  assert.throws(
    () => validateProviderOfferInput(missing, nowMs),
    /availabilityMode/i
  );
  assert.throws(
    () => validateProviderOfferInput(validOffer({ availabilityMode: "UNKNOWN" }), nowMs),
    /availabilityMode/i
  );
  assert.equal(
    validateProviderOfferInput(validOffer({ availabilityMode: "USER_VERIFIED" }), nowMs).availabilityMode,
    "USER_VERIFIED"
  );
});

test("insurance and electricity cannot masquerade as fixed-monthly comparable offers", () => {
  assert.throws(
    () => validateProviderOfferInput(validOffer({
      offerId: "insurance-fake-fixed",
      category: "ביטוח",
      serviceType: "ביטוח רכב",
      monthlyPrice: 250,
    }), nowMs),
    /category-specific pricing model/i
  );

  assert.throws(
    () => validateProviderOfferInput(validOffer({
      offerId: "electricity-fake-fixed",
      category: "חשמל",
      serviceType: "ANY",
      monthlyPrice: 300,
    }), nowMs),
    /category-specific pricing model/i
  );
});

test("unsupported pricing models are rejected until their calculation engine exists", () => {
  assert.throws(
    () => validateProviderOfferInput(validOffer({ pricingModel: "PERCENT_DISCOUNT" }), nowMs),
    /not implemented/i
  );
});

test("catalog rejects non-HTTPS or malformed source links", () => {
  assert.throws(
    () => parseHttpsUrl("http://provider.example.co.il/offer", "officialSourceUrl"),
    /https/i
  );
  assert.throws(
    () => parseHttpsUrl("not-a-url", "officialSourceUrl"),
    /valid URL/i
  );
});

test("catalog rejects expired and implausibly long-lived offers", () => {
  assert.throws(
    () => validateProviderOfferInput(validOffer({ validUntil: "2026-08-01T00:00:00Z" }), nowMs),
    /future/i
  );
  assert.throws(
    () => validateProviderOfferInput(validOffer({ validUntil: "2028-01-01T00:00:00Z" }), nowMs),
    /too long/i
  );
});

test("catalog rejects service wording that cannot be normalized safely", () => {
  assert.throws(
    () => validateProviderOfferInput(validOffer({ serviceType: "fast premium internet" }), nowMs),
    /serviceType/i
  );
});

test("commission data cannot be attached without a valid active commission model", () => {
  assert.throws(
    () => validateProviderOfferInput(validOffer({
      commercialAgreementActive: false,
      commissionType: "CPA",
      commissionValue: 180,
    }), nowMs),
    /must be NONE/i
  );
  assert.throws(
    () => validateProviderOfferInput(validOffer({
      commercialAgreementActive: false,
      commissionType: "NONE",
      commissionValue: 180,
    }), nowMs),
    /must be empty/i
  );

  const nonPartner = validateProviderOfferInput(validOffer({
    commercialAgreementActive: false,
    commissionType: "NONE",
    commissionValue: null,
  }), nowMs);
  assert.equal(nonPartner.commercialAgreementActive, false);
  assert.equal(nonPartner.commissionType, "NONE");
  assert.equal(nonPartner.commissionValue, null);
});

test("catalog only accepts supported household savings categories and IL offers", () => {
  assert.throws(
    () => validateProviderOfferInput(validOffer({ category: "מסעדות" }), nowMs),
    /monetizable/i
  );
  assert.throws(
    () => validateProviderOfferInput(validOffer({ country: "US" }), nowMs),
    /only IL/i
  );
});
