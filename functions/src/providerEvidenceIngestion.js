"use strict";

const crypto = require("node:crypto");

const EVIDENCE_KINDS = Object.freeze({
  ACKNOWLEDGEMENT: "ACKNOWLEDGEMENT",
  STATUS: "STATUS",
  CONVERSION: "CONVERSION",
  ACTIVATION: "ACTIVATION",
  COMMISSION: "COMMISSION",
});

const SOURCES = new Set(["WEBHOOK", "POSTBACK", "REPORT_IMPORT", "MANUAL_VERIFIED"]);

function requiredText(value, field, maxLength = 200) {
  const text = typeof value === "string" ? value.trim() : "";
  if (!text) throw new TypeError(`${field} is required`);
  if (text.length > maxLength) throw new TypeError(`${field} exceeds ${maxLength} characters`);
  return text;
}

function normalizeIsoTime(value, field) {
  const text = requiredText(value, field, 64);
  const parsed = Date.parse(text);
  if (!Number.isFinite(parsed)) throw new TypeError(`${field} must be a valid timestamp`);
  return new Date(parsed).toISOString();
}

function deriveEvidenceEventId(input) {
  const material = [
    requiredText(input.providerId, "providerId", 128),
    requiredText(input.providerReference, "providerReference", 200),
    requiredText(input.kind, "kind", 40),
    requiredText(input.externalEventId, "externalEventId", 200),
  ].join(":");
  return crypto.createHash("sha256").update(material).digest("hex");
}

function normalizeProviderEvidence(input) {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    throw new TypeError("provider evidence must be an object");
  }

  const kind = requiredText(input.kind, "kind", 40).toUpperCase();
  if (!Object.values(EVIDENCE_KINDS).includes(kind)) {
    throw new TypeError(`unsupported evidence kind: ${kind}`);
  }

  const source = requiredText(input.source, "source", 40).toUpperCase();
  if (!SOURCES.has(source)) throw new TypeError(`unsupported evidence source: ${source}`);

  const normalized = {
    providerId: requiredText(input.providerId, "providerId", 128),
    contractId: requiredText(input.contractId, "contractId", 128),
    providerReference: requiredText(input.providerReference, "providerReference", 200),
    externalEventId: requiredText(input.externalEventId, "externalEventId", 200),
    kind,
    source,
    observedAt: normalizeIsoTime(input.observedAt, "observedAt"),
  };

  if (input.amount != null) {
    const amount = Number(input.amount);
    if (!Number.isFinite(amount) || amount < 0) throw new TypeError("amount must be non-negative");
    normalized.amount = Math.round(amount * 100) / 100;
    normalized.currency = typeof input.currency === "string" && input.currency.trim()
      ? input.currency.trim().toUpperCase().slice(0, 8)
      : "ILS";
  }

  normalized.evidenceEventId = deriveEvidenceEventId(normalized);
  return normalized;
}

function dedupeProviderEvidence(events) {
  if (!Array.isArray(events)) throw new TypeError("provider evidence events must be an array");
  const unique = new Map();
  for (const event of events.map(normalizeProviderEvidence)) {
    if (!unique.has(event.evidenceEventId)) unique.set(event.evidenceEventId, event);
  }
  return [...unique.values()];
}

module.exports = {
  EVIDENCE_KINDS,
  deriveEvidenceEventId,
  normalizeProviderEvidence,
  dedupeProviderEvidence,
};
