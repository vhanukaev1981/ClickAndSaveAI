"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");

const {
  actorRef,
  buildOperationalPayload,
  emitOperationalEvent,
  _sanitizeOperationalDetails,
} = require("../src/operationalTelemetry");

test("operational telemetry uses deterministic irreversible actor references instead of raw uid", () => {
  const first = actorRef("user-123");
  const second = actorRef("user-123");

  assert.equal(first, second);
  assert.match(first, /^[0-9a-f]{24}$/);
  assert.notEqual(first, "user-123");
});

test("operational payload contains the canonical production operations envelope", () => {
  const payload = buildOperationalPayload({
    event: "gmail.oauth.connect",
    subsystem: "gmail",
    outcome: "success",
    severity: "INFO",
    code: "GMAIL_OAUTH_CONNECTED",
    uid: "user-123",
    correlationId: "corr-123",
    details: { status: 200, phase: "token_exchange" },
  });

  assert.deepEqual(payload, {
    schemaVersion: 1,
    event: "gmail.oauth.connect",
    subsystem: "gmail",
    outcome: "success",
    severity: "INFO",
    code: "GMAIL_OAUTH_CONNECTED",
    correlationId: "corr-123",
    actorRef: actorRef("user-123"),
    details: { status: 200, phase: "token_exchange" },
  });
  assert.equal(Object.hasOwn(payload, "uid"), false);
});

test("operational details remove secret, request-body and directly identifying fields recursively", () => {
  const safe = _sanitizeOperationalDetails({
    status: 401,
    accessToken: "access-secret",
    refresh_token: "refresh-secret",
    authorizationCode: "oauth-code",
    email: "person@example.com",
    client_secret: "client-secret",
    requestBody: { password: "pw", mode: "disconnect" },
    nested: {
      encryptionKey: "key-material",
      credential: "credential-material",
      reason: "provider_cleanup",
    },
  });

  assert.deepEqual(safe, {
    status: 401,
    nested: { reason: "provider_cleanup" },
  });
});

test("operational details are bounded to prevent unbounded log payloads", () => {
  const safe = _sanitizeOperationalDetails({
    message: "x".repeat(900),
    values: Array.from({ length: 30 }, (_, index) => index),
  });

  assert.equal(safe.message.length, 512);
  assert.equal(safe.values.length, 20);
});

test("operational payload rejects malformed canonical fields", () => {
  const base = {
    event: "gmail.oauth.connect",
    subsystem: "gmail",
    outcome: "success",
    severity: "INFO",
    code: "GMAIL_OAUTH_CONNECTED",
  };

  assert.throws(() => buildOperationalPayload({ ...base, event: "GMAIL CONNECT" }), /event/);
  assert.throws(() => buildOperationalPayload({ ...base, subsystem: "Gmail OAuth" }), /subsystem/);
  assert.throws(() => buildOperationalPayload({ ...base, outcome: "maybe" }), /outcome/);
  assert.throws(() => buildOperationalPayload({ ...base, severity: "NOTICE" }), /severity/);
  assert.throws(() => buildOperationalPayload({ ...base, code: "gmail-connected" }), /code/);
});

test("emitOperationalEvent returns the same sanitized canonical payload", () => {
  const payload = emitOperationalEvent({
    event: "privacy.account.delete",
    subsystem: "privacy",
    outcome: "retry_required",
    severity: "WARNING",
    code: "ACCOUNT_DELETE_RETRY_REQUIRED",
    uid: "user-456",
    details: { refreshToken: "forbidden", reason: "gmail_provider_cleanup" },
  });

  assert.equal(payload.event, "privacy.account.delete");
  assert.equal(payload.actorRef, actorRef("user-456"));
  assert.deepEqual(payload.details, { reason: "gmail_provider_cleanup" });
  assert.equal(Object.hasOwn(payload, "uid"), false);
});
