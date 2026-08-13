"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

function src(file) {
  return fs.readFileSync(path.join(__dirname, "..", "src", file), "utf8");
}
function android(file) {
  return fs.readFileSync(path.join(__dirname, "..", "..", "app", "src", "main", file), "utf8");
}

test("Gmail stays read-only and uses checkpoint-preserving guarded recovery", () => {
  const watch = src("gmailWatchFunctions.js");
  const recon = src("gmailIncrementalReconciliation.js");
  const guard = src("gmailReliabilityGuard.js");
  assert.match(watch, /gmail\.readonly/);
  assert.doesNotMatch(watch, /gmail\.modify|gmail\.send|gmail\.compose/);
  assert.match(recon, /incrementalLeaseOwner/);
  assert.match(recon, /HISTORY_ID_EXPIRED/);
  assert.match(recon, /state\.checkpoint/);
  assert.match(guard, /AMBIGUOUS_MAILBOX_OWNER/);
  assert.match(guard, /RECONNECT_REQUIRED/);
});

test("incremental scan returns authoritative server snapshot and recovery is bounded", () => {
  const reliable = src("gmailReliableScanFunctions.js");
  assert.match(reliable, /authoritativeInvoiceSnapshot/);
  assert.match(reliable, /mode === "INCREMENTAL"/);
  assert.match(reliable, /RECOVERY_REQUIRED/);
  assert.match(reliable, /recoveryBaselineHistoryId/);
  assert.match(reliable, /stableScan\.scanGmailInvoices/);
});

test("authoritative notifications have exact stable identities and no financial content", () => {
  const invoicePush = src("gmailInvoiceNotificationFunctions.js");
  const opportunityPush = src("opportunityNotificationFunctions.js");
  assert.match(invoicePush, /bill-detected:\$\{sourceMessageId\}/);
  assert.match(invoicePush, /sourceMessageId/);
  assert.match(invoicePush, /authenticatedAccountExists/);
  assert.match(opportunityPush, /opportunityId/);
  assert.match(opportunityPush, /offerId/);
  assert.match(opportunityPush, /authenticatedAccountExists/);
  assert.doesNotMatch(opportunityPush, /toFixed\(/);
});

test("Android uses exact isolated notification targets with explicit stale handling", () => {
  const target = android("java/com/example/PushTargetActivity.kt");
  const entry = android("java/com/example/PushEntryActivity.kt");
  assert.match(target, /sourceMessageId/);
  assert.match(target, /matchedOffer\?\.offerId == offerId/);
  assert.match(target, /לא נפתח חיוב אחר/);
  assert.match(target, /לא נפתחה הזדמנות אחרת/);
  assert.match(entry, /PushTargetActivity/);
  assert.doesNotMatch(entry, /ProvidersScreen|InvoicesScreen/);
});

test("sign-out revokes the backend registration and local FCM token", () => {
  const auth = android("java/com/example/data/repository/AuthRepository.kt");
  const lifecycle = android("java/com/example/PushTokenLifecycle.kt");
  assert.match(auth, /revokeCurrentDeviceBeforeSignOut/);
  assert.match(lifecycle, /unregisterPushToken/);
  assert.match(lifecycle, /deleteToken\(\)/);
});
