"use strict";

function normalizeText(value) {
  return String(value || "")
    .replace(/\u00a0/g, " ")
    .replace(/,/g, "")
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

function extractServiceType(category, text) {
  return normalizeServiceType(category, text);
}

module.exports = {
  internetSpeedMbps,
  mobileProfile,
  insuranceProfile,
  televisionProfile,
  canonicalServiceType,
  normalizeServiceType,
  extractServiceType,
};
