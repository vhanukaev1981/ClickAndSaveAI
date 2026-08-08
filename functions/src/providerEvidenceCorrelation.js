"use strict";

const { QUEUE_STATES } = require("./providerDispatchEnvelope");

function requiredObject(value, field) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new TypeError(`${field} must be an object`);
  }
  return value;
}

function requiredText(value, field, maxLength = 200) {
  const text = typeof value === "string" ? value.trim() : "";
  if (!text) throw new TypeError(`${field} is required`);
  if (text.length > maxLength) throw new TypeError(`${field} exceeds ${maxLength} characters`);
  return text;
}

function correlateEvidenceToDispatch(input) {
  const source = requiredObject(input, "evidence correlation input");
  const evidence = requiredObject(source.evidence, "evidence");
  const dispatch = requiredObject(source.dispatch, "dispatch");

  if (dispatch.state !== QUEUE_STATES.ACKNOWLEDGED) {
    throw new Error("provider evidence can only correlate to an acknowledged dispatch");
  }

  const evidenceProviderId = requiredText(evidence.providerId, "evidence.providerId", 128);
  const evidenceContractId = requiredText(evidence.contractId, "evidence.contractId", 128);
  const evidenceProviderReference = requiredText(evidence.providerReference, "evidence.providerReference", 200);
  const dispatchProviderId = requiredText(dispatch.providerId, "dispatch.providerId", 128);
  const dispatchContractId = requiredText(dispatch.contractId, "dispatch.contractId", 128);
  const dispatchProviderReference = requiredText(dispatch.providerReference, "dispatch.providerReference", 200);
  const dispatchId = requiredText(dispatch.dispatchId, "dispatch.dispatchId", 128);

  if (evidenceProviderId !== dispatchProviderId) {
    throw new Error("provider evidence does not match dispatch provider");
  }
  if (evidenceContractId !== dispatchContractId) {
    throw new Error("provider evidence does not match dispatch contract");
  }
  if (evidenceProviderReference !== dispatchProviderReference) {
    throw new Error("provider evidence does not match acknowledged provider reference");
  }

  return Object.freeze({
    matched: true,
    dispatchId,
    providerId: dispatchProviderId,
    contractId: dispatchContractId,
    providerReference: dispatchProviderReference,
    evidenceEventId: requiredText(evidence.evidenceEventId, "evidence.evidenceEventId", 128),
    kind: requiredText(evidence.kind, "evidence.kind", 40).toUpperCase(),
    observedAt: requiredText(evidence.observedAt, "evidence.observedAt", 64),
  });
}

module.exports = {
  correlateEvidenceToDispatch,
};
