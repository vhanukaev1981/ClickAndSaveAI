"use strict";

const ACTIONS = Object.freeze({
  NONE: "NONE",
  CONNECT_SOURCE: "CONNECT_SOURCE",
  REVIEW_INVOICE: "REVIEW_INVOICE",
  SHOW_VERIFIED_SAVINGS: "SHOW_VERIFIED_SAVINGS",
  ALERT_PRICE_CHANGE: "ALERT_PRICE_CHANGE",
});

function number(value) {
  const parsed = Number(value || 0);
  return Number.isFinite(parsed) ? parsed : 0;
}

function selectNextAction(input = {}) {
  const connectedSources = Math.max(0, Math.floor(number(input.connectedSources)));
  const unverifiedInvoices = Math.max(0, Math.floor(number(input.unverifiedInvoices)));
  const verifiedSavingsMonthly = Math.max(0, number(input.verifiedSavingsMonthly));
  const verifiedOpportunityCount = Math.max(0, Math.floor(number(input.verifiedOpportunityCount)));
  const urgentPriceAlerts = Math.max(0, Math.floor(number(input.urgentPriceAlerts)));

  if (verifiedOpportunityCount > 0 && verifiedSavingsMonthly > 0) {
    return {
      action: ACTIONS.SHOW_VERIFIED_SAVINGS,
      priority: 100,
      userMessage: `אפשר לחסוך ${Math.round(verifiedSavingsMonthly * 100) / 100} ₪ בחודש`,
      reason: "verified_savings_available",
    };
  }

  if (urgentPriceAlerts > 0) {
    return {
      action: ACTIONS.ALERT_PRICE_CHANGE,
      priority: 90,
      userMessage: "זיהינו שינוי במחיר שכדאי לבדוק",
      reason: "urgent_price_change",
    };
  }

  if (unverifiedInvoices > 0) {
    return {
      action: ACTIONS.REVIEW_INVOICE,
      priority: 70,
      userMessage: "יש חשבון חדש בבדיקה",
      reason: "invoice_requires_verification",
    };
  }

  if (connectedSources === 0) {
    return {
      action: ACTIONS.CONNECT_SOURCE,
      priority: 50,
      userMessage: "חבר מקור מידע כדי שנוכל להתחיל לחפש חיסכון",
      reason: "no_financial_source_connected",
    };
  }

  return {
    action: ACTIONS.NONE,
    priority: 0,
    userMessage: "הכל מעודכן. נעדכן כשנמצא חיסכון חדש.",
    reason: "nothing_actionable",
  };
}

function shouldNotify(decision) {
  if (!decision || typeof decision !== "object") return false;
  return decision.action === ACTIONS.SHOW_VERIFIED_SAVINGS ||
    decision.action === ACTIONS.ALERT_PRICE_CHANGE;
}

module.exports = { ACTIONS, selectNextAction, shouldNotify };
