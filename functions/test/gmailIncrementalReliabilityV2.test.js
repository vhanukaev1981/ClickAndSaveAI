"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const policy = require("../src/gmailHistoryPolicy");

const watchSource = fs.readFileSync(
  path.join(__dirname, "..", "src", "gmailWatchFunctions.js"),
  "utf8"
);
const scanSource = fs.readFileSync(
  path.join(__dirname, "..", "src", "gmailScanV5Functions.js"),
  "utf8"
);
const reconciliationSource = fs.readFileSync(
  path.join(__dirname, "..", "src", "gmailIncrementalReconciliation.js"),
  "utf8"
);

test("Gmail sync mode permits the six-month scan only for initial or parser-upgrade backfill", () => {
  assert.equal(policy.syncMode({}, 6), "INITIAL_BACKFILL");
  assert.equal(policy.syncMode({ initialBackfillCompleted: true, parserVersion: 5 }, 6), "PARSER_UPGRADE_BACKFILL");
  assert.equal(policy.syncMode({ initialBackfillCompleted: true, parserVersion: 6 }, 6), "INCREMENTAL");
  assert.equal(policy.syncMode({
    initialBackfillCompleted: true,
    parserVersion: 6,
    historyRecoveryRequired: true,
  }, 6), "RECOVERY_REQUIRED");
});

test("history checkpoint selection is monotonic and never regresses", () => {
  assert.equal(policy.selectMonotonicCheckpoint("100", "105"), "105");
  assert.equal(policy.selectMonotonicCheckpoint("105", "100"), "105");
  assert.equal(policy.selectMonotonicCheckpoint("105", "105"), "105");
  assert.equal(policy.selectMonotonicCheckpoint("", "105"), "105");
});

test("expired Gmail History enters recovery without advancing watchHistoryId", () => {
  const expiredBlock = watchSource
    .split("if (history.expired) {")[1]
    ?.split("return")[0] || "";

  assert.match(expiredBlock, /pendingHistoryId:\s*notificationHistoryId/);
  assert.match(expiredBlock, /historyRecoveryRequired:\s*true/);
  assert.match(expiredBlock, /historyRecoveryReason:\s*"HISTORY_ID_EXPIRED"/);
  assert.doesNotMatch(expiredBlock, /watchHistoryId\s*:/);
});

test("successful incremental processing advances checkpoint through a Firestore transaction", () => {
  assert.match(watchSource, /db\.runTransaction/);
  assert.match(watchSource, /selectMonotonicCheckpoint/);
  assert.match(watchSource, /historyRecoveryRequired === true/);
});

test("initial backfill completion and history baseline are persisted by the scan itself", () => {
  assert.match(scanSource, /initialBackfillCompleted/);
  assert.match(scanSource, /initialBackfillCompletedAt/);
  assert.match(scanSource, /initialBackfillHistoryBaseline/);
  assert.match(scanSource, /syncMode\(/);
  assert.match(scanSource, /INCREMENTAL/);
  assert.match(scanSource, /RECOVERY_REQUIRED/);
});

test("four-hour safety reconciliation uses Gmail History only and never launches a six-month scan", () => {
  assert.match(reconciliationSource, /schedule:\s*"0 \*\/4 \* \* \*"/);
  assert.match(reconciliationSource, /users\/me\/profile/);
  assert.match(reconciliationSource, /_processMailboxNotification/);
  assert.doesNotMatch(reconciliationSource, /newer_than:/);
  assert.doesNotMatch(reconciliationSource, /scanGmailInvoices/);
});
