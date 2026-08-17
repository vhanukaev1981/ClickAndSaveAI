import { pathToFileURL } from "node:url";

const CANONICAL_PROJECT_ID = "click-save-ai-production";
const CANONICAL_PROJECT_NUMBER = "991489557172";
const RESOURCE_MANAGER_BASE = "https://cloudresourcemanager.googleapis.com/v1";
const REQUESTED_PERMISSIONS = Object.freeze([
  "firebase.projects.get",
  "firebase.clients.list",
  "firebase.clients.get",
]);

const OUTPUT_KEYS = Object.freeze({
  "firebase.projects.get": "firebase_iam_permission_firebase_projects_get",
  "firebase.clients.list": "firebase_iam_permission_firebase_clients_list",
  "firebase.clients.get": "firebase_iam_permission_firebase_clients_get",
});

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
  NOT_ATTEMPTED_GUARD_FAILURE: "NOT_ATTEMPTED_GUARD_FAILURE",
});

function transportLine(transport) {
  return `firebase_iam_permission_test_transport=${transport}`;
}

function transportOnly(exitCode, transport) {
  return { exitCode, lines: [transportLine(transport)] };
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

function validateGrantedPermissions(body) {
  if (!body || typeof body !== "object" || Array.isArray(body)) return null;
  const permissions = body.permissions ?? [];
  if (!Array.isArray(permissions)) return null;

  const allowed = new Set(REQUESTED_PERMISSIONS);
  const seen = new Set();
  for (const permission of permissions) {
    if (
      typeof permission !== "string" ||
      !allowed.has(permission) ||
      seen.has(permission)
    ) {
      return null;
    }
    seen.add(permission);
  }
  return seen;
}

function permissionLines(granted) {
  const lines = REQUESTED_PERMISSIONS.map((permission) => {
    const status = granted.has(permission) ? "PRESENT" : "ABSENT";
    return `${OUTPUT_KEYS[permission]}=${status}`;
  });
  lines.push(transportLine(TRANSPORT.SUCCESS_2XX));
  return lines;
}

export async function probeFirebaseIamPermissions({
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
    return transportOnly(1, TRANSPORT.NOT_ATTEMPTED_GUARD_FAILURE);
  }

  const url = `${RESOURCE_MANAGER_BASE}/projects/${encodeURIComponent(
    expectedProjectId
  )}:testIamPermissions`;

  let response;
  try {
    response = await fetchImpl(url, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ permissions: [...REQUESTED_PERMISSIONS] }),
    });
  } catch {
    return transportOnly(0, TRANSPORT.NETWORK_ERROR);
  }

  if (
    !response ||
    typeof response.ok !== "boolean" ||
    !Number.isInteger(response.status)
  ) {
    return transportOnly(0, TRANSPORT.INVALID_JSON);
  }

  if (!response.ok) {
    return transportOnly(0, classifyHttpStatus(response.status));
  }

  let body;
  try {
    body = await response.json();
  } catch {
    return transportOnly(0, TRANSPORT.INVALID_JSON);
  }

  const granted = validateGrantedPermissions(body);
  if (!granted) {
    return transportOnly(1, TRANSPORT.INVALID_JSON);
  }

  return { exitCode: 0, lines: permissionLines(granted) };
}

async function runCli() {
  const result = await probeFirebaseIamPermissions({
    accessToken: process.env.BLOCK3F_IAM_TEST_ACCESS_TOKEN ?? "",
    expectedProjectId: process.env.EXPECTED_PROJECT_ID ?? "",
    expectedProjectNumber: process.env.EXPECTED_PROJECT_NUMBER ?? "",
  });
  for (const line of result.lines) console.log(line);
  process.exitCode = result.exitCode;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  await runCli();
}
