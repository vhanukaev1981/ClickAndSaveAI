"use strict";

const crypto = require("node:crypto");

const ALLOWED_MODELS = new Set(["CPL", "CPA", "REVENUE_SHARE", "DIRECT"]);
const ALLOWED_CLICK_STATUS = new Set(["CREATED", "REDIRECTED", "CONVERTED", "EXPIRED"]);
const ALLOWED_CONVERSION_STATUS = new Set(["PENDING", "CONFIRMED", "REJECTED", "REVERSED"]);

function requiredString(value, field, maxLength = 200) {
  if (typeof value !== "string") throw new TypeError(`${field} must be a string`);
  const normalized = value.trim();
  if (!normalized) throw new TypeError(`${field} is required`);
  if (normalized.length > maxLength) throw new TypeError(`${field} exceeds ${maxLength} characters`);
  return normalized;
}

function nonNegativeMoney(value, field) {
  const amount = Number(value);
  if (!Number.isFinite(amount) || amount < 0) throw new TypeError(`${field} must be non-negative`);
  return Math.round(amount * 100) / 100;
}

function createClickId({ uid, offerId, opportunityId, nonce }) {
  const payload = [
    requiredString(uid, "uid", 160),
    requiredString(offerId, "offerId", 160),
    requiredString(opportunityId, "opportunityId", 160),
    requiredString(nonce, "nonce", 160),
  ].join(":");
  return crypto.createHash("sha256").update(payload).digest("hex");
}

function normalizePartnerOffer(input) {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    throw new TypeError("offer must be an object");
  }
  const monetizationModel = requiredString(input.monetizationModel, "monetizationModel", 40);
  if (!ALLOWED_MODELS.has(monetizationModel)) throw new TypeError("monetizationModel is unsupported");

  const exactLandingUrl = requiredString(input.exactLandingUrl, "exactLandingUrl", 2048);
  let url;
  try {
    url = new URL(exactLandingUrl);
  } catch {
    throw new TypeError("exactLandingUrl is invalid");
  }
  if (url.protocol !== "https:") throw new TypeError("exactLandingUrl must use https");

  return {
    offerId: requiredString(input.offerId, "offerId", 160),
    partnerId: requiredString(input.partnerId, "partnerId", 160),
    providerName: requiredString(input.providerName, "providerName", 160),
    planName: requiredString(input.planName, "planName", 240),
    category: requiredString(input.category, "category", 80),
    exactLandingUrl: url.toString(),
    monetizationModel,
    commissionValue: nonNegativeMoney(input.commissionValue || 0, "commissionValue"),
    commissionRate: Number(input.commissionRate || 0),
    currency: requiredString(input.currency || "ILS", "currency", 8).toUpperCase(),
    active: input.active === true,
  };
}

function calculateCommission({ offer, conversionValue = 0 }) {
  const normalized = normalizePartnerOffer(offer);
  if (!normalized.active) return 0;

  switch (normalized.monetizationModel) {
    case "CPL":
    case "CPA":
    case "DIRECT":
      return normalized.commissionValue;
    case "REVENUE_SHARE": {
      const rate = Number(normalized.commissionRate);
      if (!Number.isFinite(rate) || rate < 0 || rate > 1) {
        throw new TypeError("commissionRate must be between 0 and 1");
      }
      return Math.round(nonNegativeMoney(conversionValue, "conversionValue") * rate * 100) / 100;
    }
    default:
      throw new TypeError("monetizationModel is unsupported");
  }
}

function normalizeAttributionEvent(input) {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    throw new TypeError("attribution event must be an object");
  }
  const type = requiredString(input.type, "type", 30);
  if (type === "CLICK") {
    const status = requiredString(input.status || "CREATED", "status", 30);
    if (!ALLOWED_CLICK_STATUS.has(status)) throw new TypeError("click status is unsupported");
  } else if (type === "CONVERSION") {
    const status = requiredString(input.status || "PENDING", "status", 30);
    if (!ALLOWED_CONVERSION_STATUS.has(status)) throw new TypeError("conversion status is unsupported");
  } else {
    throw new TypeError("attribution event type is unsupported");
  }

  return {
    type,
    clickId: requiredString(input.clickId, "clickId", 128),
    offerId: requiredString(input.offerId, "offerId", 160),
    partnerId: requiredString(input.partnerId, "partnerId", 160),
    opportunityId: requiredString(input.opportunityId, "opportunityId", 160),
    status: requiredString(input.status, "status", 30),
    occurredAt: requiredString(input.occurredAt, "occurredAt", 64),
    conversionValue: nonNegativeMoney(input.conversionValue || 0, "conversionValue"),
  };
}

module.exports = {
  ALLOWED_MODELS,
  ALLOWED_CLICK_STATUS,
  ALLOWED_CONVERSION_STATUS,
  createClickId,
  normalizePartnerOffer,
  calculateCommission,
  normalizeAttributionEvent,
};
