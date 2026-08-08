"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { buildOwnerMetrics, buildPartnerMetrics } = require("../src/adminMetricsDomain");

test("owner metrics calculate savings, conversion and commission KPIs", () => {
  const metrics = buildOwnerMetrics({
    activeUsers: 100,
    connectedGmailUsers: 75,
    verifiedOpportunities: 20,
    totalVerifiedMonthlySavings: 2500,
    clicks: 50,
    conversions: 10,
    confirmedConversions: 8,
    accruedCommission: 5000,
    paidCommission: 3200,
  });
  assert.equal(metrics.gmailConnectionRate, 75);
  assert.equal(metrics.totalVerifiedAnnualSavings, 30000);
  assert.equal(metrics.clickToConversionRate, 20);
  assert.equal(metrics.clickToConfirmedConversionRate, 16);
  assert.equal(metrics.outstandingCommission, 1800);
});

test("owner metrics avoid divide-by-zero artifacts", () => {
  const metrics = buildOwnerMetrics({});
  assert.equal(metrics.gmailConnectionRate, 0);
  assert.equal(metrics.clickToConversionRate, 0);
});

test("partner metrics aggregate clicks conversions and commission", () => {
  const metrics = buildPartnerMetrics([
    { partnerId: "p1", type: "CLICK", commission: 0 },
    { partnerId: "p1", type: "CLICK", commission: 0 },
    { partnerId: "p1", type: "CONVERSION", status: "CONFIRMED", commission: 120 },
    { partnerId: "p2", type: "CLICK", commission: 0 },
    { partnerId: "p2", type: "CONVERSION", status: "PENDING", commission: 50 },
  ]);
  assert.equal(metrics[0].partnerId, "p1");
  assert.equal(metrics[0].clicks, 2);
  assert.equal(metrics[0].confirmedConversions, 1);
  assert.equal(metrics[0].confirmedConversionRate, 50);
  assert.equal(metrics[0].accruedCommission, 120);
});
