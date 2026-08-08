"use strict";

function requiredText(value, field, maxLength = 200) {
  const text = typeof value === "string" ? value.trim() : "";
  if (!text) throw new TypeError(`${field} is required`);
  if (text.length > maxLength) throw new TypeError(`${field} exceeds ${maxLength} characters`);
  return text;
}

function verifyProviderDispatchIntent(input) {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    throw new TypeError("provider dispatch intent must be an object");
  }
  const accepted = input.accepted === true;
  const action = requiredText(input.action, "action", 64).toUpperCase();
  if (action !== "CONTINUE_TO_OFFER") throw new Error("provider dispatch requires explicit continue-to-offer intent");
  if (!accepted) throw new Error("provider dispatch requires explicit user acceptance");

  return {
    accepted: true,
    action,
    offerId: requiredText(input.offerId, "offerId", 128),
    providerId: requiredText(input.providerId, "providerId", 128),
    consentVersion: requiredText(input.consentVersion, "consentVersion", 50),
    acceptedAt: (() => {
      const value = requiredText(input.acceptedAt, "acceptedAt", 64);
      if (!Number.isFinite(Date.parse(value))) throw new TypeError("acceptedAt must be a valid timestamp");
      return new Date(Date.parse(value)).toISOString();
    })(),
  };
}

module.exports = {
  verifyProviderDispatchIntent,
};
