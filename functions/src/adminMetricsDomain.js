"use strict";

function money(value) {
  const amount = Number(value || 0);
  if (!Number.isFinite(amount)) throw new TypeError("money value must be finite");
  return Math.round(amount * 100) / 100;
}

function safeCount(value) {
  const count = Number(value || 0);
  if (!Number.isFinite(count) || count < 0) throw new TypeError("count must be non-negative");
  return Math.floor(count);
}

function buildOwnerMetrics(input = {}) {
  const clicks = safeCount(input.clicks);
  const conversions = safeCount(input.conversions);
  const confirmedConversions = safeCount(input.confirmedConversions);
  const activeUsers = safeCount(input.activeUsers);
  const connectedGmailUsers = safeCount(input.connectedGmailUsers);
  const verifiedOpportunities = safeCount(input.verifiedOpportunities);
  const totalVerifiedMonthlySavings = money(input.totalVerifiedMonthlySavings);
  const accruedCommission = money(input.accruedCommission);
  const paidCommission = money(input.paidCommission);

  return {
    activeUsers,
    connectedGmailUsers,
    gmailConnectionRate: activeUsers > 0 ? Math.round((connectedGmailUsers / activeUsers) * 10000) / 100 : 0,
    verifiedOpportunities,
    totalVerifiedMonthlySavings,
    totalVerifiedAnnualSavings: money(totalVerifiedMonthlySavings * 12),
    clicks,
    conversions,
    confirmedConversions,
    clickToConversionRate: clicks > 0 ? Math.round((conversions / clicks) * 10000) / 100 : 0,
    clickToConfirmedConversionRate: clicks > 0 ? Math.round((confirmedConversions / clicks) * 10000) / 100 : 0,
    accruedCommission,
    paidCommission,
    outstandingCommission: money(Math.max(0, accruedCommission - paidCommission)),
  };
}

function buildPartnerMetrics(events = []) {
  const byPartner = new Map();
  for (const event of Array.isArray(events) ? events : []) {
    const partnerId = String(event.partnerId || "").trim();
    if (!partnerId) continue;
    const current = byPartner.get(partnerId) || {
      partnerId,
      clicks: 0,
      conversions: 0,
      confirmedConversions: 0,
      accruedCommission: 0,
    };
    if (event.type === "CLICK") current.clicks += 1;
    if (event.type === "CONVERSION") {
      current.conversions += 1;
      if (event.status === "CONFIRMED") current.confirmedConversions += 1;
    }
    current.accruedCommission = money(current.accruedCommission + money(event.commission || 0));
    byPartner.set(partnerId, current);
  }
  return [...byPartner.values()].map((item) => ({
    ...item,
    confirmedConversionRate: item.clicks > 0
      ? Math.round((item.confirmedConversions / item.clicks) * 10000) / 100
      : 0,
  })).sort((a, b) => b.accruedCommission - a.accruedCommission);
}

module.exports = {
  buildOwnerMetrics,
  buildPartnerMetrics,
};
