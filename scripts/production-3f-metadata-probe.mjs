#!/usr/bin/env node

import fs from "node:fs";

const STATUS = Object.freeze({
  CONFIGURED_PRESENT: "CONFIGURED_PRESENT",
  NOT_AVAILABLE_TO_JOB: "NOT_AVAILABLE_TO_JOB",
  CONFIGURED_VALID_FORMAT: "CONFIGURED_VALID_FORMAT",
  CONFIGURED_MATCH: "CONFIGURED_MATCH",
  CONFIGURED_MISMATCH: "CONFIGURED_MISMATCH",
  CERT_METADATA_VERIFIED: "CERT_METADATA_VERIFIED",
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
  record(
    name,
    present ? STATUS.CONFIGURED_PRESENT : STATUS.NOT_AVAILABLE_TO_JOB,
    !present
  );
  return present;
}

function recordUnavailable(name) {
  record(name, STATUS.NOT_AVAILABLE_TO_JOB, true);
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

const availability = new Map();
for (const name of REQUIRED_VARIABLES) availability.set(name, recordPresence(name));
for (const name of REQUIRED_SECRETS) availability.set(name, recordPresence(name));

const projectId = process.env.PRODUCTION_FIREBASE_PROJECT_ID ?? "";
if (availability.get("PRODUCTION_FIREBASE_PROJECT_ID")) {
  const projectMatches =
    projectId === EXPECTED_PROJECT_ID && projectId !== FORBIDDEN_STAGING_PROJECT_ID;
  record(
    "firebase_project_identity",
    projectMatches ? STATUS.CONFIGURED_MATCH : STATUS.CONFIGURED_MISMATCH,
    !projectMatches
  );
} else {
  recordUnavailable("firebase_project_identity");
}

const webClientId = process.env.PRODUCTION_GOOGLE_WEB_CLIENT_ID ?? "";
if (availability.get("PRODUCTION_GOOGLE_WEB_CLIENT_ID")) {
  const validWebClientFormat = webClientId.endsWith(".apps.googleusercontent.com");
  record(
    "web_oauth_client_id_format",
    validWebClientFormat ? STATUS.CONFIGURED_VALID_FORMAT : STATUS.CONFIGURED_MISMATCH,
    !validWebClientFormat
  );
} else {
  recordUnavailable("web_oauth_client_id_format");
}

const playSha1 = normalizeFingerprint(process.env.PRODUCTION_APP_SIGNING_CERT_SHA1);
const playSha256 = normalizeFingerprint(process.env.PRODUCTION_APP_SIGNING_CERT_SHA256);
if (availability.get("PRODUCTION_APP_SIGNING_CERT_SHA1")) {
  record(
    "play_app_signing_sha1_format",
    isSha1(playSha1) ? STATUS.CONFIGURED_VALID_FORMAT : STATUS.CONFIGURED_MISMATCH,
    !isSha1(playSha1)
  );
} else {
  recordUnavailable("play_app_signing_sha1_format");
}
if (availability.get("PRODUCTION_APP_SIGNING_CERT_SHA256")) {
  record(
    "play_app_signing_sha256_format",
    isSha256(playSha256) ? STATUS.CONFIGURED_VALID_FORMAT : STATUS.CONFIGURED_MISMATCH,
    !isSha256(playSha256)
  );
} else {
  recordUnavailable("play_app_signing_sha256_format");
}
record("play_app_signing_authoritative_identity", STATUS.UNKNOWN_NO_ACCESS);

const wifProvider = process.env.GCP_WORKLOAD_IDENTITY_PROVIDER ?? "";
const expectedWifPattern = new RegExp(
  `^projects/${EXPECTED_PROJECT_NUMBER}/locations/global/workloadIdentityPools/[^/]+/providers/[^/]+$`
);
if (availability.get("GCP_WORKLOAD_IDENTITY_PROVIDER")) {
  const wifConfiguredMatch = expectedWifPattern.test(wifProvider);
  record(
    "wif_provider_configured_boundary",
    wifConfiguredMatch ? STATUS.CONFIGURED_MATCH : STATUS.CONFIGURED_MISMATCH,
    !wifConfiguredMatch
  );
} else {
  recordUnavailable("wif_provider_configured_boundary");
}

const deployServiceAccount = process.env.GCP_DEPLOY_SERVICE_ACCOUNT ?? "";
if (availability.get("GCP_DEPLOY_SERVICE_ACCOUNT")) {
  const deploySaConfiguredMatch = deployServiceAccount === EXPECTED_DEPLOY_SERVICE_ACCOUNT;
  record(
    "deploy_service_account_configured_boundary",
    deploySaConfiguredMatch ? STATUS.CONFIGURED_MATCH : STATUS.CONFIGURED_MISMATCH,
    !deploySaConfiguredMatch
  );
} else {
  recordUnavailable("deploy_service_account_configured_boundary");
}

const googleServicesConfigured = availability.get("PRODUCTION_GOOGLE_SERVICES_JSON_B64") === true;
if (googleServicesConfigured) {
  const googleServicesPath = process.env.BLOCK3F_GOOGLE_CONFIG_PATH ?? "";
  let googleServices = null;

  if (!googleServicesPath || !fs.existsSync(googleServicesPath)) {
    record("google_services_temp_file", STATUS.CONFIGURED_MISMATCH, true);
  } else {
    record("google_services_temp_file", STATUS.CONFIGURED_PRESENT);
    try {
      googleServices = JSON.parse(fs.readFileSync(googleServicesPath, "utf8"));
      record("google_services_json_format", STATUS.CONFIGURED_VALID_FORMAT);
    } catch {
      record("google_services_json_format", STATUS.CONFIGURED_MISMATCH, true);
    }
  }

  if (googleServices) {
    const configProjectId = googleServices?.project_info?.project_id;
    record(
      "google_services_project",
      configProjectId === EXPECTED_PROJECT_ID
        ? STATUS.CONFIGURED_MATCH
        : STATUS.CONFIGURED_MISMATCH,
      configProjectId !== EXPECTED_PROJECT_ID
    );

    const stagingReuse = containsExactString(googleServices, FORBIDDEN_STAGING_PROJECT_ID);
    record(
      "google_services_staging_reuse",
      stagingReuse ? STATUS.CONFIGURED_MISMATCH : STATUS.CONFIGURED_MATCH,
      stagingReuse
    );

    const clients = Array.isArray(googleServices?.client) ? googleServices.client : [];
    const packageClient = clients.find(
      (client) =>
        client?.client_info?.android_client_info?.package_name === EXPECTED_PACKAGE_NAME
    );
    record(
      "google_services_package",
      packageClient ? STATUS.CONFIGURED_MATCH : STATUS.CONFIGURED_MISMATCH,
      !packageClient
    );

    if (packageClient) {
      const oauthClients = collectOauthClients(packageClient);
      if (availability.get("PRODUCTION_APP_SIGNING_CERT_SHA1")) {
        const androidOauthMatch =
          isSha1(playSha1) &&
          oauthClients.some((client) => {
            if (Number(client.client_type) !== 1) return false;
            if (client?.android_info?.package_name !== EXPECTED_PACKAGE_NAME) return false;
            return normalizeFingerprint(client?.android_info?.certificate_hash) === playSha1;
          });
        record(
          "android_oauth_relationship",
          androidOauthMatch ? STATUS.CONFIGURED_MATCH : STATUS.CONFIGURED_MISMATCH,
          !androidOauthMatch
        );
      } else {
        recordUnavailable("android_oauth_relationship");
      }

      if (availability.get("PRODUCTION_GOOGLE_WEB_CLIENT_ID")) {
        const webOauthMatch =
          webClientId.endsWith(".apps.googleusercontent.com") &&
          oauthClients.some(
            (client) => Number(client.client_type) === 3 && client.client_id === webClientId
          );
        record(
          "web_oauth_relationship",
          webOauthMatch ? STATUS.CONFIGURED_MATCH : STATUS.CONFIGURED_MISMATCH,
          !webOauthMatch
        );
      } else {
        recordUnavailable("web_oauth_relationship");
      }
    } else {
      record("android_oauth_relationship", STATUS.CONFIGURED_MISMATCH, true);
      record("web_oauth_relationship", STATUS.CONFIGURED_MISMATCH, true);
    }
  } else {
    record("google_services_project", STATUS.CONFIGURED_MISMATCH, true);
    record("google_services_package", STATUS.CONFIGURED_MISMATCH, true);
    record("google_services_staging_reuse", STATUS.CONFIGURED_MISMATCH, true);
    record("android_oauth_relationship", STATUS.CONFIGURED_MISMATCH, true);
    record("web_oauth_relationship", STATUS.CONFIGURED_MISMATCH, true);
  }
} else {
  for (const name of [
    "google_services_temp_file",
    "google_services_json_format",
    "google_services_project",
    "google_services_package",
    "google_services_staging_reuse",
    "android_oauth_relationship",
    "web_oauth_relationship",
  ]) {
    recordUnavailable(name);
  }
}
record("firebase_android_app_external_authority", STATUS.UNKNOWN_NO_ACCESS);
record("oauth_external_authority", STATUS.UNKNOWN_NO_ACCESS);

const uploadKeystoreConfigured = availability.get("PRODUCTION_UPLOAD_KEYSTORE_B64") === true;
const uploadSha1Available = hasValue("PRODUCTION_UPLOAD_CERT_SHA1");
const uploadSha256Available = hasValue("PRODUCTION_UPLOAD_CERT_SHA256");
const uploadSha1 = normalizeFingerprint(process.env.PRODUCTION_UPLOAD_CERT_SHA1);
const uploadSha256 = normalizeFingerprint(process.env.PRODUCTION_UPLOAD_CERT_SHA256);

if (uploadKeystoreConfigured) {
  const uploadKeystorePath = process.env.BLOCK3F_UPLOAD_KEYSTORE_PATH ?? "";
  const tempKeystorePresent = Boolean(uploadKeystorePath && fs.existsSync(uploadKeystorePath));
  record(
    "upload_keystore_temp_file",
    tempKeystorePresent ? STATUS.CONFIGURED_PRESENT : STATUS.CONFIGURED_MISMATCH,
    !tempKeystorePresent
  );

  const uploadSha1Verified = uploadSha1Available && isSha1(uploadSha1);
  const uploadSha256Verified = uploadSha256Available && isSha256(uploadSha256);

  record(
    "upload_key_alias_metadata",
    tempKeystorePresent && uploadSha1Verified && uploadSha256Verified
      ? STATUS.CERT_METADATA_VERIFIED
      : uploadSha1Available || uploadSha256Available
        ? STATUS.CONFIGURED_MISMATCH
        : STATUS.NOT_AVAILABLE_TO_JOB,
    !(tempKeystorePresent && uploadSha1Verified && uploadSha256Verified)
  );
  record(
    "upload_certificate_sha1_metadata",
    uploadSha1Verified
      ? STATUS.CERT_METADATA_VERIFIED
      : uploadSha1Available
        ? STATUS.CONFIGURED_MISMATCH
        : STATUS.NOT_AVAILABLE_TO_JOB,
    !uploadSha1Verified
  );
  record(
    "upload_certificate_sha256_metadata",
    uploadSha256Verified
      ? STATUS.CERT_METADATA_VERIFIED
      : uploadSha256Available
        ? STATUS.CONFIGURED_MISMATCH
        : STATUS.NOT_AVAILABLE_TO_JOB,
    !uploadSha256Verified
  );

  if (
    uploadSha1Verified &&
    uploadSha256Verified &&
    availability.get("PRODUCTION_APP_SIGNING_CERT_SHA1") &&
    availability.get("PRODUCTION_APP_SIGNING_CERT_SHA256")
  ) {
    const distinct =
      isSha1(playSha1) &&
      isSha256(playSha256) &&
      uploadSha1 !== playSha1 &&
      uploadSha256 !== playSha256;
    record(
      "upload_signing_certificate_distinct",
      distinct ? STATUS.CONFIGURED_MATCH : STATUS.CONFIGURED_MISMATCH,
      !distinct
    );
  } else if (
    !availability.get("PRODUCTION_APP_SIGNING_CERT_SHA1") ||
    !availability.get("PRODUCTION_APP_SIGNING_CERT_SHA256")
  ) {
    recordUnavailable("upload_signing_certificate_distinct");
  } else {
    record("upload_signing_certificate_distinct", STATUS.CONFIGURED_MISMATCH, true);
  }
} else {
  for (const name of [
    "upload_keystore_temp_file",
    "upload_key_alias_metadata",
    "upload_certificate_sha1_metadata",
    "upload_certificate_sha256_metadata",
    "upload_signing_certificate_distinct",
  ]) {
    recordUnavailable(name);
  }
}

// This helper intentionally performs no external network call and requests no OIDC token.
// Configured metadata may establish only configured-state relationships. Authoritative
// Play, Firebase registration, and Google Cloud OAuth inventory remain UNKNOWN_NO_ACCESS
// until a separately-authorized authenticated external read actually proves them.

for (const [name, status] of results) {
  console.log(`${name}=${status}`);
}

if (failures.size > 0) {
  console.log("BLOCK_3F_METADATA_PROBE=FAIL");
  process.exitCode = 1;
} else {
  console.log("BLOCK_3F_METADATA_PROBE=PASS");
}
