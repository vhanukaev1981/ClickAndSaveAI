"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { getApps, initializeApp } = require("firebase-admin/app");

if (getApps().length === 0) initializeApp({ projectId: "clickandsaveai-test" });

const {
  _connectionEvents: connectionEvents,
  _billEvent: billEvent,
  _commerceEvent: commerceEvent,
  _sortNewest: sortNewest,
} = require("../src/financialActivityFunctions");

test("Activity connection projection requires authoritative Gmail read-only state", () => {
  const disconnected = connectionEvents({
    consentedAt: "2026-08-14T08:00:00Z",
    scopes: [],
  });
  assert.deepEqual(disconnected, []);

  const connected = connectionEvents({
    encryptedRefreshToken: "encrypted-not-a-credential",
    scopes: ["https://www.googleapis.com/auth/gmail.readonly"],
    consentedAt: "2026-08-14T08:00:00Z",
    lastScanAt: "2026-08-14T08:05:00Z",
  });
  assert.equal(connected.length, 2);
  assert.equal(connected[0].type, "GMAIL_CONNECTED");
  assert.equal(connected[0].destination, "GMAIL_READONLY");
  assert.equal(connected[1].type, "SCAN_COMPLETED");
});

test("Activity bill projection preserves observed amount and unverified Gmail truth", () => {
  const event = billEvent({
    id: "safe-doc-id",
    data: {
      providerName: "Provider",
      category: "internet",
      monthlyCost: 129,
      receivedDate: "2026-08-14T07:00:00Z",
      verificationStatus: "UNVERIFIED_GMAIL_IMPORT",
    },
  });
  assert.ok(event);
  assert.equal(event.type, "BILL_DETECTED");
  assert.equal(event.observedAmount, 129);
  assert.equal(event.verificationStatus, "UNVERIFIED_GMAIL_IMPORT");
});

test("Activity commerce projection never upgrades delivery or saving truth", () => {
  const event = commerceEvent({
    id: "commerce-1",
    data: {
      eventType: "LEAD_CREATED",
      createdAt: "2026-08-14T09:00:00Z",
      leadStatus: "NEW",
      providerName: "Provider A",
      category: "internet",
      potentialMonthlySaving: 40,
    },
  });
  assert.ok(event);
  assert.equal(event.type, "LEAD_CREATED");
  assert.equal(event.status, "NEW");
  assert.equal(event.destination, "COMMERCE_LEDGER");
  assert.equal(event.observedAmount, 40);
  assert.equal(event.verificationStatus, "SERVER_RECORDED");
});

test("Activity projection is newest-first and bounded to authoritative records", () => {
  const sorted = sortNewest([
    { id: "old", timestamp: "2026-08-14T07:00:00.000Z" },
    { id: "new", timestamp: "2026-08-14T09:00:00.000Z" },
  ]);
  assert.deepEqual(sorted.map((item) => item.id), ["new", "old"]);
});
