"use strict";

const crypto = require("node:crypto");
const { buildProviderDispatchPayload } = require("./providerDispatch");

const QUEUE_STATES = Object.freeze({
  READY: "READY",
  IN_FLIGHT: "IN_FLIGHT",
  ACKNOWLEDGED: "ACKNOWLEDGED",
  RETRY_WAIT: "RETRY_WAIT",
  DEAD_LETTER: "DEAD_LETTER",
});

const ALLOWED_PROVIDER_PAYLOAD_KEYS = new Set([
  "leadId",
  "contactName",
  "phone",
  "contactEmail",
  "requestedProvider",
  "category",
  "offerId",
  "consentVersion",
  "source",
]);

function required(value, field, maxLength = 200) {
  const text = typeof value === "string" ? value.trim() : "";
  if (!text) throw new TypeError(`${field} is required`);
  if (text.length > maxLength) throw new TypeError(`${field} exceeds ${maxLength} characters`);
  return text;
}

function dispatchId({ leadId, providerId, offerId, contractId }) {
  const material = [
    required(leadId, "leadId", 128),
    required(providerId, "providerId", 128),
    required(offerId, "offerId", 128),
    required(contractId, "contractId", 128),
  ].join(":");
  return crypto.createHash("sha256").update(material).digest("hex");
}

function normalizeMinimalProviderPayload(rawPayload, { leadId, offerId }) {
  if (!rawPayload || typeof rawPayload !== "object" || Array.isArray(rawPayload)) {
    throw new TypeError("payload is required");
  }

  for (const key of Object.keys(rawPayload)) {
    if (!ALLOWED_PROVIDER_PAYLOAD_KEYS.has(key)) {
      throw new TypeError(`provider payload contains unsupported field: ${key}`);
    }
  }

  const payload = buildProviderDispatchPayload(rawPayload);
  if (!payload) throw new TypeError("provider payload is incomplete");
  if (payload.leadId !== leadId) {
    throw new TypeError("provider payload leadId does not match dispatch leadId");
  }
  if (payload.offerId !== offerId) {
    throw new TypeError("provider payload offerId does not match dispatch offerId");
  }
  if (rawPayload.source && rawPayload.source !== payload.source) {
    throw new TypeError("provider payload source is unsupported");
  }

  return payload;
}

function buildDispatchEnvelope(input) {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    throw new TypeError("dispatch input must be an object");
  }
  const leadId = required(input.leadId, "leadId", 128);
  const providerId = required(input.providerId, "providerId", 128);
  const offerId = required(input.offerId, "offerId", 128);
  const contractId = required(input.contractId, "contractId", 128);
  const adapterKey = required(input.adapterKey, "adapterKey", 128);
  const payload = normalizeMinimalProviderPayload(input.payload, { leadId, offerId });

  return {
    dispatchId: dispatchId({ leadId, providerId, offerId, contractId }),
    leadId,
    providerId,
    offerId,
    contractId,
    adapterKey,
    state: QUEUE_STATES.READY,
    attempt: 0,
    payload,
    providerReference: "",
    lastErrorCode: "",
  };
}

function beginAttempt(envelope) {
  if (!envelope || envelope.state === QUEUE_STATES.ACKNOWLEDGED || envelope.state === QUEUE_STATES.DEAD_LETTER) {
    throw new Error("terminal dispatch cannot start another attempt");
  }
  if (![QUEUE_STATES.READY, QUEUE_STATES.RETRY_WAIT].includes(envelope.state)) {
    throw new Error("dispatch is not ready for an attempt");
  }
  return {
    ...envelope,
    state: QUEUE_STATES.IN_FLIGHT,
    attempt: Math.max(0, Number(envelope.attempt) || 0) + 1,
  };
}

function applyAcknowledgement(envelope, providerReference) {
  if (!envelope || envelope.state !== QUEUE_STATES.IN_FLIGHT) {
    throw new Error("only in-flight dispatch can be acknowledged");
  }
  return {
    ...envelope,
    state: QUEUE_STATES.ACKNOWLEDGED,
    providerReference: required(providerReference, "providerReference", 200),
    lastErrorCode: "",
  };
}

function applyFailure(envelope, { retryable, errorCode, deadLetter = false }) {
  if (!envelope || envelope.state !== QUEUE_STATES.IN_FLIGHT) {
    throw new Error("only in-flight dispatch can fail");
  }
  const terminal = deadLetter === true || retryable !== true;
  return {
    ...envelope,
    state: terminal ? QUEUE_STATES.DEAD_LETTER : QUEUE_STATES.RETRY_WAIT,
    lastErrorCode: required(errorCode, "errorCode", 100),
  };
}

module.exports = {
  QUEUE_STATES,
  dispatchId,
  buildDispatchEnvelope,
  beginAttempt,
  applyAcknowledgement,
  applyFailure,
};
