"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const root = path.resolve(__dirname, "..", "..");
const bootstrapPath = path.join(root, "scripts", "bootstrap-production-deploy-iam.sh");
const bootstrap = fs.readFileSync(bootstrapPath, "utf8");

const EXPECTED_DAYS = "7";
const LOCATIONS = ["europe-west1", "us-central1"];

test("bootstrap declares EXPECTED_ARTIFACT_CLEANUP_DAYS as exactly 7", () => {
  assert.match(bootstrap, /EXPECTED_ARTIFACT_CLEANUP_DAYS="7"/);
});

test("bootstrap requires Firebase CLI >= 14 before running setpolicy", () => {
  assert.match(bootstrap, /firebase.*--version/);
  assert.match(bootstrap, /FIREBASE_CLI_MAJOR.*-ge 14/);
  const cliCheck = bootstrap.indexOf("FIREBASE_CLI_MAJOR");
  const setpolicy = bootstrap.indexOf("functions:artifacts:setpolicy");
  assert.ok(cliCheck < setpolicy, "Firebase CLI version gate must precede setpolicy invocations");
});

for (const location of LOCATIONS) {
  test(`bootstrap configures setpolicy for ${location} with --project scoping and --days=${EXPECTED_DAYS}`, () => {
    const pattern = new RegExp(
      `functions:artifacts:setpolicy\\s+--project=["']?\\$PROJECT_ID["']?\\s+--location=${location}\\s+--days=${EXPECTED_DAYS}`
    );
    assert.match(bootstrap, pattern);
  });

  test(`bootstrap logs cleanup-policy configuration for ${location} before executing it`, () => {
    const logMsg = `Configuring Firebase Functions Artifact Registry cleanup policy in ${location}`;
    const cmdPattern = new RegExp(`functions:artifacts:setpolicy[^\\n]*--location=${location}`);
    const logIdx = bootstrap.indexOf(logMsg);
    const cmdMatch = cmdPattern.exec(bootstrap);
    assert.ok(logIdx !== -1, `log message for ${location} must be present`);
    assert.ok(cmdMatch !== null, `setpolicy command for ${location} must be present`);
    assert.ok(logIdx < cmdMatch.index, `log message for ${location} must precede its setpolicy command`);
  });
}

test("setpolicy commands do not use --force flag", () => {
  const allSetpolicyLines = bootstrap
    .split("\n")
    .filter((line) => line.includes("functions:artifacts:setpolicy"));
  assert.ok(allSetpolicyLines.length >= 2, "at least two setpolicy invocations must be present");
  for (const line of allSetpolicyLines) {
    assert.doesNotMatch(line, /--force/, `setpolicy command must not use --force: ${line}`);
  }
});

test("bootstrap configures cleanup policy after IAM role grants, not before", () => {
  const iamGrant = bootstrap.indexOf("add-iam-policy-binding");
  const firstSetpolicy = bootstrap.indexOf("functions:artifacts:setpolicy");
  assert.ok(iamGrant !== -1, "IAM grant block must be present");
  assert.ok(firstSetpolicy !== -1, "setpolicy block must be present");
  assert.ok(iamGrant < firstSetpolicy, "IAM grants must precede cleanup-policy setup");
});

test("bootstrap reports artifact cleanup retention on success", () => {
  assert.match(bootstrap, /Artifact cleanup retention configured/);
  assert.match(bootstrap, /europe-west1 and us-central1/);
});
