"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { triageProviderException, SEVERITIES } = require("../src/providerExceptionTriage");

test("webhook auth failure is critical and blocks automation", () => {
  const result = triageProviderException({ type: "WEBHOOK_AUTH_FAILED", providerId: "provider-a" });
  assert.equal(result.severity, SEVERITIES.CRITICAL);
  assert.equal(result.blocksAutomation, true);
});

test("partial settlement requires review before paid transition", () => {
  const result = triageProviderException({ type: "SETTLEMENT_PARTIAL" });
  assert.equal(result.severity, SEVERITIES.WARNING);
  assert.equal(result.blocksAutomation, true);
});

test("degraded provider does not necessarily block all automation", () => {
  const result = triageProviderException({ type: "PROVIDER_DEGRADED" });
  assert.equal(result.blocksAutomation, false);
});

test("unknown exception type is rejected", () => {
  assert.throws(() => triageProviderException({ type: "UNKNOWN" }), /unsupported provider exception type/);
});
