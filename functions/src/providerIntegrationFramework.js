"use strict";

const CONNECTION_STATES = Object.freeze({
  READY_FOR_ADAPTER: "READY_FOR_ADAPTER",
  ACTUALLY_CONNECTED: "ACTUALLY_CONNECTED",
  DISABLED: "DISABLED",
});

const DISPATCH_OUTCOMES = Object.freeze({
  ACKNOWLEDGED: "ACKNOWLEDGED",
  RETRYABLE_FAILURE: "RETRYABLE_FAILURE",
  PERMANENT_FAILURE: "PERMANENT_FAILURE",
});

const MAX_ATTEMPTS_DEFAULT = 5;
const MAX_BACKOFF_MS = 6 * 60 * 60 * 1000;

function requiredText(value, field, maxLength = 200) {
  const normalized = typeof value === "string" ? value.trim() : "";
  if (!normalized) throw new TypeError(`${field} is required`);
  if (normalized.length > maxLength) throw new TypeError(`${field} exceeds ${maxLength} characters`);
  return normalized;
}

function normalizeProviderConfig(input) {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    throw new TypeError("provider config must be an object");
  }

  const providerId = requiredText(input.providerId, "providerId", 128);
  const adapterKey = requiredText(input.adapterKey, "adapterKey", 128);
  const enabled = input.enabled === true;
  const adapterImplemented = input.adapterImplemented === true;
  const credentialsConfigured = input.credentialsConfigured === true;
  const connectionVerified = input.connectionVerified === true;

  let integrationState = CONNECTION_STATES.READY_FOR_ADAPTER;
  if (!enabled) integrationState = CONNECTION_STATES.DISABLED;
  else if (adapterImplemented && credentialsConfigured && connectionVerified) {
    integrationState = CONNECTION_STATES.ACTUALLY_CONNECTED;
  }

  return {
    providerId,
    adapterKey,
    enabled,
    adapterImplemented,
    credentialsConfigured,
    connectionVerified,
    integrationState,
  };
}

function assertDispatchAllowed(config) {
  const normalized = normalizeProviderConfig(config);
  if (normalized.integrationState !== CONNECTION_STATES.ACTUALLY_CONNECTED) {
    throw new Error("provider is not actually connected");
  }
  return normalized;
}

function normalizeAdapterResult(result) {
  if (!result || typeof result !== "object" || Array.isArray(result)) {
    throw new TypeError("adapter result must be an object");
  }
  const outcome = requiredText(result.outcome, "outcome", 40);
  if (!Object.values(DISPATCH_OUTCOMES).includes(outcome)) {
    throw new TypeError("adapter outcome is unsupported");
  }

  const providerReference = typeof result.providerReference === "string"
    ? result.providerReference.trim().slice(0, 200)
    : "";

  if (outcome === DISPATCH_OUTCOMES.ACKNOWLEDGED && !providerReference) {
    throw new TypeError("acknowledged dispatch requires providerReference evidence");
  }

  return {
    outcome,
    providerReference,
    retryAfterMs: Number.isFinite(Number(result.retryAfterMs))
      ? Math.max(0, Math.min(MAX_BACKOFF_MS, Math.round(Number(result.retryAfterMs))))
      : 0,
    evidenceType: typeof result.evidenceType === "string"
      ? result.evidenceType.trim().slice(0, 80)
      : "",
  };
}

function nextRetry(attempt, retryAfterMs = 0, maxAttempts = MAX_ATTEMPTS_DEFAULT) {
  const currentAttempt = Math.max(1, Math.floor(Number(attempt) || 1));
  const attemptsLimit = Math.max(1, Math.floor(Number(maxAttempts) || MAX_ATTEMPTS_DEFAULT));
  if (currentAttempt >= attemptsLimit) {
    return { shouldRetry: false, deadLetter: true, delayMs: 0 };
  }

  const exponential = Math.min(MAX_BACKOFF_MS, 30_000 * (2 ** (currentAttempt - 1)));
  const requested = Math.max(0, Math.min(MAX_BACKOFF_MS, Math.round(Number(retryAfterMs) || 0)));
  return {
    shouldRetry: true,
    deadLetter: false,
    delayMs: Math.max(exponential, requested),
  };
}

function normalizeLifecycleEvidence(input) {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    throw new TypeError("lifecycle evidence must be an object");
  }
  const stage = requiredText(input.stage, "stage", 40);
  if (!["CONTACTED", "QUOTED", "ACTIVATED", "COMMISSION_CONFIRMED"].includes(stage)) {
    throw new TypeError("unsupported lifecycle evidence stage");
  }

  const providerReference = requiredText(input.providerReference, "providerReference", 200);
  const evidenceSource = requiredText(input.evidenceSource, "evidenceSource", 80);
  const observedAt = requiredText(input.observedAt, "observedAt", 64);

  const amount = input.amount == null ? null : Number(input.amount);
  if (stage === "COMMISSION_CONFIRMED" && (!Number.isFinite(amount) || amount < 0)) {
    throw new TypeError("commission confirmation requires a non-negative amount");
  }

  return {
    stage,
    providerReference,
    evidenceSource,
    observedAt,
    amount: Number.isFinite(amount) ? Math.round(amount * 100) / 100 : null,
    currency: typeof input.currency === "string" && input.currency.trim()
      ? input.currency.trim().toUpperCase().slice(0, 8)
      : "ILS",
  };
}

module.exports = {
  CONNECTION_STATES,
  DISPATCH_OUTCOMES,
  MAX_ATTEMPTS_DEFAULT,
  normalizeProviderConfig,
  assertDispatchAllowed,
  normalizeAdapterResult,
  nextRetry,
  normalizeLifecycleEvidence,
};
