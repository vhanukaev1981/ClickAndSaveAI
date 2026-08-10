"use strict";

const { ACTIVE_GMAIL_PARSER_VERSION } = require("./gmailParserVersion");

function normalizeHistoryId(value) {
  const normalized = String(value || "").trim();
  return /^\d+$/.test(normalized) ? normalized : "";
}

function compareHistoryIds(left, right) {
  const a = normalizeHistoryId(left);
  const b = normalizeHistoryId(right);
  if (!a || !b) return null;
  const aBig = BigInt(a);
  const bBig = BigInt(b);
  return aBig === bBig ? 0 : (aBig > bBig ? 1 : -1);
}

function selectMonotonicCheckpoint(currentValue, candidateValue) {
  const current = normalizeHistoryId(currentValue);
  const candidate = normalizeHistoryId(candidateValue);
  if (!current) return candidate;
  if (!candidate) return current;
  return compareHistoryIds(current, candidate) >= 0 ? current : candidate;
}

function syncMode(connection, activeParserVersion = ACTIVE_GMAIL_PARSER_VERSION) {
  const data = connection && typeof connection === "object" ? connection : {};
  if (data.initialBackfillCompleted !== true) return "INITIAL_BACKFILL";
  if (data.historyRecoveryRequired === true) return "RECOVERY_REQUIRED";
  const storedParserVersion = Math.max(0, Number(data.parserVersion || 0));
  if (storedParserVersion < activeParserVersion) return "PARSER_UPGRADE_BACKFILL";
  return "INCREMENTAL";
}

module.exports = {
  normalizeHistoryId,
  compareHistoryIds,
  selectMonotonicCheckpoint,
  syncMode,
};
