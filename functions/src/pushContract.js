"use strict";

const ALLOWED_TYPES = new Set(["PUSH_TEST", "NEW_INVOICE", "SAVINGS_OPPORTUNITY"]);

function requiredString(value, field, maxLength = 200) {
  if (typeof value !== "string") throw new TypeError(`${field} must be a string`);
  const normalized = value.trim();
  if (!normalized) throw new TypeError(`${field} is required`);
  if (normalized.length > maxLength) throw new TypeError(`${field} exceeds ${maxLength} characters`);
  return normalized;
}

function normalizePushData(input) {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    throw new TypeError("push data must be an object");
  }
  const type = requiredString(input.type, "type", 40);
  if (!ALLOWED_TYPES.has(type)) throw new TypeError("push type is unsupported");

  if (type === "PUSH_TEST") {
    return { type, destination: "DASHBOARD" };
  }

  if (type === "NEW_INVOICE") {
    return {
      type,
      destination: "INVOICES",
      invoiceId: requiredString(input.invoiceId, "invoiceId", 200),
      importedCount: String(Math.max(1, Math.floor(Number(input.importedCount || 1)))),
    };
  }

  return {
    type,
    destination: "SAVINGS_OPPORTUNITY",
    opportunityId: requiredString(input.opportunityId, "opportunityId", 160),
    offerId: requiredString(input.offerId, "offerId", 160),
  };
}

module.exports = { ALLOWED_TYPES, normalizePushData };
