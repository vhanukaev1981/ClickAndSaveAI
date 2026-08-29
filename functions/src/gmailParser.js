"use strict";

const { extractServiceType, normalizeServiceType } = require("./serviceProfile");
const { hasPdfClassificationResult } = require("./gmailPdfAnalysisState");

// Prefer amounts next to billing labels before falling back to any currency-looking amount.
// This reduces false positives from promotional prices that may appear elsewhere in the email.
const AMOUNT_PATTERNS = [
  /(?:סה["״]?כ\s*(?:לתשלום|חיוב)?|סכום\s*(?:לתשלום|החיוב)?|לתשלום|total\s*(?:due|amount)?|amount\s*due|balance\s*due)\s*[:\-]?\s*(?:₪|ש["״]?ח|ILS)?\s*([\d,]+(?:\.\d{1,2})?)/i,
  /(?:₪|ש["״]?ח|ILS)\s*([\d,]+(?:\.\d{1,2})?)/i,
  /([\d,]+(?:\.\d{1,2})?)\s*(?:₪|ש["״]?ח|ILS)/i,
];

const CURRENT_OBLIGATION_PATTERNS = [
  /(?:amount\s+due|total\s+due|balance\s+due|current\s+amount\s+(?:charged|due)|current\s+premium\s+due)\s*[:\-]?\s*(?:₪|ILS|NIS|ש["״]?ח)?\s*([\d,]+(?:\.\d{1,2})?)/i,
  /(?:סכום\s+לתשלום|סה["״]?כ\s+לתשלום|סך\s+הכל\s+לתשלום|לתשלום|חיוב\s+שבוצע\s+בפועל)\s*[:\-]?\s*(?:₪|ILS|NIS|ש["״]?ח)?\s*([\d,]+(?:\.\d{1,2})?)/i,
];

const STRONG_RECURRING_PATTERNS = [
  /\b(?:your\s+)?(?:current|monthly)\s+(?:bill|invoice)\b/i,
  /\b(?:bill|invoice)\s+for\s+(?:the\s+)?(?:billing|service|usage|coverage)?\s*(?:period|month|year)\b/i,
  /\b(?:your\s+)?(?:bill|invoice)\s+for\s+[^.;]{0,72}(?:20\d{2}|january|february|march|april|may|june|july|august|september|october|november|december)\b/i,
  /\b(?:billing|service|usage|coverage)\s+period\b/i,
  /\bcurrent\s+recurring\s+charge\b/i,
  /\bannual\s+(?:insurance\s+)?(?:renewal|coverage|bill|invoice|premium)\b/i,
  /(?:החשבון\s+החודשי\s+שלך|החשבון\s+שלך\s+עבור|החשבונית\s+החודשית\s+שלך|תקופת\s+(?:חיוב|שירות|צריכה)|חשבונית\s+עבור\s+תקופת\s+שירות|חשבון\s+לתקופה|חיוב\s+חודשי\s+נוכחי)/i,
];

const PROMOTIONAL_PATTERNS = [
  /\b(?:offer|promotion|promotional|upgrade|trial|quote|proposal|marketing|advertisement|special\s+price)\b/i,
  /(?:מבצע|הצעה|שדרוג|ניסיון|הצעת\s+מחיר|פרסומת|מחיר\s+מיוחד)/i,
];

const NON_RECURRING_CLASS_PATTERNS = [
  ["REFUND", /\b(?:refund|reimbursement|refunded)\b|(?:החזר|זיכוי)/i],
  ["RECEIPT_ONLY", /\b(?:payment\s+)?receipt\b|(?:קבלה)/i],
  ["CONTRACT", /\b(?:contract|agreement)\b|(?:חוזה|הסכם)/i],
  ["ONE_OFF", /\b(?:one[-\s]?(?:time|off)|single\s+purchase)\b|(?:חד[\-\s]?פעמי|רכישה\s+חד[\-\s]?פעמית)/i],
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
  ["אחר", "אחר"],
  ["other", "אחר"],
]);

const INSURANCE_PROVIDERS = new Set([
  "הראל",
  "הפניקס",
  "מגדל",
  "כלל",
  "מנורה מבטחים",
  "AIG",
  "ביטוח ישיר",
  "ליברה",
  "weSure",
]);

const GENERIC_PROVIDER_LABELS = [
  /^(?:unknown|unknown provider|provider|service provider|billing provider|vendor|merchant|company)$/i,
  /^(?:insurance|insurance company|insurance provider|insurer)$/i,
  /^(?:ספק|ספק לא מזוהה|ספק שזוהה מהודעת gmail|ספק שירות|חברה|בית עסק|חברת ביטוח)$/i,
];

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

function collectPdfAttachments(payload, maxAttachments = Number.POSITIVE_INFINITY) {
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

function parseCurrentObligation(text) {
  const normalized = String(text || "").replace(/\u00a0/g, " ");
  for (const pattern of CURRENT_OBLIGATION_PATTERNS) {
    const match = pattern.exec(normalized);
    if (!match) continue;
    const value = Number(match[1].replace(/,/g, ""));
    if (Number.isFinite(value) && value > 0 && value < 1_000_000) {
      return { amount: value, index: match.index };
    }
  }
  return null;
}

function firstPatternIndex(text, patterns) {
  let first = -1;
  for (const pattern of patterns) {
    const match = pattern.exec(text);
    if (match && (first < 0 || match.index < first)) first = match.index;
  }
  return first;
}

function cueNearCompleteBill(cueIndex, recurringIndex, obligationIndex, maxDistance = 360) {
  if (cueIndex < 0) return false;
  return Math.min(
    Math.abs(cueIndex - recurringIndex),
    Math.abs(cueIndex - obligationIndex)
  ) <= maxDistance;
}

function classifyBodyDocument(text, category) {
  const normalized = String(text || "").replace(/\u00a0/g, " ");
  const recurringIndex = firstPatternIndex(normalized, STRONG_RECURRING_PATTERNS);
  const obligation = parseCurrentObligation(normalized);
  const completeRecurring = recurringIndex >= 0 && Boolean(obligation);

  for (const [documentClass, pattern] of NON_RECURRING_CLASS_PATTERNS) {
    const match = pattern.exec(normalized);
    if (!match) continue;
    if (!completeRecurring || cueNearCompleteBill(match.index, recurringIndex, obligation.index)) {
      return {
        documentClass,
        recurrenceEvidence: "NONE",
        recurrenceType: "UNKNOWN",
        currentObligationAmount: obligation?.amount ?? null,
      };
    }
  }

  if (!completeRecurring) {
    return {
      documentClass: "UNKNOWN",
      recurrenceEvidence: "NONE",
      recurrenceType: "UNKNOWN",
      currentObligationAmount: obligation?.amount ?? null,
    };
  }

  const promotionalIndex = firstPatternIndex(normalized, PROMOTIONAL_PATTERNS);
  if (cueNearCompleteBill(promotionalIndex, recurringIndex, obligation.index)) {
    return {
      documentClass: "UNKNOWN",
      recurrenceEvidence: "NONE",
      recurrenceType: "UNKNOWN",
      currentObligationAmount: obligation.amount,
    };
  }

  const explicitPeriod = /\b(?:billing|service|usage|coverage)\s+period\b|(?:תקופת\s+(?:חיוב|שירות|צריכה)|חשבונית\s+עבור\s+תקופת\s+שירות|חשבון\s+לתקופה)/i.test(normalized);
  const annual = /\b(?:annual|yearly)\b|(?:שנתי|שנתית)/i.test(normalized);
  const usage = /\b(?:usage|consumption|metered)\b|(?:צריכה|מונה)/i.test(normalized);
  const subscription = /\bsubscription\b|(?:מנוי)/i.test(normalized);
  const telecom = ["אינטרנט", "סלולר", "טלוויזיה", "תקשורת"].includes(category);

  let recurrenceEvidence = "RECURRING_SERVICE";
  if (explicitPeriod) recurrenceEvidence = "EXPLICIT_BILLING_PERIOD";
  else if (subscription) recurrenceEvidence = "SUBSCRIPTION";
  else if (category === "חשמל") recurrenceEvidence = "UTILITY_SERVICE";
  else if (telecom) recurrenceEvidence = "TELECOM_SERVICE";

  let recurrenceType = "PERIODIC_VARIABLE";
  if (annual) recurrenceType = "UNKNOWN";
  else if (usage) recurrenceType = "USAGE_RECURRING";
  else if (/\bfixed\s+monthly\b|(?:תשלום\s+חודשי\s+קבוע)/i.test(normalized)) recurrenceType = "FIXED_MONTHLY";

  return {
    documentClass: "RECURRING_BILL",
    recurrenceEvidence,
    recurrenceType,
    currentObligationAmount: obligation.amount,
  };
}

function identifyCategory(text) {
  const normalized = String(text || "").toLowerCase();
  if (/(חשמל|electric|iec|power)/i.test(normalized)) return "חשמל";
  if (/(ביטוח|insurance|פוליסה)/i.test(normalized)) return "ביטוח";
  // Prefer explicit service signals over provider names so multi-service providers
  // such as Cellcom/Partner/HOT are not incorrectly forced into mobile or TV.
  if (/(אינטרנט|internet|סיבים|fiber|broadband|bezeq|בזק|נתב|ראוטר)/i.test(normalized)) return "אינטרנט";
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

function matchStrongProvider(text) {
  const providers = [
    ["הראל", /(harel|הראל)/i],
    ["הפניקס", /(^|\W)(phoenix|fnx)(\W|$)|הפניקס/i],
    ["מגדל", /(migdal|מגדל)/i],
    ["כלל", /(clal|כלל\s*(?:ביטוח|insurance))/i],
    ["מנורה מבטחים", /(menora|מנורה\s*מבטחים|מנורה)/i],
    ["AIG", /(^|\W)aig(\W|$)/i],
    ["ביטוח ישיר", /(ביטוח\s*ישיר|direct\s*insurance|555\.co\.il)/i],
    ["ליברה", /(^|\W)libra(\W|$)|ליברה/i],
    ["weSure", /(^|\W)wesure(\W|$)|ווישור/i],
  ];
  return providers.find(([, pattern]) => pattern.test(text))?.[0] || null;
}

function knownProviderFromText(text, includeGenericPartner = true) {
  const normalized = String(text || "").trim();
  if (!normalized) return null;
  return matchProvider(normalized, includeGenericPartner) || matchStrongProvider(normalized);
}

function strongHeaderProvider(from, subject) {
  return knownProviderFromText(`${from} ${subject}`, true);
}

function isGenericProviderLabel(value) {
  const normalized = String(value || "").replace(/\s+/g, " ").trim();
  if (!normalized) return true;
  return GENERIC_PROVIDER_LABELS.some((pattern) => pattern.test(normalized));
}

function identifyProvider(from, subject, searchableText) {
  // Sender/subject are strong brand signals. Insurance brands are intentionally matched only
  // here so a brand word in arbitrary receipt body text cannot turn an unrelated receipt into
  // an insurance invoice.
  const strongMatch = strongHeaderProvider(from, subject);
  if (strongMatch) return strongMatch;

  const bodyText = String(searchableText || "").toLowerCase();
  return matchProvider(bodyText, false) || "ספק שזוהה מהודעת Gmail";
}

function fallbackCategoryForProvider(providerName) {
  if (["סלקום", "פרטנר", "HOT"].includes(providerName)) return "תקשורת";
  if (INSURANCE_PROVIDERS.has(providerName)) return "ביטוח";
  return null;
}

function normalizeDocumentCategory(value) {
  const normalized = String(value || "").trim().toLowerCase();
  if (!normalized) return null;
  if (SUPPORTED_CATEGORIES.has(normalized)) return SUPPORTED_CATEGORIES.get(normalized);
  return identifyCategory(normalized);
}

function withOptionalServiceType(invoice, serviceType) {
  return serviceType ? { ...invoice, serviceType } : invoice;
}

function normalizePdfInvoiceCandidate(candidate, message, sourceDocumentId = "") {
  if (!candidate || candidate.isInvoice !== true || !message?.id) return null;

  const payload = message.payload || {};
  const headers = payload.headers || [];
  const subject = firstHeader(headers, "Subject");
  const from = firstHeader(headers, "From");
  const headerDate = firstHeader(headers, "Date");
  const providerText = String(candidate.providerName || "").trim().slice(0, 160);
  const headerProvider = strongHeaderProvider(from, subject);
  const pdfKnownProvider = knownProviderFromText(providerText, true);

  let providerName;
  if (headerProvider && (
    isGenericProviderLabel(providerText) ||
    pdfKnownProvider === headerProvider
  )) {
    // Strong sender/subject evidence wins over an empty/generic PDF label and also
    // canonicalizes a PDF spelling variant of the same provider.
    providerName = headerProvider;
  } else if (pdfKnownProvider) {
    // A different explicit known provider in the document may be legitimate (for
    // example a forwarded invoice), so preserve the document evidence canonically.
    providerName = pdfKnownProvider;
  } else if (providerText && !isGenericProviderLabel(providerText)) {
    providerName = providerText;
  } else {
    providerName = headerProvider || "ספק לא מזוהה";
  }

  const category = normalizeDocumentCategory(candidate.category) ||
    fallbackCategoryForProvider(providerName) ||
    "אחר";
  const monthlyCost = Number(candidate.monthlyCost);
  const serviceType = normalizeServiceType(category, candidate.serviceType);

  if (!Number.isFinite(monthlyCost) || monthlyCost <= 0 || monthlyCost >= 1_000_000) return null;

  return withOptionalServiceType({
    sourceMessageId: String(sourceDocumentId || message.id),
    providerName,
    category,
    monthlyCost,
    receivedDate: String(candidate.receivedDate || headerDate || "").slice(0, 120),
    verificationStatus: "UNVERIFIED_GMAIL_IMPORT",
  }, serviceType);
}

function parseGmailMessage(message) {
  // A successfully analyzed PDF has authoritative semantic precedence. The PDF
  // analyzer records this only transiently on the in-memory Gmail message; no raw
  // content or classification side-channel is persisted by the parser.
  if (hasPdfClassificationResult(message)) return null;

  const payload = message?.payload || {};
  const headers = payload.headers || [];
  const subject = firstHeader(headers, "Subject");
  const from = firstHeader(headers, "From");
  const date = firstHeader(headers, "Date");
  const snippet = String(message?.snippet || "").slice(0, 2000);
  const bodyText = collectMessageText(payload);
  const searchableText = `${subject} ${from} ${snippet} ${bodyText}`.slice(0, 30_000);
  const providerName = identifyProvider(from, subject, searchableText);
  const category = identifyCategory(searchableText) || fallbackCategoryForProvider(providerName);
  const classification = classifyBodyDocument(searchableText, category);
  const amount = classification.currentObligationAmount ?? parseAmount(searchableText);

  if (!message?.id || amount == null || category == null) return null;

  const serviceType = extractServiceType(category, searchableText);

  // Persist only the minimum fields needed for invoice deduplication, financial context and display.
  // Raw Gmail subject, sender, snippet and body text are deliberately not returned or stored.
  return withOptionalServiceType({
    sourceMessageId: String(message.id),
    providerName,
    category,
    monthlyCost: amount,
    receivedDate: date.slice(0, 120),
    verificationStatus: "UNVERIFIED_GMAIL_IMPORT",
    documentClass: classification.documentClass,
    recurrenceEvidence: classification.recurrenceEvidence,
    recurrenceType: classification.recurrenceType,
  }, serviceType);
}

module.exports = {
  firstHeader,
  decodeBase64Url,
  collectMessageText,
  collectPdfAttachments,
  parseAmount,
  parseCurrentObligation,
  classifyBodyDocument,
  identifyCategory,
  identifyProvider,
  fallbackCategoryForProvider,
  normalizePdfInvoiceCandidate,
  parseGmailMessage,
};
