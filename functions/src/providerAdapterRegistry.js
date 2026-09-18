"use strict";

const { validateAdapterContract } = require("./providerIntegrationFramework");

const adapters = new Map();

function registerProviderAdapter(adapterKey, adapter) {
  const key = String(adapterKey || "").trim();
  if (!key) throw new TypeError("adapterKey is required");
  if (adapters.has(key)) throw new TypeError(`provider adapter already registered: ${key}`);
  adapters.set(key, validateAdapterContract(adapter));
}

function hasProviderAdapter(adapterKey) {
  return adapters.has(String(adapterKey || "").trim());
}

function getProviderAdapter(adapterKey) {
  return adapters.get(String(adapterKey || "").trim()) || null;
}

function registeredProviderAdapterKeys() {
  return [...adapters.keys()].sort();
}

// No live provider adapter is registered by default.
// Connectivity must be added explicitly in source and backed by real authorized credentials/evidence.

module.exports = {
  registerProviderAdapter,
  hasProviderAdapter,
  getProviderAdapter,
  registeredProviderAdapterKeys,
};
