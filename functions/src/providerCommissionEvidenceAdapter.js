"use strict";

const {
  COMMISSION_STATES,
  normalizeCommissionRecord,
  reconcileCommission,
} = require("./providerReconciliation");

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

function reconcileCommissionFromProviderEvidence(currentInput, evidenceInput, correlationInput) {
  const current = normalizeCommissionRecord(currentInput);
  const evidence = requiredObject(evidenceInput, "commission evidence");
  const correlation = requiredObject(correlationInput, "evidence correlation");

  if (correlation.matched !== true) {
    throw new Error("commission evidence must be correlated to an acknowledged dispatch");
  }

  const kind = requiredText(evidence.kind, "evidence.kind", 40).toUpperCase();
  if (kind !== "COMMISSION") {
    throw new TypeError("only COMMISSION provider evidence can confirm commission");
  }

  const providerReference = requiredText(evidence.providerReference, "evidence.providerReference", 200);
  if (providerReference !== current.providerReference || providerReference !== correlation.providerReference) {
    throw new Error("commission evidence provider reference does not match reconciliation identity");
  }

  const evidenceEventId = requiredText(evidence.evidenceEventId, "evidence.evidenceEventId", 128);
  if (evidenceEventId !== correlation.evidenceEventId) {
    throw new Error("commission evidence event does not match correlation evidence event");
  }

  const amount = Number(evidence.amount);
  if (!Number.isFinite(amount) || amount <= 0) {
    throw new TypeError("commission provider evidence requires a positive amount");
  }

  const currency = requiredText(evidence.currency || "ILS", "evidence.currency", 8).toUpperCase();
  if (currency !== current.currency) {
    throw new Error("commission evidence currency does not match expected commission currency");
  }

  const source = requiredText(evidence.source, "evidence.source", 40).toUpperCase();
  const observedAt = requiredText(evidence.observedAt, "evidence.observedAt", 64);
  if (!Number.isFinite(Date.parse(observedAt))) {
    throw new TypeError("evidence.observedAt must be a valid timestamp");
  }

  return reconcileCommission(current, {
    state: COMMISSION_STATES.CONFIRMED,
    confirmedAmount: Math.round(amount * 100) / 100,
    evidenceSource: `PROVIDER_${source}`,
    evidenceObservedAt: observedAt,
  });
}

module.exports = {
  reconcileCommissionFromProviderEvidence,
};
