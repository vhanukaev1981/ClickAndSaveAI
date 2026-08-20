"use strict";

const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { spawnSync } = require("node:child_process");
const test = require("node:test");
const assert = require("node:assert/strict");

const repoRoot = path.resolve(__dirname, "..", "..");
const classifier = path.join(repoRoot, "scripts", "staging-firebase-deploy-classifier.mjs");

function runClassifier({ exitCode, log }) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "staging-firebase-classifier-"));
  const logPath = path.join(dir, "firebase.log");
  const functionsPath = path.join(dir, "functions.txt");
  fs.writeFileSync(logPath, log, "utf8");
  const result = spawnSync(
    process.execPath,
    [classifier, "--exit-code", String(exitCode), "--log", logPath, "--write-functions", functionsPath],
    { encoding: "utf8" },
  );
  return {
    ...result,
    functions: fs.existsSync(functionsPath) ? fs.readFileSync(functionsPath, "utf8").trim() : "",
  };
}

test("cleanup-only post-success evidence may be downgraded and records successful functions", () => {
  const result = runClassifier({
    exitCode: 2,
    log: [
      "✔  functions[scanGmailInvoices(us-central1)] Successful update operation.",
      "✔  functions[financialHome(us-central1)] Successful update operation.",
      "✔  firestore: released rules rules.firestore to cloud.firestore",
      "✔  firestore: deployed indexes in firestore.indexes.json successfully for (default) database",
      "⚠  functions: Failed to set up cleanup policy for repositories in region us-central1.",
      "⚠  functions: Functions successfully deployed but could not set up cleanup policy in region us-central1.",
    ].join("\n"),
  });
  assert.equal(result.status, 0, result.stderr || result.stdout);
  assert.match(result.stdout, /CLASSIFICATION=CLEANUP_ONLY_POST_SUCCESS/);
  assert.match(result.functions, /scanGmailInvoices/);
  assert.match(result.functions, /financialHome/);
});

test("unknown nonzero deploy failure remains fatal", () => {
  const result = runClassifier({ exitCode: 2, log: "Error: There was an error deploying functions" });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /CLASSIFICATION=FATAL_UNKNOWN/);
});

test("cleanup warning cannot mask an individual function failure", () => {
  const result = runClassifier({
    exitCode: 2,
    log: [
      "✔  functions[financialHome(us-central1)] Successful update operation.",
      "Could not create or update Cloud Run service scanGmailInvoices, Container Healthcheck failed.",
      "Functions deploy had errors with the following functions:",
      "scanGmailInvoices(us-central1)",
      "⚠  functions: Functions successfully deployed but could not set up cleanup policy in region us-central1.",
      "Error: There was an error deploying functions",
    ].join("\n"),
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /CLASSIFICATION=FATAL_DEPLOY_ERROR/);
});

test("cleanup warning without explicit successful function evidence remains fatal", () => {
  const result = runClassifier({
    exitCode: 2,
    log: "⚠  functions: Functions successfully deployed but could not set up cleanup policy in region us-central1.",
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /CLASSIFICATION=FATAL_INSUFFICIENT_PROOF/);
});

test("auth IAM build and secret failures remain fatal even if cleanup text is present", () => {
  for (const fatal of [
    "PERMISSION_DENIED: Permission iam.serviceAccounts.actAs denied",
    "Build failed with status FAILURE",
    "Secret Manager secret access denied",
    "Authentication Error: invalid_grant",
  ]) {
    const result = runClassifier({
      exitCode: 2,
      log: [
        "✔  functions[financialHome(us-central1)] Successful update operation.",
        fatal,
        "⚠  functions: Functions successfully deployed but could not set up cleanup policy in region us-central1.",
      ].join("\n"),
    });
    assert.notEqual(result.status, 0, fatal);
    assert.match(result.stderr, /CLASSIFICATION=FATAL_DEPLOY_ERROR/);
  }
});

test("zero Firebase exit remains success without requiring cleanup classification", () => {
  const result = runClassifier({
    exitCode: 0,
    log: "✔  functions[financialHome(us-central1)] Successful update operation.",
  });
  assert.equal(result.status, 0, result.stderr || result.stdout);
  assert.match(result.stdout, /CLASSIFICATION=SUCCESS/);
  assert.match(result.functions, /financialHome/);
});
