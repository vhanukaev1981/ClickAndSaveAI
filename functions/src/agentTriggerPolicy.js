"use strict";

const BACKFILL_BATCH_MODE = "BACKFILL_BATCH";
const REALTIME_MODE = "REALTIME";

function importTriggerMode(documentData) {
  return String(documentData?.agentTriggerMode || "").trim().toUpperCase();
}

function shouldRunAgentForImport(documentData) {
  return importTriggerMode(documentData) !== BACKFILL_BATCH_MODE;
}

function isPrivacyDeletion(documentData) {
  return documentData?.privacyDeletionRequested === true;
}

function shouldRunAgentForImportEvent(beforeData, afterData) {
  if (isPrivacyDeletion(beforeData) || isPrivacyDeletion(afterData)) return false;
  return shouldRunAgentForImport(afterData);
}

module.exports = {
  BACKFILL_BATCH_MODE,
  REALTIME_MODE,
  importTriggerMode,
  shouldRunAgentForImport,
  shouldRunAgentForImportEvent,
};
