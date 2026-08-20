"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const source = fs.readFileSync(path.join(__dirname, "../src/gmailDisconnectFunctions.js"), "utf8");

test("manual credential-loss recovery is Staging-only, explicit and fail-closed", () => {
  assert.match(source, /confirmStagingGmailProviderCleanup\s*=\s*onCall/);
  assert.match(source, /enforceAppCheck:\s*true/);
  assert.match(source, /canUseStagingCredentialLossRecovery/);
  assert.match(source, /MANUAL_PROVIDER_CLEANUP_CONFIRMATION/);
  assert.match(source, /await\s+ref\.delete\(\)/);
  assert.match(source, /GMAIL_PROVIDER_CLEANUP_MANUALLY_CONFIRMED/);
  assert.doesNotMatch(source, /externalCleanupConfirmed:\s*true/);
});
