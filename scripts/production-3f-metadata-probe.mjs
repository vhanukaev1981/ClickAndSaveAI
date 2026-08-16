#!/usr/bin/env node

import fs from "node:fs";

const STATUS = Object.freeze({
  PRESENT: "VERIFIED_PRESENT",
  ABSENT: "VERIFIED_ABSENT",
  MISMATCH: "MISMATCH",
  UNKNOWN_NO_ACCESS: "UNKNOWN_NO_ACCESS",
});

const EXPECTED_PROJECT_ID = "click-save-ai-production";
const FORBIDDEN_STAGING_PROJECT_ID = "clickandsaveai-staging";
const EXPECTED_PACKAGE_NAME = "com.aistudio.clickandsaveai.app";
const EXPECTED_PROJECT_NUMBER = "991489557172";
const EXPECTED_DEPLOY_SERVICE_ACCOUNT =
  "clickandsaveai-github-deployer@click-save-ai-production.iam.gserviceaccount.com";

const REQUIRED_VARIABLES = [
  "PRODUCTION_FIREBASE_PROJECT_ID",
  "PRODUCTION_GOOGLE_WEB_CLIENT_ID",
  "PRODUCTION_APP_SIGNING_CERT_SHA1",
  "PRODUCTION_APP_SIGNING_CERT_SHA256",
  "PRODUCTION_UPLOAD_KEY_ALIAS",
  "GCP_WORKLOAD_IDENTITY_PROVIDER",
  "GCP_DEPLOY_SERVICE_ACCOUNT",
];

const REQUIRED_SECRETS = [
  "PRODUCTION_GOOGLE_SERVICES_JSON_B64",
  "PRODUCTION_UPLOAD_KEYSTORE_B64",
  "PRODUCTION_UPLOAD_STORE_PASSWORD",
  "PRODUCTION_UPLOAD_KEY_PASSWORD",
];

const results = [];
const failures = new Set();

function hasValue(name) {
  return typeof process.env[name] === "string" && process.env[name].length > 0;
}

function record(name, status, fail = false) {
  results.push([name, status]);
  if (fail) failures.add(name);
}

function recordPresence(name) {
  const present = hasValue(name);
  record(name, present ? STATUS.PRESENT : STATUS.ABSENT, !present);
  return present;
}

function normalizeFingerprint(value) {
  return String(value ?? "")
    .replaceAll(":", "")
    .replaceAll(/\s/g, "")
    .toLowerCase();
}

function isSha1(value) {
  return /^[0-9a-f]{40}$/.test(normalizeFingerprint(value));
}

function isSha256(value) {
  return /^[0-9a-f]{64}$/.test(normalizeFingerprint(value));
}

function containsExactString(node, target) {
  if (typeof node === "string") return node === target;
  if (Array.isArray(node)) return node.some((value) => containsExactString(value, target));
  if (node && typeof node === "object") {
    return Object.values(node).some((value) => containsExactString(value, target));
  }
  return false;
}

function collectOauthClients(node, out = []) {
  if (Array.isArray(node)) {
    for (const value of node) collectOauthClients(value, out);
    return out;
  }
  if (!node || typeof node !== "object") return out;

  if (Object.hasOwn(node, "client_type") && typeof node.client_id === "string") {
    out.push(node);
  }
  for (const value of Object.values(node)) collectOauthClients(value, out);
  return out;
}

for (const name of REQUIRED_VARIABLES) recordPresence(name);
for (const name of REQUIRED_SECRETS) recordPresence(name);

const projectId = process.env.PRODUCTION_FIREBASE_PROJECT_ID ?? "";
record(
  "firebase_project_identity",
  projectId === EXPECTED_PROJECT_ID && projectId !== FORBIDDEN_STAGING_PROJECT_ID
    ? STATUS.PRESENT
    : STATUS.MISMATCH,
  projectId !== EXPECTED_PROJECT_ID || projectId === FORBIDDEN_STAGING_PROJECT_ID
);

const webClientId = process.env.PRODUCTION_GOOGLE_WEB_CLIENT_ID ?? "";
record(
  "web_oauth_client_id_shape",
  webClientId.endsWith(".apps.googleusercontent.com") ? STATUS.PRESENT : STATUS.MISMATCH,
  !webClientId.endsWith(".apps.googleusercontent.com")
);

const playSha1 = normalizeFingerprint(process.env.PRODUCTION_APP_SIGNING_CERT_SHA1);
const playSha256 = normalizeFingerprint(process.env.PRODUCTION_APP_SIGNING_CERT_SHA256);
record(
  "play_app_signing_sha1_format",
  isSha1(playSha1) ? STATUS.PRESENT : STATUS.MISMATCH,
  !isSha1(playSha1)
);
record(
  "play_app_signing_sha256_format",
  isSha256(playSha256) ? STATUS.PRESENT : STATUS.MISMATCH,
  !isSha256(playSha256)
);

const wifProvider = process.env.GCP_WORKLOAD_IDENTITY_PROVIDER ?? "";
const expectedWifPattern = new RegExp(
  `^projects/${EXPECTED_PROJECT_NUMBER}/locations/global/workloadIdentityPools/[^/]+/providers/[^/]+$`
);
record(
  "wif_provider_boundary",
  expectedWifPattern.test(wifProvider) ? STATUS.PRESENT : STATUS.MISMATCH,
  !expectedWifPattern.test(wifProvider)
);

const deployServiceAccount = process.env.GCP_DEPLOY_SERVICE_ACCOUNT ?? "";
record(
  "deploy_service_account_boundary",
  deployServiceAccount === EXPECTED_DEPLOY_SERVICE_ACCOUNT ? STATUS.PRESENT : STATUS.MISMATCH,
  deployServiceAccount !== EXPECTED_DEPLOY_SERVICE_ACCOUNT
);

const googleServicesConfigured = hasValue("PRODUCTION_GOOGLE_SERVICES_JSON_B64");
if (googleServicesConfigured) {
  const googleServicesPath = process.env.BLOCK3F_GOOGLE_CONFIG_PATH ?? "";
  let googleServices = null;

  if (!googleServicesPath || !fs.existsSync(googleServicesPath)) {
    record("google_services_temp_file", STATUS.MISMATCH, true);
  } else {
    record("google_services_temp_file", STATUS.PRESENT);
    try {
      googleServices = JSON.parse(fs.readFileSync(googleServicesPath, "utf8"));
      record("google_services_json_parse", STATUS.PRESENT);
    } catch {
      record("google_services_json_parse", STATUS.MISMATCH, true);
    }
  }

  if (googleServices) {
    const configProjectId = googleServices?.project_info?.project_id;
    record(
      "google_services_project",
      configProjectId === EXPECTED_PROJECT_ID ? STATUS.PRESENT : STATUS.MISMATCH,
      configProjectId !== EXPECTED_PROJECT_ID
    );

    const stagingReuse = containsExactString(googleServices, FORBIDDEN_STAGING_PROJECT_ID);
    record("google_services_staging_reuse", stagingReuse ? STATUS.MISMATCH : STATUS.PRESENT, stagingReuse);

    const clients = Array.isArray(googleServices?.client) ? googleServices.client : [];
    const packageClient = clients.find(
      (client) =>
        client?.client_info?.android_client_info?.package_name === EXPECTED_PACKAGE_NAME
    );
    record(
      "google_services_package",
      packageClient ? STATUS.PRESENT : STATUS.MISMATCH,
      !packageClient
    );

    if (packageClient) {
      const oauthClients = collectOauthClients(packageClient);
      const androidOauthMatch = oauthClients.some((client) => {
        if (Number(client.client_type) !== 1) return false;
        if (client?.android_info?.package_name !== EXPECTED_PACKAGE_NAME) return false;
        return normalizeFingerprint(client?.android_info?.certificate_hash) === playSha1;
      });
      record(
        "android_oauth_relationship",
        androidOauthMatch ? STATUS.PRESENT : STATUS.MISMATCH,
        !androidOauthMatch
      );

      const webOauthMatch = oauthClients.some(
        (client) => Number(client.client_type) === 3 && client.client_id === webClientId
      );
      record(
        "web_oauth_relationship",
        webOauthMatch ? STATUS.PRESENT : STATUS.MISMATCH,
        !webOauthMatch
      );
    } else {
      record("android_oauth_relationship", STATUS.MISMATCH, true);
      record("web_oauth_relationship", STATUS.MISMATCH, true);
    }
  }
} else {
  record("google_services_temp_file", STATUS.ABSENT, true);
  record("google_services_project", STATUS.ABSENT, true);
  record("google_services_package", STATUS.ABSENT, true);
  record("google_services_staging_reuse", STATUS.ABSENT, true);
  record("android_oauth_relationship", STATUS.ABSENT, true);
  record("web_oauth_relationship", STATUS.ABSENT, true);
}

const uploadKeystoreConfigured = hasValue("PRODUCTION_UPLOAD_KEYSTORE_B64");
const uploadSha1 = normalizeFingerprint(process.env.PRODUCTION_UPLOAD_CERT_SHA1);
const uploadSha256 = normalizeFingerprint(process.env.PRODUCTION_UPLOAD_CERT_SHA256);

if (uploadKeystoreConfigured) {
  const uploadKeystorePath = process.env.BLOCK3F_UPLOAD_KEYSTORE_PATH ?? "";
  const tempKeystorePresent = Boolean(uploadKeystorePath && fs.existsSync(uploadKeystorePath));
  record(
    "upload_keystore_temp_file",
    tempKeystorePresent ? STATUS.PRESENT : STATUS.MISMATCH,
    !tempKeystorePresent
  );

  record(
    "upload_certificate_sha1_format",
    isSha1(uploadSha1) ? STATUS.PRESENT : STATUS.MISMATCH,
    !isSha1(uploadSha1)
  );
  record(
    "upload_certificate_sha256_format",
    isSha256(uploadSha256) ? STATUS.PRESENT : STATUS.MISMATCH,
    !isSha256(uploadSha256)
  );

  const distinct =
    isSha1(uploadSha1) &&
    isSha256(uploadSha256) &&
    uploadSha1 !== playSha1 &&
    uploadSha256 !== playSha256;
  record(
    "upload_signing_certificate_distinct",
    distinct ? STATUS.PRESENT : STATUS.MISMATCH,
    !distinct
  );
} else {
  record("upload_keystore_temp_file", STATUS.ABSENT, true);
  record("upload_certificate_sha1_format", STATUS.ABSENT, true);
  record("upload_certificate_sha256_format", STATUS.ABSENT, true);
  record("upload_signing_certificate_distinct", STATUS.ABSENT, true);
}

// This helper intentionally performs no external network call and requests no OIDC token.
// If a future separately-authorized read-only Firebase/GCP check is added, a 403 or
// missing permission must be classified as UNKNOWN_NO_ACCESS; it must never trigger
// IAM mutation, role addition, API enablement, or service-account key creation.
void STATUS.UNKNOWN_NO_ACCESS;

for (const [name, status] of results) {
  console.log(`${name}=${status}`);
}

if (failures.size > 0) {
  console.log("BLOCK_3F_METADATA_PROBE=FAIL");
  process.exitCode = 1;
} else {
  console.log("BLOCK_3F_METADATA_PROBE=PASS");
}
