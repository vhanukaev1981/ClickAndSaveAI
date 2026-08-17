"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const root = path.resolve(__dirname, "..", "..");
const workflow = fs.readFileSync(
  path.join(root, ".github/workflows/production-release.yml"),
  "utf8"
);

const deployStart = workflow.indexOf("\n  deploy-firebase-production:");
assert.ok(deployStart >= 0, "deploy-firebase-production job must exist");
const deployJob = workflow.slice(deployStart);

test("Block 3F production deploy binds the authoritative Production Web client ID non-interactively", () => {
  assert.match(
    deployJob,
    /PRODUCTION_GOOGLE_WEB_CLIENT_ID:\s*\$\{\{ vars\.PRODUCTION_GOOGLE_WEB_CLIENT_ID \}\}/
  );
  assert.match(
    deployJob,
    /\[\[ "\$PRODUCTION_FIREBASE_PROJECT_ID" == 'click-save-ai-production' \]\]/
  );
  assert.match(deployJob, /\[\[ -n "\$PRODUCTION_GOOGLE_WEB_CLIENT_ID" \]\]/);
  assert.match(
    deployJob,
    /RUNTIME_PARAMS_FILE="functions\/\.env\.\$\{PRODUCTION_FIREBASE_PROJECT_ID\}"/
  );
  assert.match(deployJob, /umask 077/);
  assert.match(deployJob, /printf 'GOOGLE_OAUTH_CLIENT_ID=%s\\n'/);
  assert.match(deployJob, /trap 'rm -f "\$RUNTIME_PARAMS_FILE"' EXIT/);
  assert.match(deployJob, /firebase deploy[\s\S]*--non-interactive/);
});

test("Block 3F production deploy keeps runtime OAuth and token-encryption secrets out of GitHub workflow inputs", () => {
  assert.doesNotMatch(deployJob, /secrets\.GOOGLE_OAUTH_CLIENT_SECRET/);
  assert.doesNotMatch(deployJob, /secrets\.OAUTH_TOKEN_ENCRYPTION_KEY/);
  assert.doesNotMatch(deployJob, /GOOGLE_OAUTH_CLIENT_SECRET=/);
  assert.doesNotMatch(deployJob, /OAUTH_TOKEN_ENCRYPTION_KEY=/);
});

test("Block 3F production deploy does not create Android OAuth, touch Play, or broaden IAM", () => {
  assert.doesNotMatch(deployJob, /oauth.*android|android.*oauth/i);
  assert.doesNotMatch(deployJob, /google[ -]?play|play.*publish|play.*upload/i);
  assert.doesNotMatch(deployJob, /gcloud\s+projects\s+add-iam-policy-binding/);
  assert.doesNotMatch(deployJob, /gcloud\s+services\s+(enable|disable)/);
  assert.doesNotMatch(deployJob, /service-accounts\s+keys\s+create/);
});
