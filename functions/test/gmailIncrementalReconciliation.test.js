"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const entry = require("../src/entry");
const reconciliation = require("../src/gmailIncrementalReconciliation");

const source = fs.readFileSync(
  path.join(__dirname, "..", "src", "gmailIncrementalReconciliation.js"),
  "utf8"
);

test("four-hour Gmail reconciliation is exported without replacing the public Gmail push handler", () => {
  assert.equal(typeof entry.gmailIncrementalReconciliation, "function");
  assert.equal(typeof entry.onGmailConnectionCheckpointChanged, "function");
  assert.equal(typeof entry.gmailPushNotification, "function");
  assert.equal(typeof entry.gmailPushNotification.run, "function");
  assert.match(source, /schedule:\s*"0 \*\/4 \* \* \*"/);
  assert.match(source, /timeZone:\s*"Asia\/Jerusalem"/);
});

test("history ids are normalized and compared monotonically as integers", () => {
  assert.equal(reconciliation._normalizeHistoryId(" 00123 "), "00123");
  assert.equal(reconciliation._normalizeHistoryId("abc"), "");
  assert.equal(reconciliation._compareHistoryIds("101", "100"), 1);
  assert.equal(reconciliation._compareHistoryIds("100", "100"), 0);
  assert.equal(reconciliation._compareHistoryIds("99", "100"), -1);
  assert.equal(reconciliation._compareHistoryIds("bad", "100"), null);
});

test("observed six-month scan state becomes an explicit initial-backfill completion marker", () => {
  const lastScanAt = { seconds: 123, nanoseconds: 0 };
  const update = reconciliation._backfillCompletionUpdate({
    initialBackfillLookback: "6m",
    lastScanAt,
  });
  assert.equal(update.initialBackfillCompleted, true);
  assert.equal(update.initialBackfillCompletedAt, lastScanAt);

  assert.equal(reconciliation._backfillCompletionUpdate({
    initialBackfillCompleted: true,
    initialBackfillLookback: "6m",
    lastScanAt,
  }), null);
  assert.equal(reconciliation._backfillCompletionUpdate({
    initialBackfillLookback: "6m",
  }), null);
});

test("scheduled reconciliation reuses the existing Gmail Pub/Sub ingestion path", () => {
  const event = reconciliation._syntheticPubSubEvent("USER@example.com", "987654321");
  const decoded = JSON.parse(Buffer.from(event.data.message.data, "base64").toString("utf8"));
  assert.deepEqual(decoded, {
    emailAddress: "USER@example.com",
    historyId: "987654321",
  });

  assert.match(source, /require\("\.\/gmailWatchFunctions"\)/);
  assert.match(source, /gmailPushNotification\.run/);
  assert.doesNotMatch(source, /newer_than:/);
  assert.doesNotMatch(source, /users\/me\/messages\?/);
});

test("reconciliation preflights Gmail History and enters recovery without advancing the checkpoint", () => {
  assert.match(source, /users\/me\/profile/);
  assert.match(source, /users\/me\/history\?/);
  assert.match(source, /response\.status === 404/);
  assert.match(source, /historyRecoveryRequired:\s*true/);
  assert.match(source, /historyRecoveryReason:\s*"HISTORY_ID_EXPIRED"/);

  const expiredBlock = source.split("if (!readable) {")[1]?.split("\n  }")[0] || "";
  assert.doesNotMatch(expiredBlock, /watchHistoryId\s*:/);
});

test("connection update guard protects Gmail History checkpoints from regression and expired-history advance", () => {
  assert.match(source, /ordering !== null && ordering < 0/);
  assert.match(source, /update\.watchHistoryId = beforeHistoryId/);
  assert.match(source, /after\.historyRecoveryRequired === true/);
  assert.match(source, /normalizeHistoryId\(after\.pendingHistoryId\) === afterHistoryId/);
  assert.match(source, /historyCheckpointProtectedAt/);
});
