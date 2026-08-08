"use strict";

const crypto = require("node:crypto");

const DEFAULT_REPLAY_WINDOW_MS = 5 * 60 * 1000;
const MAX_REPLAY_WINDOW_MS = 60 * 60 * 1000;
const SIGNATURE_PREFIX = "sha256=";

function requiredText(value, field, maxLength = 4096) {
  const text = typeof value === "string" ? value.trim() : "";
  if (!text) throw new TypeError(`${field} is required`);
  if (text.length > maxLength) throw new TypeError(`${field} exceeds ${maxLength} characters`);
  return text;
}

function normalizeReplayWindow(value) {
  if (value == null) return DEFAULT_REPLAY_WINDOW_MS;
  const number = Number(value);
  if (!Number.isInteger(number) || number <= 0 || number > MAX_REPLAY_WINDOW_MS) {
    throw new TypeError(`replayWindowMs must be an integer between 1 and ${MAX_REPLAY_WINDOW_MS}`);
  }
  return number;
}

function normalizeTimestampMs(value, field = "timestampMs") {
  const number = Number(value);
  if (!Number.isFinite(number) || number <= 0) throw new TypeError(`${field} must be a positive finite timestamp`);
  return Math.round(number);
}

function rawBodyBuffer(value) {
  if (Buffer.isBuffer(value)) return value;
  if (typeof value === "string") return Buffer.from(value, "utf8");
  throw new TypeError("rawBody must be a string or Buffer");
}

function normalizeSignature(value) {
  const text = requiredText(value, "signature", 256).toLowerCase();
  if (!text.startsWith(SIGNATURE_PREFIX)) {
    throw new TypeError("signature must use sha256= prefix");
  }
  const hex = text.slice(SIGNATURE_PREFIX.length);
  if (!/^[a-f0-9]{64}$/.test(hex)) throw new TypeError("signature must contain a 64-character sha256 hex digest");
  return hex;
}

function signingMaterial(timestampMs, body) {
  return Buffer.concat([
    Buffer.from(String(timestampMs), "utf8"),
    Buffer.from(".", "utf8"),
    body,
  ]);
}

function deriveWebhookSignature({ rawBody, timestampMs, secretMaterial }) {
  const body = rawBodyBuffer(rawBody);
  const timestamp = normalizeTimestampMs(timestampMs);
  const secret = requiredText(secretMaterial, "secretMaterial", 4096);
  const digest = crypto.createHmac("sha256", secret).update(signingMaterial(timestamp, body)).digest("hex");
  return `${SIGNATURE_PREFIX}${digest}`;
}

function verifyProviderWebhook(input = {}) {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    throw new TypeError("webhook verification input must be an object");
  }

  const body = rawBodyBuffer(input.rawBody);
  const timestampMs = normalizeTimestampMs(input.timestampMs);
  const nowMs = input.nowMs == null ? Date.now() : normalizeTimestampMs(input.nowMs, "nowMs");
  const replayWindowMs = normalizeReplayWindow(input.replayWindowMs);
  const secret = requiredText(input.secretMaterial, "secretMaterial", 4096);
  const suppliedHex = normalizeSignature(input.signature);
  const ageMs = Math.abs(nowMs - timestampMs);

  if (ageMs > replayWindowMs) {
    return {
      verified: false,
      reason: "webhook timestamp is outside the replay window",
      timestampMs,
      ageMs,
    };
  }

  const expectedHex = crypto.createHmac("sha256", secret)
    .update(signingMaterial(timestampMs, body))
    .digest("hex");
  const supplied = Buffer.from(suppliedHex, "hex");
  const expected = Buffer.from(expectedHex, "hex");
  const verified = supplied.length === expected.length && crypto.timingSafeEqual(supplied, expected);

  return {
    verified,
    reason: verified ? "provider webhook signature verified" : "provider webhook signature mismatch",
    timestampMs,
    ageMs,
  };
}

module.exports = {
  DEFAULT_REPLAY_WINDOW_MS,
  MAX_REPLAY_WINDOW_MS,
  deriveWebhookSignature,
  verifyProviderWebhook,
};
