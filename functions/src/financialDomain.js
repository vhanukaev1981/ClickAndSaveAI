"use strict";

const ALLOWED_SOURCE_TYPES = new Set([
  "GMAIL_INVOICE",
  "BANK_TRANSACTION",
  "CARD_TRANSACTION",
  "MANUAL",
  "MCP_CONNECTOR",
]);

const ALLOWED_CADENCES = new Set(["ONE_TIME", "WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY", "UNKNOWN"]);
const ALLOWED_VERIFICATION = new Set(["UNVERIFIED", "SOURCE_VERIFIED", "OFFER_VERIFIED"]);

function requiredString(value, field, maxLength = 160) {
  if (typeof value !== "string") throw new TypeError(`${field} must be a string`);
  const normalized = value.trim();
  if (!normalized) throw new TypeError(`${field} is required`);
  if (normalized.length > maxLength) throw new TypeError(`${field} exceeds ${maxLength} characters`);
  return normalized;
}

function optionalString(value, maxLength = 160) {
  if (value == null || value === "") return "";
  const normalized = String(value).trim();
  if (normalized.length > maxLength) throw new TypeError(`value exceeds ${maxLength} characters`);
  return normalized;
}

function positiveAmount(value, field) {
  const amount = Number(value);
  if (!Number.isFinite(amount) || amount <= 0) throw new TypeError(`${field} must be a positive number`);
  return Math.round(amount * 100) / 100;
}

function normalizeFinancialEvent(input) {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    throw new TypeError("financial event must be an object");
  }

  const sourceType = requiredString(input.sourceType, "sourceType", 40);
  if (!ALLOWED_SOURCE_TYPES.has(sourceType)) throw new TypeError("sourceType is unsupported");

  const cadence = optionalString(input.cadence || "UNKNOWN", 20) || "UNKNOWN";
  if (!ALLOWED_CADENCES.has(cadence)) throw new TypeError("cadence is unsupported");

  const verificationStatus = optionalString(input.verificationStatus || "UNVERIFIED", 30) || "UNVERIFIED";
  if (!ALLOWED_VERIFICATION.has(verificationStatus)) throw new TypeError("verificationStatus is unsupported");

  return {
    sourceType,
    sourceId: requiredString(input.sourceId, "sourceId", 200),
    providerName: requiredString(input.providerName, "providerName", 160),
    category: requiredString(input.category, "category", 80),
    amount: positiveAmount(input.amount, "amount"),
    currency: optionalString(input.currency || "ILS", 8).toUpperCase(),
    occurredAt: requiredString(input.occurredAt, "occurredAt", 64),
    cadence,
    recurringKey: optionalString(input.recurringKey, 200),
    verificationStatus,
    accountScope: optionalString(input.accountScope, 120),
  };
}

function monthlyEquivalent(event) {
  const amount = positiveAmount(event.amount, "amount");
  switch (event.cadence) {
    case "WEEKLY": return Math.round((amount * 52 / 12) * 100) / 100;
    case "MONTHLY": return amount;
    case "QUARTERLY": return Math.round((amount / 3) * 100) / 100;
    case "YEARLY": return Math.round((amount / 12) * 100) / 100;
    case "ONE_TIME":
    case "UNKNOWN":
    default:
      return 0;
  }
}

function buildFinancialSnapshot(events) {
  const normalized = (Array.isArray(events) ? events : []).map(normalizeFinancialEvent);
  const recurring = normalized.filter((event) => monthlyEquivalent(event) > 0);
  const monthlyRecurringSpend = Math.round(
    recurring.reduce((sum, event) => sum + monthlyEquivalent(event), 0) * 100
  ) / 100;

  const byCategory = {};
  for (const event of recurring) {
    byCategory[event.category] = Math.round(
      ((byCategory[event.category] || 0) + monthlyEquivalent(event)) * 100
    ) / 100;
  }

  return {
    monthlyRecurringSpend,
    annualRecurringSpend: Math.round(monthlyRecurringSpend * 12 * 100) / 100,
    recurringCount: recurring.length,
    eventCount: normalized.length,
    byCategory,
  };
}

module.exports = {
  ALLOWED_SOURCE_TYPES,
  ALLOWED_CADENCES,
  ALLOWED_VERIFICATION,
  normalizeFinancialEvent,
  monthlyEquivalent,
  buildFinancialSnapshot,
};
