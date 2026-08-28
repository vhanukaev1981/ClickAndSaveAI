"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const probePath = path.resolve(__dirname, "../../scripts/production-3f-firebase-iam-permission-probe.mjs");
const probe = fs.readFileSync(probePath, "utf8");

const REQUIRED_BOOTSTRAP_PERMISSIONS = [
  "iam.roles.create",
  "iam.roles.get",
  "resourcemanager.projects.getIamPolicy",
  "resourcemanager.projects.setIamPolicy",
  "artifactregistry.repositories.get",
  "artifactregistry.repositories.update",
  "run.services.setIamPolicy",
];

test("Production IAM probe exposes every permission required for GitHub-only bootstrap", () => {
  for (const permission of REQUIRED_BOOTSTRAP_PERMISSIONS) {
    assert.match(probe, new RegExp(permission.replaceAll(".", "\\.")));
  }
});

test("bootstrap capability probe remains read-only and sanitized", () => {
  assert.match(probe, /:testIamPermissions/);
  assert.doesNotMatch(probe, /add-iam-policy-binding/);
  assert.doesNotMatch(probe, /setIamPolicy[^"'`\n]*\(/);
  assert.doesNotMatch(probe, /functions:artifacts:setpolicy/);
  assert.match(probe, /bootstrap_permission_/);
});
