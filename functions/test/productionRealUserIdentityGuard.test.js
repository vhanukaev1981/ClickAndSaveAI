"use strict";

const { mkdtempSync, rmSync, writeFileSync } = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { spawnSync } = require("node:child_process");
const test = require("node:test");
const assert = require("node:assert/strict");

const REPO_ROOT = path.resolve(__dirname, "../..");
const GUARD = path.join(REPO_ROOT, "scripts/production-readiness-guard.mjs");
const APP_ID = "com.aistudio.clickandsaveai.app";
const PROJECT_ID = "clickandsaveai-production";
const WEB_CLIENT_ID = "123456789012-production-web.apps.googleusercontent.com";
const ANDROID_CLIENT_ID = "123456789012-production-android.apps.googleusercontent.com";
const PLAY_SHA1 = "11223344556677889900AABBCCDDEEFF00112233";

function writeGoogleServices({ includeAndroidOAuth = true, includeWebOAuth = true, certificateHash = PLAY_SHA1 } = {}) {
  const dir = mkdtempSync(path.join(os.tmpdir(), "clickandsave-prod-identity-"));
  const configPath = path.join(dir, "google-services.json");
  const oauthClient = [];
  if (includeAndroidOAuth) {
    oauthClient.push({
      client_id: ANDROID_CLIENT_ID,
      client_type: 1,
      android_info: {
        package_name: APP_ID,
        certificate_hash: certificateHash,
      },
    });
  }
  if (includeWebOAuth) {
    oauthClient.push({ client_id: WEB_CLIENT_ID, client_type: 3 });
  }
  writeFileSync(configPath, JSON.stringify({
    project_info: { project_id: PROJECT_ID },
    client: [{
      client_info: { android_client_info: { package_name: APP_ID } },
      oauth_client: oauthClient,
    }],
  }));
  return { dir, configPath };
}

function runGuard(configPath, extraEnv = {}) {
  return spawnSync(process.execPath, [GUARD, "materialized"], {
    cwd: REPO_ROOT,
    encoding: "utf8",
    env: {
      ...process.env,
      PRODUCTION_FIREBASE_PROJECT_ID: PROJECT_ID,
      PRODUCTION_GOOGLE_WEB_CLIENT_ID: WEB_CLIENT_ID,
      PRODUCTION_APP_SIGNING_CERT_SHA1: PLAY_SHA1,
      PRODUCTION_GOOGLE_SERVICES_JSON_PATH: configPath,
      ...extraEnv,
    },
  });
}

test("materialized production guard rejects missing Android OAuth client", () => {
  const fixture = writeGoogleServices({ includeAndroidOAuth: false });
  try {
    const result = runGuard(fixture.configPath);
    assert.notEqual(result.status, 0, result.stdout + result.stderr);
    assert.match(result.stderr, /Android OAuth client/i);
  } finally {
    rmSync(fixture.dir, { recursive: true, force: true });
  }
});

test("materialized production guard rejects Android OAuth client with wrong Play SHA-1", () => {
  const fixture = writeGoogleServices({ certificateHash: "FFEEDDCCBBAA0099887766554433221100FFEEDD" });
  try {
    const result = runGuard(fixture.configPath);
    assert.notEqual(result.status, 0, result.stdout + result.stderr);
    assert.match(result.stderr, /SHA-1/i);
  } finally {
    rmSync(fixture.dir, { recursive: true, force: true });
  }
});

test("materialized production guard rejects missing Web OAuth client in google-services config", () => {
  const fixture = writeGoogleServices({ includeWebOAuth: false });
  try {
    const result = runGuard(fixture.configPath);
    assert.notEqual(result.status, 0, result.stdout + result.stderr);
    assert.match(result.stderr, /Web OAuth client/i);
  } finally {
    rmSync(fixture.dir, { recursive: true, force: true });
  }
});

test("materialized production guard accepts exact Android and Web OAuth production bindings", () => {
  const fixture = writeGoogleServices();
  try {
    const result = runGuard(fixture.configPath);
    assert.equal(result.status, 0, result.stdout + result.stderr);
    assert.match(result.stdout, /Materialized production configuration guard PASS/);
  } finally {
    rmSync(fixture.dir, { recursive: true, force: true });
  }
});
