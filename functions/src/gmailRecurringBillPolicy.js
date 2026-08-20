"use strict";

const crypto = require("node:crypto");

const RECURRING_DOCUMENT_CLASS = "RECURRING_BILL";
const REPEATED_PROVIDER_EVIDENCE = "REPEATED_PROVIDER_HISTORY";
const HARD_REJECT_CLASSES = new Set(["ONE_OFF", "REFUND", "CONTRACT", "UNKNOWN"]);
const HISTORY_ELIGIBLE_CLASSES = new Set(["RECURRING_BILL", "RECEIPT_ONLY"]);
const MIN_HISTORY_GAP_MS = 14 * 24 * 60 * 60 * 1000;

function normalizeText(value) {
  return String(value || "").replace(/\s+/g, " ").trim().toLowerCase();
}

function normalizedAmount(value) {
  const amount = Number(value);
  return Number.isFinite(amount) && amount > 0 ? amount.toFixed(2) : "";
}

function parseDate(value) {
  const timestamp = Date.parse(String(value || ""));
  return Number.isFinite(timestamp) ? timestamp : null;
}

function pdfContentFingerprint(pdfBase64) {
  const raw = String(pdfBase64 || "").trim();
  if (!raw) return "";
  let bytes;
  try {
    bytes = Buffer.from(raw, "base64");
  } catch {
    return "";
  }
  if (!bytes.length) return "";
  return `sha256:${crypto.createHash("sha256").update(bytes).digest("hex")}`;
}

function contentDedupeKey(item) {
  const fingerprint = String(item?.contentFingerprint || "").trim().toLowerCase();
  return /^sha256:[a-f0-9]{64}$/.test(fingerprint) ? fingerprint : "";
}

function transactionDedupeKey(item) {
  const provider = normalizeText(item?.providerName);
  const amount = normalizedAmount(item?.monthlyCost);
  const date = String(item?.receivedDate || "").trim().slice(0, 10);
  if (!provider || !amount || !date) return "";
  return [provider, normalizeText(item?.category), normalizeText(item?.serviceType), amount, date].join("|");
}

function historyGroupKey(item) {
  return [
    normalizeText(item?.providerName),
    normalizeText(item?.category),
    normalizeText(item?.serviceType),
  ].join("|");
}

function classRank(documentClass) {
  if (documentClass === RECURRING_DOCUMENT_CLASS) return 3;
  if (documentClass === "RECEIPT_ONLY") return 2;
  return 1;
}

function dedupeByContent(items) {
  const seen = new Set();
  const result = [];
  for (const item of items) {
    const key = contentDedupeKey(item);
    if (key && seen.has(key)) continue;
    if (key) seen.add(key);
    result.push(item);
  }
  return result;
}

function dedupeTransactions(items) {
  const byKey = new Map();
  const noKey = [];
  for (const item of items) {
    const key = transactionDedupeKey(item);
    if (!key) {
      noKey.push(item);
      continue;
    }
    const current = byKey.get(key);
    if (!current || classRank(item.documentClass) > classRank(current.documentClass)) {
      byKey.set(key, item);
    }
  }
  return [...byKey.values(), ...noKey];
}

function hasExplicitRecurrence(item) {
  if (item?.documentClass !== RECURRING_DOCUMENT_CLASS) return false;
  const evidence = String(item?.recurrenceEvidence || "").trim();
  return Boolean(evidence && evidence !== "NONE" && evidence !== REPEATED_PROVIDER_EVIDENCE);
}

function hasDistinctHistoryDates(items) {
  const dates = [...new Set(items.map((item) => parseDate(item?.receivedDate)).filter((value) => value !== null))]
    .sort((a, b) => a - b);
  if (dates.length < 2) return false;
  return dates[dates.length - 1] - dates[0] >= MIN_HISTORY_GAP_MS;
}

function selectRecurringBills(input) {
  const safe = (Array.isArray(input) ? input : [])
    .filter((item) => item && typeof item === "object")
    .map((item) => ({ ...item }));

  const contentUnique = dedupeByContent(safe);
  const transactionUnique = dedupeTransactions(contentUnique);
  const direct = transactionUnique.filter(hasExplicitRecurrence);

  const eligible = transactionUnique.filter((item) =>
    !HARD_REJECT_CLASSES.has(item.documentClass) &&
    HISTORY_ELIGIBLE_CLASSES.has(item.documentClass)
  );
  const groups = new Map();
  for (const item of eligible) {
    const key = historyGroupKey(item);
    if (!key || key === "||") continue;
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(item);
  }

  const selectedBySource = new Map();
  for (const item of direct) selectedBySource.set(String(item.sourceMessageId || ""), item);

  for (const group of groups.values()) {
    if (!hasDistinctHistoryDates(group)) continue;
    for (const item of group) {
      const sourceId = String(item.sourceMessageId || "");
      if (selectedBySource.has(sourceId)) continue;
      selectedBySource.set(sourceId, {
        ...item,
        recurrenceEvidence: REPEATED_PROVIDER_EVIDENCE,
        recurrenceType: item.recurrenceType && item.recurrenceType !== "UNKNOWN"
          ? item.recurrenceType
          : "USAGE_RECURRING",
      });
    }
  }

  return [...selectedBySource.values()];
}

module.exports = {
  pdfContentFingerprint,
  selectRecurringBills,
  REPEATED_PROVIDER_EVIDENCE,
};
