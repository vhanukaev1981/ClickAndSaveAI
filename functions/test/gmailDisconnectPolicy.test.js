"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { providerCleanupConfirmed } = require("../src/gmailDisconnectPolicy");

test("provider cleanup is confirmed when no Google credential exists", () => {
  assert.equal(providerCleanupConfirmed("NO_CREDENTIAL", "NO_CREDENTIAL"), true);
  assert.equal(providerCleanupConfirmed("NO_CONNECTION", "NO_CONNECTION"), true);
});

test("OAuth revocation confirmation is authoritative even if watch stop was not confirmed", () => {
  assert.equal(providerCleanupConfirmed("UNCONFIRMED_EXTERNAL_ERROR", "CONFIRMED"), true);
  assert.equal(
    providerCleanupConfirmed("UNCONFIRMED_EXTERNAL_ERROR", "CONFIRMED_OR_ALREADY_INVALID"),
    true
  );
});

test("provider cleanup remains retry-required when OAuth revocation is unconfirmed", () => {
  assert.equal(providerCleanupConfirmed("CONFIRMED", "UNCONFIRMED_EXTERNAL_ERROR"), false);
  assert.equal(providerCleanupConfirmed("CONFIRMED", "UNCONFIRMED_HTTP_503"), false);
  assert.equal(
    providerCleanupConfirmed("UNCONFIRMED_CREDENTIAL_ERROR", "UNCONFIRMED_CREDENTIAL_ERROR"),
    false
  );
});
