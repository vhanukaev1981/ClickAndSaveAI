"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { getApps, initializeApp } = require("firebase-admin/app");

if (getApps().length === 0) initializeApp({ projectId: "clickandsaveai-test" });

const {
  _validateCommerceOutcomeInput: validateCommerceOutcomeInput,
  _assertTransition: assertTransition,
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

test("commission confirmation requires an actual positive commission amount", () => {
  assert.throws(() => validateCommerceOutcomeInput({
    leadId: "lead-1",
    newStatus: "COMMISSION_CONFIRMED",
    actualCommissionAmount: 0,
  }), /positive/i);

  const validated = validateCommerceOutcomeInput({
    leadId: "lead-1",
    newStatus: "COMMISSION_CONFIRMED",
    actualCommissionAmount: 180,
    externalReference: "provider-activation-123",
  });
  assert.equal(validated.actualCommissionAmount, 180);
  assert.equal(validated.externalReference, "provider-activation-123");
});

test("provider outcomes map into the user opportunity lifecycle", () => {
  assert.equal(opportunityStatusForLeadStatus("CONTACTED"), "PROVIDER_PROCESSING");
  assert.equal(opportunityStatusForLeadStatus("QUOTED"), "PROVIDER_PROCESSING");
  assert.equal(opportunityStatusForLeadStatus("ACTIVATED"), "ACTIVATED");
  assert.equal(opportunityStatusForLeadStatus("COMMISSION_CONFIRMED"), "COMPLETED");
  assert.equal(opportunityStatusForLeadStatus("REJECTED"), "PROVIDER_REJECTED");
});
