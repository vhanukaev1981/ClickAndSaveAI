"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { validateDealQuery, validateLeadInput } = require("../src/validation");
const { decryptToken, encryptToken } = require("../src/tokenCrypto");
const { parseGmailMessage } = require("../src/gmailParser");

function validLead(overrides = {}) {
  return {
    consentAccepted: true,
    contactName: "ישראל ישראלי",
    phone: "+972 50-123-4567",
    contactEmail: "user@example.com",
    currentProvider: "ספק קיים",
    requestedProvider: "ספק חדש",
    category: "סלולר",
    invoiceLocalId: "42",
    idempotencyKey: "lead-42-abc",
    consentVersion: "provider-lead-v1",
    ...overrides,
  };
}

test("lead validation requires explicit consent", () => {
  assert.throws(() => validateLeadInput({}), /explicit lead consent/);
});

test("lead validation rejects unknown consent versions", () => {
  assert.throws(
    () => validateLeadInput(validLead({ consentVersion: "provider-lead-v999" })),
    /unsupported lead consent version/
  );
});

test("lead validation rejects unsupported categories", () => {
  assert.throws(
    () => validateLeadInput(validLead({ category: "אחר" })),
    /category is unsupported/
  );
});

test("lead validation accepts ambiguous telecom category", () => {
  const lead = validateLeadInput(validLead({ category: "תקשורת" }));
  assert.equal(lead.category, "תקשורת");
});

test("lead validation normalizes valid contact data", () => {
  const lead = validateLeadInput(validLead({
    contactName: "  ישראל ישראלי ",
    contactEmail: "USER@EXAMPLE.COM",
  }));
  assert.equal(lead.contactName, "ישראל ישראלי");
  assert.equal(lead.contactEmail, "user@example.com");
});

test("deal query rejects blank content", () => {
  assert.throws(() => validateDealQuery({ query: "   " }), /query is required/);
});

test("refresh token encryption round trips", () => {
  const key = Buffer.alloc(32, 7).toString("base64");
  const encrypted = encryptToken("refresh-token", key);
  assert.notEqual(encrypted.ciphertext, "refresh-token");
  assert.equal(decryptToken(encrypted, key), "refresh-token");
});

test("Gmail parser returns only minimal deterministic invoice fields", () => {
  const parsed = parseGmailMessage({
    id: "gmail-message-1",
    snippet: "סכום לתשלום ₪123.45",
    payload: {
      headers: [
        { name: "Subject", value: "חשבונית חשמל" },
        { name: "From", value: "חברת החשמל <billing@example.com>" },
        { name: "Date", value: "Thu, 6 Aug 2026 10:00:00 +0300" },
      ],
    },
  });
  assert.equal(parsed.sourceMessageId, "gmail-message-1");
  assert.equal(parsed.category, "חשמל");
  assert.equal(parsed.monthlyCost, 123.45);
  assert.equal(parsed.verificationStatus, "UNVERIFIED_GMAIL_IMPORT");
  assert.equal(parsed.subject, undefined);
  assert.equal(parsed.sender, undefined);
  assert.equal(parsed.snippet, undefined);
});
