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

test("sync lifecycle separates initial, recovery, upgrade and incremental modes", () => {
  assert.equal(syncMode({}, 6), "INITIAL_BACKFILL");
  assert.equal(syncMode({ initialBackfillCompleted: true, historyRecoveryRequired: true, parserVersion: 6 }, 6), "RECOVERY_REQUIRED");
  assert.equal(syncMode({ initialBackfillCompleted: true, historyRecoveryRequired: false, parserVersion: 5 }, 6), "PARSER_UPGRADE_BACKFILL");
  assert.equal(syncMode({ initialBackfillCompleted: true, historyRecoveryRequired: false, parserVersion: 6 }, 6), "INCREMENTAL");
});
