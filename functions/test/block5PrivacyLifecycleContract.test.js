"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

function read(relativePath) {
  return fs.readFileSync(path.join(__dirname, "..", relativePath), "utf8");
}

test("Block 5 exposes three separate server-authoritative destructive operations", () => {
  const disconnect = read("src/gmailDisconnectFunctions.js");
  const privacy = read("src/privacyLifecycleFunctions.js");
  const entry = read("src/entry.js");

  assert.match(disconnect, /exports\.disconnectGmail\s*=\s*onCall/);
  assert.match(privacy, /exports\.deleteImportedFinancialData\s*=\s*onCall/);
  assert.match(privacy, /exports\.deleteAccount\s*=\s*onCall/);
  assert.match(disconnect, /enforceAppCheck:\s*true/);
  assert.match(privacy, /enforceAppCheck:\s*true/g);
  assert.match(entry, /\.\.\.gmailDisconnectFunctions/);
  assert.match(entry, /\.\.\.privacyLifecycleFunctions/);
});

test("Gmail disconnect disables ingestion before external cleanup and does not delete imported data", () => {
  const disconnect = read("src/gmailDisconnectFunctions.js");

  assert.match(disconnect, /watchEnabled:\s*false/);
  assert.match(disconnect, /scopes:\s*\[\]/);
  assert.match(disconnect, /pendingHistoryId:\s*FieldValue\.delete\(\)/);
  assert.match(disconnect, /watchHistoryId:\s*FieldValue\.delete\(\)/);
  assert.match(disconnect, /gmail\/v1\/users\/me\/stop/);
  assert.match(disconnect, /oauth2\.googleapis\.com\/revoke/);
  assert.match(disconnect, /gmailConnections/);
  assert.doesNotMatch(disconnect, /deleteImportedFinancialData|deleteAccount/);
});

test("Gmail disconnect retains encrypted cleanup evidence only when provider revocation needs retry", () => {
  const disconnect = read("src/gmailDisconnectFunctions.js");
  const connect = read("src/gmailConnectFunctions.js");
  const reliableScan = read("src/gmailReliableScanFunctions.js");

  assert.match(disconnect, /disconnectState:\s*"RETRY_REQUIRED"/);
  assert.match(disconnect, /if \(externalCleanupConfirmed\)\s*\{\s*await ref\.delete\(\)/s);
  assert.match(disconnect, /DISCONNECTED_PENDING_PROVIDER_CLEANUP/);
  assert.match(connect, /disconnectState\s*===\s*"RETRY_REQUIRED"/);
  assert.match(reliableScan, /DISCONNECT_STATES/);
  assert.match(reliableScan, /Gmail ingestion is disabled while provider disconnect cleanup is pending/);
});

test("imported-data deletion is scoped to imported and derived financial data", () => {
  const privacy = read("src/privacyLifecycleFunctions.js");

  for (const collection of [
    "gmailInvoices",
    "gmailMessageImports",
    "financialContext",
    "financialInsights",
    "opportunities",
  ]) {
    assert.match(privacy, new RegExp(`\\"${collection}\\"`));
  }
  assert.match(privacy, /commerceMatches/);
  assert.match(privacy, /commerceEvents/);
  assert.match(privacy, /where\(\"uid\",\s*\"==\",\s*uid\)/);
  assert.match(privacy, /providerHandoffRecordsPreserved:\s*true/);
  assert.match(privacy, /gmailConnectionPreserved:\s*true/);
});

test("account deletion blocks concurrent recreation, cleans account state, and deletes auth last", () => {
  const privacy = read("src/privacyLifecycleFunctions.js");
  const authorization = read("src/accountAuthorization.js");

  assert.match(privacy, /state:\s*"DELETING"/);
  for (const collection of [
    "providerLeads",
    "providerDispatchQueue",
    "commerceMatches",
    "commerceEvents",
  ]) {
    assert.match(privacy, new RegExp(`\\"${collection}\\"`));
  }
  assert.match(privacy, /recursiveDelete/);
  assert.match(privacy, /deleteUser\(uid\)/);
  assert.match(privacy, /pushTokens/);
  assert.match(authorization, /getAuth\(\)\.getUser\(normalizedUid\)/);
  assert.match(authorization, /BLOCKED_ACCOUNT_LIFECYCLE_STATES/);
  assert.match(authorization, /"DELETING"/);
  assert.match(authorization, /"DELETE_RETRY_REQUIRED"/);
});

test("account deletion pauses before destructive data removal when Gmail provider cleanup is unconfirmed", () => {
  const privacy = read("src/privacyLifecycleFunctions.js");
  const cleanupGate = privacy.indexOf("if (!gmailCleanup.externalCleanupConfirmed)");
  const topLevelDelete = privacy.indexOf("for (const collectionName of ACCOUNT_TOP_LEVEL_UID_COLLECTIONS)");
  const authDelete = privacy.indexOf("const authAlreadyMissing = await deleteAuthUserIdempotently(uid)");

  assert.ok(cleanupGate >= 0);
  assert.ok(topLevelDelete > cleanupGate);
  assert.ok(authDelete > topLevelDelete);
  assert.match(privacy, /state:\s*"DELETE_RETRY_REQUIRED"/);
  assert.match(privacy, /Retry deletion/);
});

test("destructive callables derive identity only from authenticated context and require explicit delete confirmation", () => {
  const disconnect = read("src/gmailDisconnectFunctions.js");
  const privacy = read("src/privacyLifecycleFunctions.js");

  assert.match(disconnect, /const uid = requireAuth\(request\)/);
  assert.match(privacy, /const uid = requireAuth\(request\)/g);
  assert.doesNotMatch(`${disconnect}\n${privacy}`, /request\.data\?\.uid|request\.data\.uid/);
  assert.match(privacy, /DELETE_IMPORTED_FINANCIAL_DATA/);
  assert.match(privacy, /DELETE_ACCOUNT/);
});
