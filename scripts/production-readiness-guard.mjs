import fs from "node:fs";

const mode = process.argv[2] || "repository";
const APP_ID = "com.aistudio.clickandsaveai.app";
const STAGING_PROJECT = "clickandsaveai-staging";
const STAGING_WEB_CLIENT = "716864421960-hnt5709tqk9qp79si8ggplf5jif1ulfu.apps.googleusercontent.com";

// Public certificate fingerprints observed from the protected production surfaces.
// Keep legacy identities during signing transitions; Google Play can legitimately
// serve different app-signing identities across Android generations/key upgrades.
const KNOWN_PLAY_SIGNING_IDENTITIES = [
  {
    name: "legacy-production",
    sha1: "1D127D3BB3DB8E7319DDA55F437485F455B44D8D",
    sha256: "42BF0D29B4C1C15D055F0FA89B078CF55D5C0FFBABB95DBCE9581EC03BF939D3",
  },
  {
    name: "play-classic-current",
    sha1: "23F46B7A8332A17B7541B483A3685AD5BA17D37F",
    sha256: "44D5E1A00B2893370BA12CAD7025DF44531B55527287EB6BD5B3123336801FD3",
  },
];

function requireCondition(condition, message) {
  if (!condition) throw new Error(message);
}

function read(path) {
  return fs.readFileSync(path, "utf8");
}

function normalizeFingerprint(value) {
  return String(value || "").trim().replaceAll(":", "").toUpperCase();
}

function repositoryChecks() {
  const gradle = read("app/build.gradle.kts");
  const mainStrings = read("app/src/main/res/values/strings.xml");
  const debugStrings = read("app/src/debug/res/values/strings.xml");
  const rules = read("firestore.rules");
  const workflow = read(".github/workflows/production-release.yml");

  requireCondition(gradle.includes(`applicationId = "${APP_ID}"`), "Canonical production applicationId changed");
  requireCondition(gradle.includes("PRODUCTION_RELEASE_CANDIDATE"), "Production release fail-closed switch is missing");
  requireCondition(gradle.includes("PRODUCTION_UPLOAD_KEYSTORE_PATH"), "Production upload signing path is missing");
  requireCondition(gradle.includes("PRODUCTION_GOOGLE_WEB_CLIENT_ID"), "Production OAuth resource injection is missing");
  requireCondition(!mainStrings.includes(STAGING_WEB_CLIENT), "Staging OAuth client leaked into main resources");
  requireCondition(debugStrings.includes(STAGING_WEB_CLIENT), "Staging OAuth client is not isolated to debug resources");
  requireCondition(!workflow.includes("\npull_request:"), "Production workflow must not run from pull requests");
  requireCondition(!workflow.includes("\npush:"), "Production workflow must not run from pushes");
  requireCondition(workflow.includes("workflow_dispatch:"), "Production workflow must require explicit dispatch");
  requireCondition(workflow.includes("environment: production"), "Production workflow must use protected production environment");
  requireCondition(workflow.includes("DEPLOY_FIREBASE_PRODUCTION"), "Production deploy must require explicit authorization phrase");
  requireCondition(workflow.includes("PRODUCTION_APP_SIGNING_CERT_SHA1"), "Production workflow must require the Play app-signing SHA-1 identity");
  requireCondition(rules.includes("allow read, write: if false;"), "Firestore deny-by-default rule was weakened");
  requireCondition(!workflow.includes(STAGING_WEB_CLIENT), "Staging OAuth client leaked into production workflow");
  requireCondition(!workflow.includes(`--project ${STAGING_PROJECT}`), "Production workflow targets staging");

  for (const identity of KNOWN_PLAY_SIGNING_IDENTITIES) {
    requireCondition(/^[0-9A-F]{40}$/.test(identity.sha1), `Invalid known Play SHA-1 for ${identity.name}`);
    requireCondition(/^[0-9A-F]{64}$/.test(identity.sha256), `Invalid known Play SHA-256 for ${identity.name}`);
  }

  console.log("Repository production-isolation guard PASS");
}

function materializedChecks() {
  repositoryChecks();
  const projectId = String(process.env.PRODUCTION_FIREBASE_PROJECT_ID || "").trim();
  const webClientId = String(process.env.PRODUCTION_GOOGLE_WEB_CLIENT_ID || "").trim();
  const configuredPlaySigningSha1 = normalizeFingerprint(process.env.PRODUCTION_APP_SIGNING_CERT_SHA1);
  const configPath = String(process.env.PRODUCTION_GOOGLE_SERVICES_JSON_PATH || "").trim();

  requireCondition(projectId && projectId !== STAGING_PROJECT, "Production Firebase project must be explicit and distinct from staging");
  requireCondition(webClientId && webClientId !== STAGING_WEB_CLIENT, "Production Web OAuth client must be explicit and distinct from staging");
  requireCondition(/^[0-9]+-[A-Za-z0-9_-]+\.apps\.googleusercontent\.com$/.test(webClientId), "Production Web OAuth client ID format is invalid");
  requireCondition(/^[0-9A-F]{40}$/.test(configuredPlaySigningSha1), "PRODUCTION_APP_SIGNING_CERT_SHA1 must be a SHA-1 certificate fingerprint");
  requireCondition(configPath && fs.existsSync(configPath), "Production google-services configuration is missing");

  const configText = read(configPath);
  const config = JSON.parse(configText);
  requireCondition(config?.project_info?.project_id === projectId, "Production google-services project_id mismatch");
  const clients = Array.isArray(config.client) ? config.client : [];
  const firebaseAndroidClient = clients.find((client) => client?.client_info?.android_client_info?.package_name === APP_ID);
  requireCondition(Boolean(firebaseAndroidClient), `Production google-services configuration does not contain ${APP_ID}`);

  const oauthClients = clients.flatMap((client) => Array.isArray(client?.oauth_client) ? client.oauth_client : []);
  const configuredAndroidOauthClient = oauthClients.find((client) =>
    Number(client?.client_type) === 1 &&
    client?.android_info?.package_name === APP_ID &&
    normalizeFingerprint(client?.android_info?.certificate_hash) === configuredPlaySigningSha1
  );
  requireCondition(Boolean(configuredAndroidOauthClient), `Production Android OAuth client for configured ${APP_ID} signing SHA-1 is missing`);

  const knownPlayOauthClients = KNOWN_PLAY_SIGNING_IDENTITIES.filter((identity) =>
    oauthClients.some((client) =>
      Number(client?.client_type) === 1 &&
      client?.android_info?.package_name === APP_ID &&
      normalizeFingerprint(client?.android_info?.certificate_hash) === identity.sha1
    )
  );
  requireCondition(
    knownPlayOauthClients.length > 0,
    "Production Android OAuth client for a known Play signing identity is missing"
  );

  const webOauthClient = oauthClients.find((client) =>
    Number(client?.client_type) === 3 && String(client?.client_id || "").trim() === webClientId
  );
  requireCondition(Boolean(webOauthClient), "Production Web OAuth client is missing from google-services configuration");

  requireCondition(!configText.includes(STAGING_PROJECT), "Staging project identifier leaked into production google-services configuration");
  requireCondition(!configText.includes(STAGING_WEB_CLIENT), "Staging OAuth client leaked into production google-services configuration");
  console.log(`Materialized production configuration guard PASS; recognized Play OAuth identities: ${knownPlayOauthClients.map((identity) => identity.name).join(", ")}`);
}

if (mode === "repository") repositoryChecks();
else if (mode === "materialized") materializedChecks();
else throw new Error("Usage: node scripts/production-readiness-guard.mjs <repository|materialized>");
