"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");

const {
  createHandoffTruth,
  normalizeHandoffTruth,
  applyDeliveryEvidence,
  applyProviderContactEvidence,
  applyCompletionEvidence,
  applyRejectionEvidence,
  applySavingRealizationEvidence,
} = require("../src/handoffTruth");

function initialTruth() {
  return createHandoffTruth({ consentAccepted: true, requestCreated: true });
}

test("consent and request creation do not imply submission, delivery, provider contact, completion or realized saving", () => {
  const truth = initialTruth();
  assert.equal(truth.consentState, "CONSENTED");
  assert.equal(truth.requestState, "REQUEST_CREATED");
  assert.equal(truth.deliveryAttemptState, "NOT_ATTEMPTED");
  assert.equal(truth.submissionState, "NOT_SUBMITTED");
  assert.equal(truth.deliveryState, "NOT_CONFIRMED");
  assert.equal(truth.providerContactState, "UNKNOWN");
  assert.equal(truth.completionState, "NOT_COMPLETED");
  assert.equal(truth.savingRealizationState, "UNKNOWN");
  assert.equal(truth.realizedMonthlySaving, null);
  assert.equal(truth.realizedAnnualSaving, null);
});

test("inconsistent legacy truth fails closed instead of inventing consent or downstream states", () => {
  const truth = normalizeHandoffTruth({
    consentState: "NOT_CONSENTED",
    requestState: "REQUEST_CREATED",
    submissionState: "SUBMITTED",
    deliveryState: "DELIVERY_CONFIRMED",
    providerContactState: "CONTACTED",
    completionState: "DEAL_COMPLETED",
    savingRealizationState: "REALIZED",
    realizedMonthlySaving: 30,
    realizedAnnualSaving: 360,
  });
  assert.equal(truth.requestState, "NOT_CREATED");
  assert.equal(truth.submissionState, "NOT_SUBMITTED");
  assert.equal(truth.deliveryState, "NOT_CONFIRMED");
  assert.equal(truth.providerContactState, "UNKNOWN");
  assert.equal(truth.completionState, "NOT_COMPLETED");
  assert.equal(truth.savingRealizationState, "UNKNOWN");
  assert.equal(truth.realizedMonthlySaving, null);
});

test("a delivery attempt may fail before submission without claiming provider receipt", () => {
  const truth = applyDeliveryEvidence(initialTruth(), {
    attemptId: "attempt-1",
    transport: "PARTNER_API",
    submissionAccepted: false,
    deliveryConfirmed: false,
    failureCode: "TIMEOUT",
  });
  assert.equal(truth.deliveryAttemptState, "ATTEMPTED");
  assert.equal(truth.submissionState, "NOT_SUBMITTED");
  assert.equal(truth.deliveryState, "DELIVERY_FAILED");
  assert.equal(truth.providerContactState, "UNKNOWN");
});

test("submitted is distinct from delivery confirmed", () => {
  const truth = applyDeliveryEvidence(initialTruth(), {
    attemptId: "attempt-2",
    transport: "PARTNER_API",
    submissionAccepted: true,
    deliveryConfirmed: false,
  });
  assert.equal(truth.deliveryAttemptState, "ATTEMPTED");
  assert.equal(truth.submissionState, "SUBMITTED");
  assert.equal(truth.deliveryState, "NOT_CONFIRMED");
  assert.equal(truth.providerContactState, "UNKNOWN");
});

test("delivery confirmation requires authoritative receipt evidence and still does not imply provider contact", () => {
  assert.throws(() => applyDeliveryEvidence(initialTruth(), {
    attemptId: "attempt-3",
    transport: "PARTNER_API",
    submissionAccepted: true,
    deliveryConfirmed: true,
  }), /receipt/i);

  const truth = applyDeliveryEvidence(initialTruth(), {
    attemptId: "attempt-3",
    transport: "PARTNER_API",
    submissionAccepted: true,
    deliveryConfirmed: true,
    externalReceiptReference: "provider-receipt-123",
  });
  assert.equal(truth.submissionState, "SUBMITTED");
  assert.equal(truth.deliveryState, "DELIVERY_CONFIRMED");
  assert.equal(truth.providerContactState, "UNKNOWN");
});

test("authoritative submission and delivery evidence cannot be regressed", () => {
  const submitted = applyDeliveryEvidence(initialTruth(), {
    attemptId: "attempt-regression-1",
    transport: "PARTNER_API",
    submissionAccepted: true,
    deliveryConfirmed: false,
  });
  assert.throws(() => applyDeliveryEvidence(submitted, {
    attemptId: "attempt-regression-2",
    transport: "PARTNER_API",
    submissionAccepted: false,
    deliveryConfirmed: false,
  }), /regressed/i);

  const delivered = applyDeliveryEvidence(initialTruth(), {
    attemptId: "attempt-regression-3",
    transport: "PARTNER_API",
    submissionAccepted: true,
    deliveryConfirmed: true,
    externalReceiptReference: "provider-receipt-regression",
  });
  assert.throws(() => applyDeliveryEvidence(delivered, {
    attemptId: "attempt-regression-4",
    transport: "PARTNER_API",
    submissionAccepted: true,
    deliveryConfirmed: false,
  }), /regressed/i);
});

test("provider contact requires delivery confirmation and evidence", () => {
  assert.throws(() => applyProviderContactEvidence(initialTruth(), {
    contacted: true,
    externalReference: "provider-contact-1",
  }), /delivery/i);

  const delivered = applyDeliveryEvidence(initialTruth(), {
    attemptId: "attempt-4",
    transport: "PARTNER_API",
    submissionAccepted: true,
    deliveryConfirmed: true,
    externalReceiptReference: "provider-receipt-456",
  });
  assert.throws(() => applyProviderContactEvidence(delivered, { contacted: true }), /evidence/i);
  const contacted = applyProviderContactEvidence(delivered, {
    contacted: true,
    externalReference: "provider-contact-1",
  });
  assert.equal(contacted.providerContactState, "CONTACTED");
  assert.equal(contacted.completionState, "NOT_COMPLETED");
});

test("deal completion requires provider contact evidence and does not imply saving realization", () => {
  const delivered = applyDeliveryEvidence(initialTruth(), {
    attemptId: "attempt-5",
    transport: "PARTNER_API",
    submissionAccepted: true,
    deliveryConfirmed: true,
    externalReceiptReference: "provider-receipt-789",
  });
  const contacted = applyProviderContactEvidence(delivered, {
    contacted: true,
    externalReference: "provider-contact-2",
  });
  assert.throws(() => applyCompletionEvidence(contacted, { dealCompleted: true }), /evidence/i);
  const completed = applyCompletionEvidence(contacted, {
    dealCompleted: true,
    externalReference: "activation-123",
  });
  assert.equal(completed.completionState, "DEAL_COMPLETED");
  assert.equal(completed.savingRealizationState, "UNKNOWN");
  assert.equal(completed.realizedMonthlySaving, null);
  assert.throws(() => applyRejectionEvidence(completed, {
    externalReference: "late-rejection",
  }), /cannot be regressed/i);
});

test("known zero saving is distinct from unknown saving and only follows completed deal evidence", () => {
  const delivered = applyDeliveryEvidence(initialTruth(), {
    attemptId: "attempt-6",
    transport: "PARTNER_API",
    submissionAccepted: true,
    deliveryConfirmed: true,
    externalReceiptReference: "provider-receipt-999",
  });
  const contacted = applyProviderContactEvidence(delivered, {
    contacted: true,
    externalReference: "provider-contact-3",
  });
  const completed = applyCompletionEvidence(contacted, {
    dealCompleted: true,
    externalReference: "activation-999",
  });

  const realized = applySavingRealizationEvidence(completed, {
    currentComparableMonthlyCost: 129,
    realizedComparableMonthlyCost: 99,
    externalReference: "bill-after-switch-1",
  });
  assert.equal(realized.savingRealizationState, "REALIZED");
  assert.equal(realized.realizedMonthlySaving, 30);
  assert.equal(realized.realizedAnnualSaving, 360);

  const completedAgain = applyCompletionEvidence(realized, {
    dealCompleted: true,
    externalReference: "activation-999",
  });
  assert.equal(completedAgain.savingRealizationState, "REALIZED");
  assert.equal(completedAgain.realizedMonthlySaving, 30);

  const knownZero = applySavingRealizationEvidence(completed, {
    currentComparableMonthlyCost: 129,
    realizedComparableMonthlyCost: 129,
    externalReference: "bill-after-switch-2",
  });
  assert.equal(knownZero.savingRealizationState, "NOT_REALIZED");
  assert.equal(knownZero.realizedMonthlySaving, 0);
  assert.equal(knownZero.realizedAnnualSaving, 0);
});
