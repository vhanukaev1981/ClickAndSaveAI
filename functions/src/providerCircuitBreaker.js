"use strict";

const CIRCUIT_STATES = Object.freeze({
  CLOSED: "CLOSED",
  OPEN: "OPEN",
  HALF_OPEN: "HALF_OPEN",
});

const DEFAULT_FAILURE_THRESHOLD = 5;
const DEFAULT_RECOVERY_TIMEOUT_MS = 60_000;
const MAX_RECOVERY_TIMEOUT_MS = 6 * 60 * 60 * 1000;

function integer(value, field, { min = 0, max = Number.MAX_SAFE_INTEGER, fallback } = {}) {
  const raw = value == null && fallback != null ? fallback : Number(value);
  if (!Number.isInteger(raw) || raw < min || raw > max) {
    throw new TypeError(`${field} must be an integer between ${min} and ${max}`);
  }
  return raw;
}

function normalizeCircuitState(input = {}) {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    throw new TypeError("circuit state must be an object");
  }

  const state = input.state || CIRCUIT_STATES.CLOSED;
  if (!Object.values(CIRCUIT_STATES).includes(state)) {
    throw new TypeError(`unsupported circuit state: ${state}`);
  }

  const failureThreshold = integer(input.failureThreshold, "failureThreshold", {
    min: 1,
    max: 100,
    fallback: DEFAULT_FAILURE_THRESHOLD,
  });
  const recoveryTimeoutMs = integer(input.recoveryTimeoutMs, "recoveryTimeoutMs", {
    min: 1_000,
    max: MAX_RECOVERY_TIMEOUT_MS,
    fallback: DEFAULT_RECOVERY_TIMEOUT_MS,
  });
  const consecutiveFailures = integer(input.consecutiveFailures, "consecutiveFailures", {
    min: 0,
    max: 1_000_000,
    fallback: 0,
  });
  const openedAtMs = input.openedAtMs == null ? null : Number(input.openedAtMs);
  if (openedAtMs != null && !Number.isFinite(openedAtMs)) {
    throw new TypeError("openedAtMs must be a finite timestamp");
  }

  if (state === CIRCUIT_STATES.OPEN && openedAtMs == null) {
    throw new TypeError("OPEN circuit requires openedAtMs");
  }

  return {
    state,
    failureThreshold,
    recoveryTimeoutMs,
    consecutiveFailures,
    openedAtMs,
    probeInFlight: input.probeInFlight === true,
  };
}

function evaluateCircuit(input = {}, nowMs = Date.now()) {
  const circuit = normalizeCircuitState(input);
  const now = Number(nowMs);
  if (!Number.isFinite(now)) throw new TypeError("nowMs must be finite");

  if (circuit.state === CIRCUIT_STATES.CLOSED) {
    return { state: CIRCUIT_STATES.CLOSED, allowRequest: true, delayMs: 0, probe: false };
  }

  if (circuit.state === CIRCUIT_STATES.OPEN) {
    const elapsed = Math.max(0, now - circuit.openedAtMs);
    if (elapsed < circuit.recoveryTimeoutMs) {
      return {
        state: CIRCUIT_STATES.OPEN,
        allowRequest: false,
        delayMs: circuit.recoveryTimeoutMs - elapsed,
        probe: false,
      };
    }
    return {
      state: CIRCUIT_STATES.HALF_OPEN,
      allowRequest: !circuit.probeInFlight,
      delayMs: 0,
      probe: !circuit.probeInFlight,
    };
  }

  return {
    state: CIRCUIT_STATES.HALF_OPEN,
    allowRequest: !circuit.probeInFlight,
    delayMs: 0,
    probe: !circuit.probeInFlight,
  };
}

function recordCircuitSuccess(input = {}) {
  const circuit = normalizeCircuitState(input);
  return {
    ...circuit,
    state: CIRCUIT_STATES.CLOSED,
    consecutiveFailures: 0,
    openedAtMs: null,
    probeInFlight: false,
  };
}

function recordCircuitFailure(input = {}, nowMs = Date.now()) {
  const circuit = normalizeCircuitState(input);
  const now = Number(nowMs);
  if (!Number.isFinite(now)) throw new TypeError("nowMs must be finite");

  const consecutiveFailures = circuit.consecutiveFailures + 1;
  const mustOpen = circuit.state === CIRCUIT_STATES.HALF_OPEN ||
    circuit.state === CIRCUIT_STATES.OPEN ||
    consecutiveFailures >= circuit.failureThreshold;

  return {
    ...circuit,
    state: mustOpen ? CIRCUIT_STATES.OPEN : CIRCUIT_STATES.CLOSED,
    consecutiveFailures,
    openedAtMs: mustOpen ? now : null,
    probeInFlight: false,
  };
}

module.exports = {
  CIRCUIT_STATES,
  DEFAULT_FAILURE_THRESHOLD,
  DEFAULT_RECOVERY_TIMEOUT_MS,
  MAX_RECOVERY_TIMEOUT_MS,
  normalizeCircuitState,
  evaluateCircuit,
  recordCircuitSuccess,
  recordCircuitFailure,
};
