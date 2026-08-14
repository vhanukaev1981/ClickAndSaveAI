"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const root = path.resolve(process.cwd(), "..");
const read = (relative) => fs.readFileSync(path.join(root, relative), "utf8");

const APP_ID = "com.aistudio.clickandsaveai.app";
const STAGING_PROJECT = "clickandsaveai-staging";
const STAGING_CLIENT = "716864421960-hnt5709tqk9qp79si8ggplf5jif1ulfu.apps.googleusercontent.com";
const GMAIL_READONLY = "https://www.googleapis.com/auth/gmail.readonly";

test("production Android identity remains canonical and signing is explicit", () => {
  const gradle = read("app/build.gradle.kts");
  assert.match(gradle, new RegExp(`applicationId = "${APP_ID.replaceAll(".", "\\.")}"`));
  assert.match(gradle, /PRODUCTION_RELEASE_CANDIDATE/);
  assert.match(gradle, /PRODUCTION_UPLOAD_KEYSTORE_PATH/);
  assert.match(gradle, /PRODUCTION_UPLOAD_STORE_PASSWORD/);
  assert.match(gradle, /PRODUCTION_UPLOAD_KEY_ALIAS/);
  assert.match(gradle, /PRODUCTION_UPLOAD_KEY_PASSWORD/);
  assert.doesNotMatch(gradle, /storePassword\s*=\s*"[^"$]+"/);
  assert.doesNotMatch(gradle, /keyPassword\s*=\s*"[^"$]+"/);
});

test("OAuth resources are environment isolated", () => {
  const main = read("app/src/main/res/values/strings.xml");
  const debug = read("app/src/debug/res/values/strings.xml");
  const gradle = read("app/build.gradle.kts");
  assert.doesNotMatch(main, new RegExp(STAGING_CLIENT.replaceAll(".", "\\.")));
  assert.match(debug, new RegExp(STAGING_CLIENT.replaceAll(".", "\\.")));
  assert.match(gradle, /PRODUCTION_GOOGLE_WEB_CLIENT_ID/);
});

test("Gmail authorization remains read-only on client and server", () => {
  const activity = read("app/src/main/java/com/example/MainActivity.kt");
  const connect = read("functions/src/gmailConnectFunctions.js");
  assert.match(activity, new RegExp(GMAIL_READONLY.replaceAll(".", "\\.")));
  assert.match(connect, new RegExp(GMAIL_READONLY.replaceAll(".", "\\.")));
  for (const forbidden of ["gmail.send", "gmail.modify", "https://mail.google.com/"]) {
    assert.doesNotMatch(activity, new RegExp(forbidden.replaceAll(".", "\\.")));
    assert.doesNotMatch(connect, new RegExp(forbidden.replaceAll(".", "\\.")));
  }
  assert.match(connect, /enforceAppCheck:\s*true/);
});

test("App Check providers stay separated by build type", () => {
  assert.match(read("app/src/release/java/com/example/AppCheckInstaller.kt"), /PlayIntegrityAppCheckProviderFactory/);
  assert.doesNotMatch(read("app/src/release/java/com/example/AppCheckInstaller.kt"), /DebugAppCheckProviderFactory/);
  assert.match(read("app/src/debug/java/com/example/AppCheckInstaller.kt"), /DebugAppCheckProviderFactory/);
});

test("Firestore direct client access remains deny-by-default", () => {
  const rules = read("firestore.rules");
  assert.match(rules, /allow read, write: if false;/);
  assert.doesNotMatch(rules, /allow\s+(read|write|read, write):\s+if\s+request\.auth/);
});

test("production workflow is manual, protected and staging-isolated", () => {
  const workflow = read(".github/workflows/production-release.yml");
  assert.match(workflow, /workflow_dispatch:/);
  assert.doesNotMatch(workflow, /\npull_request:/);
  assert.doesNotMatch(workflow, /\npush:/);
  assert.match(workflow, /environment: production/);
  assert.match(workflow, /DEPLOY_FIREBASE_PRODUCTION/);
  assert.doesNotMatch(workflow, new RegExp(`--project ${STAGING_PROJECT}`));
  assert.doesNotMatch(workflow, new RegExp(STAGING_CLIENT.replaceAll(".", "\\.")));
});

test("repository includes current-tree and full-history secret audit", () => {
  const scanner = read("scripts/repository-secret-audit.mjs");
  assert.match(scanner, /rev-list/);
  assert.match(scanner, /PRIVATE_KEY/);
  assert.match(scanner, /OAUTH_CLIENT_SECRET/);
  assert.match(scanner, /GITHUB_TOKEN/);
  assert.match(scanner, /KEYSTORE_FILE/);
});

test("production readiness guard rejects environment leakage", () => {
  const guard = read("scripts/production-readiness-guard.mjs");
  assert.match(guard, new RegExp(STAGING_PROJECT));
  assert.match(guard, /Production Firebase project must be explicit and distinct from staging/);
  assert.match(guard, /Staging OAuth client leaked into production google-services configuration/);
});
