"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  compareHistoryIds,
  normalizeHistoryId,
  selectMonotonicCheckpoint,
  syncMode,
} = require("../src/gmailHistoryPolicy");

test("History ids are normalized and monotonic", () => {
  assert.equal(normalizeHistoryId(" 123 "), "123");
  assert.equal(compareHistoryIds("10", "9"), 1);
  assert.equal(compareHistoryIds("9", "10"), -1);
  assert.equal(selectMonotonicCheckpoint("10", "9"), "10");
  assert.equal(selectMonotonicCheckpoint("10", "11"), "11");
});

test("sync lifecycle allows one initial backfill, recovery, then incremental only", () => {
  assert.equal(syncMode({}, 7), "INITIAL_BACKFILL");
  assert.equal(syncMode({ initialBackfillCompleted: true, historyRecoveryRequired: true, parserVersion: 7 }, 7), "RECOVERY_REQUIRED");
  assert.equal(
    syncMode({ initialBackfillCompleted: true, historyRecoveryRequired: false, parserVersion: 6 }, 7),
    "INCREMENTAL",
    "parser upgrades must not reopen the six-month mailbox window"
  );
  assert.equal(syncMode({ initialBackfillCompleted: true, historyRecoveryRequired: false, parserVersion: 7 }, 7), "INCREMENTAL");
});
