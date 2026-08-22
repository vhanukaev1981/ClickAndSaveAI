"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const repoRoot = path.resolve(__dirname, "..", "..");
const workflowPath = path.join(repoRoot, ".github", "workflows", "deploy-staging.yml");
const firebasePath = path.join(repoRoot, "firebase.json");
const preflightPath = path.join(repoRoot, "scripts", "staging-artifact-cleanup-preflight.sh");
const bridgePath = path.join(repoRoot, "scripts", "staging-artifact-cleanup-predeploy.sh");
const classifierPath = path.join(repoRoot, "scripts", "staging-firebase-deploy-classifier.mjs");
const workflow = fs.readFileSync(workflowPath, "utf8");

test("A/B/J: staging workflow remains exact-SHA guarded and accepts only the staging project", () => {
  assert.match(workflow, /SOURCE_SHA:\s*\$\{\{ inputs\.source_sha \}\}/);
  assert.match(workflow, /ACTUAL_SHA="\$\(git rev-parse HEAD\)"/);
  assert.match(workflow, /if \[\[ "\$ACTUAL_SHA" != "\$SOURCE_SHA" \]\]/);
  assert.match(workflow, /inputs\.confirm_project == 'clickandsaveai-staging'/);
  assert.match(workflow, /--project clickandsaveai-staging/);
  assert.doesNotMatch(workflow, /--project\s+\$\{\{/);
  assert.doesNotMatch(workflow, /confirm_project\s*!=?\s*'clickandsaveai-staging'/);

  const projectArgs = [...workflow.matchAll(/--project(?:=|\s+)([^\s\\]+)/g)].map((match) => match[1]);
  assert.ok(projectArgs.length > 0, "staging workflow must explicitly pass a Firebase/GCP project");
  assert.deepEqual([...new Set(projectArgs)], ["clickandsaveai-staging"]);
});

test("exact source SHA is gated by all three required CI workflows", () => {
  assert.match(workflow, /android-ci\.yml\|Android and Backend CI/);
  assert.match(workflow, /production-operations-ci\.yml\|Production Operations CI/);
  assert.match(workflow, /production-enablement-ci\.yml\|Production Enablement Security CI/);
  assert.match(workflow, /head_sha=\$SOURCE_SHA/);
});

test("C/D: bounded Artifact Registry cleanup preflight exists and is hard-pinned to staging", () => {
  assert.ok(fs.existsSync(preflightPath), "missing staging Artifact Registry cleanup preflight");
  const preflight = fs.readFileSync(preflightPath, "utf8");
  assert.match(workflow, /Run Staging Artifact Registry cleanup preflight/);
  assert.match(workflow, /scripts\/staging-artifact-cleanup-preflight\.sh/);
  assert.match(preflight, /readonly PROJECT_ID="clickandsaveai-staging"/);
  assert.match(preflight, /readonly LOCATION="us-central1"/);
  assert.match(preflight, /gcloud artifacts repositories list/);
  assert.match(preflight, /gcloud artifacts repositories describe/);
  assert.match(preflight, /gcloud artifacts repositories list-cleanup-policies/);
  assert.match(preflight, /gcloud artifacts repositories set-cleanup-policies/);
  assert.match(preflight, /--no-dry-run/);
  assert.match(preflight, /firebase-functions-cleanup/);
  assert.match(preflight, /tagState:\s*"any"/);
  assert.match(preflight, /olderThan:\s*"1d"/);
  assert.match(preflight, /artifactregistry\.repositories\.get/);
  assert.match(preflight, /artifactregistry\.repositories\.update/);
  assert.match(preflight, /artifactregistry\.versions\.delete/);
  assert.doesNotMatch(preflight, /add-iam-policy-binding|set-iam-policy|repositories delete/);
  assert.doesNotMatch(preflight, /PROJECT_ID:-|PROJECT_ID=\"\$\{/);
});

test("C/D compatibility: exact-source deploys from protected main still execute the staging-only preflight", () => {
  assert.ok(fs.existsSync(bridgePath), "missing staging predeploy bridge");
  const bridge = fs.readFileSync(bridgePath, "utf8");
  const firebase = JSON.parse(fs.readFileSync(firebasePath, "utf8"));
  const functionsConfigs = Array.isArray(firebase.functions) ? firebase.functions : [firebase.functions];
  const predeploy = functionsConfigs.flatMap((entry) => entry?.predeploy || []);
  assert.ok(
    predeploy.some((command) => String(command).includes("scripts/staging-artifact-cleanup-predeploy.sh")),
    "firebase.json must invoke the staging-only predeploy compatibility bridge",
  );
  assert.match(bridge, /GCLOUD_PROJECT/);
  assert.match(bridge, /clickandsaveai-staging/);
  assert.match(bridge, /STAGING_ARTIFACT_CLEANUP_PREFLIGHT_VERIFIED/);
  assert.match(bridge, /staging-artifact-cleanup-preflight\.sh/);
  assert.doesNotMatch(bridge, /clickandsaveai-prod/);
});

test("E/F/G: Firebase deploy is never blindly suppressed and cleanup downgrade requires explicit proof", () => {
  assert.doesNotMatch(workflow, /firebase deploy[^\n]*\|\|\s*true/);
  assert.ok(fs.existsSync(classifierPath), "missing guarded Firebase deploy classifier");
  assert.match(workflow, /staging-firebase-deploy-classifier\.mjs/);
  assert.match(workflow, /FIREBASE_DEPLOY_EXIT/);
  assert.match(workflow, /PIPESTATUS\[0\]/);
  assert.match(workflow, /Functions successfully deployed but could not set up cleanup policy/);
  assert.match(workflow, /touch "\$RUNNER_TEMP\/staging-firestore-deploy-proven"/);
  assert.match(workflow, /test -f "\$RUNNER_TEMP\/staging-firestore-deploy-proven"/);
  assert.match(workflow, /Verify deployed staging Functions are ACTIVE/);
});

test("H/I: successful deploy path still reaches authenticated smoke and Block 5 E2E", () => {
  const deployIndex = workflow.indexOf("Deploy Functions to staging with guarded classifier");
  const activeIndex = workflow.indexOf("Verify deployed staging Functions are ACTIVE");
  const truthSmokeIndex = workflow.indexOf("Run authenticated staging truth smoke");
  const smokeUploadIndex = workflow.indexOf("Upload sanitized staging smoke evidence");
  const block5Index = workflow.indexOf("Run Block 5 destructive lifecycle E2E with ephemeral accounts");
  const block5UploadIndex = workflow.indexOf("Upload sanitized Block 5 staging E2E evidence");
  assert.ok(deployIndex >= 0, "guarded Functions deploy step is missing");
  assert.ok(activeIndex > deployIndex, "ACTIVE verification must remain downstream of Functions deploy");
  assert.ok(truthSmokeIndex > activeIndex, "authenticated truth smoke must remain downstream of deploy verification");
  assert.ok(smokeUploadIndex > truthSmokeIndex, "smoke evidence upload must remain reachable");
  assert.ok(block5Index > smokeUploadIndex, "Block 5 E2E must remain reachable");
  assert.ok(block5UploadIndex > block5Index, "Block 5 evidence upload must remain reachable");
});

test("Firestore deploy remains independently fail-closed", () => {
  const firestoreIndex = workflow.indexOf("Deploy Firestore to staging");
  const functionsIndex = workflow.indexOf("Deploy Functions to staging with guarded classifier");
  assert.ok(firestoreIndex >= 0, "separate Firestore deploy step is missing");
  assert.ok(functionsIndex > firestoreIndex, "Functions deploy must follow successful Firestore deploy");
  const firestoreSlice = workflow.slice(firestoreIndex, functionsIndex);
  assert.match(firestoreSlice, /firebase deploy/);
  assert.match(firestoreSlice, /--only firestore:rules,firestore:indexes/);
  assert.doesNotMatch(firestoreSlice, /set \+e|\|\|\s*true/);
});
