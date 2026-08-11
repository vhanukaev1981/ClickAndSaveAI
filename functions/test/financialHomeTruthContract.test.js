"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const source = fs.readFileSync(
  path.resolve(__dirname, "..", "src", "financialAgentFunctions.js"),
  "utf8"
);

test("Financial Home marks an existing authoritative context READY", () => {
  const getHomeBlock = source.slice(source.indexOf("exports.getFinancialHome"));
  assert.match(getHomeBlock, /contextStatus:\s*"READY"/);
  assert.match(getHomeBlock, /observedRecurringMonthlySpend:\s*Number\(context\.observedRecurringMonthlySpend \|\| 0\)/);
  assert.match(getHomeBlock, /recurringServiceCount:\s*Number\(context\.recurringServiceCount \|\| 0\)/);
});

test("Financial Home never converts a still-missing context document into a zero-filled READY response", () => {
  const getHomeBlock = source.slice(source.indexOf("exports.getFinancialHome"));
  assert.doesNotMatch(getHomeBlock, /contextSnapshot\.data\(\) \|\| \{\}/);
  assert.match(getHomeBlock, /if \(!contextSnapshot\.exists\)[\s\S]*new HttpsError\([\s\S]*"unavailable"/);
});
