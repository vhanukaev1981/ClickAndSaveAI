"use strict";

const { buildPartnerMetrics, normalizeCommissionRecord } = require("./providerReconciliation");

function roundMoney(value) {
  return Math.round(Number(value || 0) * 100) / 100;
}

function buildPartnerFinanceSummary(records) {
  const normalized = (Array.isArray(records) ? records : []).map(normalizeCommissionRecord);
  const metrics = buildPartnerMetrics(normalized);
  const outstandingConfirmed = roundMoney(metrics.confirmedAmount - metrics.paidAmount);
  const unconfirmedExpected = roundMoney(metrics.expectedAmount - metrics.confirmedAmount);

  return {
    records: metrics.records,
    expectedAmount: metrics.expectedAmount,
    confirmedAmount: metrics.confirmedAmount,
    paidAmount: metrics.paidAmount,
    outstandingConfirmed: Math.max(0, outstandingConfirmed),
    unconfirmedExpected: Math.max(0, unconfirmedExpected),
    rejectedCount: metrics.rejectedCount,
    collectionRate: metrics.confirmedAmount > 0
      ? Math.round((metrics.paidAmount / metrics.confirmedAmount) * 10000) / 10000
      : 0,
  };
}

module.exports = {
  buildPartnerFinanceSummary,
};
