"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const root = path.join(__dirname, "..", "..");

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), "utf8");
}

test("staging Block 5 E2E uses ephemeral users and proves all destructive boundaries", () => {
  const source = read("scripts/staging-block5-e2e.mjs");
  assert.match(source, /clickandsaveai-staging/);
  assert.match(source, /createUser/);
  assert.match(source, /disconnectGmail/);
  assert.match(source, /deleteImportedFinancialData/);
  assert.match(source, /DELETE_IMPORTED_FINANCIAL_DATA/);
  assert.match(source, /deleteAccount/);
  assert.match(source, /DELETE_ACCOUNT/);
  assert.match(source, /registerPushToken/);
  assert.match(source, /createProviderLead/);
  assert.match(source, /staleTokenRejected/);
  assert.match(source, /staleLeadRejected/);
  assert.match(source, /controlAccountUntouched/);
  assert.match(source, /RETRY_REQUIRED/);
  assert.match(source, /DELETE_RETRY_REQUIRED/);
  assert.match(source, /accountDeletionPaused/);
  assert.match(source, /disconnectRetryIdempotent/);
  assert.match(source, /finally/);
});

test("staging deployment runs and uploads Block 5 E2E evidence after deploy", () => {
  const workflow = read(".github/workflows/deploy-staging.yml");
  assert.match(workflow, /staging-block5-e2e\.mjs/);
  assert.match(workflow, /staging-block5-e2e\.json/);
  assert.match(workflow, /staging-block5-e2e-\$\{\{ env\.SOURCE_SHA \}\}/);
});
