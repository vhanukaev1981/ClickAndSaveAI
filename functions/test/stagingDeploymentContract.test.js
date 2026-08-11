"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const workflowPath = path.resolve(__dirname, "..", "..", ".github", "workflows", "deploy-staging.yml");
const workflow = fs.readFileSync(workflowPath, "utf8");

test("staging deploy is pinned to an immutable source SHA", () => {
  assert.match(workflow, /source_sha:/i);
  assert.match(workflow, /Verify immutable Core source/);
  assert.match(workflow, /git rev-parse HEAD/);
  assert.match(workflow, /SOURCE_SHA/);
  assert.doesNotMatch(workflow, /default:\s*agent\/ai-native-financial-core/);
});

test("staging deploy remains fail-closed on target project and required identity inputs", () => {
  assert.match(workflow, /clickandsaveai-staging/);
  assert.match(workflow, /WIF_PROVIDER:\s*\$\{\{ vars\.GCP_WORKLOAD_IDENTITY_PROVIDER \}\}/);
  assert.match(workflow, /DEPLOY_SERVICE_ACCOUNT:\s*\$\{\{ vars\.GCP_DEPLOY_SERVICE_ACCOUNT \}\}/);
  assert.match(workflow, /STAGING_SMOKE_USER_UID:\s*\$\{\{ vars\.STAGING_SMOKE_USER_UID \}\}/);
  assert.match(workflow, /missing=\(\)/);
  assert.match(workflow, /missing\+=\(GCP_WORKLOAD_IDENTITY_PROVIDER\)/);
  assert.match(workflow, /missing\+=\(GCP_DEPLOY_SERVICE_ACCOUNT\)/);
  assert.match(workflow, /missing\+=\(STAGING_SMOKE_USER_UID\)/);
  assert.match(workflow, /if \(\( \$\{#missing\[@\]\} > 0 \)\); then/);
  assert.match(workflow, /exit 1/);
});

test("staging deploy includes only required Firebase Core targets", () => {
  assert.match(workflow, /firebase deploy/);
  assert.match(workflow, /--project clickandsaveai-staging/);
  assert.match(workflow, /--only firestore:rules,firestore:indexes,functions/);
  assert.match(workflow, /--non-interactive/);
});
