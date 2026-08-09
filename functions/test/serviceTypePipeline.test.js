"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { parseGmailMessage, normalizePdfInvoiceCandidate } = require("../src/gmailParser");
const { detectFinancialSignals } = require("../src/financialIntelligence");
const { matchVerifiedOffers } = require("../src/commerceEngine");

function b64url(value) {
  return Buffer.from(value, "utf8")
    .toString("base64")
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
}

function gmailMessage({ id, body, date }) {
  return {
    id,
    payload: {
      headers: [
        { name: "Subject", value: "חשבונית אינטרנט סיבים" },
        { name: "From", value: "Partner <billing@example.test>" },
        { name: "Date", value: date },
      ],
      parts: [{ mimeType: "text/plain", body: { data: b64url(body) } }],
    },
  };
}

test("explicit Gmail internet speed reaches the proactive opportunity as a canonical service type", () => {
  const first = parseGmailMessage(gmailMessage({
    id: "internet-july",
    date: "Wed, 1 Jul 2026 08:00:00 +0300",
    body: "השירות שלך: אינטרנט סיבים במהירות 1 Gbps. סה\"כ לתשלום 129 ₪",
  }));
  const second = parseGmailMessage(gmailMessage({
    id: "internet-august",
    date: "Sat, 1 Aug 2026 08:00:00 +0300",
    body: "החבילה שלך: אינטרנט סיבים במהירות 1000 Mbps. סה\"כ לתשלום 129 ₪",
  }));

  assert.equal(first.serviceType, "INTERNET_1000_MBPS");
  assert.equal(second.serviceType, "INTERNET_1000_MBPS");

  const { opportunities } = detectFinancialSignals([first, second]);
  assert.equal(opportunities.length, 1);
  assert.equal(opportunities[0].serviceType, "INTERNET_1000_MBPS");
});

test("PDF service wording is normalized before it enters financial state", () => {
  const parsed = normalizePdfInvoiceCandidate({
    isInvoice: true,
    providerName: "בזק",
    category: "internet",
    serviceType: "Fiber 500 Mbps",
    monthlyCost: 119.9,
    receivedDate: "2026-08-01",
  }, gmailMessage({
    id: "pdf-internet",
    date: "Sat, 1 Aug 2026 08:00:00 +0300",
    body: "",
  }));

  assert.equal(parsed.serviceType, "INTERNET_500_MBPS");
});

test("canonical user service profile matches equivalent provider wording but rejects a lower service profile", () => {
  const nowMs = Date.parse("2026-08-08T12:00:00Z");
  const opportunity = {
    category: "אינטרנט",
    currentMonthlyCost: 129,
    serviceType: "INTERNET_1000_MBPS",
  };
  const baseOffer = {
    providerName: "Provider A",
    category: "אינטרנט",
    pricingModel: "FIXED_MONTHLY",
    country: "IL",
    priceGuaranteedMonths: 12,
    oneTimeFees: 0,
    consumerPriceIncludesVat: true,
    requiredRecurringFees: 0,
    requiredRecurringFeesDescription: "",
    availabilityMode: "NATIONWIDE",
    verifiedAt: "2026-08-08T08:00:00Z",
    validUntil: "2026-09-08T08:00:00Z",
    officialSourceVerified: true,
    availabilityStatus: "AVAILABLE",
    userFitScore: 0.9,
    commercialAgreementActive: false,
    commissionType: "NONE",
  };

  const matches = matchVerifiedOffers(opportunity, [
    {
      ...baseOffer,
      offerId: "same-speed",
      monthlyPrice: 89,
      serviceType: "1 Gbps",
    },
    {
      ...baseOffer,
      offerId: "lower-speed",
      monthlyPrice: 69,
      serviceType: "500 Mbps",
    },
  ], { nowMs });

  assert.deepEqual(matches.map((item) => item.offerId), ["same-speed"]);
  assert.equal(matches[0].serviceType, "INTERNET_1000_MBPS");
  assert.equal(matches[0].annualSaving, 480);
});
