"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  CAPABILITIES,
  normalizeAdapterDescriptor,
  createAdapterRegistry,
  assertAdapterCapability,
} = require("../src/providerAdapterRegistry");

function descriptor(overrides = {}) {
  return {
    adapterKey: "provider-a-v1",
    providerId: "provider-a",
    version: "1",
    implemented: true,
    capabilities: [CAPABILITIES.DISPATCH, CAPABILITIES.STATUS_LOOKUP],
    ...overrides,
  };
}

test("implemented adapter declares only known capabilities", () => {
  const normalized = normalizeAdapterDescriptor(descriptor());
  assert.deepEqual(normalized.capabilities, [CAPABILITIES.DISPATCH, CAPABILITIES.STATUS_LOOKUP].sort());
});

test("unimplemented adapter cannot claim capabilities", () => {
  assert.throws(() => normalizeAdapterDescriptor(descriptor({ implemented: false })), /cannot declare capabilities/);
});

test("unknown capability is rejected", () => {
  assert.throws(() => normalizeAdapterDescriptor(descriptor({ capabilities: ["MAGIC"] })), /unsupported adapter capability/);
});

test("registry rejects duplicate adapter keys", () => {
  assert.throws(() => createAdapterRegistry([descriptor(), descriptor()]), /duplicate adapterKey/);
});

test("capability assertion succeeds only when explicitly implemented", () => {
  const registry = createAdapterRegistry([descriptor()]);
  assert.equal(assertAdapterCapability(registry, "provider-a-v1", CAPABILITIES.DISPATCH).providerId, "provider-a");
  assert.throws(() => assertAdapterCapability(registry, "provider-a-v1", CAPABILITIES.COMMISSION_RECONCILIATION), /does not support capability/);
});

test("missing adapter never masquerades as implemented", () => {
  const registry = createAdapterRegistry([descriptor()]);
  assert.throws(() => assertAdapterCapability(registry, "missing-adapter", CAPABILITIES.DISPATCH), /not implemented/);
});
