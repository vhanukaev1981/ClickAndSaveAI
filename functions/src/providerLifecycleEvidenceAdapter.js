"use strict";

const { normalizeLifecycleEvidence } = require("./providerIntegrationFramework");

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

function buildLifecycleEvidenceFromProviderEvidence(evidenceInput, correlationInput) {
  const evidence = requiredObject(evidenceInput, "provider evidence");
  const correlation = requiredObject(correlationInput, "evidence correlation");

  if (correlation.matched !== true) {
    throw new Error("provider lifecycle evidence must be correlated to an acknowledged dispatch");
  }

  const evidenceEventId = requiredText(evidence.evidenceEventId, "evidence.evidenceEventId", 128);
  if (evidenceEventId !== requiredText(correlation.evidenceEventId, "correlation.evidenceEventId", 128)) {
    throw new Error("provider lifecycle evidence event does not match correlation event");
  }

  const providerReference = requiredText(evidence.providerReference, "evidence.providerReference", 200);
  if (providerReference !== requiredText(correlation.providerReference, "correlation.providerReference", 200)) {
    throw new Error("provider lifecycle evidence reference does not match correlation reference");
  }

  const kind = requiredText(evidence.kind, "evidence.kind", 40).toUpperCase();
  const source = requiredText(evidence.source, "evidence.source", 40).toUpperCase();
  const observedAt = requiredText(evidence.observedAt, "evidence.observedAt", 64);

  if (kind === "ACTIVATION") {
    return normalizeLifecycleEvidence({
      stage: "ACTIVATED",
      providerReference,
      evidenceSource: `PROVIDER_${source}`,
      observedAt,
    });
  }

  if (kind === "COMMISSION") {
    return normalizeLifecycleEvidence({
      stage: "COMMISSION_CONFIRMED",
      providerReference,
      evidenceSource: `PROVIDER_${source}`,
      observedAt,
      amount: evidence.amount,
      currency: evidence.currency,
    });
  }

  throw new TypeError(`provider evidence kind does not map unambiguously to lifecycle stage: ${kind}`);
}

module.exports = {
  buildLifecycleEvidenceFromProviderEvidence,
};
