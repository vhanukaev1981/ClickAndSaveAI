"use strict";

const crypto = require("node:crypto");

function normalizeSourceId(value) {
  return String(value || "").trim();
}

function gmailMessageIdFromInvoiceSource(sourceMessageId) {
  const sourceId = normalizeSourceId(sourceMessageId);
  if (!sourceId) return "";
  const pdfMarkerIndex = sourceId.indexOf(":pdf:");
  return pdfMarkerIndex > 0 ? sourceId.slice(0, pdfMarkerIndex) : sourceId;
}

function gmailInvoiceDocumentId(sourceMessageId) {
  const sourceId = normalizeSourceId(sourceMessageId);
  if (!sourceId) return "";
  return crypto.createHash("sha256").update(sourceId).digest("hex");
}

function sourceIds(invoices) {
  const ids = new Set();
  for (const invoice of Array.isArray(invoices) ? invoices : []) {
    const sourceId = normalizeSourceId(invoice?.sourceMessageId);
    if (sourceId) ids.add(sourceId);
  }
  return ids;
}

function staleInvoiceSourceIds(previousInvoices, currentInvoices) {
  const previousIds = sourceIds(previousInvoices);
  const currentIds = sourceIds(currentInvoices);
  return [...previousIds].filter((sourceId) => !currentIds.has(sourceId)).sort();
}

function invoiceSourceBelongsToMessage(sourceMessageId, gmailMessageId) {
  const sourceId = normalizeSourceId(sourceMessageId);
  const messageId = normalizeSourceId(gmailMessageId);
  if (!sourceId || !messageId) return false;
  return sourceId === messageId || sourceId.startsWith(`${messageId}:pdf:`);
}

module.exports = {
  normalizeSourceId,
  gmailMessageIdFromInvoiceSource,
  gmailInvoiceDocumentId,
  sourceIds,
  staleInvoiceSourceIds,
  invoiceSourceBelongsToMessage,
};
