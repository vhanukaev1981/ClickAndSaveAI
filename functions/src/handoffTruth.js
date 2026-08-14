"use strict";

const CONSENT_STATES = new Set(["NOT_CONSENTED", "CONSENTED"]);
const REQUEST_STATES = new Set(["NOT_CREATED", "REQUEST_CREATED"]);
const DELIVERY_ATTEMPT_STATES = new Set(["NOT_ATTEMPTED", "ATTEMPTED"]);
const SUBMISSION_STATES = new Set(["NOT_SUBMITTED", "SUBMITTED"]);
const DELIVERY_STATES = new Set(["NOT_CONFIRMED", "DELIVERY_FAILED", "DELIVERY_CONFIRMED"]);
const PROVIDER_CONTACT_STATES = new Set(["UNKNOWN", "CONTACTED"]);
const COMPLETION_STATES = new Set(["NOT_COMPLETED", "DEAL_REJECTED", "DEAL_COMPLETED"]);
const SAVING_REALIZATION_STATES = new Set(["UNKNOWN", "NOT_REALIZED", "REALIZED"]);

function text(value, maxLength = 240) {
  return String(value || "").trim().slice(0, maxLength);
}

function roundMoney(value) {
  return Math.round((Number(value) + Number.EPSILON) * 100) / 100;
}

function enumOr(value, allowed, fallback) {
  const normalized = text(value, 80).toUpperCase();
  return allowed.has(normalized) ? normalized : fallback;
}

function createHandoffTruth({ consentAccepted = false, requestCreated = false } = {}) {
  if (requestCreated === true && consentAccepted !== true) {
    throw new TypeError("request creation requires explicit consent");
  }
  return {
    consentState: consentAccepted === true ? "CONSENTED" : "NOT_CONSENTED",
    requestState: requestCreated === true ? "REQUEST_CREATED" : "NOT_CREATED",
    deliveryAttemptState: "NOT_ATTEMPTED",
    submissionState: "NOT_SUBMITTED",
    deliveryState: "NOT_CONFIRMED",
    providerContactState: "UNKNOWN",
    completionState: "NOT_COMPLETED",
    savingRealizationState: "UNKNOWN",
    realizedMonthlySaving: null,
    realizedAnnualSaving: null,
  };
}

function normalizeHandoffTruth(record) {
  const source = record && typeof record === "object" ? record : {};
  const fallback = createHandoffTruth({
    consentAccepted: source.consentAccepted === true,
    requestCreated: source.requestState === "REQUEST_CREATED" || source.handoffRequestState === "REQUEST_CREATED",
  });
  const normalized = {
    consentState: enumOr(source.consentState, CONSENT_STATES, fallback.consentState),
    requestState: enumOr(source.requestState || source.handoffRequestState, REQUEST_STATES, fallback.requestState),
    deliveryAttemptState: enumOr(source.deliveryAttemptState, DELIVERY_ATTEMPT_STATES, fallback.deliveryAttemptState),
    submissionState: enumOr(source.submissionState, SUBMISSION_STATES, fallback.submissionState),
    deliveryState: enumOr(source.deliveryState, DELIVERY_STATES, fallback.deliveryState),
    providerContactState: enumOr(source.providerContactState, PROVIDER_CONTACT_STATES, fallback.providerContactState),
    completionState: enumOr(source.completionState, COMPLETION_STATES, fallback.completionState),
    savingRealizationState: enumOr(
      source.savingRealizationState,
      SAVING_REALIZATION_STATES,
      fallback.savingRealizationState
    ),
    realizedMonthlySaving: source.realizedMonthlySaving === null || source.realizedMonthlySaving === undefined
      ? null
      : Number(source.realizedMonthlySaving),
    realizedAnnualSaving: source.realizedAnnualSaving === null || source.realizedAnnualSaving === undefined
      ? null
      : Number(source.realizedAnnualSaving),
  };
  if (!Number.isFinite(normalized.realizedMonthlySaving)) normalized.realizedMonthlySaving = null;
  if (!Number.isFinite(normalized.realizedAnnualSaving)) normalized.realizedAnnualSaving = null;
  return normalized;
}

function assertRequestReady(current) {
  if (current.consentState !== "CONSENTED") {
    throw new TypeError("delivery evidence requires explicit consent");
  }
  if (current.requestState !== "REQUEST_CREATED") {
    throw new TypeError("delivery evidence requires a created handoff request");
  }
}

function applyDeliveryEvidence(state, evidence) {
  const current = normalizeHandoffTruth(state);
  assertRequestReady(current);
  if (!evidence || typeof evidence !== "object" || Array.isArray(evidence)) {
    throw new TypeError("delivery evidence is required");
  }
  const attemptId = text(evidence.attemptId, 128);
  const transport = text(evidence.transport, 80);
  if (!attemptId) throw new TypeError("delivery attempt id is required");
  if (!transport) throw new TypeError("delivery transport is required");
  if (typeof evidence.submissionAccepted !== "boolean") {
    throw new TypeError("submissionAccepted must be explicit");
  }
  if (typeof evidence.deliveryConfirmed !== "boolean") {
    throw new TypeError("deliveryConfirmed must be explicit");
  }
  if (evidence.deliveryConfirmed === true && evidence.submissionAccepted !== true) {
    throw new TypeError("delivery cannot be confirmed when submission was not accepted");
  }
  const externalReceiptReference = text(evidence.externalReceiptReference, 200);
  if (evidence.deliveryConfirmed === true && !externalReceiptReference) {
    throw new TypeError("delivery confirmation requires authoritative receipt evidence");
  }
  const failureCode = text(evidence.failureCode, 120);

  return {
    ...current,
    deliveryAttemptState: "ATTEMPTED",
    submissionState: evidence.submissionAccepted === true ? "SUBMITTED" : "NOT_SUBMITTED",
    deliveryState: evidence.deliveryConfirmed === true
      ? "DELIVERY_CONFIRMED"
      : (evidence.submissionAccepted === false ? "DELIVERY_FAILED" : "NOT_CONFIRMED"),
    lastDeliveryAttemptId: attemptId,
    lastDeliveryTransport: transport,
    externalReceiptReference: externalReceiptReference || null,
    deliveryFailureCode: failureCode || null,
  };
}

function applyProviderContactEvidence(state, evidence) {
  const current = normalizeHandoffTruth(state);
  if (current.deliveryState !== "DELIVERY_CONFIRMED") {
    throw new TypeError("provider contact cannot be attributed before delivery is confirmed");
  }
  if (!evidence || evidence.contacted !== true) {
    throw new TypeError("provider contact evidence must explicitly state contacted=true");
  }
  const externalReference = text(evidence.externalReference, 200);
  if (!externalReference) {
    throw new TypeError("provider contact requires authoritative evidence reference");
  }
  return {
    ...current,
    providerContactState: "CONTACTED",
    providerContactEvidenceReference: externalReference,
  };
}

function applyCompletionEvidence(state, evidence) {
  const current = normalizeHandoffTruth(state);
  if (current.providerContactState !== "CONTACTED") {
    throw new TypeError("deal completion requires provider contact evidence");
  }
  if (!evidence || evidence.dealCompleted !== true) {
    throw new TypeError("deal completion evidence must explicitly state dealCompleted=true");
  }
  const externalReference = text(evidence.externalReference, 200);
  if (!externalReference) {
    throw new TypeError("deal completion requires authoritative evidence reference");
  }
  return {
    ...current,
    completionState: "DEAL_COMPLETED",
    completionEvidenceReference: externalReference,
    savingRealizationState: "UNKNOWN",
    realizedMonthlySaving: null,
    realizedAnnualSaving: null,
  };
}

function applyRejectionEvidence(state, evidence) {
  const current = normalizeHandoffTruth(state);
  const externalReference = text(evidence?.externalReference, 200);
  if (!externalReference) {
    throw new TypeError("deal rejection requires authoritative evidence reference");
  }
  return {
    ...current,
    completionState: "DEAL_REJECTED",
    completionEvidenceReference: externalReference,
    savingRealizationState: "UNKNOWN",
    realizedMonthlySaving: null,
    realizedAnnualSaving: null,
  };
}

function comparableCost(value, field) {
  const number = Number(value);
  if (!Number.isFinite(number) || number <= 0 || number >= 1_000_000) {
    throw new TypeError(`${field} must be a positive comparable monthly cost`);
  }
  return roundMoney(number);
}

function applySavingRealizationEvidence(state, evidence) {
  const current = normalizeHandoffTruth(state);
  if (current.completionState !== "DEAL_COMPLETED") {
    throw new TypeError("saving realization requires authoritative deal completion first");
  }
  const externalReference = text(evidence?.externalReference, 200);
  if (!externalReference) {
    throw new TypeError("saving realization requires authoritative evidence reference");
  }
  const currentComparableMonthlyCost = comparableCost(
    evidence.currentComparableMonthlyCost,
    "currentComparableMonthlyCost"
  );
  const realizedComparableMonthlyCost = comparableCost(
    evidence.realizedComparableMonthlyCost,
    "realizedComparableMonthlyCost"
  );
  const rawSaving = roundMoney(currentComparableMonthlyCost - realizedComparableMonthlyCost);
  const realizedMonthlySaving = rawSaving > 0 ? rawSaving : 0;
  const realizedAnnualSaving = roundMoney(realizedMonthlySaving * 12);
  return {
    ...current,
    savingRealizationState: realizedMonthlySaving > 0 ? "REALIZED" : "NOT_REALIZED",
    currentComparableMonthlyCost,
    realizedComparableMonthlyCost,
    realizedMonthlySaving,
    realizedAnnualSaving,
    savingEvidenceReference: externalReference,
  };
}

module.exports = {
  CONSENT_STATES,
  REQUEST_STATES,
  DELIVERY_ATTEMPT_STATES,
  SUBMISSION_STATES,
  DELIVERY_STATES,
  PROVIDER_CONTACT_STATES,
  COMPLETION_STATES,
  SAVING_REALIZATION_STATES,
  createHandoffTruth,
  normalizeHandoffTruth,
  applyDeliveryEvidence,
  applyProviderContactEvidence,
  applyCompletionEvidence,
  applyRejectionEvidence,
  applySavingRealizationEvidence,
};
