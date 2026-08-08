"use strict";

const EXCEPTION_TYPES = Object.freeze({
  DEAD_LETTER: "DEAD_LETTER",
  WEBHOOK_AUTH_FAILED: "WEBHOOK_AUTH_FAILED",
  SETTLEMENT_PARTIAL: "SETTLEMENT_PARTIAL",
  SETTLEMENT_OVERPAID: "SETTLEMENT_OVERPAID",
  SETTLEMENT_MISMATCH: "SETTLEMENT_MISMATCH",
  PROVIDER_DEGRADED: "PROVIDER_DEGRADED",
});

const SEVERITIES = Object.freeze({
  INFO: "INFO",
  WARNING: "WARNING",
  HIGH: "HIGH",
  CRITICAL: "CRITICAL",
});

function triageProviderException(input) {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    throw new TypeError("provider exception must be an object");
  }
  const type = typeof input.type === "string" ? input.type.trim().toUpperCase() : "";
  if (!Object.values(EXCEPTION_TYPES).includes(type)) throw new TypeError(`unsupported provider exception type: ${type}`);

  const mapping = {
    [EXCEPTION_TYPES.WEBHOOK_AUTH_FAILED]: { severity: SEVERITIES.CRITICAL, action: "VERIFY_WEBHOOK_SECRET_AND_SOURCE", blocksAutomation: true },
    [EXCEPTION_TYPES.DEAD_LETTER]: { severity: SEVERITIES.HIGH, action: "REVIEW_DEAD_LETTER_AND_PROVIDER_HEALTH", blocksAutomation: true },
    [EXCEPTION_TYPES.SETTLEMENT_MISMATCH]: { severity: SEVERITIES.HIGH, action: "RECONCILE_PARTNER_REFERENCE_AND_CURRENCY", blocksAutomation: true },
    [EXCEPTION_TYPES.SETTLEMENT_PARTIAL]: { severity: SEVERITIES.WARNING, action: "WAIT_OR_RECONCILE_REMAINING_PAYMENT", blocksAutomation: true },
    [EXCEPTION_TYPES.SETTLEMENT_OVERPAID]: { severity: SEVERITIES.WARNING, action: "REVIEW_OVERPAYMENT_BEFORE_POSTING", blocksAutomation: true },
    [EXCEPTION_TYPES.PROVIDER_DEGRADED]: { severity: SEVERITIES.WARNING, action: "REVIEW_PROVIDER_FAILURE_RATE", blocksAutomation: false },
  };

  return {
    type,
    providerId: typeof input.providerId === "string" ? input.providerId.trim().slice(0, 128) : "",
    reference: typeof input.reference === "string" ? input.reference.trim().slice(0, 200) : "",
    ...mapping[type],
  };
}

module.exports = {
  EXCEPTION_TYPES,
  SEVERITIES,
  triageProviderException,
};
