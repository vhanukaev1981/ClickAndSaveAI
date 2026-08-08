"use strict";

const { normalizeProviderEvidence } = require("./providerEvidenceIngestion");

const SIGNED_SOURCES = new Set(["WEBHOOK", "POSTBACK"]);

function requiredObject(value, field) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new TypeError(`${field} must be an object`);
  }
  return value;
}

function ingestVerifiedProviderEvidence(input, options = {}) {
  const source = requiredObject(input, "verified evidence intake");
  const evidence = requiredObject(source.evidence, "evidence");
  const evidenceSource = typeof evidence.source === "string" ? evidence.source.trim().toUpperCase() : "";

  if (SIGNED_SOURCES.has(evidenceSource)) {
    const verification = requiredObject(source.verification, "verification");
    if (verification.verified !== true) {
      throw new Error("signed provider evidence requires verified webhook authenticity");
    }
    if (!Number.isFinite(Number(verification.timestampMs))) {
      throw new TypeError("verified webhook evidence requires verification timestamp evidence");
    }
  }

  return normalizeProviderEvidence(evidence, options);
}

module.exports = {
  SIGNED_SOURCES,
  ingestVerifiedProviderEvidence,
};
