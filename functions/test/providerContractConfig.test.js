"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  normalizeProviderContract,
  isContractActive,
  buildAttribution,
} = require("../src/providerContractConfig");

function contract(overrides = {}) {
  return {
    contractId: "contract-1",
    providerId: "provider-a",
    commercialModel: "CPA",
    deliveryMode: "API",
    conversionEvidenceMode: "POSTBACK",
    adapterKey: "provider-a-v1",
    credentialSecretName: "PROVIDER_A_API_CREDENTIALS",
    attributionField: "sub_id",
    campaignId: "campaign-1",
    activeFrom: "2026-08-01T00:00:00Z",
    activeUntil: "2026-09-01T00:00:00Z",
    enabled: true,
    ...overrides,
  };
}

test("normalizes an attributable provider contract without credential material", () => {
  const normalized = normalizeProviderContract(contract());
  assert.equal(normalized.commercialModel, "CPA");
  assert.equal(normalized.credentialSecretName, "PROVIDER_A_API_CREDENTIALS");
});

test("remote delivery requires a Secret Manager reference", () => {
  assert.throws(() => normalizeProviderContract(contract({ credentialSecretName: "" })), /Secret Manager reference/);
});

test("credential material is rejected from contract config", () => {
  assert.throws(() => normalizeProviderContract(contract({ credentialSecretName: "sk-secret-material" })), /secret reference/);
});

test("non-direct commercial models require attribution field", () => {
  assert.throws(() => normalizeProviderContract(contract({ attributionField: "" })), /attributionField/);
});

test("contract active window requires valid timestamps in chronological order", () => {
  assert.throws(() => normalizeProviderContract(contract({ activeFrom: "not-a-date" })), /activeFrom must be a valid timestamp/);
  assert.throws(() => normalizeProviderContract(contract({ activeUntil: "not-a-date" })), /activeUntil must be a valid timestamp/);
  assert.throws(() => normalizeProviderContract(contract({ activeUntil: "2026-07-31T23:59:59Z" })), /after activeFrom/);
});

test("contract activity is deterministic and independent of commission amount", () => {
  const midAugust = Date.parse("2026-08-15T12:00:00Z");
  assert.equal(isContractActive(contract(), midAugust), true);
  assert.equal(isContractActive(contract({ enabled: false }), midAugust), false);
  assert.equal(isContractActive(contract(), Date.parse("2026-09-02T00:00:00Z")), false);
});

test("buildAttribution binds external provider attribution to clickId", () => {
  assert.deepEqual(buildAttribution(contract(), "click-123"), {
    sub_id: "click-123",
    campaignId: "campaign-1",
  });
});

test("direct agreements may omit external attribution field", () => {
  const direct = contract({
    commercialModel: "DIRECT",
    deliveryMode: "MANUAL_OPERATOR",
    conversionEvidenceMode: "MANUAL_VERIFIED",
    credentialSecretName: "",
    attributionField: "",
  });
  assert.deepEqual(buildAttribution(direct, "click-123"), {});
});
