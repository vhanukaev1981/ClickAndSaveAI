"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { ROTATION_STATES, evaluateCredentialRotation } = require("../src/providerCredentialRotationPolicy");

const NOW = Date.parse("2026-08-08T20:00:00Z");

test("missing expiry remains explicitly unconfigured", () => {
  assert.equal(evaluateCredentialRotation({ nowMs: NOW }).state, ROTATION_STATES.UNCONFIGURED);
});

test("healthy credential stays active", () => {
  const result = evaluateCredentialRotation({ nowMs: NOW, expiresAt: "2026-09-08T20:00:00Z" });
  assert.equal(result.state, ROTATION_STATES.HEALTHY);
  assert.equal(result.blocksDispatch, false);
});

test("credential near expiry asks for rotation without blocking yet", () => {
  const result = evaluateCredentialRotation({ nowMs: NOW, expiresAt: "2026-08-10T20:00:00Z" });
  assert.equal(result.state, ROTATION_STATES.ROTATE_SOON);
  assert.equal(result.blocksDispatch, false);
});

test("expired credential blocks dispatch", () => {
  const result = evaluateCredentialRotation({ nowMs: NOW, expiresAt: "2026-08-07T20:00:00Z" });
  assert.equal(result.state, ROTATION_STATES.EXPIRED);
  assert.equal(result.blocksDispatch, true);
});
