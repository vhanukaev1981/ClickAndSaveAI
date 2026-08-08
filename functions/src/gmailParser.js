"use strict";

// Prefer amounts next to billing labels before falling back to any currency-looking amount.
// This reduces false positives from promotional prices that may appear elsewhere in the email.
const AMOUNT_PATTERNS = [
  /(?:סה["״]?כ\s*(?:לתשלום|חיוב)?|סכום\s*(?:לתשלום|החיוב)?|לתשלום|total\s*(?:due|amount)?|amount\s*due|balance\s*due)\s*[:\-]?\s*(?:₪|ש["״]?ח|ILS)?\s*([\d,]+(?:\.\d{1,2})?)/i,
  /(?:₪|ש["״]?ח|ILS)\s*([\d,]+(?:\.\d{1,2})?)/i,
  /([\d,]+(?:\.\d{1,2})?)\s*(?:₪|ש["״]?ח|ILS)/i,
];

const SUPPORTED_CATEGORIES = new Map([
  ["חשמל", "חשמל"],
  ["electricity", "חשמל"],
  ["electric", "חשמל"],
  ["אינטרנט", "אינטרנט"],
  ["internet", "אינטרנט"],
  ["fiber", "אינטרנט"],
  ["broadband", "אינטרנט"],
  ["סלולר", "סלולר"],
  ["mobile", "סלולר"],
  ["cellular", "סלולר"],
  ["טלוויזיה", "טלוויזיה"],
  ["television", "טלוויזיה"],
  ["tv", "טלוויזיה"],
  ["ביטוח", "ביטוח"],
  ["insurance", "ביטוח"],
  ["תקשורת", "תקשורת"],
  ["telecom", "תקשורת"],
  ["communications", "תקשורת"],
]);

function firstHeader(headers, name) {
  if (!Array.isArray(headers)) return "";
  return headers.find((header) =>
    String(header?.name || "").toLowerCase() === name.toLowerCase()
  )?.value || "";
}

function decodeBase64Url(value) {
  if (typeof value !== "string" || !value) return "";
  try {
    const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
    return Buffer.from(normalized, "base64").toString("utf8");
  } catch {
    return "";
  }
}

function htmlToText(value) {
  return String(value || "")
    .replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, " ")
    .replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi, " ")
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;|&#160;|\u00a0/gi, " ")
    .replace(/&amp;/gi, "&")
    .replace(/&quot;/gi, '"')
    .replace(/&#39;|&apos;/gi, "'")
    .replace(/\s+/g, " ")
    .trim();
}

function collectMessageText(payload, maxChars = 24_000) {
  const chunks = [];
  let total = 0;

  function visit(part, depth = 0) {
    if (!part || depth > 12 || total >= maxChars) return;
    const mimeType = String(part.mimeType || "").toLowerCase();
    const data = part.body?.data;
    if (data && (mimeType === "text/plain" || mimeType === "text/html")) {
      const decoded = decodeBase64Url(data);
      const text = mimeType === "text/html" ? htmlToText(decoded) : decoded;
      if (text) {
        const remaining = maxChars - total;
        const bounded = text.slice(0, remaining);
        chunks.push(bounded);
        total += bounded.length;
      }
    }
    if (Array.isArray(part.parts)) {
      for (const child of part.parts) visit(child, depth + 1);
    }
  }

  visit(payload);
  return chunks.join(" ");
}

function collectPdfAttachments(payload, maxAttachments = 3) {
  const attachments = [];

  function visit(part, depth = 0) {
    if (!part || depth > 12 || attachments.length >= maxAttachments) return;
    const mimeType = String(part.mimeType || "").toLowerCase();
    const filename = String(part.filename || "").trim();
    const isPdf = mimeType === "application/pdf" || /\.pdf$/i.test(filename);

    if (isPdf) {
      const attachmentId = typeof part.body?.attachmentId === "string"
        ? part.body.attachmentId
        : "";
      const inlineData = typeof part.body?.data === "string"
        ? part.body.data
        : "";
      const size = Number(part.body?.size || 0);
      if (attachmentId || inlineData) {
        attachments.push({
          attachmentId,
          inlineData,
          size: Number.isFinite(size) && size > 0 ? size : 0,
          filename: filename.slice(0, 180),
        });
      }
    }

    if (Array.isArray(part.parts)) {
      for (const child of part.parts) visit(child, depth + 1);
    }
  }

  visit(payload);
  return attachments;
}

function parseAmount(text) {
  const normalized = String(text || "").replace(/\u00a0/g, " ");
  for (const pattern of AMOUNT_PATTERNS) {
    const match = pattern.exec(normalized);
    if (!match) continue;
    const value = Number(match[1].replace(/,/g, ""));
    if (Number.isFinite(value) && value > 0 && value < 1_000_000) return value;
  }
  return null;
}

function identifyCategory(text) {
  const normalized = String(text || "").toLowerCase();
  if (/(חשמל|electric|iec|power)/i.test(normalized)) return "חשמל";
  if (/(ביטוח|insurance|פוליסה)/i.test(normalized)) return "ביטוח";
  // Prefer explicit service signals over provider names so multi-service providers
  // such as Cellcom/Partner/HOT are not incorrectly forced into mobile or TV.
  if (/(אינטרנט|סיבים|fiber|broadband|bezeq|בזק|נתב|ראוטר)/i.test(normalized)) return "אינטרנט";
  if (/(סלולר|mobile|cellular|פלאפון|pelephone|wecom|019|קו נייד|חבילת גלישה)/i.test(normalized)) return "סלולר";
  if (/(טלוויזיה|streaming|netflix|(^|\W)yes(\W|$)|freetv)/i.test(normalized)) return "טלוויזיה";
  return null;
}

function matchProvider(text, includeGenericPartner = false) {
  const providers = [
    ["חברת החשמל", /(iec|חברת החשמל)/i],
    ["בזק", /(bezeq|בזק)/i],
    ["סלקום", /(cellcom|סלקום)/i],
    ["פרטנר", includeGenericPartner ? /(^|\W)(partner|פרטנר)(\W|$)/i : /פרטנר/i],
    ["פלאפון", /(pelephone|פלאפון)/i],
    ["HOT", /(^|\W)hot(\W|$)/i],
    ["yes", /(^|\W)yes(\W|$)/i],
    ["Netflix", /(^|\W)netflix(\W|$)/i],
  ];
  return providers.find(([, pattern]) => pattern.test(text))?.[0] || null;
}

function identifyProvider(from, subject, searchableText) {
  // Sender/subject are strong brand signals. Body text is a weaker fallback because words such
  // as the English "partner" can occur generically in unrelated receipts.
  const strongText = `${from} ${subject}`.toLowerCase();
  const strongMatch = matchProvider(strongText, true);
  if (strongMatch) return strongMatch;

  const bodyText = String(searchableText || "").toLowerCase();
  return matchProvider(bodyText, false) || "ספק שזוהה מהודעת Gmail";
}

function fallbackCategoryForProvider(providerName) {
  if (["סלקום", "פרטנר", "HOT"].includes(providerName)) return "תקשורת";
  return null;
}

function normalizeDocumentCategory(value) {
  const normalized = String(value || "").trim().toLowerCase();
  if (!normalized) return null;
  if (SUPPORTED_CATEGORIES.has(normalized)) return SUPPORTED_CATEGORIES.get(normalized);
  return identifyCategory(normalized);
}

function normalizePdfInvoiceCandidate(candidate, message) {
  if (!candidate || candidate.isInvoice !== true || !message?.id) return null;

  const payload = message.payload || {};
  const headers = payload.headers || [];
  const subject = firstHeader(headers, "Subject");
  const from = firstHeader(headers, "From");
  const headerDate = firstHeader(headers, "Date");
  const providerText = String(candidate.providerName || "").trim().slice(0, 160);
  const detectedProvider = identifyProvider(from, subject, providerText);
  const providerName = providerText ||
    (detectedProvider !== "ספק שזוהה מהודעת Gmail" ? detectedProvider : "");
  const category = normalizeDocumentCategory(candidate.category) ||
    fallbackCategoryForProvider(providerName);
  const monthlyCost = Number(candidate.monthlyCost);

  if (!providerName || !category) return null;
  if (!Number.isFinite(monthlyCost) || monthlyCost <= 0 || monthlyCost >= 1_000_000) return null;

  return {
    sourceMessageId: String(message.id),
    providerName,
    category,
    monthlyCost,
    receivedDate: String(candidate.receivedDate || headerDate || "").slice(0, 120),
    verificationStatus: "UNVERIFIED_GMAIL_IMPORT",
  };
}

function parseGmailMessage(message) {
  const payload = message?.payload || {};
  const headers = payload.headers || [];
  const subject = firstHeader(headers, "Subject");
  const from = firstHeader(headers, "From");
  const date = firstHeader(headers, "Date");
  const snippet = String(message?.snippet || "").slice(0, 2000);
  const bodyText = collectMessageText(payload);
  const searchableText = `${subject} ${from} ${snippet} ${bodyText}`.slice(0, 30_000);
  const amount = parseAmount(searchableText);
  const providerName = identifyProvider(from, subject, searchableText);
  const category = identifyCategory(searchableText) || fallbackCategoryForProvider(providerName);

  if (!message?.id || amount == null || category == null) return null;

  // Persist only the minimum fields needed for invoice deduplication and display.
  // Raw Gmail subject, sender, snippet and body text are deliberately not returned or stored.
  return {
    sourceMessageId: String(message.id),
    providerName,
    category,
    monthlyCost: amount,
    receivedDate: date.slice(0, 120),
    verificationStatus: "UNVERIFIED_GMAIL_IMPORT",
  };
}

module.exports = {
  firstHeader,
  decodeBase64Url,
  collectMessageText,
  collectPdfAttachments,
  parseAmount,
  identifyCategory,
  identifyProvider,
  fallbackCategoryForProvider,
  normalizePdfInvoiceCandidate,
  parseGmailMessage,
};
