import fs from "node:fs";

const mode = process.argv[2] || "repository";
const APP_ID = "com.aistudio.clickandsaveai.app";
const STAGING_PROJECT = "clickandsaveai-staging";
const STAGING_WEB_CLIENT = "716864421960-hnt5709tqk9qp79si8ggplf5jif1ulfu.apps.googleusercontent.com";

function requireCondition(condition, message) {
  if (!condition) throw new Error(message);
}

function read(path) {
  return fs.readFileSync(path, "utf8");
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
  requireCondition(rules.includes("allow read, write: if false;"), "Firestore deny-by-default rule was weakened");
  requireCondition(!workflow.includes(STAGING_WEB_CLIENT), "Staging OAuth client leaked into production workflow");
  requireCondition(!workflow.includes(`--project ${STAGING_PROJECT}`), "Production workflow targets staging");
  console.log("Repository production-isolation guard PASS");
}

function materializedChecks() {
  repositoryChecks();
  const projectId = String(process.env.PRODUCTION_FIREBASE_PROJECT_ID || "").trim();
  const webClientId = String(process.env.PRODUCTION_GOOGLE_WEB_CLIENT_ID || "").trim();
  const configPath = String(process.env.PRODUCTION_GOOGLE_SERVICES_JSON_PATH || "").trim();

  requireCondition(projectId && projectId !== STAGING_PROJECT, "Production Firebase project must be explicit and distinct from staging");
  requireCondition(webClientId && webClientId !== STAGING_WEB_CLIENT, "Production Web OAuth client must be explicit and distinct from staging");
  requireCondition(/^[0-9]+-[A-Za-z0-9_-]+\.apps\.googleusercontent\.com$/.test(webClientId), "Production Web OAuth client ID format is invalid");
  requireCondition(configPath && fs.existsSync(configPath), "Production google-services configuration is missing");

  const config = JSON.parse(read(configPath));
  requireCondition(config?.project_info?.project_id === projectId, "Production google-services project_id mismatch");
  const clients = Array.isArray(config.client) ? config.client : [];
  const androidClient = clients.find((client) => client?.client_info?.android_client_info?.package_name === APP_ID);
  requireCondition(Boolean(androidClient), `Production google-services configuration does not contain ${APP_ID}`);
  requireCondition(!read(configPath).includes(STAGING_PROJECT), "Staging project identifier leaked into production google-services configuration");
  requireCondition(!read(configPath).includes(STAGING_WEB_CLIENT), "Staging OAuth client leaked into production google-services configuration");
  console.log("Materialized production configuration guard PASS");
}

if (mode === "repository") repositoryChecks();
else if (mode === "materialized") materializedChecks();
else throw new Error("Usage: node scripts/production-readiness-guard.mjs <repository|materialized>");
