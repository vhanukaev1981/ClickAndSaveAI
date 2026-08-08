"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  normalizeServiceType,
  extractServiceType,
} = require("../src/serviceProfile");

test("internet speeds normalize to one canonical Mbps profile", () => {
  assert.equal(normalizeServiceType("אינטרנט", "Fiber 1Gbps"), "INTERNET_1000_MBPS");
  assert.equal(normalizeServiceType("אינטרנט", "מהירות 1000 מגה"), "INTERNET_1000_MBPS");
  assert.equal(normalizeServiceType("internet", "500 Mbps broadband"), "INTERNET_500_MBPS");
});

test("internet profile is not invented from fiber text without an explicit speed", () => {
  assert.equal(normalizeServiceType("אינטרנט", "fiber internet service"), null);
});

test("mobile family profile requires both explicit line count and data allowance", () => {
  assert.equal(
    normalizeServiceType("סלולר", "4 קווים עם 500GB גלישה"),
    "MOBILE_4_LINES_500_GB"
  );
  assert.equal(normalizeServiceType("סלולר", "500GB גלישה"), null);
});

test("insurance type is extracted only from explicit insurance wording", () => {
  assert.equal(normalizeServiceType("ביטוח", "ביטוח רכב מקיף"), "INSURANCE_CAR");
  assert.equal(normalizeServiceType("ביטוח", "ביטוח דירה ומבנה"), "INSURANCE_HOME");
  assert.equal(normalizeServiceType("ביטוח", "פוליסה חודשית"), null);
});

test("ANY remains a deliberate universal provider-offer profile", () => {
  assert.equal(normalizeServiceType("חשמל", "ANY"), "ANY");
});

test("service type can be extracted from a larger billing body without retaining the body", () => {
  assert.equal(
    extractServiceType("אינטרנט", "החבילה שלך: אינטרנט סיבים במהירות 1 Gbps. סך לתשלום 129 ₪"),
    "INTERNET_1000_MBPS"
  );
});
