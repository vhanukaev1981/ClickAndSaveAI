"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");

test("production provider adapter registry ships with no fabricated live adapters", () => {
  const registry = require("../src/providerAdapterRegistry");
  assert.deepEqual(registry.registeredProviderAdapterKeys(), []);
  assert.equal(registry.hasProviderAdapter("provider-a-v1"), false);
  assert.equal(registry.getProviderAdapter("provider-a-v1"), null);
});
