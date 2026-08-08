"use strict";

const ROTATION_STATES = Object.freeze({
  HEALTHY: "HEALTHY",
  ROTATE_SOON: "ROTATE_SOON",
  EXPIRED: "EXPIRED",
  UNCONFIGURED: "UNCONFIGURED",
});

function evaluateCredentialRotation(input = {}) {
  if (!input || typeof input !== "object" || Array.isArray(input)) throw new TypeError("credential rotation input must be an object");
  const expiresAt = typeof input.expiresAt === "string" ? Date.parse(input.expiresAt) : NaN;
  if (!Number.isFinite(expiresAt)) return { state: ROTATION_STATES.UNCONFIGURED, reason: "credential expiry is not configured" };

  const nowMs = Number.isFinite(Number(input.nowMs)) ? Number(input.nowMs) : Date.now();
  const rotateBeforeMs = Number.isFinite(Number(input.rotateBeforeMs)) && Number(input.rotateBeforeMs) > 0
    ? Number(input.rotateBeforeMs)
    : 7 * 24 * 60 * 60 * 1000;
  const remainingMs = expiresAt - nowMs;

  if (remainingMs <= 0) return { state: ROTATION_STATES.EXPIRED, reason: "provider credential expired", remainingMs: 0, blocksDispatch: true };
  if (remainingMs <= rotateBeforeMs) return { state: ROTATION_STATES.ROTATE_SOON, reason: "provider credential rotation window reached", remainingMs, blocksDispatch: false };
  return { state: ROTATION_STATES.HEALTHY, reason: "provider credential within validity window", remainingMs, blocksDispatch: false };
}

module.exports = {
  ROTATION_STATES,
  evaluateCredentialRotation,
};
