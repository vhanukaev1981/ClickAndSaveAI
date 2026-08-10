"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const policy = require("../src/gmailHistoryPolicy");
const entry = require("../src/entry");
const reconciliation = require("../src/gmailIncrementalReconciliation");
const syncStatus = require("../src/gmailSyncStatusFunctions");

const reliableScanSource = fs.readFileSync(
  path.join(__dirname, "..", "src", "gmailReliableScanFunctions.js"),
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

test("expired Gmail History enters recovery while preserving the last processed checkpoint", () => {
  assert.match(reconciliationSource, /response\.status === 404\) return false/);
  assert.match(reconciliationSource, /historyRecoveryRequired:\s*true/);
  assert.match(reconciliationSource, /historyRecoveryReason:\s*reason/);
  assert.match(reconciliationSource, /pendingHistoryId:\s*selectMonotonicCheckpoint\(data\.pendingHistoryId, targetHistoryId\)/);
  assert.match(reconciliationSource, /if \(state\.checkpoint\) update\.watchHistoryId = state\.checkpoint/);
  assert.doesNotMatch(reconciliationSource, /update\.watchHistoryId = targetHistoryId/);
});

test("public incremental processing is serialized and finalizes checkpoints transactionally", () => {
  assert.equal(entry.gmailPushNotification, reconciliation.gmailPushNotification);
  assert.match(reconciliationSource, /incrementalLeaseOwner/);
  assert.match(reconciliationSource, /db\.runTransaction/);
  assert.match(reconciliationSource, /selectMonotonicCheckpoint/);
  assert.match(reconciliationSource, /data\.historyRecoveryRequired === true/);
});

test("PubSub does not race the first or parser-upgrade backfill and recovery releases mailbox leases", () => {
  assert.match(reconciliationSource, /mode === "INITIAL_BACKFILL" \|\| mode === "PARSER_UPGRADE_BACKFILL"/);
  assert.match(reconciliationSource, /status:\s*"BACKFILL_PENDING"/);
  const recoveryBranch = reconciliationSource
    .split("if (!readable) {")[1]
    ?.split("return { status: \"RECOVERY_REQUIRED\"")[0] || "";
  assert.match(recoveryBranch, /releaseMailboxLease\(lease\.states, lease\.owner\)/);
});

test("public scan persists first-backfill completion and refuses normal six-month rescans", () => {
  assert.match(reliableScanSource, /initialBackfillCompleted/);
  assert.match(reliableScanSource, /initialBackfillCompletedAt/);
  assert.match(reliableScanSource, /initialBackfillHistoryBaseline/);
  assert.match(reliableScanSource, /syncMode\(/);
  assert.match(reliableScanSource, /mode === "INCREMENTAL"/);
  assert.match(reliableScanSource, /mode === "RECOVERY_REQUIRED"/);
  assert.match(reliableScanSource, /stableScanRunner\(\)\(request\)/);
});

test("four-hour safety reconciliation uses Gmail History only and never launches a six-month scan", () => {
  assert.equal(typeof entry.gmailIncrementalReconciliation, "function");
  assert.match(reconciliationSource, /schedule:\s*"0 \*\/4 \* \* \*"/);
  assert.match(reconciliationSource, /users\/me\/profile/);
  assert.match(reconciliationSource, /_processMailboxNotification/);
  assert.doesNotMatch(reconciliationSource, /newer_than:/);
  assert.doesNotMatch(reconciliationSource, /scanGmailInvoices/);
});

test("Gmail sync status exposes backfill and incremental checkpoint health without mailbox content", () => {
  const lastIncrementalScanAt = { seconds: 123, nanoseconds: 0 };
  const result = syncStatus._buildGmailSyncStatus({
    scopes: ["https://www.googleapis.com/auth/gmail.readonly"],
    encryptedRefreshToken: "encrypted",
    parserVersion: 6,
    initialBackfillCompleted: true,
    watchHistoryId: "100",
    pendingHistoryId: "101",
    historyRecoveryRequired: false,
    lastIncrementalScanAt,
  });

  assert.equal(result.initialBackfillCompleted, true);
  assert.equal(result.incrementalCheckpointHistoryId, "100");
  assert.equal(result.pendingHistoryId, "101");
  assert.equal(result.historyRecoveryRequired, false);
  assert.equal(result.lastIncrementalScanAt, lastIncrementalScanAt);
  assert.equal(result.syncMode, "INCREMENTAL");
});
