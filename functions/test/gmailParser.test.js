"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  collectMessageText,
  collectPdfAttachments,
  normalizePdfInvoiceCandidate,
  parseAmount,
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

test("collects PDF attachment metadata without retaining document content", () => {
  const payload = {
    mimeType: "multipart/mixed",
    parts: [
      { mimeType: "text/plain", body: { data: b64url("hello") } },
      {
        mimeType: "application/pdf",
        filename: "invoice-august.pdf",
        body: { attachmentId: "att-1", size: 12345 },
      },
    ],
  };

  assert.deepEqual(collectPdfAttachments(payload), [{
    attachmentId: "att-1",
    inlineData: "",
    size: 12345,
    filename: "invoice-august.pdf",
  }]);
});

test("collects every PDF attachment instead of stopping at three", () => {
  const payload = {
    mimeType: "multipart/mixed",
    parts: Array.from({ length: 5 }, (_, index) => ({
      mimeType: "application/pdf",
      filename: `invoice-${index}.pdf`,
      body: { attachmentId: `att-${index}`, size: 1000 + index },
    })),
  };

  const attachments = collectPdfAttachments(payload);
  assert.equal(attachments.length, 5);
  assert.deepEqual(attachments.map((item) => item.attachmentId), [
    "att-0", "att-1", "att-2", "att-3", "att-4",
  ]);
});

test("billing label amount is preferred over an earlier promotional price", () => {
  const amount = parseAmount("מבצע חדש רק 29.90 ₪. סה\"כ לתשלום: 169.90 ₪");
  assert.equal(amount, 169.9);
});

test("parses labeled amount even when currency symbol is omitted", () => {
  const amount = parseAmount("סכום לתשלום: 245.70");
  assert.equal(amount, 245.7);
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

test("generic English partner in body is not treated as Partner telecom", () => {
  const parsed = parseGmailMessage(message({
    subject: "Receipt",
    from: "shop@example.test",
    body: "Thank you for being our partner. Total due: 88.00 ₪",
  }));

  assert.equal(parsed, null);
});

test("Partner sender is recognized as telecom even without explicit service type", () => {
  const parsed = parseGmailMessage(message({
    subject: "Your monthly bill",
    from: "Partner <billing@example.test>",
    body: "Amount due 119.90",
  }));

  assert.equal(parsed.providerName, "פרטנר");
  assert.equal(parsed.category, "תקשורת");
  assert.equal(parsed.monthlyCost, 119.9);
});

test("does not import generic receipts from email body without a supported category/provider", () => {
  const parsed = parseGmailMessage(message({
    subject: "קבלה",
    from: "store@example.test",
    body: "תודה על הקנייה. סה\"כ 79.90 ₪",
  }));

  assert.equal(parsed, null);
});

test("normalizes a verified PDF invoice candidate into the existing invoice shape", () => {
  const parsed = normalizePdfInvoiceCandidate({
    isInvoice: true,
    providerName: "בזק",
    category: "internet",
    monthlyCost: 149.9,
    receivedDate: "2026-08-01",
  }, message({
    id: "pdf-message-1",
    subject: "החשבונית שלך",
    from: "Bezeq <billing@example.test>",
  }));

  assert.deepEqual(parsed, {
    sourceMessageId: "pdf-message-1",
    providerName: "בזק",
    category: "אינטרנט",
    monthlyCost: 149.9,
    receivedDate: "2026-08-01",
    verificationStatus: "UNVERIFIED_GMAIL_IMPORT",
  });
});

test("accepts a real PDF invoice outside the predefined service categories", () => {
  const parsed = normalizePdfInvoiceCandidate({
    isInvoice: true,
    providerName: "Supabase Pte. Ltd.",
    category: "software subscription",
    monthlyCost: 25,
    receivedDate: "2026-08-02",
  }, message({ id: "pdf-message-generic" }), "pdf-message-generic:pdf:abc123");

  assert.deepEqual(parsed, {
    sourceMessageId: "pdf-message-generic:pdf:abc123",
    providerName: "Supabase Pte. Ltd.",
    category: "אחר",
    monthlyCost: 25,
    receivedDate: "2026-08-02",
    verificationStatus: "UNVERIFIED_GMAIL_IMPORT",
  });
});

test("keeps each PDF invoice independently addressable within one Gmail message", () => {
  const gmailMessage = message({ id: "message-with-two-pdfs" });
  const first = normalizePdfInvoiceCandidate({
    isInvoice: true,
    providerName: "Vendor A",
    category: "other",
    monthlyCost: 10,
  }, gmailMessage, "message-with-two-pdfs:pdf:first");
  const second = normalizePdfInvoiceCandidate({
    isInvoice: true,
    providerName: "Vendor B",
    category: "other",
    monthlyCost: 20,
  }, gmailMessage, "message-with-two-pdfs:pdf:second");

  assert.notEqual(first.sourceMessageId, second.sourceMessageId);
  assert.equal(first.monthlyCost, 10);
  assert.equal(second.monthlyCost, 20);
});

test("rejects uncertain PDF document extraction results", () => {
  const parsed = normalizePdfInvoiceCandidate({
    isInvoice: false,
    providerName: "בזק",
    category: "internet",
    monthlyCost: 149.9,
  }, message({ id: "pdf-message-2" }));

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
