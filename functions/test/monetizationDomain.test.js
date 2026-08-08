"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  createClickId,
  normalizePartnerOffer,
  calculateCommission,
  normalizeAttributionEvent,
} = require("../src/monetizationDomain");

function offer(overrides = {}) {
  return {
    offerId: "offer-1",
    partnerId: "partner-1",
    providerName: "Provider",
    planName: "Plan A",
    category: "אינטרנט",
    exactLandingUrl: "https://provider.example/plan-a",
    monetizationModel: "CPA",
    commissionValue: 120,
    commissionRate: 0,
    currency: "ILS",
    active: true,
    ...overrides,
  };
}

test("click ids are deterministic and bound to exact offer plus opportunity", () => {
  const a = createClickId({ uid: "u1", offerId: "o1", opportunityId: "s1", nonce: "n1" });
  const b = createClickId({ uid: "u1", offerId: "o1", opportunityId: "s1", nonce: "n1" });
  const c = createClickId({ uid: "u1", offerId: "o2", opportunityId: "s1", nonce: "n1" });
  assert.equal(a, b);
  assert.notEqual(a, c);
});

test("partner offers require an exact HTTPS landing page", () => {
  assert.equal(normalizePartnerOffer(offer()).exactLandingUrl, "https://provider.example/plan-a");
  assert.throws(() => normalizePartnerOffer(offer({ exactLandingUrl: "http://provider.example/plan-a" })), /https/);
});

test("fixed CPA commission is deterministic", () => {
  assert.equal(calculateCommission({ offer: offer(), conversionValue: 999 }), 120);
});

test("revenue share commission uses confirmed conversion value", () => {
  assert.equal(calculateCommission({
    offer: offer({ monetizationModel: "REVENUE_SHARE", commissionValue: 0, commissionRate: 0.15 }),
    conversionValue: 1000,
  }), 150);
});

test("inactive offers cannot accrue commission", () => {
  assert.equal(calculateCommission({ offer: offer({ active: false }), conversionValue: 1000 }), 0);
});

test("attribution events preserve the click-to-offer-to-opportunity chain", () => {
  const normalized = normalizeAttributionEvent({
    type: "CONVERSION",
    clickId: "click-1",
    offerId: "offer-1",
    partnerId: "partner-1",
    opportunityId: "saving-1",
    status: "CONFIRMED",
    occurredAt: "2026-08-08T18:00:00Z",
    conversionValue: 400,
  });
  assert.equal(normalized.clickId, "click-1");
  assert.equal(normalized.status, "CONFIRMED");
  assert.equal(normalized.conversionValue, 400);
});
