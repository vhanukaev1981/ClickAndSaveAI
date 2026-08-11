import fs from "node:fs/promises";
import { pathToFileURL } from "node:url";

const STAGING_PROJECT_ID = "clickandsaveai-staging";
const FUNCTIONS_REGION = "europe-west1";

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

async function exchangeFirebaseRefreshToken({ apiKey, refreshToken, fetchImpl }) {
  const response = await fetchImpl(
    `https://securetoken.googleapis.com/v1/token?key=${encodeURIComponent(apiKey)}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "refresh_token",
        refresh_token: refreshToken,
      }),
    }
  );
  const payload = await response.json().catch(() => null);
  if (!response.ok || !payload?.id_token) {
    throw new Error(`Firebase test-user token refresh failed with HTTP ${response.status}.`);
  }
  return String(payload.id_token);
}

async function exchangeAppCheckDebugToken({ projectId, appId, apiKey, debugToken, fetchImpl }) {
  const response = await fetchImpl(
    `https://firebaseappcheck.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/apps/${encodeURIComponent(appId)}:exchangeDebugToken?key=${encodeURIComponent(apiKey)}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ debugToken }),
    }
  );
  const payload = await response.json().catch(() => null);
  if (!response.ok || !payload?.token) {
    throw new Error(`App Check debug-token exchange failed with HTTP ${response.status}.`);
  }
  return String(payload.token);
}

export async function resolveSmokeTokens(env = process.env, fetchImpl = fetch) {
  const directIdToken = String(env.STAGING_FIREBASE_ID_TOKEN || "").trim();
  const directAppCheckToken = String(env.STAGING_FIREBASE_APPCHECK_TOKEN || "").trim();
  if (directIdToken && directAppCheckToken) {
    return { idToken: directIdToken, appCheckToken: directAppCheckToken };
  }

  const apiKey = String(env.STAGING_FIREBASE_API_KEY || "").trim();
  const refreshToken = String(env.STAGING_TEST_FIREBASE_REFRESH_TOKEN || "").trim();
  const appId = String(env.STAGING_APPCHECK_APP_ID || "").trim();
  const debugToken = String(env.STAGING_APPCHECK_DEBUG_TOKEN || "").trim();
  const missing = [
    ["STAGING_FIREBASE_API_KEY", apiKey],
    ["STAGING_TEST_FIREBASE_REFRESH_TOKEN", refreshToken],
    ["STAGING_APPCHECK_APP_ID", appId],
    ["STAGING_APPCHECK_DEBUG_TOKEN", debugToken],
  ].filter(([, value]) => !value).map(([name]) => name);

  if (missing.length > 0) {
    throw new Error(
      `Authenticated staging smoke credentials are not configured. Missing: ${missing.join(", ")}.`
    );
  }

  const [idToken, appCheckToken] = await Promise.all([
    exchangeFirebaseRefreshToken({ apiKey, refreshToken, fetchImpl }),
    exchangeAppCheckDebugToken({
      projectId: STAGING_PROJECT_ID,
      appId,
      apiKey,
      debugToken,
      fetchImpl,
    }),
  ]);
  return { idToken, appCheckToken };
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
