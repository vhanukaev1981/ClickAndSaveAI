import fs from "node:fs/promises";
import { createRequire } from "node:module";
import { pathToFileURL } from "node:url";

const STAGING_PROJECT_ID = "clickandsaveai-staging";
const FUNCTIONS_REGION = "europe-west1";
const SMOKE_APP_NAME = "clickandsaveai-staging-smoke";
const SMOKE_TOKEN_TTL_MILLIS = 30 * 60 * 1000;
const requireFromFunctions = createRequire(new URL("../functions/package.json", import.meta.url));

function finiteNumberOrNull(value) {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function integerOrNull(value) {
  return typeof value === "number" && Number.isInteger(value) ? value : null;
}

function safeArray(value) {
  return Array.isArray(value) ? value.map((item) => String(item)) : [];
}

export function sanitizeSmokeSummary({
  projectId,
  sourceSha,
  gmailResponse = {},
  scanResponse = {},
  financialHomeResponse = {},
}) {
  if (projectId !== STAGING_PROJECT_ID) {
    throw new Error("Staging smoke may target clickandsaveai-staging only.");
  }
  if (!/^[0-9a-f]{40}$/.test(String(sourceSha || ""))) {
    throw new Error("sourceSha must be an exact lowercase 40-character commit SHA.");
  }

  const context = financialHomeResponse?.context && typeof financialHomeResponse.context === "object"
    ? financialHomeResponse.context
    : {};

  return {
    projectId: STAGING_PROJECT_ID,
    sourceSha,
    gmail: {
      connected: gmailResponse?.connected === true,
      consentVersion: typeof gmailResponse?.consentVersion === "string"
        ? gmailResponse.consentVersion
        : "",
    },
    scan: {
      scannedMessages: integerOrNull(scanResponse?.scannedMessages),
      returnedInvoices: Array.isArray(scanResponse?.invoices) ? scanResponse.invoices.length : null,
      importedCount: integerOrNull(scanResponse?.importedCount),
      parserVersion: integerOrNull(scanResponse?.parserVersion),
      agentRefreshed: typeof scanResponse?.agentRefreshed === "boolean"
        ? scanResponse.agentRefreshed
        : null,
    },
    financialHome: {
      recurringServiceCount: integerOrNull(context?.recurringServiceCount),
      observedRecurringMonthlySpend: finiteNumberOrNull(context?.observedRecurringMonthlySpend),
      sourceCoverage: safeArray(context?.sourceCoverage),
      insightCount: Array.isArray(financialHomeResponse?.insights)
        ? financialHomeResponse.insights.length
        : null,
      opportunityCount: Array.isArray(financialHomeResponse?.opportunities)
        ? financialHomeResponse.opportunities.length
        : null,
    },
  };
}

async function parseJsonResponse(response, operationName) {
  const payload = await response.json().catch(() => null);
  if (!response.ok) {
    throw new Error(`${operationName} failed with HTTP ${response.status}.`);
  }
  if (!payload || typeof payload !== "object") {
    throw new Error(`${operationName} returned a non-JSON response.`);
  }
  if (payload.error) {
    const code = payload.error?.status || payload.error?.code || "callable-error";
    throw new Error(`${operationName} failed with ${code}.`);
  }
  if (Object.prototype.hasOwnProperty.call(payload, "result")) return payload.result;
  if (Object.prototype.hasOwnProperty.call(payload, "data")) return payload.data;
  throw new Error(`${operationName} returned no callable result.`);
}

async function defaultAdminProvider() {
  const { applicationDefault, getApps, initializeApp } = requireFromFunctions("firebase-admin/app");
  const { getAuth } = requireFromFunctions("firebase-admin/auth");
  const { getAppCheck } = requireFromFunctions("firebase-admin/app-check");
  return { applicationDefault, getApps, initializeApp, getAuth, getAppCheck };
}

async function exchangeFirebaseCustomToken({ apiKey, customToken, fetchImpl }) {
  const response = await fetchImpl(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=${encodeURIComponent(apiKey)}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        token: customToken,
        returnSecureToken: true,
      }),
    }
  );
  const payload = await response.json().catch(() => null);
  if (!response.ok || !payload?.idToken) {
    throw new Error(`Firebase custom-token exchange failed with HTTP ${response.status}.`);
  }
  return String(payload.idToken);
}

export async function mintSmokeTokensWithAdmin({
  uid,
  projectId = STAGING_PROJECT_ID,
  appId,
  apiKey,
  serviceAccountId,
  fetchImpl = fetch,
  adminProvider = defaultAdminProvider,
}) {
  if (projectId !== STAGING_PROJECT_ID) {
    throw new Error("Smoke token minting may target clickandsaveai-staging only.");
  }
  const required = [
    ["STAGING_SMOKE_USER_UID", uid],
    ["STAGING_FIREBASE_API_KEY", apiKey],
    ["STAGING_APPCHECK_APP_ID", appId],
    ["GCP_DEPLOY_SERVICE_ACCOUNT", serviceAccountId],
  ];
  const missing = required
    .filter(([, value]) => !String(value || "").trim())
    .map(([name]) => name);
  if (missing.length > 0) {
    throw new Error(`Short-lived staging smoke token inputs are missing: ${missing.join(", ")}.`);
  }

  const admin = await adminProvider();
  const existingApp = admin.getApps().find((app) => app.name === SMOKE_APP_NAME);
  const app = existingApp || admin.initializeApp(
    {
      credential: admin.applicationDefault(),
      projectId: STAGING_PROJECT_ID,
      serviceAccountId,
    },
    SMOKE_APP_NAME
  );

  const auth = admin.getAuth(app);
  const appCheck = admin.getAppCheck(app);
  const customAuthTokenPromise = auth.createCustomToken(String(uid));
  const appCheckTokenPromise = appCheck.createToken(String(appId), {
    ttlMillis: SMOKE_TOKEN_TTL_MILLIS,
  });
  const [customAuthToken, appCheckResult] = await Promise.all([
    customAuthTokenPromise,
    appCheckTokenPromise,
  ]);

  const idToken = await exchangeFirebaseCustomToken({
    apiKey,
    customToken: customAuthToken,
    fetchImpl,
  });
  if (!appCheckResult?.token) {
    throw new Error("Firebase Admin did not return an App Check token.");
  }

  return {
    idToken,
    appCheckToken: String(appCheckResult.token),
  };
}

export async function resolveSmokeTokens(env = process.env, fetchImpl = fetch) {
  const directIdToken = String(env.STAGING_FIREBASE_ID_TOKEN || "").trim();
  const directAppCheckToken = String(env.STAGING_FIREBASE_APPCHECK_TOKEN || "").trim();
  if (directIdToken && directAppCheckToken) {
    return { idToken: directIdToken, appCheckToken: directAppCheckToken };
  }

  return mintSmokeTokensWithAdmin({
    uid: String(env.STAGING_SMOKE_USER_UID || "").trim(),
    projectId: STAGING_PROJECT_ID,
    appId: String(env.STAGING_APPCHECK_APP_ID || "").trim(),
    apiKey: String(env.STAGING_FIREBASE_API_KEY || "").trim(),
    serviceAccountId: String(env.GCP_DEPLOY_SERVICE_ACCOUNT || "").trim(),
    fetchImpl,
  });
}

export async function callStagingCallable(
  functionName,
  { idToken, appCheckToken, fetchImpl = fetch }
) {
  if (!idToken || !appCheckToken) {
    throw new Error("Authenticated callable requires Firebase Auth and App Check tokens.");
  }
  const response = await fetchImpl(
    `https://${FUNCTIONS_REGION}-${STAGING_PROJECT_ID}.cloudfunctions.net/${encodeURIComponent(functionName)}`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${idToken}`,
        "X-Firebase-AppCheck": appCheckToken,
      },
      body: JSON.stringify({ data: {} }),
    }
  );
  return parseJsonResponse(response, functionName);
}

export async function runStagingCoreSmoke({
  sourceSha,
  env = process.env,
  fetchImpl = fetch,
}) {
  if (!/^[0-9a-f]{40}$/.test(String(sourceSha || ""))) {
    throw new Error("SOURCE_SHA must be an exact lowercase 40-character commit SHA.");
  }

  const { idToken, appCheckToken } = await resolveSmokeTokens(env, fetchImpl);
  const callableOptions = { idToken, appCheckToken, fetchImpl };

  const gmailResponse = await callStagingCallable("getGmailConnectionStatus", callableOptions);
  if (gmailResponse?.connected !== true) {
    throw new Error("The designated staging smoke account is not Gmail-connected.");
  }

  const scanResponse = await callStagingCallable("scanGmailInvoices", callableOptions);
  const financialHomeResponse = await callStagingCallable("getFinancialHome", callableOptions);

  return sanitizeSmokeSummary({
    projectId: STAGING_PROJECT_ID,
    sourceSha,
    gmailResponse,
    scanResponse,
    financialHomeResponse,
  });
}

async function main() {
  const sourceSha = String(process.env.SOURCE_SHA || "").trim();
  const outputPath = String(process.env.STAGING_SMOKE_OUTPUT || "staging-core-smoke.json").trim();
  const summary = await runStagingCoreSmoke({ sourceSha });
  await fs.writeFile(outputPath, `${JSON.stringify(summary, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
  process.stdout.write(`${JSON.stringify(summary)}\n`);
}

const invokedPath = process.argv[1] ? pathToFileURL(process.argv[1]).href : "";
if (import.meta.url === invokedPath) {
  main().catch((error) => {
    const message = error instanceof Error ? error.message : "Unknown staging smoke failure.";
    process.stderr.write(`STAGING_SMOKE_FAILED: ${message}\n`);
    process.exitCode = 1;
  });
}
