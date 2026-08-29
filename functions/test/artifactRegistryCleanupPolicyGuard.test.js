"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const root = path.resolve(__dirname, "..", "..");
const bootstrapPath = path.join(root, "scripts", "bootstrap-production-deploy-iam.sh");
const bootstrap = fs.readFileSync(bootstrapPath, "utf8");

const LOCATIONS = ["europe-west1", "us-central1"];

test("bootstrap declares the exact 7-day Artifact Registry cleanup contract", () => {
  assert.match(bootstrap, /EXPECTED_ARTIFACT_CLEANUP_DAYS="7"/);
  assert.match(bootstrap, /ARTIFACT_REPOSITORY_ID="gcf-artifacts"/);
  assert.match(bootstrap, /ARTIFACT_CLEANUP_POLICY_ID="firebase-functions-cleanup"/);
  assert.match(bootstrap, /"name": "firebase-functions-cleanup"/);
  assert.match(bootstrap, /"action": \{"type": "Delete"\}/);
  assert.match(bootstrap, /"tagState": "any"/);
  assert.match(bootstrap, /"olderThan": "7d"/);
});

test("bootstrap uses direct Artifact Registry cleanup-policy mutation rather than Firebase setpolicy", () => {
  assert.match(bootstrap, /gcloud artifacts repositories set-cleanup-policies "\$ARTIFACT_REPOSITORY_ID"/);
  assert.match(bootstrap, /--project="\$PROJECT_ID"/);
  assert.match(bootstrap, /--location="\$location"/);
  assert.match(bootstrap, /--policy="\$ARTIFACT_CLEANUP_POLICY_FILE"/);
  assert.match(bootstrap, /--no-dry-run/);
  assert.match(bootstrap, /--quiet/);
  assert.doesNotMatch(bootstrap, /functions:artifacts:setpolicy/);
});

test("bootstrap checks for gcf-artifacts before attempting cleanup configuration", () => {
  const functionStart = bootstrap.indexOf("configure_artifact_cleanup() {");
  const describe = bootstrap.indexOf("gcloud artifacts repositories describe", functionStart);
  const mutation = bootstrap.indexOf("gcloud artifacts repositories set-cleanup-policies", functionStart);
  const missingRepoGuard = bootstrap.indexOf('if [[ -z "$repo_json" ]]', functionStart);

  assert.ok(functionStart !== -1, "cleanup configuration function must be present");
  assert.ok(describe > functionStart, "repository describe must be present in cleanup function");
  assert.ok(missingRepoGuard > describe, "missing-repository guard must follow repository lookup");
  assert.ok(mutation > missingRepoGuard, "cleanup mutation must happen only after repository-presence guard");
});

test("bootstrap logs cleanup configuration before the direct mutation", () => {
  const functionStart = bootstrap.indexOf("configure_artifact_cleanup() {");
  const log = bootstrap.indexOf("Configuring Artifact Registry cleanup policy", functionStart);
  const mutation = bootstrap.indexOf("gcloud artifacts repositories set-cleanup-policies", functionStart);

  assert.ok(log > functionStart, "cleanup configuration log must be present");
  assert.ok(mutation > log, "cleanup log must precede Artifact Registry mutation");
});

test("direct cleanup mutation does not use --force", () => {
  const mutationStart = bootstrap.indexOf("gcloud artifacts repositories set-cleanup-policies");
  assert.ok(mutationStart !== -1, "Artifact Registry cleanup mutation must be present");
  const mutationEnd = bootstrap.indexOf(">/dev/null", mutationStart);
  const mutationBlock = bootstrap.slice(mutationStart, mutationEnd === -1 ? undefined : mutationEnd);
  assert.doesNotMatch(mutationBlock, /--force/);
});

test("bootstrap verifies the exact live cleanup policy after mutation", () => {
  assert.match(bootstrap, /cleanupPolicies/);
  assert.match(bootstrap, /assert policy is not None/);
  assert.match(bootstrap, /assert policy\.get\('id'\) == policy_id/);
  assert.match(bootstrap, /assert policy\.get\('action'\) == 'DELETE'/);
  assert.match(bootstrap, /assert condition\.get\('tagState'\) == 'ANY'/);
  assert.match(bootstrap, /assert condition\.get\('olderThan'\) == '604800s'/);
  assert.match(bootstrap, /assert repo\.get\('cleanupPolicyDryRun', False\) is False/);
});

test("bootstrap configures cleanup in both canonical Functions artifact regions", () => {
  for (const location of LOCATIONS) {
    assert.match(bootstrap, new RegExp(`configure_artifact_cleanup ${location}`));
  }
});

test("bootstrap configures cleanup only after IAM role grants and exact final-role verification", () => {
  const iamGrant = bootstrap.indexOf("add-iam-policy-binding");
  const finalRoleVerification = bootstrap.indexOf("Final deploy-SA project role set is not exactly");
  const firstCleanupCall = bootstrap.indexOf("configure_artifact_cleanup europe-west1");

  assert.ok(iamGrant !== -1, "IAM grant block must be present");
  assert.ok(finalRoleVerification !== -1, "final IAM role-set verification must be present");
  assert.ok(firstCleanupCall !== -1, "cleanup configuration call must be present");
  assert.ok(iamGrant < finalRoleVerification, "IAM grants must precede final role-set verification");
  assert.ok(finalRoleVerification < firstCleanupCall, "exact IAM verification must precede cleanup-policy setup");
});

test("bootstrap reports artifact cleanup retention on success", () => {
  assert.match(bootstrap, /Artifact cleanup retention configured/);
  assert.match(bootstrap, /europe-west1 and us-central1/);
});
