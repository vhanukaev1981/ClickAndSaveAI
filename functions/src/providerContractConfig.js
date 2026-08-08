"use strict";

const COMMERCIAL_MODELS = new Set(["CPL", "CPA", "REVENUE_SHARE", "DIRECT"]);
const DELIVERY_MODES = new Set(["API", "CRM", "SFTP_REPORT", "MANUAL_OPERATOR"]);
const CONVERSION_EVIDENCE_MODES = new Set(["POSTBACK", "WEBHOOK", "REPORT_IMPORT", "MANUAL_VERIFIED"]);

function required(value, field, maxLength = 200) {
  const text = typeof value === "string" ? value.trim() : "";
  if (!text) throw new TypeError(`${field} is required`);
  if (text.length > maxLength) throw new TypeError(`${field} exceeds ${maxLength} characters`);
  return text;
}

function optional(value, maxLength = 200) {
  if (value == null || value === "") return "";
  const text = String(value).trim();
  if (text.length > maxLength) throw new TypeError(`value exceeds ${maxLength} characters`);
  return text;
}

function normalizeProviderContract(input) {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    throw new TypeError("provider contract must be an object");
  }

  const commercialModel = required(input.commercialModel, "commercialModel", 30);
  const deliveryMode = required(input.deliveryMode, "deliveryMode", 30);
  const conversionEvidenceMode = required(input.conversionEvidenceMode, "conversionEvidenceMode", 30);
  if (!COMMERCIAL_MODELS.has(commercialModel)) throw new TypeError("commercialModel is unsupported");
  if (!DELIVERY_MODES.has(deliveryMode)) throw new TypeError("deliveryMode is unsupported");
  if (!CONVERSION_EVIDENCE_MODES.has(conversionEvidenceMode)) {
    throw new TypeError("conversionEvidenceMode is unsupported");
  }

  const credentialSecretName = optional(input.credentialSecretName, 160);
  if (["API", "CRM", "SFTP_REPORT"].includes(deliveryMode) && !credentialSecretName) {
    throw new TypeError("remote delivery mode requires a Secret Manager reference");
  }
  if (/sk-|password|secret=|token=/i.test(credentialSecretName)) {
    throw new TypeError("credentialSecretName must be a secret reference, not credential material");
  }

  const attributionField = optional(input.attributionField, 80);
  if (!attributionField && commercialModel !== "DIRECT") {
    throw new TypeError("attributable commercial model requires attributionField");
  }

  const activeFrom = required(input.activeFrom, "activeFrom", 64);
  const activeUntil = optional(input.activeUntil, 64);
  if (activeUntil && Date.parse(activeUntil) <= Date.parse(activeFrom)) {
    throw new TypeError("activeUntil must be after activeFrom");
  }

  return {
    contractId: required(input.contractId, "contractId", 128),
    providerId: required(input.providerId, "providerId", 128),
    commercialModel,
    deliveryMode,
    conversionEvidenceMode,
    adapterKey: required(input.adapterKey, "adapterKey", 128),
    credentialSecretName,
    attributionField,
    campaignId: optional(input.campaignId, 128),
    activeFrom,
    activeUntil,
    enabled: input.enabled === true,
  };
}

function isContractActive(contractInput, nowMs = Date.now()) {
  const contract = normalizeProviderContract(contractInput);
  if (!contract.enabled) return false;
  const start = Date.parse(contract.activeFrom);
  const end = contract.activeUntil ? Date.parse(contract.activeUntil) : Number.POSITIVE_INFINITY;
  if (!Number.isFinite(start)) return false;
  return nowMs >= start && nowMs < end;
}

function buildAttribution(contractInput, clickId) {
  const contract = normalizeProviderContract(contractInput);
  const normalizedClickId = required(clickId, "clickId", 160);
  if (!contract.attributionField) return {};
  return {
    [contract.attributionField]: normalizedClickId,
    ...(contract.campaignId ? { campaignId: contract.campaignId } : {}),
  };
}

module.exports = {
  COMMERCIAL_MODELS,
  DELIVERY_MODES,
  CONVERSION_EVIDENCE_MODES,
  normalizeProviderContract,
  isContractActive,
  buildAttribution,
};
