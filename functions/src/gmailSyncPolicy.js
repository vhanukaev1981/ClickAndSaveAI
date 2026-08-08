"use strict";

const INITIAL_LOOKBACK = "6m";
const RECONCILIATION_INTERVAL_MS = 4 * 60 * 60 * 1000;

function asMillis(value) {
  if (value == null) return 0;
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (value instanceof Date) return value.getTime();
  if (typeof value.toMillis === "function") return Number(value.toMillis()) || 0;
  const parsed = Date.parse(String(value));
  return Number.isFinite(parsed) ? parsed : 0;
}

function initialBackfillRequired(connection = {}) {
  return connection.initialBackfillCompleted !== true;
}

function reconciliationRequired(connection = {}, now = Date.now()) {
  if (connection.watchEnabled !== true) return false;
  if (connection.historyRecoveryRequired === true) return true;
  const last = asMillis(connection.lastReconciliationAt || connection.lastIncrementalScanAt);
  if (!last) return true;
  return Number(now) - last >= RECONCILIATION_INTERVAL_MS;
}

function nextSyncAction(connection = {}, now = Date.now()) {
  if (initialBackfillRequired(connection)) {
    return { type: "INITIAL_BACKFILL", lookback: INITIAL_LOOKBACK };
  }
  if (reconciliationRequired(connection, now)) {
    return { type: "RECONCILE", lookback: "incremental" };
  }
  return { type: "NONE", lookback: "" };
}

function markInitialBackfillCompleteFields() {
  return {
    initialBackfillCompleted: true,
    initialBackfillLookback: INITIAL_LOOKBACK,
  };
}

module.exports = {
  INITIAL_LOOKBACK,
  RECONCILIATION_INTERVAL_MS,
  initialBackfillRequired,
  reconciliationRequired,
  nextSyncAction,
  markInitialBackfillCompleteFields,
};
