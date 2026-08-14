"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { getApps, initializeApp } = require("firebase-admin/app");

if (getApps().length === 0) initializeApp({ projectId: "clickandsaveai-test" });

const { enrichOpportunityWithBestOffer } = require("../src/commerceEngine");
const { _buildDispatchQueueRecord: buildDispatchQueueRecord } = require("../src/providerDispatchFunctions");
const { _userFacingOpportunity: userFacingOpportunity } = require("../src/savingsOpportunityFunctions");
const {
  _validateDeliveryEvidenceInput: validateDeliveryEvidenceInput,
  _validateSavingRealizationInput: validateSavingRealizationInput,
} = require("../src/commerceOperationsFunctions");

function offer(overrides = {}) {
  return {
    id: "offer-1",
    providerName: "Provider B",
    category: "אינטרנט",
    country: "IL",
    pricingModel: "FIXED_MONTHLY",
    monthlyPrice: 89,
    priceGuaranteedMonths: 12,
    oneTimeFees: 0,
    serviceType: "ANY",
    verifiedAt: "2026-08-13T08:00:00Z",
    validUntil: "2026-09-13T08:00:00Z",
    officialSourceVerified: true,
    officialSourceUrl: "https://provider.example/offer-1",
    officialSourceName: "Provider official offer",
    availabilityStatus: "AVAILABLE",
    availabilityMode: "NATIONWIDE",
    consumerPriceIncludesVat: true,
    requiredRecurringFees: 0,
    requiredRecurringFeesDescription: "",
    commercialAgreementActive: true,
    commissionType: "CPA",
    commissionValue: 100,
    ...overrides,
  };
}

test("matched offer explicitly distinguishes verified, fresh and eligible from potential saving", () => {
  const result = enrichOpportunityWithBestOffer({
    id: "opp-1",
    providerName: "Provider A",
    category: "אינטרנט",
    currentMonthlyCost: 129,
  }, [offer()], { country: "IL", nowMs: Date.parse("2026-08-14T00:00:00Z") });

  assert.ok(result.matchedOffer);
  assert.equal(result.matchedOffer.verificationState, "VERIFIED");
  assert.equal(result.matchedOffer.freshnessState, "FRESH");
  assert.equal(result.matchedOffer.eligibilityState, "ELIGIBLE");
  assert.equal(result.matchedOffer.officialSourceUrl, "https://provider.example/offer-1");
  assert.equal(result.potentialMonthlySaving, 40);
  assert.equal(result.realizedMonthlySaving, undefined);
});

test("dispatch queue starts at request-created truth and never implies submission or receipt", () => {
  const result = buildDispatchQueueRecord("lead-1", {
    uid: "user-1",
    contactName: "Test User",
    phone: "0501234567",
    contactEmail: "test@example.com",
    requestedProvider: "Provider B",
    category: "אינטרנט",
    offerId: "offer-1",
    opportunityId: "opp-1",
    consentVersion: "opportunity-action-v1",
    consentAccepted: true,
    consentState: "CONSENTED",
    requestState: "REQUEST_CREATED",
    source: "AI_PROACTIVE_OPPORTUNITY",
    status: "NEW",
  }, {
    uid: "user-1",
    opportunityId: "opp-1",
    offerId: "offer-1",
    leadId: "lead-1",
    agreementActive: true,
    commissionType: "CPA",
    commissionValue: 100,
  });

  assert.ok(result);
  assert.equal(result.status, "PENDING");
  assert.equal(result.consentState, "CONSENTED");
  assert.equal(result.requestState, "REQUEST_CREATED");
  assert.equal(result.deliveryAttemptState, "NOT_ATTEMPTED");
  assert.equal(result.submissionState, "NOT_SUBMITTED");
  assert.equal(result.deliveryState, "NOT_CONFIRMED");
});

test("user-facing opportunity preserves unknown values instead of collapsing them to zero", () => {
  const result = userFacingOpportunity("opp-1", {
    status: "USER_ACCEPTED",
    actionMode: "IN_APP_PROVIDER_REQUEST",
    providerName: "Provider A",
    category: "אינטרנט",
    currentMonthlyCost: 129,
    potentialMonthlySaving: 40,
    consentState: "CONSENTED",
    requestState: "REQUEST_CREATED",
    deliveryAttemptState: "NOT_ATTEMPTED",
    submissionState: "NOT_SUBMITTED",
    deliveryState: "NOT_CONFIRMED",
    providerContactState: "UNKNOWN",
    completionState: "NOT_COMPLETED",
    savingRealizationState: "UNKNOWN",
  });

  assert.equal(result.previousMonthlyCost, null);
  assert.equal(result.monthlyIncrease, null);
  assert.equal(result.percentIncrease, null);
  assert.equal(result.consentState, "CONSENTED");
  assert.equal(result.requestState, "REQUEST_CREATED");
  assert.equal(result.submissionState, "NOT_SUBMITTED");
  assert.equal(result.deliveryState, "NOT_CONFIRMED");
  assert.equal(result.providerContactState, "UNKNOWN");
  assert.equal(result.completionState, "NOT_COMPLETED");
  assert.equal(result.savingRealizationState, "UNKNOWN");
  assert.equal(result.realizedMonthlySaving, null);
});

test("delivery evidence requires explicit submission result and receipt proof for confirmed delivery", () => {
  assert.throws(() => validateDeliveryEvidenceInput({
    leadId: "lead-1",
    attemptId: "attempt-1",
    transport: "PARTNER_API",
    deliveryConfirmed: false,
  }), /submissionAccepted/i);

  assert.throws(() => validateDeliveryEvidenceInput({
    leadId: "lead-1",
    attemptId: "attempt-1",
    transport: "PARTNER_API",
    submissionAccepted: true,
    deliveryConfirmed: true,
  }), /receipt/i);

  const result = validateDeliveryEvidenceInput({
    leadId: "lead-1",
    attemptId: "attempt-1",
    transport: "PARTNER_API",
    submissionAccepted: true,
    deliveryConfirmed: true,
    externalReceiptReference: "receipt-1",
  });
  assert.equal(result.submissionAccepted, true);
  assert.equal(result.deliveryConfirmed, true);
  assert.equal(result.externalReceiptReference, "receipt-1");
});

test("saving realization evidence requires comparable actual cost and an authoritative reference", () => {
  assert.throws(() => validateSavingRealizationInput({
    leadId: "lead-1",
    currentComparableMonthlyCost: 129,
    realizedComparableMonthlyCost: 99,
  }), /reference/i);

  const result = validateSavingRealizationInput({
    leadId: "lead-1",
    currentComparableMonthlyCost: 129,
    realizedComparableMonthlyCost: 99,
    externalReference: "bill-after-switch-1",
  });
  assert.equal(result.currentComparableMonthlyCost, 129);
  assert.equal(result.realizedComparableMonthlyCost, 99);
});
