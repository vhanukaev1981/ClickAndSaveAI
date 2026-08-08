"use strict";

const crypto = require("node:crypto");

const MONETIZABLE_CATEGORIES = new Set([
  "אינטרנט",
  "סלולר",
  "טלוויזיה",
  "ביטוח",
  "חשמל",
  "תקשורת",
]);

function normalizeInvoice(invoice) {
  if (!invoice || typeof invoice !== "object") return null;
  const providerName = String(invoice.providerName || "").trim();
  const category = String(invoice.category || "").trim();
  const monthlyCost = Number(invoice.monthlyCost);
  const sourceMessageId = String(invoice.sourceMessageId || "").trim();
  if (!providerName || !category || !sourceMessageId) return null;
  if (!Number.isFinite(monthlyCost) || monthlyCost <= 0 || monthlyCost >= 1_000_000) return null;

  const receivedDate = String(invoice.receivedDate || "").trim();
  const parsedDate = Date.parse(receivedDate);
  return {
    providerName,
    category,
    monthlyCost,
    sourceMessageId,
    receivedDate,
    receivedAtMs: Number.isFinite(parsedDate) ? parsedDate : 0,
    verificationStatus: String(invoice.verificationStatus || "UNVERIFIED_GMAIL_IMPORT"),
  };
}

function serviceKey(invoice) {
  return `${invoice.providerName.toLocaleLowerCase("he-IL")}::${invoice.category}`;
}

function roundMoney(value) {
  return Math.round((Number(value) + Number.EPSILON) * 100) / 100;
}

function groupInvoices(invoices) {
  const groups = new Map();
  for (const rawInvoice of Array.isArray(invoices) ? invoices : []) {
    const invoice = normalizeInvoice(rawInvoice);
    if (!invoice) continue;
    const key = serviceKey(invoice);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(invoice);
  }

  for (const group of groups.values()) {
    group.sort((a, b) => {
      if (a.receivedAtMs !== b.receivedAtMs) return a.receivedAtMs - b.receivedAtMs;
      return a.sourceMessageId.localeCompare(b.sourceMessageId);
    });
  }
  return groups;
}

function distinctObservedMonths(group) {
  const months = new Set();
  for (const invoice of group) {
    if (!invoice.receivedAtMs) continue;
    const date = new Date(invoice.receivedAtMs);
    months.add(`${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, "0")}`);
  }
  return months.size;
}

function isRecurringGroup(group) {
  const observedMonths = distinctObservedMonths(group);
  return group.length >= 2 && (observedMonths >= 2 || group.length >= 3);
}

function buildFinancialContext(invoices) {
  const groups = groupInvoices(invoices);
  const recurringServices = [];
  const categoryTotals = new Map();
  let observedRecurringMonthlySpend = 0;

  for (const group of groups.values()) {
    const latest = group[group.length - 1];
    const observedMonths = distinctObservedMonths(group);
    if (!isRecurringGroup(group)) continue;

    recurringServices.push({
      providerName: latest.providerName,
      category: latest.category,
      latestMonthlyCost: roundMoney(latest.monthlyCost),
      observationCount: group.length,
      observedMonths,
      latestSourceMessageId: latest.sourceMessageId,
      verificationStatus: latest.verificationStatus,
    });

    observedRecurringMonthlySpend += latest.monthlyCost;
    categoryTotals.set(
      latest.category,
      roundMoney((categoryTotals.get(latest.category) || 0) + latest.monthlyCost)
    );
  }

  recurringServices.sort((a, b) => b.latestMonthlyCost - a.latestMonthlyCost);
  const categories = [...categoryTotals.entries()]
    .map(([category, observedMonthlySpend]) => ({ category, observedMonthlySpend }))
    .sort((a, b) => b.observedMonthlySpend - a.observedMonthlySpend);

  return {
    sourceCoverage: ["GMAIL_READONLY"],
    isCompleteHouseholdSpend: false,
    observedRecurringMonthlySpend: roundMoney(observedRecurringMonthlySpend),
    recurringServiceCount: recurringServices.length,
    recurringServices,
    categories,
  };
}

function stableId(parts) {
  return crypto.createHash("sha256").update(parts.join("|")).digest("hex").slice(0, 32);
}

function detectFinancialSignals(invoices) {
  const groups = groupInvoices(invoices);
  const insights = [];
  const opportunities = [];

  for (const group of groups.values()) {
    const latest = group[group.length - 1];
    const previous = group.length >= 2 ? group[group.length - 2] : null;
    const recurring = isRecurringGroup(group);
    let materialIncrease = false;
    let roundedDelta = 0;
    let roundedPercent = 0;

    if (recurring) {
      insights.push({
        id: stableId(["recurring", serviceKey(latest)]),
        type: "RECURRING_SERVICE_OBSERVED",
        providerName: latest.providerName,
        category: latest.category,
        currentMonthlyCost: roundMoney(latest.monthlyCost),
        observationCount: group.length,
        sourceMessageId: latest.sourceMessageId,
        severity: "INFO",
      });
    }

    if (previous) {
      const delta = latest.monthlyCost - previous.monthlyCost;
      const percent = previous.monthlyCost > 0 ? (delta / previous.monthlyCost) * 100 : 0;
      materialIncrease = delta >= 5 && percent >= 5;
      roundedDelta = roundMoney(delta);
      roundedPercent = roundMoney(percent);

      if (materialIncrease) {
        insights.push({
          id: stableId(["price-increase", serviceKey(latest), latest.sourceMessageId]),
          type: "PRICE_INCREASE_DETECTED",
          providerName: latest.providerName,
          category: latest.category,
          previousMonthlyCost: roundMoney(previous.monthlyCost),
          currentMonthlyCost: roundMoney(latest.monthlyCost),
          monthlyIncrease: roundedDelta,
          percentIncrease: roundedPercent,
          sourceMessageId: latest.sourceMessageId,
          previousSourceMessageId: previous.sourceMessageId,
          severity: roundedPercent >= 20 ? "HIGH" : "MEDIUM",
        });
      }
    }

    const monetizable = MONETIZABLE_CATEGORIES.has(latest.category);
    const shouldOptimize = monetizable && (recurring || materialIncrease);
    if (!shouldOptimize) continue;

    opportunities.push({
      id: stableId(["optimize-service", serviceKey(latest)]),
      type: materialIncrease ? "COMPARE_AFTER_PRICE_INCREASE" : "OPTIMIZE_RECURRING_SERVICE",
      providerName: latest.providerName,
      category: latest.category,
      currentMonthlyCost: roundMoney(latest.monthlyCost),
      previousMonthlyCost: previous ? roundMoney(previous.monthlyCost) : 0,
      monthlyIncrease: materialIncrease ? roundedDelta : 0,
      percentIncrease: materialIncrease ? roundedPercent : 0,
      potentialMonthlySaving: null,
      potentialAnnualSaving: null,
      recommendationAction: "FIND_VERIFIED_ALTERNATIVES",
      evidenceSourceMessageIds: previous
        ? [previous.sourceMessageId, latest.sourceMessageId]
        : [latest.sourceMessageId],
      commercial: {
        monetizableCategory: true,
        partnerMatchStatus: "NOT_CHECKED",
        commissionStatus: "UNKNOWN",
        userIntent: "SYSTEM_DETECTED_SAVINGS_NEED",
      },
      truthfulness: {
        savingsClaimAvailable: false,
        reason: "No verified current market offer has been matched yet.",
      },
    });
  }

  return { insights, opportunities };
}

module.exports = {
  MONETIZABLE_CATEGORIES,
  normalizeInvoice,
  buildFinancialContext,
  detectFinancialSignals,
};
