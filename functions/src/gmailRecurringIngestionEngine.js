"use strict";

const crypto = require("node:crypto");
const {
  firstHeader,
  normalizePdfInvoiceCandidate,
} = require("./gmailParser");
const { pdfContentFingerprint } = require("./gmailRecurringBillPolicy");
const {
  PDF_ANALYSIS_STATES,
  recordPdfClassificationResult,
  resolvePdfBodyCandidates,
} = require("./gmailPdfAnalysisState");

const MAX_PDF_BYTES = 10 * 1024 * 1024;
const DOCUMENT_CLASSES = new Set([
  "RECURRING_BILL",
  "ONE_OFF",
  "REFUND",
  "RECEIPT_ONLY",
  "CONTRACT",
  "UNKNOWN",
]);
const RECURRENCE_TYPES = new Set([
  "FIXED_MONTHLY",
  "PERIODIC_VARIABLE",
  "USAGE_RECURRING",
  "UNKNOWN",
]);
const RECURRENCE_EVIDENCE = new Set([
  "EXPLICIT_BILLING_PERIOD",
  "SUBSCRIPTION",
  "UTILITY_SERVICE",
  "TELECOM_SERVICE",
  "RECURRING_SERVICE",
  "NONE",
]);

function boundedEnum(value, allowed, fallback) {
  const normalized = String(value || "").trim().toUpperCase();
  return allowed.has(normalized) ? normalized : fallback;
}

function base64UrlToBase64(value) {
  const normalized = String(value || "").replace(/-/g, "+").replace(/_/g, "/");
  const padding = normalized.length % 4;
  return padding === 0 ? normalized : normalized + "=".repeat(4 - padding);
}

async function loadPdfAttachmentBase64(accessToken, messageId, attachment) {
  if (attachment.inlineData) {
    const data = base64UrlToBase64(attachment.inlineData);
    if (Buffer.byteLength(data, "base64") > MAX_PDF_BYTES) return null;
    return data;
  }
  if (!attachment.attachmentId || attachment.size > MAX_PDF_BYTES) return null;
  const response = await fetch(
    `https://gmail.googleapis.com/gmail/v1/users/me/messages/${encodeURIComponent(messageId)}/attachments/${encodeURIComponent(attachment.attachmentId)}`,
    { headers: { Authorization: `Bearer ${accessToken}` } }
  );
  if (!response.ok) return null;
  const payload = await response.json().catch(() => ({}));
  const data = base64UrlToBase64(payload.data);
  if (!data || Buffer.byteLength(data, "base64") > MAX_PDF_BYTES) return null;
  return data;
}

function pdfSourceDocumentId(messageId, attachment, index) {
  const fingerprint = crypto
    .createHash("sha256")
    .update(`${messageId}:${attachment.attachmentId || attachment.filename || "inline"}:${index}`)
    .digest("hex")
    .slice(0, 20);
  return `${messageId}:pdf:${fingerprint}`;
}

function recurringPdfPrompt(message, filename) {
  const headers = message?.payload?.headers || [];
  const subject = firstHeader(headers, "Subject").slice(0, 300);
  const from = firstHeader(headers, "From").slice(0, 300);
  const date = firstHeader(headers, "Date").slice(0, 120);
  const safeFilename = String(filename || "").slice(0, 180);

  return [
    "Analyze this PDF independently of the email body.",
    "Return JSON only with keys: isInvoice, providerName, category, serviceType, monthlyCost, receivedDate, documentClass, recurrenceEvidence, recurrenceType.",
    "documentClass must be exactly one of RECURRING_BILL, ONE_OFF, REFUND, RECEIPT_ONLY, CONTRACT, UNKNOWN.",
    "RECURRING_BILL means the PDF itself shows an ongoing service/account plus a recurring billing relationship, billing period, subscription, utility bill, telecom bill or other continuing service charge.",
    "ONE_OFF means a purchase, school payment, gift, fee, fine, government payment or other non-recurring charge.",
    "REFUND means refund, reimbursement, credit or returned payment. Never classify a refund as a bill.",
    "RECEIPT_ONLY means a legitimate payment receipt where the PDF itself does not prove recurrence.",
    "CONTRACT means an agreement, policy wording, purchase contract or commitment that is not itself a current recurring bill.",
    "UNKNOWN means the evidence is insufficient or ambiguous.",
    "recurrenceEvidence must be exactly one of EXPLICIT_BILLING_PERIOD, SUBSCRIPTION, UTILITY_SERVICE, TELECOM_SERVICE, RECURRING_SERVICE, NONE.",
    "recurrenceType must be exactly one of FIXED_MONTHLY, PERIODIC_VARIABLE, USAGE_RECURRING, UNKNOWN.",
    "Use RECURRING_BILL only when recurrence is supported by the PDF itself; provider reputation alone is not evidence.",
    "For electricity/water/gas/telecom or another ongoing account, a stated service/billing period is valid recurrence evidence even when the amount varies.",
    "For a receipt that could be from an ongoing usage service but the PDF does not prove recurrence, use RECEIPT_ONLY + NONE + UNKNOWN; cross-document history is handled separately.",
    "isInvoice may be true for a bill, tax invoice or receipt with a reliable monetary total; classification decides whether it is a current recurring bill.",
    "For category, use a short useful category when clear; otherwise use other.",
    "For serviceType, return a concise service descriptor only when explicitly written in the PDF. Otherwise return an empty string.",
    "monthlyCost is the actual document total/amount charged/paid/current amount due, never a promotion or savings figure.",
    "If no reliable monetary total can be extracted, set isInvoice=false and documentClass=UNKNOWN.",
    "Do not return account numbers, addresses, IDs, phone numbers, payment details or other personal data.",
    `Email subject context only: ${subject}`,
    `Email sender context only: ${from}`,
    `Email date context only: ${date}`,
    `Attachment filename context only: ${safeFilename}`,
  ].join("\n");
}

async function analyzePdfCandidate(message, pdfBase64, filename, sourceDocumentId, apiKey) {
  const { GoogleGenAI } = await import("@google/genai");
  const ai = new GoogleGenAI({ apiKey });
  const response = await ai.models.generateContent({
    model: "gemini-3.6-flash",
    contents: [{
      role: "user",
      parts: [
        { inlineData: { mimeType: "application/pdf", data: pdfBase64 } },
        { text: recurringPdfPrompt(message, filename) },
      ],
    }],
    config: { responseMimeType: "application/json" },
  });

  const raw = JSON.parse(response.text || "{}");
  const documentClass = boundedEnum(raw.documentClass, DOCUMENT_CLASSES, "UNKNOWN");
  let recurrenceEvidence = boundedEnum(raw.recurrenceEvidence, RECURRENCE_EVIDENCE, "NONE");
  let recurrenceType = boundedEnum(raw.recurrenceType, RECURRENCE_TYPES, "UNKNOWN");
  if (documentClass !== "RECURRING_BILL") {
    recurrenceEvidence = "NONE";
    if (documentClass !== "RECEIPT_ONLY") recurrenceType = "UNKNOWN";
  }

  const normalized = normalizePdfInvoiceCandidate(raw, message, sourceDocumentId);
  const candidate = normalized ? {
    ...normalized,
    documentClass,
    recurrenceEvidence,
    recurrenceType,
    contentFingerprint: pdfContentFingerprint(pdfBase64),
  } : null;

  // Successful model analysis is semantically distinct from monetary normalization.
  // Record the supported semantic result transiently even when isInvoice=false or
  // normalization cannot produce a monetary candidate. Parser/body fallback then
  // cannot override a successfully analyzed PDF.
  recordPdfClassificationResult(message, { documentClass, candidate });
  return candidate;
}

function bodyCandidate(invoice) {
  if (!invoice) return null;
  return {
    ...invoice,
    documentClass: boundedEnum(invoice.documentClass, DOCUMENT_CLASSES, "UNKNOWN"),
    recurrenceEvidence: boundedEnum(invoice.recurrenceEvidence, RECURRENCE_EVIDENCE, "NONE"),
    recurrenceType: boundedEnum(invoice.recurrenceType, RECURRENCE_TYPES, "UNKNOWN"),
    contentFingerprint: "",
  };
}

function normalizeStoredCandidate(candidate) {
  if (!candidate || typeof candidate !== "object") return null;
  const monthlyCost = Number(candidate.monthlyCost);
  if (!candidate.sourceMessageId || !candidate.providerName || !Number.isFinite(monthlyCost) || monthlyCost <= 0) {
    return null;
  }
  const normalized = {
    sourceMessageId: String(candidate.sourceMessageId),
    providerName: String(candidate.providerName),
    category: String(candidate.category || "other"),
    monthlyCost,
    receivedDate: String(candidate.receivedDate || ""),
    verificationStatus: "UNVERIFIED_GMAIL_IMPORT",
    documentClass: boundedEnum(candidate.documentClass, DOCUMENT_CLASSES, "UNKNOWN"),
    recurrenceEvidence: boundedEnum(candidate.recurrenceEvidence, RECURRENCE_EVIDENCE, "NONE"),
    recurrenceType: boundedEnum(candidate.recurrenceType, RECURRENCE_TYPES, "UNKNOWN"),
    contentFingerprint: String(candidate.contentFingerprint || "").slice(0, 80),
  };
  const serviceType = String(candidate.serviceType || "").trim();
  return serviceType ? { ...normalized, serviceType } : normalized;
}

function storedCandidates(data) {
  const raw = Array.isArray(data?.candidates)
    ? data.candidates
    : (Array.isArray(data?.invoices) ? data.invoices : (data?.invoice ? [data.invoice] : []));
  return raw.map(normalizeStoredCandidate).filter(Boolean);
}

module.exports = {
  MAX_PDF_BYTES,
  PDF_ANALYSIS_STATES,
  analyzePdfCandidate,
  bodyCandidate,
  loadPdfAttachmentBase64,
  normalizeStoredCandidate,
  pdfSourceDocumentId,
  recurringPdfPrompt,
  resolvePdfBodyCandidates,
  storedCandidates,
};
