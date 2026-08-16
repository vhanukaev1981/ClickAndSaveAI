"use strict";

const PHONE_PATTERN = /^[+]?[-()\s\d]{7,20}$/;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const LEAD_CONSENT_VERSION = "provider-lead-v1";
const ALLOWED_LEAD_CATEGORIES = new Set([
  "חשמל",
  "סלולר",
  "אינטרנט",
  "תקשורת",
  "ביטוח",
  "טלוויזיה",
  "טלוויזיה ומנויים",
]);

function requiredString(value, field, maxLength) {
  if (typeof value !== "string") {
    throw new TypeError(`${field} must be a string`);
  }
  const normalized = value.trim();
  if (!normalized) {
    throw new TypeError(`${field} is required`);
  }
  if (normalized.length > maxLength) {
    throw new TypeError(`${field} exceeds ${maxLength} characters`);
  }
  return normalized;
}

function optionalString(value, field, maxLength) {
  if (value == null || value === "") return "";
  return requiredString(value, field, maxLength);
}

function validateEmail(value) {
  const email = requiredString(value, "contactEmail", 254).toLowerCase();
  if (!EMAIL_PATTERN.test(email)) {
    throw new TypeError("contactEmail is invalid");
  }
  return email;
}

function validatePhone(value) {
  const phone = requiredString(value, "phone", 20);
  if (!PHONE_PATTERN.test(phone)) {
    throw new TypeError("phone is invalid");
  }
  return phone;
}

function validateLeadInput(data) {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    throw new TypeError("request data must be an object");
  }

  if (data.consentAccepted !== true) {
    throw new TypeError("explicit lead consent is required");
  }

  const consentVersion = requiredString(data.consentVersion, "consentVersion", 40);
  if (consentVersion !== LEAD_CONSENT_VERSION) {
    throw new TypeError("unsupported lead consent version");
  }

  const category = requiredString(data.category, "category", 60);
  if (!ALLOWED_LEAD_CATEGORIES.has(category)) {
    throw new TypeError("category is unsupported");
  }

  return {
    contactName: requiredString(data.contactName, "contactName", 120),
    phone: validatePhone(data.phone),
    contactEmail: validateEmail(data.contactEmail),
    currentProvider: requiredString(data.currentProvider, "currentProvider", 120),
    requestedProvider: optionalString(data.requestedProvider, "requestedProvider", 120),
    category,
    invoiceLocalId: optionalString(String(data.invoiceLocalId ?? ""), "invoiceLocalId", 64),
    idempotencyKey: requiredString(data.idempotencyKey, "idempotencyKey", 128),
    consentVersion,
    notes: optionalString(data.notes, "notes", 1000),
  };
}

function validateDealQuery(data) {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    throw new TypeError("request data must be an object");
  }
  return requiredString(data.query, "query", 2000);
}

module.exports = {
  LEAD_CONSENT_VERSION,
  ALLOWED_LEAD_CATEGORIES,
  requiredString,
  optionalString,
  validateEmail,
  validatePhone,
  validateLeadInput,
  validateDealQuery,
};
