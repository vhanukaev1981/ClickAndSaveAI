"use strict";

const crypto = require("node:crypto");

const ALLOWED_OPPORTUNITY_STATUS = new Set([
  "CANDIDATE",
  "VERIFIED",
  "DISMISSED",
  "EXPIRED",
]);

function requiredString(value, field, maxLength = 200) {
  if (typeof value !== "string") throw new TypeError(`${field} must be a string`);
  const normalized = value.trim();
  if (!normalized) throw new TypeError(`${field} is required`);
  if (normalized.length > maxLength) throw new TypeError(`${field} exceeds ${maxLength} characters`);
  return normalized;
}

function positiveMoney(value, field) {
  const amount = Number(value);
  if (!Number.isFinite(amount) || amount <= 0) throw new TypeError(`${field} must be positive`);
  return Math.round(amount * 100) / 100;
}

function nonNegativeMoney(value, field) {
  const amount = Number(value);
  if (!Number.isFinite(amount) || amount < 0) throw new TypeError(`${field} must be non-negative`);
  return Math.round(amount * 100) / 100;
}

function createOpportunityId({ uid, sourceId, offerId }) {
  const payload = [
    requiredString(uid, "uid", 160),
    requiredString(sourceId, "sourceId", 200),
    requiredString(offerId, "offerId", 160),
  ].join(":");
  return crypto.createHash("sha256").update(payload).digest("hex");
}

function normalizeSavingsOpportunity(input) {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    throw new TypeError("savings opportunity must be an object");
  }

  const status = requiredString(input.status || "CANDIDATE", "status", 30);
  if (!ALLOWED_OPPORTUNITY_STATUS.has(status)) {
    throw new TypeError("status is unsupported");
  }

  const currentMonthlyCost = positiveMoney(input.currentMonthlyCost, "currentMonthlyCost");
  const offeredMonthlyCost = positiveMoney(input.offeredMonthlyCost, "offeredMonthlyCost");
  const monthlySavings = Math.max(0, Math.round((currentMonthlyCost - offeredMonthlyCost) * 100) / 100);

  if (status === "VERIFIED") {
    if (monthlySavings <= 0) throw new TypeError("verified opportunity must have positive savings");
    if (input.evidenceVerified !== true) throw new TypeError("verified opportunity requires verified evidence");
  }

  return {
    opportunityId: requiredString(input.opportunityId, "opportunityId", 128),
    uid: requiredString(input.uid, "uid", 160),
    sourceId: requiredString(input.sourceId, "sourceId", 200),
    sourceType: requiredString(input.sourceType, "sourceType", 40),
    providerName: requiredString(input.providerName, "providerName", 160),
    category: requiredString(input.category, "category", 80),
    offerId: requiredString(input.offerId, "offerId", 160),
    currentMonthlyCost,
    offeredMonthlyCost,
    monthlySavings,
    annualSavings: Math.round(monthlySavings * 12 * 100) / 100,
    status,
    evidenceVerified: input.evidenceVerified === true,
    evidenceSource: String(input.evidenceSource || "").trim().slice(0, 500),
    evidenceCheckedAt: String(input.evidenceCheckedAt || "").trim().slice(0, 64),
    currency: String(input.currency || "ILS").trim().toUpperCase().slice(0, 8),
    confidence: nonNegativeMoney(input.confidence || 0, "confidence"),
  };
}

function canDisplaySavings(opportunity) {
  const normalized = normalizeSavingsOpportunity(opportunity);
  return normalized.status === "VERIFIED" &&
    normalized.evidenceVerified === true &&
    normalized.monthlySavings > 0;
}

module.exports = {
  ALLOWED_OPPORTUNITY_STATUS,
  createOpportunityId,
  normalizeSavingsOpportunity,
  canDisplaySavings,
};
