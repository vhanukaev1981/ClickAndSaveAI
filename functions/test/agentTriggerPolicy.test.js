"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  BACKFILL_BATCH_MODE,
  REALTIME_MODE,
  importTriggerMode,
  shouldRunAgentForImport,
} = require("../src/agentTriggerPolicy");

test("backfill batch writes are coalesced instead of waking the agent per message", () => {
  assert.equal(importTriggerMode({ agentTriggerMode: BACKFILL_BATCH_MODE }), "BACKFILL_BATCH");
  assert.equal(shouldRunAgentForImport({ agentTriggerMode: BACKFILL_BATCH_MODE }), false);
});

test("real-time Gmail writes still wake the financial agent immediately", () => {
  assert.equal(importTriggerMode({ agentTriggerMode: REALTIME_MODE }), "REALTIME");
  assert.equal(shouldRunAgentForImport({ agentTriggerMode: REALTIME_MODE }), true);
});

test("legacy and delete-trigger data default to running the agent for safe reconciliation", () => {
  assert.equal(shouldRunAgentForImport({}), true);
  assert.equal(shouldRunAgentForImport(null), true);
});
