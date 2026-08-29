"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const bootstrapPath = path.resolve(__dirname, "../../scripts/bootstrap-production-deploy-iam.sh");
const verifyPath = path.resolve(__dirname, "../../scripts/verify-production-deploy-iam.sh");
const bootstrap = fs.readFileSync(bootstrapPath, "utf8");
const verify = fs.readFileSync(verifyPath, "utf8");

function intendedRolesBlock(source) {
  const match = source.match(/INTENDED_ROLES=\(([\s\S]*?)\)\n(?:APPROVED_PREEXISTING_ROLES|FORBIDDEN_ROLES)=/);
  assert.ok(match, "INTENDED_ROLES block must be present");
  return match[1];
}

function approvedPreexistingRolesBlock(source) {
  const match = source.match(/APPROVED_PREEXISTING_ROLES=\(([\s\S]*?)\)\nFORBIDDEN_ROLES=/);
  assert.ok(match, "APPROVED_PREEXISTING_ROLES block must be present");
  return match[1];
}

test("production deploy IAM uses a one-permission custom role for Cloud Run IAM policy updates", () => {
  assert.match(bootstrap, /CUSTOM_DEPLOY_ROLE_ID="clickandsaveaiFirebaseDeployIamPolicy"/);
  assert.match(bootstrap, /run\.services\.setIamPolicy/);
  assert.match(bootstrap, /--permissions="run\.services\.setIamPolicy"/);
  assert.match(verify, /CUSTOM_DEPLOY_ROLE_ID="clickandsaveaiFirebaseDeployIamPolicy"/);
  assert.match(verify, /run\.services\.setIamPolicy/);

  const intended = intendedRolesBlock(bootstrap);
  assert.doesNotMatch(intended, /roles\/run\.admin/);
  assert.doesNotMatch(intended, /roles\/cloudfunctions\.admin/);
  assert.doesNotMatch(intended, /roles\/artifactregistry\.admin/);
});

test("production IAM bootstra preserves only the exact actual Firebase Metadata Reader role when pre-existing", () => {
  const metadataReaderRole = "projects/click-save-ai-production/roles/clickandsaveFirebaseMetadataReader";
  const bootstrapApproved = approvedPreexistingRolesBlock(bootstrap);
  const verifyApproved = approvedPreexistingRolesBlock(verify);

  assert.match(bootstrapApproved, new RegExp(metadataReaderRole.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  assert.match(verifyApproved, new RegExp(metadataReaderRole.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  assert.doesNotMatch(intendedRolesBlock(bootstrap), /clickandsaveFirebaseMetadataReader/);
  assert.doesNotMatch(intendedRolesBlock(verify), /clickandsaveFirebaseMetadataReader/);
  assert.doesNotMatch(bootstrapApproved, /clickandsaveaiFirebaseMetadataReader/);
  assert.doesNotMatch(verifyApproved, /clickandsaveaiFirebaseMetadataReader/);
  assert.match(bootstrap, /PRESERVED_PREEXISTING_ROLES/);
  assert.match(verify, /approved pre-existing deploy-SA role/);
});

test("production IAM bootstrap preconfigures Firebase artifact cleanup in both deployment regions", () => {
  assert.match(
    bootstrap,
    /functions:artifacts:setpolicy[\s\S]*--location=europe-west1[\s\S]*--days=7/
  );
  assert.match(
    bootstrap,
    /functions:artifacts:setpolicy[\s\S]*--location=us-central1[\s\S]*--days=7/
  );
  assert.match(bootstrap, /EXPECTED_ARTIFACT_CLEANUP_DAYS="7"/);
});
