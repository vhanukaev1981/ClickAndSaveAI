"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  BACKFILL_BATCH_MODE,
  REALTIME_MODE,
  importTriggerMode,
  shouldRunAgentForImport,
  shouldRunAgentForImportEvent,
} = require("../src/agentTriggerPolicy");

test("backfill batch writes are coalesced instead of waking the agent per message", () => {
  assert.equal(importTriggerMode({ agentTriggerMode: BACKFILL_BATCH_MODE }), "BACKFILL_BATCH");
  assert.equal(shouldRunAgentForImport({ agentTriggerMode: BACKFILL_BATCH_MODE }), false);
});

test("real-time Gmail writes still wake the financial agent immediately", () => {
  assert.equal(importTriggerMode({ agentTriggerMode: REALTIME_MODE }), "REALTIME");
  assert.equal(shouldRunAgentForImport({ agentTriggerMode: REALTIME_MODE }), true);
});

test("legacy and ordinary delete-trigger data still run the agent for safe reconciliation", () => {
  assert.equal(shouldRunAgentForImport({}), true);
  assert.equal(shouldRunAgentForImport(null), true);
  assert.equal(shouldRunAgentForImportEvent({}, null), true);
});

test("privacy deletion update and delete events never recreate derived financial state", () => {
  const privacyMarked = { privacyDeletionRequested: true };
  assert.equal(shouldRunAgentForImportEvent({}, privacyMarked), false);
  assert.equal(shouldRunAgentForImportEvent(privacyMarked, null), false);
});
