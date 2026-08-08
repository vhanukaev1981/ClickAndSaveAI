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
    country: "IL",
    serviceType: "1Gbps fiber",
    monthlyPrice: 89,
    availabilityStatus: "AVAILABLE",
    officialSourceUrl: "https://provider.example.co.il/fiber/1g",
    officialSourceName: "Provider A official website",
    sourceEvidenceNote: "Public 1Gbps monthly price",
    verifiedAt: "2026-08-08T16:30:00Z",
    validUntil: "2026-09-08T16:30:00Z",
    commercialAgreementActive: true,
    commissionType: "CPA",
    commissionValue: 180,
    ...overrides,
  };
}

test("catalog accepts a current Israeli offer and stores a canonical service profile", () => {
  const result = validateProviderOfferInput(validOffer(), nowMs);
  assert.equal(result.offerId, "internet-provider-a-1g-202608");
  assert.equal(result.monthlyPrice, 89);
  assert.equal(result.category, "אינטרנט");
  assert.equal(result.serviceType, "INTERNET_1000_MBPS");
  assert.equal(result.commissionType, "CPA");
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
