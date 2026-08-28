"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const bootstrapPath = path.resolve(__dirname, "../../scripts/bootstrap-production-deploy-iam.sh");
const verifyPath = path.resolve(__dirname, "../../scripts/verify-production-deploy-iam.sh");
const bootstrap = fs.readFileSync(bootstrapPath, "utf8");
const verify = fs.readFileSync(verifyPath, "utf8");

test("production deploy IAM uses a one-permission custom role for Cloud Run IAM policy updates", () => {
  assert.match(bootstrap, /CUSTOM_DEPLOY_ROLE_ID="clickandsaveaiFirebaseDeployIamPolicy"/);
  assert.match(bootstrap, /run\.services\.setIamPolicy/);
  assert.match(bootstrap, /--permissions="run\.services\.setIamPolicy"/);
  assert.match(verify, /CUSTOM_DEPLOY_ROLE_ID="clickandsaveaiFirebaseDeployIamPolicy"/);
  assert.match(verify, /run\.services\.setIamPolicy/);

  assert.doesNotMatch(bootstrap, /INTENDED_ROLES=\([\s\S]*roles\/run\.admin/);
  assert.doesNotMatch(bootstrap, /INTENDED_ROLES=\([\s\S]*roles\/cloudfunctions\.admin/);
  assert.doesNotMatch(bootstrap, /INTENDED_ROLES=\([\s\S]*roles\/artifactregistry\.admin/);
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
