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

  // Parser upgrades never reopen the six-month mailbox window. Once the initial
  // baseline exists, all subsequent processing stays on Gmail History/watch.
  // `activeParserVersion` remains an explicit argument because callers use the
  // same policy across releases and tests, but it must not alter the lifecycle.
  void activeParserVersion;
  return "INCREMENTAL";
}

module.exports = {
  normalizeHistoryId,
  compareHistoryIds,
  selectMonotonicCheckpoint,
  syncMode,
};
