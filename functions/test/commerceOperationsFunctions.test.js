"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { getApps, initializeApp } = require("firebase-admin/app");

if (getApps().length === 0) initializeApp({ projectId: "clickandsaveai-test" });

const {
  _validateCommerceOutcomeInput: validateCommerceOutcomeInput,
  _validateDeliveryEvidenceInput: validateDeliveryEvidenceInput,
  _validateSavingRealizationInput: validateSavingRealizationInput,
  _assertTransition: assertTransition,
  _classifyDeliveryAttemptReplay: classifyDeliveryAttemptReplay,
  _opportunityStatusForLeadStatus: opportunityStatusForLeadStatus,
} = require("../src/commerceOperationsFunctions");

test("provider lead lifecycle only allows forward commercial transitions", () => {
  assert.equal(assertTransition("NEW", "CONTACTED"), "CHANGE");
  assert.equal(assertTransition("CONTACTED", "QUOTED"), "CHANGE");
  assert.equal(assertTransition("QUOTED", "ACTIVATED"), "CHANGE");
  assert.equal(assertTransition("ACTIVATED", "COMMISSION_CONFIRMED"), "CHANGE");
  assert.equal(assertTransition("ACTIVATED", "ACTIVATED"), "IDEMPOTENT");
  assert.throws(
    () => assertTransition("NEW", "COMMISSION_CONFIRMED"),
    /cannot move/i
  );
});

test("truth-changing provider outcomes require authoritative external evidence", () => {
  assert.throws(() => validateCommerceOutcomeInput({
    leadId: "lead-1",
    newStatus: "CONTACTED",
  }), /externalReference/i);
  assert.throws(() => validateCommerceOutcomeInput({
    leadId: "lead-1",
    newStatus: "ACTIVATED",
  }), /externalReference/i);

  const contacted = validateCommerceOutcomeInput({
    leadId: "lead-1",
    newStatus: "CONTACTED",
    externalReference: "provider-contact-123",
  });
  assert.equal(contacted.externalReference, "provider-contact-123");
});

test("commission confirmation requires an actual positive commission amount but is not savings evidence", () => {
  assert.throws(() => validateCommerceOutcomeInput({
    leadId: "lead-1",
    newStatus: "COMMISSION_CONFIRMED",
    actualCommissionAmount: 0,
  }), /positive/i);

  const validated = validateCommerceOutcomeInput({
    leadId: "lead-1",
    newStatus: "COMMISSION_CONFIRMED",
    actualCommissionAmount: 180,
    externalReference: "commission-123",
  });
  assert.equal(validated.actualCommissionAmount, 180);
  assert.equal(validated.externalReference, "commission-123");
});

test("delivery evidence distinguishes submission from delivery confirmation", () => {
  const submitted = validateDeliveryEvidenceInput({
    leadId: "lead-1",
    attemptId: "attempt-1",
    transport: "PARTNER_API",
    submissionAccepted: true,
    deliveryConfirmed: false,
  });
  assert.equal(submitted.submissionAccepted, true);
  assert.equal(submitted.deliveryConfirmed, false);

  assert.throws(() => validateDeliveryEvidenceInput({
    leadId: "lead-1",
    attemptId: "attempt-2",
    transport: "PARTNER_API",
    submissionAccepted: true,
    deliveryConfirmed: true,
  }), /receipt/i);
});

test("same delivery attempt is idempotent only when all authoritative evidence matches", () => {
  const stored = {
    lastDeliveryAttemptId: "attempt-1",
    lastDeliveryTransport: "PARTNER_API",
    submissionState: "SUBMITTED",
    deliveryState: "DELIVERY_CONFIRMED",
    externalReceiptReference: "receipt-1",
    deliveryFailureCode: null,
  };
  const same = validateDeliveryEvidenceInput({
    leadId: "lead-1",
    attemptId: "attempt-1",
    transport: "PARTNER_API",
    submissionAccepted: true,
    deliveryConfirmed: true,
    externalReceiptReference: "receipt-1",
  });
  assert.equal(classifyDeliveryAttemptReplay(stored, same), "IDEMPOTENT");

  const conflicting = { ...same, externalReceiptReference: "receipt-2" };
  assert.equal(classifyDeliveryAttemptReplay(stored, conflicting), "CONFLICT");
  assert.equal(classifyDeliveryAttemptReplay(stored, { ...same, attemptId: "attempt-2" }), "NEW");
});

test("failed delivery attempt replay also remains idempotent", () => {
  const stored = {
    lastDeliveryAttemptId: "attempt-failed",
    lastDeliveryTransport: "PARTNER_API",
    submissionState: "NOT_SUBMITTED",
    deliveryState: "DELIVERY_FAILED",
    externalReceiptReference: null,
    deliveryFailureCode: "TIMEOUT",
  };
  const same = validateDeliveryEvidenceInput({
    leadId: "lead-1",
    attemptId: "attempt-failed",
    transport: "PARTNER_API",
    submissionAccepted: false,
    deliveryConfirmed: false,
    failureCode: "TIMEOUT",
  });
  assert.equal(classifyDeliveryAttemptReplay(stored, same), "IDEMPOTENT");
});

test("saving realization validation requires comparable costs and evidence", () => {
  assert.throws(() => validateSavingRealizationInput({
    leadId: "lead-1",
    currentComparableMonthlyCost: 129,
    realizedComparableMonthlyCost: 99,
  }), /external reference/i);
  const validated = validateSavingRealizationInput({
    leadId: "lead-1",
    currentComparableMonthlyCost: 129,
    realizedComparableMonthlyCost: 99,
    externalReference: "bill-after-switch-1",
  });
  assert.equal(validated.currentComparableMonthlyCost, 129);
  assert.equal(validated.realizedComparableMonthlyCost, 99);
});

test("provider outcomes map into an explicit completion lifecycle without claiming realized saving", () => {
  assert.equal(opportunityStatusForLeadStatus("CONTACTED"), "PROVIDER_PROCESSING");
  assert.equal(opportunityStatusForLeadStatus("QUOTED"), "PROVIDER_PROCESSING");
  assert.equal(opportunityStatusForLeadStatus("ACTIVATED"), "DEAL_COMPLETED");
  assert.equal(opportunityStatusForLeadStatus("COMMISSION_CONFIRMED"), "DEAL_COMPLETED");
  assert.equal(opportunityStatusForLeadStatus("REJECTED"), "PROVIDER_REJECTED");
});
