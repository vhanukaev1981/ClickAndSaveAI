"use strict";

const { requiredString, optionalString } = require("./validation");

const PROVIDER_INTEGRATION_STATES = Object.freeze({
  DISABLED: "DISABLED",
  READY_FOR_ADAPTER: "READY_FOR_ADAPTER",
  ACTUALLY_CONNECTED: "ACTUALLY_CONNECTED",
  DEGRADED: "DEGRADED",
});

const DISPATCH_STATES = Object.freeze({
  PENDING: "PENDING",
  RETRY_SCHEDULED: "RETRY_SCHEDULED",
  DELIVERED: "DELIVERED",
  DEAD_LETTER: "DEAD_LETTER",
});

const DEFAULT_RETRY_POLICY = Object.freeze({
  maxAttempts: 5,
  baseDelaySeconds: 60,
  maxDelaySeconds: 3600,
});

const FORBIDDEN_PROVIDER_PAYLOAD_KEYS = new Set([
  "gmail",
  "gmailcontent",
  "messagebody",
  "rawemail",
  "rawpdf",
  "currentmonthlycost",
  "currentspend",
  "commissiontype",
  "commissionvalue",
  "actualcommissionamount",
]);

function normalizedKey(value) {
  return String(value || "").trim().toLowerCase().replace(/[^a-z0-9]/g, "");
}

function assertPrivacySafeProviderPayload(payload) {
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
    throw new TypeError("provider payload must be an object");
  }
  for (const key of Object.keys(payload)) {
    if (FORBIDDEN_PROVIDER_PAYLOAD_KEYS.has(normalizedKey(key))) {
      throw new TypeError(`provider payload contains forbidden field: ${key}`);
    }
  }
  return payload;
}

function validateRetryPolicy(raw = {}) {
  const maxAttempts = Number(raw.maxAttempts ?? DEFAULT_RETRY_POLICY.maxAttempts);
  const baseDelaySeconds = Number(raw.baseDelaySeconds ?? DEFAULT_RETRY_POLICY.baseDelaySeconds);
  const maxDelaySeconds = Number(raw.maxDelaySeconds ?? DEFAULT_RETRY_POLICY.maxDelaySeconds);
  if (!Number.isInteger(maxAttempts) || maxAttempts < 1 || maxAttempts > 20) {
    throw new TypeError("retryPolicy.maxAttempts must be between 1 and 20");
  }
  if (!Number.isInteger(baseDelaySeconds) || baseDelaySeconds < 10 || baseDelaySeconds > 3600) {
    throw new TypeError("retryPolicy.baseDelaySeconds must be between 10 and 3600");
  }
  if (!Number.isInteger(maxDelaySeconds) || maxDelaySeconds < baseDelaySeconds || maxDelaySeconds > 86400) {
    throw new TypeError("retryPolicy.maxDelaySeconds is invalid");
  }
  return { maxAttempts, baseDelaySeconds, maxDelaySeconds };
}

function validateProviderIntegrationConfig(data) {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    throw new TypeError("provider integration config must be an object");
  }
  const providerId = requiredString(data.providerId, "providerId", 128);
  if (!/^[A-Za-z0-9._:-]+$/.test(providerId)) {
    throw new TypeError("providerId contains unsupported characters");
  }
  const providerName = requiredString(data.providerName, "providerName", 160);
  const adapterKey = requiredString(data.adapterKey, "adapterKey", 128);
  if (!/^[A-Za-z0-9._:-]+$/.test(adapterKey)) {
    throw new TypeError("adapterKey contains unsupported characters");
  }
  const contractVersion = requiredString(data.contractVersion || "v1", "contractVersion", 40);
  const credentialBindingName = optionalString(
    data.credentialBindingName,
    "credentialBindingName",
    160
  );
  if (credentialBindingName && !/^[A-Za-z0-9._:-]+$/.test(credentialBindingName)) {
    throw new TypeError("credentialBindingName contains unsupported characters");
  }
  for (const forbidden of ["apiKey", "token", "password", "secret", "clientSecret"]) {
    if (data[forbidden] !== undefined && String(data[forbidden] || "").trim()) {
      throw new TypeError(`${forbidden} must not be stored in provider integration config`);
    }
  }
  return {
    providerId,
    providerName,
    adapterKey,
    contractVersion,
    credentialBindingName,
    enabled: data.enabled === true,
    supportsDeliveryReceipts: data.supportsDeliveryReceipts === true,
    supportsCommissionReconciliation: data.supportsCommissionReconciliation === true,
    retryPolicy: validateRetryPolicy(data.retryPolicy || {}),
  };
}

function integrationStatus(config, runtime = {}) {
  if (!config?.enabled) {
    return {
      state: PROVIDER_INTEGRATION_STATES.DISABLED,
      reason: "Provider integration is disabled.",
    };
  }
  if (runtime.adapterRegistered !== true) {
    return {
      state: PROVIDER_INTEGRATION_STATES.READY_FOR_ADAPTER,
      reason: "Configuration is valid but no authorized runtime adapter is registered.",
    };
  }
  if (runtime.healthVerified !== true) {
    return {
      state: PROVIDER_INTEGRATION_STATES.DEGRADED,
      reason: "Runtime adapter exists but current provider health/authority is not verified.",
    };
  }
  return {
    state: PROVIDER_INTEGRATION_STATES.ACTUALLY_CONNECTED,
    reason: "Authorized adapter is registered and current provider health is verified.",
  };
}

function retryDelaySeconds(attemptNumber, policy = DEFAULT_RETRY_POLICY) {
  const normalized = validateRetryPolicy(policy);
  const attempt = Number(attemptNumber);
  if (!Number.isInteger(attempt) || attempt < 1) throw new TypeError("attemptNumber must be positive");
  const delay = normalized.baseDelaySeconds * (2 ** Math.max(0, attempt - 1));
  return Math.min(delay, normalized.maxDelaySeconds);
}

function normalizeAdapterDispatchEvidence(result) {
  if (!result || typeof result !== "object") throw new TypeError("adapter dispatch evidence is required");
  if (typeof result.submissionAccepted !== "boolean") {
    throw new TypeError("submissionAccepted must be explicit");
  }
  if (typeof result.deliveryConfirmed !== "boolean") {
    throw new TypeError("deliveryConfirmed must be explicit");
  }
  if (result.deliveryConfirmed && !result.submissionAccepted) {
    throw new TypeError("delivery cannot be confirmed when submission was not accepted");
  }
  const externalReceiptReference = optionalString(
    result.externalReceiptReference,
    "externalReceiptReference",
    200
  );
  if (result.deliveryConfirmed && !externalReceiptReference) {
    throw new TypeError("confirmed delivery requires provider receipt evidence");
  }
  return {
    submissionAccepted: result.submissionAccepted,
    deliveryConfirmed: result.deliveryConfirmed,
    externalReceiptReference,
    failureCode: optionalString(result.failureCode, "failureCode", 120),
    retryable: result.retryable === true,
  };
}

function nextDispatchState(record, evidence, nowMs = Date.now()) {
  const attempts = Number(record?.attempts || 0) + 1;
  const policy = validateRetryPolicy(record?.retryPolicy || {});
  const normalizedEvidence = normalizeAdapterDispatchEvidence(evidence);
  if (normalizedEvidence.deliveryConfirmed) {
    return {
      status: DISPATCH_STATES.DELIVERED,
      attempts,
      nextAttemptAtMs: null,
      evidence: normalizedEvidence,
    };
  }
  if (normalizedEvidence.retryable && attempts < policy.maxAttempts) {
    return {
      status: DISPATCH_STATES.RETRY_SCHEDULED,
      attempts,
      nextAttemptAtMs: Number(nowMs) + (retryDelaySeconds(attempts, policy) * 1000),
      evidence: normalizedEvidence,
    };
  }
  return {
    status: DISPATCH_STATES.DEAD_LETTER,
    attempts,
    nextAttemptAtMs: null,
    evidence: normalizedEvidence,
  };
}

function validateCommissionReconciliationEvidence(data) {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    throw new TypeError("commission reconciliation evidence must be an object");
  }
  const leadId = requiredString(data.leadId, "leadId", 128);
  const externalReference = requiredString(data.externalReference, "externalReference", 200);
  const amount = Number(data.actualCommissionAmount);
  if (!Number.isFinite(amount) || amount <= 0 || amount > 1_000_000) {
    throw new TypeError("actualCommissionAmount must be a positive verified amount");
  }
  const currency = requiredString(data.currency || "ILS", "currency", 8).toUpperCase();
  return {
    leadId,
    externalReference,
    actualCommissionAmount: Math.round(amount * 100) / 100,
    currency,
  };
}

function validateAdapterContract(adapter) {
  if (!adapter || typeof adapter !== "object") throw new TypeError("provider adapter is required");
  if (typeof adapter.dispatch !== "function") throw new TypeError("provider adapter must implement dispatch()");
  if (adapter.reconcileCommission !== undefined && typeof adapter.reconcileCommission !== "function") {
    throw new TypeError("reconcileCommission must be a function when implemented");
  }
  if (adapter.healthCheck !== undefined && typeof adapter.healthCheck !== "function") {
    throw new TypeError("healthCheck must be a function when implemented");
  }
  return adapter;
}

module.exports = {
  PROVIDER_INTEGRATION_STATES,
  DISPATCH_STATES,
  DEFAULT_RETRY_POLICY,
  assertPrivacySafeProviderPayload,
  validateRetryPolicy,
  validateProviderIntegrationConfig,
  integrationStatus,
  retryDelaySeconds,
  normalizeAdapterDispatchEvidence,
  nextDispatchState,
  validateCommissionReconciliationEvidence,
  validateAdapterContract,
};
