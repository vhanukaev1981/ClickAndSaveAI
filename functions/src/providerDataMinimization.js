"use strict";

const ALLOWED_EXTERNAL_FIELDS = Object.freeze([
  "contactName",
  "phone",
  "contactEmail",
  "requestedProvider",
  "category",
  "offerId",
  "consentVersion",
  "source",
]);

const FORBIDDEN_EXTERNAL_FIELDS = new Set([
  "gmailRaw",
  "gmailMessageId",
  "currentSpend",
  "commission",
  "commissionAmount",
  "internalUserId",
  "firebaseUid",
  "rankingScore",
  "aiReasoning",
]);

function buildMinimizedProviderPayload(input) {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    throw new TypeError("provider payload input must be an object");
  }
  for (const key of Object.keys(input)) {
    if (FORBIDDEN_EXTERNAL_FIELDS.has(key)) {
      throw new TypeError(`provider payload contains forbidden internal field: ${key}`);
    }
  }

  const output = {};
  for (const key of ALLOWED_EXTERNAL_FIELDS) {
    if (input[key] != null && String(input[key]).trim() !== "") output[key] = input[key];
  }

  for (const field of ["contactName", "phone", "contactEmail", "requestedProvider", "category", "offerId", "consentVersion"]) {
    if (output[field] == null || String(output[field]).trim() === "") throw new TypeError(`${field} is required`);
  }
  output.source = "CLICKANDSAVE_VERIFIED_OPPORTUNITY";
  return output;
}

module.exports = {
  ALLOWED_EXTERNAL_FIELDS,
  FORBIDDEN_EXTERNAL_FIELDS,
  buildMinimizedProviderPayload,
};
