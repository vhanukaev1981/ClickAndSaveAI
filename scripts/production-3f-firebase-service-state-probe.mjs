import { pathToFileURL } from "node:url";

const CANONICAL_PROJECT_ID = "click-save-ai-production";
const CANONICAL_PROJECT_NUMBER = "991489557172";
const CANONICAL_SERVICE = "firebase.googleapis.com";
const SERVICE_USAGE_BASE = "https://serviceusage.googleapis.com/v1";
const UNKNOWN = "UNKNOWN_NO_ACCESS";

const TRANSPORT = Object.freeze({
  HTTP_401: "HTTP_401",
  HTTP_403: "HTTP_403",
  HTTP_404: "HTTP_404",
  HTTP_429: "HTTP_429",
  HTTP_5XX: "HTTP_5XX",
  HTTP_OTHER_NON_2XX: "HTTP_OTHER_NON_2XX",
  NETWORK_ERROR: "NETWORK_ERROR",
  INVALID_JSON: "INVALID_JSON",
  SUCCESS_2XX: "SUCCESS_2XX",
});

function outputLines(state = UNKNOWN, transport = TRANSPORT.NETWORK_ERROR) {
  return [
    `firebase_management_api_service_state=${state}`,
    `firebase_management_api_service_read_transport=${transport}`,
  ];
}

function resultFor(exitCode, state = UNKNOWN, transport = TRANSPORT.NETWORK_ERROR) {
  return { exitCode, lines: outputLines(state, transport) };
}

function classifyHttpStatus(status) {
  if (status === 401) return TRANSPORT.HTTP_401;
  if (status === 403) return TRANSPORT.HTTP_403;
  if (status === 404) return TRANSPORT.HTTP_404;
  if (status === 429) return TRANSPORT.HTTP_429;
  if (status >= 500 && status <= 599) return TRANSPORT.HTTP_5XX;
  return TRANSPORT.HTTP_OTHER_NON_2XX;
}

function validateExpectedInputs(expectedProjectId, expectedProjectNumber) {
  return (
    expectedProjectId === CANONICAL_PROJECT_ID &&
    expectedProjectNumber === CANONICAL_PROJECT_NUMBER
  );
}

export async function probeFirebaseServiceState({
  accessToken,
  expectedProjectId,
  expectedProjectNumber,
  fetchImpl = globalThis.fetch,
}) {
  if (
    typeof accessToken !== "string" ||
    accessToken.length === 0 ||
    typeof fetchImpl !== "function" ||
    !validateExpectedInputs(expectedProjectId, expectedProjectNumber)
  ) {
    return resultFor(1);
  }

  const expectedName = `projects/${CANONICAL_PROJECT_NUMBER}/services/${CANONICAL_SERVICE}`;
  const url = `${SERVICE_USAGE_BASE}/${expectedName}`;

  let response;
  try {
    response = await fetchImpl(url, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        Accept: "application/json",
      },
    });
  } catch {
    return resultFor(0, UNKNOWN, TRANSPORT.NETWORK_ERROR);
  }

  if (
    !response ||
    typeof response.ok !== "boolean" ||
    !Number.isInteger(response.status)
  ) {
    return resultFor(0, UNKNOWN, TRANSPORT.INVALID_JSON);
  }

  if (!response.ok) {
    return resultFor(0, UNKNOWN, classifyHttpStatus(response.status));
  }

  let body;
  try {
    body = await response.json();
  } catch {
    return resultFor(0, UNKNOWN, TRANSPORT.INVALID_JSON);
  }

  if (!body || typeof body !== "object" || body.name !== expectedName) {
    return resultFor(1, UNKNOWN, TRANSPORT.SUCCESS_2XX);
  }

  if (body.state === "ENABLED") {
    return resultFor(0, "ENABLED", TRANSPORT.SUCCESS_2XX);
  }
  if (body.state === "DISABLED") {
    return resultFor(0, "DISABLED", TRANSPORT.SUCCESS_2XX);
  }

  return resultFor(1, UNKNOWN, TRANSPORT.SUCCESS_2XX);
}

async function runCli() {
  const result = await probeFirebaseServiceState({
    accessToken: process.env.BLOCK3F_SERVICE_USAGE_ACCESS_TOKEN ?? "",
    expectedProjectId: process.env.EXPECTED_PROJECT_ID ?? "",
    expectedProjectNumber: process.env.EXPECTED_PROJECT_NUMBER ?? "",
  });
  for (const line of result.lines) console.log(line);
  process.exitCode = result.exitCode;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  await runCli();
}
