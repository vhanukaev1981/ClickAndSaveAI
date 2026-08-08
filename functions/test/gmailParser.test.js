"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  collectMessageText,
  parseGmailMessage,
} = require("../src/gmailParser");

function b64url(value) {
  return Buffer.from(value, "utf8")
    .toString("base64")
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
}

function message({ id = "m1", subject = "", from = "", body = "", mimeType = "text/plain", snippet = "" }) {
  return {
    id,
    snippet,
    payload: {
      mimeType: "multipart/alternative",
      headers: [
        { name: "Subject", value: subject },
        { name: "From", value: from },
        { name: "Date", value: "Fri, 8 Aug 2026 08:00:00 +0300" },
      ],
      parts: [
        {
          mimeType,
          body: { data: b64url(body) },
        },
      ],
    },
  };
}

test("extracts text from nested HTML Gmail bodies", () => {
  const payload = message({
    body: "<html><body><p>סה\"כ לתשלום 321.45 ₪</p></body></html>",
    mimeType: "text/html",
  }).payload;

  assert.match(collectMessageText(payload), /321\.45 ₪/);
});

test("parses an electricity invoice from message body", () => {
  const parsed = parseGmailMessage(message({
    subject: "החשבון החודשי שלך",
    from: "חברת החשמל <billing@example.test>",
    body: "חשבונית חשמל. סך הכל לתשלום 487.20 ₪",
  }));

  assert.equal(parsed.providerName, "חברת החשמל");
  assert.equal(parsed.category, "חשמל");
  assert.equal(parsed.monthlyCost, 487.2);
});

test("keeps a recognized Cellcom invoice even when service type is not explicit", () => {
  const parsed = parseGmailMessage(message({
    subject: "החשבונית החודשית שלך",
    from: "Cellcom <billing@example.test>",
    body: "סה\"כ לתשלום 169.90 ₪",
  }));

  assert.equal(parsed.providerName, "סלקום");
  assert.equal(parsed.category, "תקשורת");
  assert.equal(parsed.monthlyCost, 169.9);
});

test("explicit fiber signal overrides telecom fallback category", () => {
  const parsed = parseGmailMessage(message({
    subject: "Cellcom invoice",
    from: "Cellcom <billing@example.test>",
    body: "שירות סיבים Fiber לחודש זה. ILS 129.90",
  }));

  assert.equal(parsed.providerName, "סלקום");
  assert.equal(parsed.category, "אינטרנט");
});

test("does not import generic receipts without a supported category/provider", () => {
  const parsed = parseGmailMessage(message({
    subject: "קבלה",
    from: "store@example.test",
    body: "תודה על הקנייה. סה\"כ 79.90 ₪",
  }));

  assert.equal(parsed, null);
});

test("does not persist raw Gmail content in parsed invoice", () => {
  const parsed = parseGmailMessage(message({
    subject: "חשבונית בזק",
    from: "Bezeq <billing@example.test>",
    body: "אינטרנט ביתי. לתשלום 99.90 ₪. SECRET_BODY_TEXT",
    snippet: "SECRET_SNIPPET_TEXT",
  }));

  const serialized = JSON.stringify(parsed);
  assert.equal(serialized.includes("SECRET_BODY_TEXT"), false);
  assert.equal(serialized.includes("SECRET_SNIPPET_TEXT"), false);
  assert.equal(Object.hasOwn(parsed, "subject"), false);
  assert.equal(Object.hasOwn(parsed, "from"), false);
});
