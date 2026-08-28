"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const bootstrapProbePath = path.resolve(__dirname, "../../scripts/production-bootstrap-capability-probe.mjs");
const canonicalProbePath = path.resolve(__dirname, "../../scripts/production-3f-firebase-iam-permission-probe.mjs");
const probe = fs.readFileSync(bootstrapProbePath, "utf8");
const canonicalProbe = fs.readFileSync(canonicalProbePath, "utf8");

const REQUIRED_BOOTSTRAP_PERMISSIONS = [
  "iam.roles.create",
  "iam.roles.get",
  "resourcemanager.projects.getIamPolicy",
  "resourcemanager.projects.setIamPolicy",
  "artifactregistry.repositories.get",
  "artifactregistry.repositories.update",
  "run.services.setIamPolicy",
];

test("Production bootstrap capability probe exposes every required permission", () => {
  for (const permission of REQUIRED_BOOTSTRAP_PERMISSIONS) {
    assert.match(probe, new RegExp(permission.replaceAll(".", "\\.")));
  }
  assert.match(probe, /financialagentsweep/);
  assert.match(probe, /gmailincrementalreconciliation/);
  assert.match(probe, /renewgmailwatches/);
  assert.match(probe, /europe-west1/);
  assert.match(probe, /us-central1/);
  assert.match(probe, /gcf-artifacts/);
});

test("bootstrap capability probe remains testIamPermissions-only and sanitized", () => {
  assert.match(probe, /:testIamPermissions/);
  assert.match(probe, /bootstrap_permission_/);
  assert.doesNotMatch(probe, /add-iam-policy-binding|remove-iam-policy-binding/);
  assert.doesNotMatch(probe, /functions:artifacts:setpolicy/);
  assert.doesNotMatch(probe, /method:\s*["'](?:PUT|PATCH|DELETE)["']/i);
});

test("canonical authorized 3F IAM CLI chains the bootstrap capability probe", () => {
  assert.match(canonicalProbe, /production-bootstrap-capability-probe\.mjs/);
  assert.match(canonicalProbe, /probeProductionBootstrapCapabilities/);
  assert.match(canonicalProbe, /BLOCK3F_IAM_TEST_ACCESS_TOKEN/);
  assert.match(canonicalProbe, /Math\.max\(result\.exitCode, bootstrapResult\.exitCode\)/);
});
