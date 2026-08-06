"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { validateDealQuery, validateLeadInput } = require("../src/validation");
const { decryptToken, encryptToken } = require("../src/tokenCrypto");
const { parseGmailMessage } = require("../src/gmailParser");

test("lead validation requires explicit consent", () => {
  assert.throws(() => validateLeadInput({}), /explicit lead consent/);
});

test("lead validation normalizes valid contact data", () => {
  const lead = validateLeadInput({
    consentAccepted: true,
    contactName: "  ישראל ישראלי ",
    phone: "+972 50-123-4567",
    contactEmail: "USER@EXAMPLE.COM",
    currentProvider: "ספק קיים",
    requestedProvider: "ספק חדש",
    category: "סלולר",
    invoiceLocalId: "42",
    idempotencyKey: "lead-42-abc",
    consentVersion: "provider-lead-v1",
  });
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

test("Gmail parser returns only deterministic unverified invoices", () => {
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
});
