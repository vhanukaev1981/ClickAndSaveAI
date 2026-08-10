"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");

const entry = require("../src/entry");
const status = require("../src/gmailSyncStatusFunctions");

test("Gmail sync status requires one-time upgrade below active parser revision", () => {
  const result = status._buildGmailSyncStatus({
    scopes: ["https://www.googleapis.com/auth/gmail.readonly"],
    encryptedRefreshToken: "encrypted",
    parserVersion: 5,
    initialBackfillCompleted: true,
  });

  assert.equal(result.connected, true);
  assert.equal(result.activeParserVersion, 6);
  assert.equal(result.storedParserVersion, 5);
  assert.equal(result.upgradeRequired, true);
  assert.equal(result.lookback, "6m");
});

test("legacy parser-6 connection without explicit backfill marker requests one migration backfill", () => {
  const result = status._buildGmailSyncStatus({
    scopes: ["https://www.googleapis.com/auth/gmail.readonly"],
    encryptedRefreshToken: "encrypted",
    parserVersion: 6,
  });

  assert.equal(result.initialBackfillCompleted, false);
  assert.equal(result.syncMode, "INITIAL_BACKFILL");
  assert.equal(result.upgradeRequired, true);
});

test("Gmail sync status is current after revision 6 backfill is explicitly complete", () => {
  const result = status._buildGmailSyncStatus({
    scopes: ["https://www.googleapis.com/auth/gmail.readonly"],
    encryptedRefreshToken: "encrypted",
    parserVersion: 6,
    initialBackfillCompleted: true,
  });

  assert.equal(result.upgradeRequired, false);
  assert.equal(result.syncMode, "INCREMENTAL");
  assert.equal(entry.getGmailSyncStatus, status.getGmailSyncStatus);
});

test("disconnected account never requests parser backfill", () => {
  const result = status._buildGmailSyncStatus({ parserVersion: 0 });
  assert.equal(result.connected, false);
  assert.equal(result.upgradeRequired, false);
  assert.equal(result.syncMode, "DISCONNECTED");
});
