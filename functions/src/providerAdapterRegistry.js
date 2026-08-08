"use strict";

const CAPABILITIES = Object.freeze({
  DISPATCH: "DISPATCH",
  STATUS_LOOKUP: "STATUS_LOOKUP",
  CONVERSION_IMPORT: "CONVERSION_IMPORT",
  COMMISSION_RECONCILIATION: "COMMISSION_RECONCILIATION",
});

const SUPPORTED_CAPABILITIES = new Set(Object.values(CAPABILITIES));

function requiredText(value, field, maxLength = 160) {
  const text = typeof value === "string" ? value.trim() : "";
  if (!text) throw new TypeError(`${field} is required`);
  if (text.length > maxLength) throw new TypeError(`${field} exceeds ${maxLength} characters`);
  return text;
}

function normalizeCapabilities(input) {
  if (!Array.isArray(input)) throw new TypeError("capabilities must be an array");
  const unique = [];
  const seen = new Set();
  for (const raw of input) {
    const capability = requiredText(raw, "capability", 64).toUpperCase();
    if (!SUPPORTED_CAPABILITIES.has(capability)) {
      throw new TypeError(`unsupported adapter capability: ${capability}`);
    }
    if (!seen.has(capability)) {
      seen.add(capability);
      unique.push(capability);
    }
  }
  return unique.sort();
}

function normalizeAdapterDescriptor(input) {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    throw new TypeError("adapter descriptor must be an object");
  }

  const implemented = input.implemented === true;
  const capabilities = normalizeCapabilities(input.capabilities || []);
  if (!implemented && capabilities.length > 0) {
    throw new TypeError("unimplemented adapter cannot declare capabilities");
  }

  return {
    adapterKey: requiredText(input.adapterKey, "adapterKey", 128),
    providerId: requiredText(input.providerId, "providerId", 128),
    version: requiredText(input.version || "1", "version", 32),
    implemented,
    capabilities,
  };
}

function createAdapterRegistry(descriptors) {
  if (!Array.isArray(descriptors)) throw new TypeError("adapter descriptors must be an array");
  const registry = new Map();
  for (const descriptorInput of descriptors) {
    const descriptor = normalizeAdapterDescriptor(descriptorInput);
    if (registry.has(descriptor.adapterKey)) {
      throw new TypeError(`duplicate adapterKey: ${descriptor.adapterKey}`);
    }
    registry.set(descriptor.adapterKey, Object.freeze(descriptor));
  }
  return registry;
}

function getAdapterDescriptor(registry, adapterKey) {
  if (!(registry instanceof Map)) throw new TypeError("adapter registry must be a Map");
  const key = requiredText(adapterKey, "adapterKey", 128);
  return registry.get(key) || null;
}

function assertAdapterCapability(registry, adapterKey, capabilityInput) {
  const descriptor = getAdapterDescriptor(registry, adapterKey);
  if (!descriptor || descriptor.implemented !== true) {
    throw new Error("provider adapter is not implemented");
  }
  const capability = requiredText(capabilityInput, "capability", 64).toUpperCase();
  if (!SUPPORTED_CAPABILITIES.has(capability)) {
    throw new TypeError(`unsupported adapter capability: ${capability}`);
  }
  if (!descriptor.capabilities.includes(capability)) {
    throw new Error(`provider adapter does not support capability: ${capability}`);
  }
  return descriptor;
}

module.exports = {
  CAPABILITIES,
  normalizeCapabilities,
  normalizeAdapterDescriptor,
  createAdapterRegistry,
  getAdapterDescriptor,
  assertAdapterCapability,
};
