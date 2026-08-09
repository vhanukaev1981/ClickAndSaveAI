"use strict";

const BACKFILL_BATCH_MODE = "BACKFILL_BATCH";
const REALTIME_MODE = "REALTIME";

function importTriggerMode(documentData) {
  return String(documentData?.agentTriggerMode || "").trim().toUpperCase();
}

function shouldRunAgentForImport(documentData) {
  return importTriggerMode(documentData) !== BACKFILL_BATCH_MODE;
}

module.exports = {
  BACKFILL_BATCH_MODE,
  REALTIME_MODE,
  importTriggerMode,
  shouldRunAgentForImport,
};
