"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  DEFAULT_REPLAY_WINDOW_MS,
  deriveWebhookSignature,
  verifyProviderWebhook,
} = require("../src/providerWebhookVerification");

const NOW_MS = Date.parse("2026-08-08T20:45:00Z");
const SECRET = "unit-test-secret-material";
const BODY = JSON.stringify({ eventId: "evt-1", status: "ACTIVATED" });

function signedInput(overrides = {}) {
  const timestampMs = overrides.timestampMs ?? NOW_MS - 30_000;
  return {
    rawBody: BODY,
    timestampMs,
    nowMs: NOW_MS,
    secretMaterial: SECRET,
    signature: deriveWebhookSignature({
      rawBody: BODY,
      timestampMs,
      secretMaterial: SECRET,
    }),
    ...overrides,
  };
}

test("valid provider webhook signature verifies inside the replay window", () => {
  const result = verifyProviderWebhook(signedInput());
  assert.equal(result.verified, true);
  assert.match(result.reason, /verified/);
  assert.equal(result.ageMs, 30_000);
});

test("tampered raw body fails signature verification", () => {
  const input = signedInput();
  const result = verifyProviderWebhook({ ...input, rawBody: `${BODY} ` });
  assert.equal(result.verified, false);
  assert.match(result.reason, /mismatch/);
});

test("wrong secret fails signature verification", () => {
  const result = verifyProviderWebhook({
    ...signedInput(),
    secretMaterial: "wrong-secret",
  });
  assert.equal(result.verified, false);
});

test("stale webhook is rejected before evidence ingestion", () => {
  const timestampMs = NOW_MS - DEFAULT_REPLAY_WINDOW_MS - 1;
  const result = verifyProviderWebhook(signedInput({
    timestampMs,
    signature: deriveWebhookSignature({ rawBody: BODY, timestampMs, secretMaterial: SECRET }),
  }));
  assert.equal(result.verified, false);
  assert.match(result.reason, /replay window/);
});

test("future webhook outside the replay window is rejected", () => {
  const timestampMs = NOW_MS + DEFAULT_REPLAY_WINDOW_MS + 1;
  const result = verifyProviderWebhook(signedInput({
    timestampMs,
    signature: deriveWebhookSignature({ rawBody: BODY, timestampMs, secretMaterial: SECRET }),
  }));
  assert.equal(result.verified, false);
  assert.match(result.reason, /replay window/);
});

test("malformed signatures cannot be treated as verified", () => {
  assert.throws(() => verifyProviderWebhook(signedInput({ signature: "bad" })), /sha256=/);
  assert.throws(() => verifyProviderWebhook(signedInput({ signature: "sha256=abcd" })), /64-character/);
});

test("verification does not expose secret material in its result", () => {
  const result = verifyProviderWebhook(signedInput());
  assert.equal(Object.hasOwn(result, "secretMaterial"), false);
  assert.equal(JSON.stringify(result).includes(SECRET), false);
});
