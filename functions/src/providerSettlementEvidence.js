"use strict";

const crypto = require("node:crypto");

const SETTLEMENT_SOURCES = new Set(["BANK_REPORT", "PROVIDER_PAYOUT_REPORT", "MANUAL_VERIFIED"]);

function requiredText(value, field, maxLength = 200) {
  const text = typeof value === "string" ? value.trim() : "";
  if (!text) throw new TypeError(`${field} is required`);
  if (text.length > maxLength) throw new TypeError(`${field} exceeds ${maxLength} characters`);
  return text;
}

function positiveMoney(value, field) {
  const amount = Number(value);
  if (!Number.isFinite(amount) || amount <= 0) throw new TypeError(`${field} must be a positive number`);
  return Math.round(amount * 100) / 100;
}

function normalizeTimestamp(value, field) {
  const text = requiredText(value, field, 64);
  if (!Number.isFinite(Date.parse(text))) throw new TypeError(`${field} must be a valid timestamp`);
  return new Date(Date.parse(text)).toISOString();
}

function deriveSettlementEvidenceId(input) {
  const material = [
    requiredText(input.partnerId, "partnerId", 128),
    requiredText(input.providerReference, "providerReference", 200),
    requiredText(input.externalPaymentId, "externalPaymentId", 200),
    requiredText(input.currency, "currency", 8).toUpperCase(),
    String(positiveMoney(input.amount, "amount")),
  ].join(":");
  return crypto.createHash("sha256").update(material).digest("hex");
}

function normalizeSettlementEvidence(input) {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    throw new TypeError("settlement evidence must be an object");
  }

  const source = requiredText(input.source, "source", 40).toUpperCase();
  if (!SETTLEMENT_SOURCES.has(source)) throw new TypeError(`unsupported settlement source: ${source}`);

  const evidence = {
    partnerId: requiredText(input.partnerId, "partnerId", 128),
    providerReference: requiredText(input.providerReference, "providerReference", 200),
    externalPaymentId: requiredText(input.externalPaymentId, "externalPaymentId", 200),
    amount: positiveMoney(input.amount, "amount"),
    currency: requiredText(input.currency || "ILS", "currency", 8).toUpperCase(),
    source,
    paidAt: normalizeTimestamp(input.paidAt, "paidAt"),
  };
  evidence.settlementEvidenceId = deriveSettlementEvidenceId(evidence);
  return evidence;
}

function dedupeSettlementEvidence(events) {
  if (!Array.isArray(events)) throw new TypeError("settlement evidence events must be an array");
  const unique = new Map();
  for (const event of events.map(normalizeSettlementEvidence)) {
    if (!unique.has(event.settlementEvidenceId)) unique.set(event.settlementEvidenceId, event);
  }
  return [...unique.values()];
}

module.exports = {
  SETTLEMENT_SOURCES,
  deriveSettlementEvidenceId,
  normalizeSettlementEvidence,
  dedupeSettlementEvidence,
};
