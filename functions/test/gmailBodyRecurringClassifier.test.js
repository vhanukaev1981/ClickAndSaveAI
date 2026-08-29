"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { parseGmailMessage } = require("../src/gmailParser");
const {
  bodyCandidate,
  PDF_ANALYSIS_STATES,
  resolvePdfBodyCandidates,
} = require("../src/gmailRecurringIngestionEngine");
const { selectRecurringBills } = require("../src/gmailRecurringBillPolicy");

function base64Url(value) {
  return Buffer.from(String(value), "utf8")
    .toString("base64")
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
}

function gmailMessage({
  id = "synthetic-message",
  subject = "",
  from = "billing@example.test",
  date = "Wed, 05 Aug 2026 10:00:00 +0300",
  snippet = "",
  body = "",
} = {}) {
  return {
    id,
    snippet,
    payload: {
      mimeType: "text/plain",
      headers: [
        { name: "Subject", value: subject },
        { name: "From", value: from },
        { name: "Date", value: date },
      ],
      body: { data: base64Url(body) },
    },
  };
}

function classify(fixture) {
  return bodyCandidate(parseGmailMessage(gmailMessage(fixture)));
}

test("Hebrew body-only current monthly bill becomes recurring", () => {
  const candidate = classify({
    subject: "החשבון החודשי שלך",
    body: "אינטרנט. תקופת חיוב: יולי 2026. סכום לתשלום: ₪149.90.",
  });
  assert.equal(candidate.documentClass, "RECURRING_BILL");
  assert.notEqual(candidate.recurrenceEvidence, "NONE");
});

test("English body-only current bill becomes recurring", () => {
  const candidate = classify({
    subject: "Your monthly bill",
    body: "Internet service. Billing period: July 2026. Amount due: ₪89.00.",
  });
  assert.equal(candidate.documentClass, "RECURRING_BILL");
  assert.notEqual(candidate.recurrenceEvidence, "NONE");
});

test("first-ever body recurring bill is accepted without history", () => {
  const candidate = classify({
    id: "first-ever-body-bill",
    subject: "Your current bill",
    body: "Internet service. Service period: August 2026. Total due: ₪129.90.",
  });
  const selected = selectRecurringBills([candidate]);
  assert.equal(selected.length, 1);
  assert.equal(selected[0].sourceMessageId, "first-ever-body-bill");
});

test("variable utility body bill is recurring and usage-recurring", () => {
  const candidate = classify({
    subject: "Your current electricity bill",
    body: "Electricity usage period: 01/07/2026-31/07/2026. Amount due: ₪327.40.",
  });
  assert.equal(candidate.documentClass, "RECURRING_BILL");
  assert.equal(candidate.recurrenceType, "USAGE_RECURRING");
});

test("annual current renewal bill may remain recurrenceType UNKNOWN", () => {
  const candidate = classify({
    subject: "Your annual insurance renewal bill",
    body: "Insurance coverage period: 01/09/2026-31/08/2027. Current premium due: ₪1200.",
  });
  assert.equal(candidate.documentClass, "RECURRING_BILL");
  assert.equal(candidate.recurrenceType, "UNKNOWN");
});

test("no-PDF telecom current bill becomes recurring", () => {
  const candidate = classify({
    subject: "Your current internet bill",
    body: "Internet service period: August 2026. Total due: ₪129.90.",
  });
  assert.equal(candidate.documentClass, "RECURRING_BILL");
  assert.ok(["TELECOM_SERVICE", "EXPLICIT_BILLING_PERIOD"].includes(candidate.recurrenceEvidence));
});

test("ambiguous monetary email stays UNKNOWN", () => {
  const candidate = classify({
    subject: "Account update",
    body: "Internet account information. ₪99.",
  });
  assert.equal(candidate.documentClass, "UNKNOWN");
});

test("promotional monthly pricing stays UNKNOWN", () => {
  const candidate = classify({
    subject: "Special price",
    body: "Internet special offer: fiber from ₪99/month.",
  });
  assert.equal(candidate.documentClass, "UNKNOWN");
});

test("upgrade offer stays UNKNOWN", () => {
  const candidate = classify({
    subject: "Upgrade now",
    body: "Upgrade now to internet fiber for ₪89/month.",
  });
  assert.equal(candidate.documentClass, "UNKNOWN");
});

test("free trial pricing stays UNKNOWN", () => {
  const candidate = classify({
    subject: "Free trial",
    body: "Internet trial: first month free, then ₪49/month.",
  });
  assert.equal(candidate.documentClass, "UNKNOWN");
});

test("quote or proposal stays UNKNOWN", () => {
  const candidate = classify({
    subject: "Proposal",
    body: "Your proposed internet package is ₪129/month.",
  });
  assert.equal(candidate.documentClass, "UNKNOWN");
});

test("one-off invoice never becomes recurring", () => {
  const candidate = classify({
    subject: "One-time invoice",
    body: "One-time internet equipment invoice. Total due: ₪299.",
  });
  assert.ok(["ONE_OFF", "UNKNOWN"].includes(candidate.documentClass));
  assert.notEqual(candidate.documentClass, "RECURRING_BILL");
});

test("body receipt is RECEIPT_ONLY and not directly recurring", () => {
  const candidate = classify({
    subject: "Payment receipt",
    body: "Internet payment receipt. Paid ₪129.",
  });
  assert.equal(candidate.documentClass, "RECEIPT_ONLY");
  assert.equal(candidate.recurrenceEvidence, "NONE");
});

test("body refund is REFUND", () => {
  const candidate = classify({
    subject: "Refund processed",
    body: "Internet service refund processed: ₪129.",
  });
  assert.equal(candidate.documentClass, "REFUND");
});

test("body contract is CONTRACT", () => {
  const candidate = classify({
    subject: "Service agreement",
    body: "Internet service contract agreement. Total amount: ₪129.",
  });
  assert.equal(candidate.documentClass, "CONTRACT");
});

test("promotional footer does not erase an independently complete current-bill block", () => {
  const candidate = classify({
    subject: "Your monthly bill",
    body: [
      "Internet service. Billing period: July 2026. Amount due: ₪89.00.",
      "Thank you for being a customer.",
      "Terms and account notices apply.",
      "Learn about our upgrade offers and special promotions in the customer portal.",
    ].join(" ".repeat(220)),
  });
  assert.equal(candidate.documentClass, "RECURRING_BILL");
});

test("body parsing strips raw Gmail privacy fields from the canonical candidate", () => {
  const candidate = classify({
    subject: "Your monthly bill PRIVATE-SUBJECT",
    from: "private-sender@example.test",
    snippet: "PRIVATE-SNIPPET",
    body: "Internet billing period: July 2026. Amount due: ₪89.00. PRIVATE-BODY",
  });
  for (const forbidden of ["subject", "body", "bodyText", "snippet", "sender", "from", "rawText"]) {
    assert.equal(Object.prototype.hasOwnProperty.call(candidate, forbidden), false);
  }
  const serialized = JSON.stringify(candidate);
  for (const forbiddenValue of ["PRIVATE-SUBJECT", "PRIVATE-SNIPPET", "PRIVATE-BODY", "private-sender@example.test"]) {
    assert.equal(serialized.includes(forbiddenValue), false);
  }
});

test("PDF semantic success wins over recurring-looking body", () => {
  assert.equal(typeof resolvePdfBodyCandidates, "function");
  assert.equal(PDF_ANALYSIS_STATES.PDF_CLASSIFICATION_RESULT, "PDF_CLASSIFICATION_RESULT");
  const recurringBody = { sourceMessageId: "body", documentClass: "RECURRING_BILL" };
  for (const documentClass of ["CONTRACT", "REFUND", "ONE_OFF", "RECEIPT_ONLY", "UNKNOWN", "RECURRING_BILL"]) {
    const pdfCandidate = { sourceMessageId: `pdf-${documentClass}`, documentClass };
    const resolved = resolvePdfBodyCandidates({
      pdfAttachmentCount: 1,
      pdfOutcomes: [{
        state: PDF_ANALYSIS_STATES.PDF_CLASSIFICATION_RESULT,
        documentClass,
        candidate: pdfCandidate,
      }],
      fallbackBody: recurringBody,
    });
    assert.equal(resolved.pdfState, "PDF_CLASSIFICATION_RESULT");
    assert.deepEqual(resolved.candidates, [pdfCandidate]);
  }
});

test("successful non-normalizable PDF semantic result still suppresses body fallback", () => {
  const recurringBody = { sourceMessageId: "body", documentClass: "RECURRING_BILL" };
  const resolved = resolvePdfBodyCandidates({
    pdfAttachmentCount: 1,
    pdfOutcomes: [{
      state: PDF_ANALYSIS_STATES.PDF_CLASSIFICATION_RESULT,
      documentClass: "CONTRACT",
      candidate: null,
    }],
    fallbackBody: recurringBody,
  });
  assert.equal(resolved.pdfState, "PDF_CLASSIFICATION_RESULT");
  assert.deepEqual(resolved.candidates, []);
});

test("NO_PDF permits conservative body fallback", () => {
  const recurringBody = { sourceMessageId: "body", documentClass: "RECURRING_BILL" };
  const resolved = resolvePdfBodyCandidates({
    pdfAttachmentCount: 0,
    pdfOutcomes: [],
    fallbackBody: recurringBody,
  });
  assert.equal(resolved.pdfState, "NO_PDF");
  assert.deepEqual(resolved.candidates, [recurringBody]);
});

test("genuine PDF analysis failure permits body fallback but supplies no recurrence evidence", () => {
  const recurringBody = { sourceMessageId: "body", documentClass: "RECURRING_BILL", recurrenceEvidence: "TELECOM_SERVICE" };
  const resolved = resolvePdfBodyCandidates({
    pdfAttachmentCount: 1,
    pdfOutcomes: [{ state: "PDF_ANALYSIS_FAILURE" }],
    fallbackBody: recurringBody,
  });
  assert.equal(resolved.pdfState, "PDF_ANALYSIS_FAILURE");
  assert.deepEqual(resolved.candidates, [recurringBody]);
});

test("genuine PDF analysis failure with weak body remains UNKNOWN", () => {
  const weakBody = { sourceMessageId: "body", documentClass: "UNKNOWN", recurrenceEvidence: "NONE" };
  const resolved = resolvePdfBodyCandidates({
    pdfAttachmentCount: 1,
    pdfOutcomes: [{ state: "PDF_ANALYSIS_FAILURE" }],
    fallbackBody: weakBody,
  });
  assert.deepEqual(resolved.candidates, [weakBody]);
  assert.equal(resolved.candidates[0].documentClass, "UNKNOWN");
});
