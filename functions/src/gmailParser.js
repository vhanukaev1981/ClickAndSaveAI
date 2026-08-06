"use strict";

const AMOUNT_PATTERNS = [
  /(?:₪|ש[\"״]?ח|ILS)\s*([\d,]+(?:\.\d{1,2})?)/i,
  /([\d,]+(?:\.\d{1,2})?)\s*(?:₪|ש[\"״]?ח|ILS)/i,
];

function firstHeader(headers, name) {
  if (!Array.isArray(headers)) return "";
  return headers.find((header) =>
    String(header?.name || "").toLowerCase() === name.toLowerCase()
  )?.value || "";
}

function parseAmount(text) {
  for (const pattern of AMOUNT_PATTERNS) {
    const match = pattern.exec(text);
    if (!match) continue;
    const value = Number(match[1].replace(/,/g, ""));
    if (Number.isFinite(value) && value > 0 && value < 1_000_000) return value;
  }
  return null;
}

function identifyCategory(text) {
  const normalized = text.toLowerCase();
  if (/(חשמל|electric|iec|power)/i.test(normalized)) return "חשמל";
  if (/(סלולר|mobile|cellular|פלאפון|סלקום|פרטנר|wecom|019)/i.test(normalized)) return "סלולר";
  if (/(אינטרנט|סיבים|fiber|broadband|bezeq)/i.test(normalized)) return "אינטרנט";
  if (/(ביטוח|insurance|פוליסה)/i.test(normalized)) return "ביטוח";
  if (/(טלוויזיה|streaming|netflix|yes|hot|freetv)/i.test(normalized)) return "טלוויזיה";
  return null;
}

function identifyProvider(from, subject, snippet) {
  const text = `${from} ${subject} ${snippet}`.toLowerCase();
  const providers = [
    ["חברת החשמל", /(iec|חברת החשמל)/i],
    ["בזק", /(bezeq|בזק)/i],
    ["סלקום", /(cellcom|סלקום)/i],
    ["פרטנר", /(partner|פרטנר)/i],
    ["פלאפון", /(pelephone|פלאפון)/i],
    ["HOT", /(^|\W)hot(\W|$)/i],
    ["yes", /(^|\W)yes(\W|$)/i],
  ];
  return providers.find(([, pattern]) => pattern.test(text))?.[0] || "ספק שזוהה מהודעת Gmail";
}

function parseGmailMessage(message) {
  const payload = message?.payload || {};
  const headers = payload.headers || [];
  const subject = firstHeader(headers, "Subject");
  const from = firstHeader(headers, "From");
  const date = firstHeader(headers, "Date");
  const snippet = String(message?.snippet || "").slice(0, 1000);
  const searchableText = `${subject} ${from} ${snippet}`;
  const amount = parseAmount(searchableText);
  const category = identifyCategory(searchableText);

  if (!message?.id || amount == null || category == null) return null;

  // Persist only the minimum fields needed for invoice deduplication and display.
  // The Gmail subject, sender and message snippet are deliberately not returned.
  return {
    sourceMessageId: String(message.id),
    providerName: identifyProvider(from, subject, snippet),
    category,
    monthlyCost: amount,
    receivedDate: date.slice(0, 120),
    verificationStatus: "UNVERIFIED_GMAIL_IMPORT",
  };
}

module.exports = {
  firstHeader,
  parseAmount,
  identifyCategory,
  identifyProvider,
  parseGmailMessage,
};
