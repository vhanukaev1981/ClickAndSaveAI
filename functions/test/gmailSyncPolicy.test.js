"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  INITIAL_LOOKBACK,
  RECONCILIATION_INTERVAL_MS,
  initialBackfillRequired,
  reconciliationRequired,
  nextSyncAction,
  markInitialBackfillCompleteFields,
} = require("../src/gmailSyncPolicy");

test("initial backfill is required exactly until completion is recorded", () => {
  assert.equal(initialBackfillRequired({}), true);
  assert.equal(initialBackfillRequired({ initialBackfillCompleted: false }), true);
  assert.equal(initialBackfillRequired({ initialBackfillCompleted: true }), false);
});

test("initial sync action always uses six month lookback", () => {
  assert.deepEqual(nextSyncAction({ initialBackfillCompleted: false }), {
    type: "INITIAL_BACKFILL",
    lookback: "6m",
  });
  assert.equal(INITIAL_LOOKBACK, "6m");
});

test("watch-enabled accounts reconcile after four hours", () => {
  const now = Date.parse("2026-08-08T20:00:00Z");
  const recent = new Date(now - RECONCILIATION_INTERVAL_MS + 1);
  const stale = new Date(now - RECONCILIATION_INTERVAL_MS);
  assert.equal(reconciliationRequired({ watchEnabled: true, lastReconciliationAt: recent }, now), false);
  assert.equal(reconciliationRequired({ watchEnabled: true, lastReconciliationAt: stale }, now), true);
});

test("history recovery forces reconciliation immediately", () => {
  assert.equal(reconciliationRequired({
    watchEnabled: true,
    historyRecoveryRequired: true,
    lastReconciliationAt: new Date(),
  }), true);
});

test("completed and fresh account needs no extra scan", () => {
  const now = Date.now();
  assert.deepEqual(nextSyncAction({
    initialBackfillCompleted: true,
    watchEnabled: true,
    lastReconciliationAt: new Date(now),
  }, now), { type: "NONE", lookback: "" });
});

test("completion marker records six-month backfill permanently", () => {
  assert.deepEqual(markInitialBackfillCompleteFields(), {
    initialBackfillCompleted: true,
    initialBackfillLookback: "6m",
  });
});
