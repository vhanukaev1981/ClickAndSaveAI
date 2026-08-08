"use strict";

const { CAPABILITIES, assertAdapterCapability } = require("./providerAdapterRegistry");
const { assertDispatchAllowed, nextRetry } = require("./providerIntegrationFramework");
const { QUEUE_STATES } = require("./providerDispatchEnvelope");
const { isContractActive } = require("./providerContractConfig");

const PLAN_ACTIONS = Object.freeze({
  DISPATCH: "DISPATCH",
  WAIT: "WAIT",
  DEAD_LETTER: "DEAD_LETTER",
  NOOP: "NOOP",
});

function requiredObject(value, field) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new TypeError(`${field} must be an object`);
  }
  return value;
}

function planDispatch(input) {
  const source = requiredObject(input, "dispatch planner input");
  const envelope = requiredObject(source.envelope, "envelope");
  const providerConfig = assertDispatchAllowed(requiredObject(source.providerConfig, "providerConfig"));
  const contract = requiredObject(source.contract, "contract");
  const registry = source.adapterRegistry;
  const nowMs = Number.isFinite(Number(source.nowMs)) ? Number(source.nowMs) : Date.now();

  if (envelope.providerId !== providerConfig.providerId) {
    throw new Error("dispatch provider does not match provider config");
  }
  if (envelope.adapterKey !== providerConfig.adapterKey) {
    throw new Error("dispatch adapter does not match provider config");
  }
  if (envelope.providerId !== contract.providerId || envelope.contractId !== contract.contractId) {
    throw new Error("dispatch does not match provider contract");
  }
  if (envelope.adapterKey !== contract.adapterKey) {
    throw new Error("dispatch adapter does not match provider contract");
  }

  assertAdapterCapability(registry, envelope.adapterKey, CAPABILITIES.DISPATCH);

  if (!isContractActive(contract, nowMs)) {
    return {
      action: PLAN_ACTIONS.NOOP,
      reason: "provider contract is inactive",
      dispatchId: envelope.dispatchId,
    };
  }

  if (envelope.state === QUEUE_STATES.ACKNOWLEDGED || envelope.state === QUEUE_STATES.DEAD_LETTER) {
    return {
      action: PLAN_ACTIONS.NOOP,
      reason: "dispatch is terminal",
      dispatchId: envelope.dispatchId,
    };
  }

  if (envelope.state === QUEUE_STATES.IN_FLIGHT) {
    return {
      action: PLAN_ACTIONS.WAIT,
      reason: "dispatch attempt already in flight",
      dispatchId: envelope.dispatchId,
    };
  }

  if (![QUEUE_STATES.READY, QUEUE_STATES.RETRY_WAIT].includes(envelope.state)) {
    throw new Error(`unsupported dispatch queue state: ${envelope.state}`);
  }

  if (envelope.state === QUEUE_STATES.RETRY_WAIT) {
    const retry = nextRetry(Math.max(1, Number(envelope.attempt) || 1), source.retryAfterMs, source.maxAttempts);
    if (retry.deadLetter) {
      return {
        action: PLAN_ACTIONS.DEAD_LETTER,
        reason: "retry limit reached",
        dispatchId: envelope.dispatchId,
        delayMs: 0,
      };
    }
    return {
      action: PLAN_ACTIONS.WAIT,
      reason: "retry backoff required",
      dispatchId: envelope.dispatchId,
      delayMs: retry.delayMs,
    };
  }

  return {
    action: PLAN_ACTIONS.DISPATCH,
    reason: "provider, contract and adapter capability verified",
    dispatchId: envelope.dispatchId,
  };
}

module.exports = {
  PLAN_ACTIONS,
  planDispatch,
};
