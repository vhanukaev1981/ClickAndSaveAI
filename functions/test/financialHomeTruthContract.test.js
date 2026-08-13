"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const source = fs.readFileSync(
  path.resolve(__dirname, "..", "src", "financialAgentFunctions.js"),
  "utf8"
);

test("Financial Home marks an existing authoritative context READY without coercing missing values to zero", () => {
  const getHomeBlock = source.slice(source.indexOf("exports.getFinancialHome"));
  assert.match(getHomeBlock, /contextStatus:\s*"READY"/);
  assert.match(getHomeBlock, /observedRecurringMonthlySpend:\s*nullableFiniteNumber\(context\.observedRecurringMonthlySpend\)/);
  assert.match(getHomeBlock, /recurringServiceCount:\s*nullableFiniteNumber\(context\.recurringServiceCount\)/);
  assert.doesNotMatch(getHomeBlock, /observedRecurringMonthlySpend:\s*Number\(context\.observedRecurringMonthlySpend \|\| 0\)/);
  assert.doesNotMatch(getHomeBlock, /recurringServiceCount:\s*Number\(context\.recurringServiceCount \|\| 0\)/);
});

test("Financial Home never converts a still-missing context document into a zero-filled READY response", () => {
  const getHomeBlock = source.slice(source.indexOf("exports.getFinancialHome"));
  assert.doesNotMatch(getHomeBlock, /contextSnapshot\.data\(\) \|\| \{\}/);
  assert.match(getHomeBlock, /if \(!contextSnapshot\.exists\)[\s\S]*new HttpsError\([\s\S]*"unavailable"/);
});

test("Financial Activity is a server-authoritative ledger built only from stored transition timestamps", () => {
  assert.match(source, /function buildFinancialActivityLedger/);
  assert.match(source, /exports\.getFinancialActivity\s*=\s*onCall/);
  assert.match(source, /GMAIL_CONNECTED/);
  assert.match(source, /SCAN_COMPLETED/);
  assert.match(source, /BILL_DETECTED/);
  assert.match(source, /consentedAt/);
  assert.match(source, /lastScanAt/);
  assert.match(source, /importedAt/);
  assert.doesNotMatch(source, /Gmail body/);
});

test("Financial Activity explicitly reports incomplete historical coverage instead of pretending empty means complete", () => {
  const activityBlock = source.slice(source.indexOf("function buildFinancialActivityLedger"));
  assert.match(activityBlock, /isCompleteHistory:\s*false/);
});
