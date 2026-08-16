"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const workflowPath = path.resolve(__dirname, "..", "..", ".github", "workflows", "deploy-staging.yml");
const workflow = fs.readFileSync(workflowPath, "utf8");
const approvedBlock4Sha = "7fa4b23cf927ca1dada6c61c51f48477996a5a66";

test("staging deploy is pinned to the exact Block 5 SHA descended from the approved Block 4 checkpoint", () => {
  assert.match(workflow, /source_sha:/i);
  assert.match(workflow, /Verify immutable Block 5 source/);
  assert.match(workflow, /SOURCE_SHA:\s*\$\{\{ inputs\.source_sha \|\| github\.sha \}\}/);
  assert.match(workflow, new RegExp(`BLOCK5_BASE_SHA:\\s*${approvedBlock4Sha}`));
  assert.match(workflow, /ref:\s*\$\{\{ env\.SOURCE_SHA \}\}/);
  assert.match(workflow, /git rev-parse HEAD/);
  assert.match(workflow, /git merge-base --is-ancestor "\$BLOCK5_BASE_SHA" "\$ACTUAL_SHA"/);
  assert.match(workflow, /Wait for full CI success on exact source SHA/);
  assert.match(workflow, /head_sha=\$SOURCE_SHA/);
  assert.match(workflow, /CONCLUSION.*success/s);
  assert.doesNotMatch(workflow, /default:\s*agent\/ai-native-financial-core/);
});

test("automatic staging gate is isolated to the Block 5 branch while manual exact-SHA dispatch remains available", () => {
  assert.match(workflow, /workflow_dispatch:/);
  assert.match(workflow, /push:\s*\n\s*branches:\s*\n\s*- agent\/p0-block5-privacy-lifecycle/);
  assert.match(workflow, /github\.event_name == 'push'/);
  assert.match(workflow, /inputs\.confirm_project == 'clickandsaveai-staging'/);
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
