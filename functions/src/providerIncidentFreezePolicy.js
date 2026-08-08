"use strict";

const FREEZE_REASONS = new Set([
  "WEBHOOK_AUTH_FAILURE",
  "CREDENTIAL_COMPROMISE",
  "PROVIDER_OUTAGE",
  "DATA_INTEGRITY_RISK",
  "MANUAL_OPERATOR_FREEZE",
]);

function requiredText(value, field, maxLength = 200) {
  const text = typeof value === "string" ? value.trim() : "";
  if (!text) throw new TypeError(`${field} is required`);
  if (text.length > maxLength) throw new TypeError(`${field} exceeds ${maxLength} characters`);
  return text;
}

function evaluateProviderFreeze(input = {}) {
  if (!input || typeof input !== "object" || Array.isArray(input)) throw new TypeError("provider freeze input must be an object");
  const providerId = requiredText(input.providerId, "providerId", 128);
  if (input.frozen !== true) {
    return { providerId, frozen: false, blocksDispatch: false, reason: "provider integration is not frozen" };
  }
  const reason = requiredText(input.reason, "reason", 64).toUpperCase();
  if (!FREEZE_REASONS.has(reason)) throw new TypeError(`unsupported provider freeze reason: ${reason}`);
  const frozenAt = requiredText(input.frozenAt, "frozenAt", 64);
  if (!Number.isFinite(Date.parse(frozenAt))) throw new TypeError("frozenAt must be a valid timestamp");
  return {
    providerId,
    frozen: true,
    blocksDispatch: true,
    reason,
    frozenAt: new Date(Date.parse(frozenAt)).toISOString(),
    operatorReference: typeof input.operatorReference === "string" ? input.operatorReference.trim().slice(0, 200) : "",
  };
}

module.exports = {
  FREEZE_REASONS,
  evaluateProviderFreeze,
};
