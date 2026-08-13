"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

function readBackend(relativePath) {
  return fs.readFileSync(path.join(__dirname, "..", relativePath), "utf8");
}

function readAndroid(relativePath) {
  return fs.readFileSync(path.join(__dirname, "..", "..", "app", relativePath), "utf8");
}

test("expired Gmail History never advances the processed checkpoint before guarded recovery", () => {
  const source = readBackend("src/gmailWatchFunctions.js");
  const expiredBranch = source.split("if (history.expired) {")[1]?.split("return;")[0] || "";
  assert.match(expiredBranch, /historyRecoveryRequired:\s*true/);
  assert.doesNotMatch(expiredBranch, /watchHistoryId:\s*notificationHistoryId/);
});

test("serialized checkpoint-aware Gmail reconciliation is installed", () => {
  const reconciliationPath = path.join(__dirname, "..", "src", "gmailIncrementalReconciliation.js");
  assert.equal(fs.existsSync(reconciliationPath), true);
  const source = fs.existsSync(reconciliationPath) ? fs.readFileSync(reconciliationPath, "utf8") : "";
  assert.match(source, /incrementalLeaseOwner/);
  assert.match(source, /historyRecoveryRequired/);
  assert.match(source, /gmailIncrementalReconciliation/);
});

test("push token ownership is server-authoritative across authenticated accounts", () => {
  const source = readBackend("src/pushFunctions.js");
  assert.match(source, /pushTokenOwners/);
  assert.match(source, /runTransaction/);
  assert.match(source, /ownerUid/);
});

test("a single-invoice push carries the exact authoritative Gmail source identity", () => {
  const source = readBackend("src/gmailWatchFunctions.js");
  const pushSection = source.split("await sendPushToUser(uid")[1] || "";
  assert.match(pushSection, /sourceMessageId:\s*first\.sourceMessageId/);
});

test("Android notification navigation is entity-aware", () => {
  const policyPath = path.join(__dirname, "..", "..", "app", "src", "main", "java", "com", "example", "PushNavigationPolicy.kt");
  assert.equal(fs.existsSync(policyPath), true);
  const policy = fs.existsSync(policyPath) ? fs.readFileSync(policyPath, "utf8") : "";
  assert.match(policy, /targetId/);
  assert.match(policy, /opportunityId/);
  assert.match(policy, /sourceMessageId/);
});

test("stale exact targets have explicit no-fallback handling", () => {
  const invoices = readAndroid("src/main/java/com/example/ui/screens/InvoicesScreen.kt");
  const providers = readAndroid("src/main/java/com/example/ui/screens/ProvidersScreen.kt");
  assert.match(invoices, /focusSourceMessageId/);
  assert.match(providers, /focusOpportunityId/);
});
