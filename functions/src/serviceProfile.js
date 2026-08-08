"use strict";

function normalizeText(value) {
  return String(value || "")
    .replace(/\u00a0/g, " ")
    .replace(/,/g, "")
    .replace(/\s+/g, " ")
    .trim();
}

function roundWhole(value) {
  const number = Number(value);
  if (!Number.isFinite(number)) return null;
  return Math.round(number);
}

function internetSpeedMbps(text) {
  const raw = normalizeText(text);
  if (!raw) return null;

  const gigabit = raw.match(
    /(?:^|\W)(\d+(?:\.\d+)?)\s*(?:gbps|gbit(?:\/s)?|gigabit(?:\/s)?|giga(?:bit)?|גיגה(?:ביט)?)(?:\W|$)/i
  );
  if (gigabit) {
    const value = roundWhole(Number(gigabit[1]) * 1000);
    return value != null && value >= 10 && value <= 10_000 ? value : null;
  }

  const megabit = raw.match(
    /(?:^|\W)(\d+(?:\.\d+)?)\s*(?:mbps|mbit(?:\/s)?|megabit(?:\/s)?|מגה(?:ביט)?)(?:\W|$)/i
  );
  if (megabit) {
    const value = roundWhole(megabit[1]);
    return value != null && value >= 10 && value <= 10_000 ? value : null;
  }

  return null;
}

function mobileProfile(text) {
  const raw = normalizeText(text);
  if (!raw) return null;
  const linesMatch = raw.match(/(?:^|\W)(\d{1,2})\s*(?:קווים|קוי(?:ם)?|lines?)(?:\W|$)/i);
  const dataMatch = raw.match(/(?:^|\W)(\d+(?:\.\d+)?)\s*(?:gb|gigabytes?|ג[׳']?ב|גיגה(?:בייט)?)(?:\W|$)/i);
  if (!linesMatch || !dataMatch) return null;
  const lines = roundWhole(linesMatch[1]);
  const dataGb = roundWhole(dataMatch[1]);
  if (lines == null || lines < 1 || lines > 20) return null;
  if (dataGb == null || dataGb < 1 || dataGb > 100_000) return null;
  return `MOBILE_${lines}_LINES_${dataGb}_GB`;
}

function insuranceProfile(text) {
  const raw = normalizeText(text).toLowerCase();
  if (!raw) return null;
  if (/(ביטוח\s*(?:רכב|מקיף|חובה)|car\s+insurance|vehicle\s+insurance)/i.test(raw)) {
    return "INSURANCE_CAR";
  }
  if (/(ביטוח\s*(?:דירה|מבנה|תכולה)|home\s+insurance|property\s+insurance)/i.test(raw)) {
    return "INSURANCE_HOME";
  }
  if (/(ביטוח\s*(?:נסיעות|נסיעה)|travel\s+insurance)/i.test(raw)) {
    return "INSURANCE_TRAVEL";
  }
  return null;
}

function televisionProfile(text) {
  const raw = normalizeText(text);
  if (/(streaming|סטרימינג)/i.test(raw)) return "TV_STREAMING";
  return null;
}

function canonicalServiceType(category, raw) {
  const upper = raw.toUpperCase();
  const normalizedCategory = normalizeText(category).toLowerCase();

  if (["אינטרנט", "internet", "fiber", "broadband"].includes(normalizedCategory)) {
    const match = upper.match(/^INTERNET_(\d{2,5})_MBPS$/);
    if (!match) return null;
    const speed = Number(match[1]);
    return speed >= 10 && speed <= 10_000 ? `INTERNET_${speed}_MBPS` : null;
  }

  if (["סלולר", "mobile", "cellular"].includes(normalizedCategory)) {
    const match = upper.match(/^MOBILE_(\d{1,2})_LINES_(\d{1,6})_GB$/);
    if (!match) return null;
    const lines = Number(match[1]);
    const dataGb = Number(match[2]);
    if (lines < 1 || lines > 20 || dataGb < 1 || dataGb > 100_000) return null;
    return `MOBILE_${lines}_LINES_${dataGb}_GB`;
  }

  if (["ביטוח", "insurance"].includes(normalizedCategory)) {
    return ["INSURANCE_CAR", "INSURANCE_HOME", "INSURANCE_TRAVEL"].includes(upper)
      ? upper
      : null;
  }

  if (["טלוויזיה", "television", "tv"].includes(normalizedCategory)) {
    return upper === "TV_STREAMING" ? upper : null;
  }

  return null;
}

function normalizeServiceType(category, value) {
  const raw = normalizeText(value);
  if (!raw) return null;
  if (raw.toUpperCase() === "ANY") return "ANY";

  const canonical = canonicalServiceType(category, raw);
  if (canonical) return canonical;

  const normalizedCategory = normalizeText(category).toLowerCase();
  if (["אינטרנט", "internet", "fiber", "broadband"].includes(normalizedCategory)) {
    const speed = internetSpeedMbps(raw);
    return speed == null ? null : `INTERNET_${speed}_MBPS`;
  }
  if (["סלולר", "mobile", "cellular"].includes(normalizedCategory)) {
    return mobileProfile(raw);
  }
  if (["ביטוח", "insurance"].includes(normalizedCategory)) {
    return insuranceProfile(raw);
  }
  if (["טלוויזיה", "television", "tv"].includes(normalizedCategory)) {
    return televisionProfile(raw);
  }
  return null;
}

const CURRENT_SERVICE_CUES = /(?:החבילה\s*שלך|המסלול\s*שלך|השירות\s*שלך|המהירות\s*שלך|חבילה\s*נוכחית|מסלול\s*נוכחי|שירות\s*נוכחי|מהירות\s*נוכחית|פרטי\s*החבילה|פרטי\s*השירות|service\s*details|current\s*(?:plan|package|service|speed)|your\s*(?:plan|package|service|speed)|(?:שירות|חבילה|מסלול|מהירות|speed|plan|package)\s*(?:[:\-]|אינטרנט|סיבים))/i;
const PROMOTIONAL_CUES = /(?:מבצע|הצעה|שדרוג|הצטרפות|חדש\s*עבורך|upgrade|special\s*offer|offer|promotion|promo|switch\s*to|join\s*now)/i;
const SPEED_CANDIDATE = /(\d+(?:\.\d+)?)\s*(gbps|gbit(?:\/s)?|gigabit(?:\/s)?|giga(?:bit)?|גיגה(?:ביט)?|mbps|mbit(?:\/s)?|megabit(?:\/s)?|מגה(?:ביט)?)/ig;

function extractObservedInternetProfile(text) {
  const raw = normalizeText(text);
  if (!raw) return null;
  SPEED_CANDIDATE.lastIndex = 0;
  let match;
  while ((match = SPEED_CANDIDATE.exec(raw)) !== null) {
    const start = match.index;
    const prefix = raw.slice(Math.max(0, start - 110), start);
    const suffix = raw.slice(start + match[0].length, Math.min(raw.length, start + match[0].length + 35));
    const context = `${prefix} ${match[0]} ${suffix}`;
    const promotionalPrefix = prefix.slice(-75);
    if (!CURRENT_SERVICE_CUES.test(context)) continue;
    if (PROMOTIONAL_CUES.test(promotionalPrefix)) continue;
    const canonical = normalizeServiceType("אינטרנט", match[0]);
    if (canonical) return canonical;
  }
  return null;
}

function extractObservedMobileProfile(text) {
  const raw = normalizeText(text);
  if (!raw) return null;
  const profile = mobileProfile(raw);
  if (!profile) return null;
  const linesIndex = raw.search(/\d{1,2}\s*(?:קווים|קוי(?:ם)?|lines?)/i);
  if (linesIndex < 0) return null;
  const prefix = raw.slice(Math.max(0, linesIndex - 110), linesIndex);
  const context = raw.slice(Math.max(0, linesIndex - 110), Math.min(raw.length, linesIndex + 180));
  if (!CURRENT_SERVICE_CUES.test(context)) return null;
  if (PROMOTIONAL_CUES.test(prefix.slice(-75))) return null;
  return profile;
}

function extractServiceType(category, text) {
  const normalizedCategory = normalizeText(category).toLowerCase();
  if (["אינטרנט", "internet", "fiber", "broadband"].includes(normalizedCategory)) {
    return extractObservedInternetProfile(text);
  }
  if (["סלולר", "mobile", "cellular"].includes(normalizedCategory)) {
    return extractObservedMobileProfile(text);
  }
  if (["ביטוח", "insurance"].includes(normalizedCategory)) {
    const raw = normalizeText(text);
    if (PROMOTIONAL_CUES.test(raw) && !/(פוליסה|policy|הפוליסה\s*שלך|your\s+policy)/i.test(raw)) return null;
    return insuranceProfile(raw);
  }
  if (["טלוויזיה", "television", "tv"].includes(normalizedCategory)) {
    const raw = normalizeText(text);
    if (PROMOTIONAL_CUES.test(raw)) return null;
    return televisionProfile(raw);
  }
  return null;
}

module.exports = {
  internetSpeedMbps,
  mobileProfile,
  insuranceProfile,
  televisionProfile,
  canonicalServiceType,
  normalizeServiceType,
  extractObservedInternetProfile,
  extractObservedMobileProfile,
  extractServiceType,
};
