import { pathToFileURL } from "node:url";

const CANONICAL_PROJECT_ID = "click-save-ai-production";
const CANONICAL_PROJECT_NUMBER = "991489557172";
const RESOURCE_MANAGER_BASE = "https://cloudresourcemanager.googleapis.com/v1";
const CLOUD_RUN_BASE = "https://run.googleapis.com/v2";
const ARTIFACT_REGISTRY_BASE = "https://artifactregistry.googleapis.com/v1";

const PROJECT_PERMISSIONS = Object.freeze([
  "iam.roles.create",
  "iam.roles.get",
  "resourcemanager.projects.getIamPolicy",
  "resourcemanager.projects.setIamPolicy",
]);
const RUN_PERMISSIONS = Object.freeze(["run.services.setIamPolicy"]);
const ARTIFACT_PERMISSIONS = Object.freeze([
  "artifactregistry.repositories.get",
  "artifactregistry.repositories.update",
]);
const CLOUD_RUN_SERVICES = Object.freeze([
  "financialagentsweep",
  "gmailincrementalreconciliation",
  "renewgmailwatches",
]);
const ARTIFACT_REGIONS = Object.freeze(["europe-west1", "us-central1"]);
const ARTIFACT_REPOSITORY = "gcf-artifacts";

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

function classifyHttpStatus(status) {
  if (status === 401) return TRANSPORT.HTTP_401;
  if (status === 403) return TRANSPORT.HTTP_403;
  if (status === 404) return TRANSPORT.HTTP_404;
  if (status === 429) return TRANSPORT.HTTP_429;
  if (status >= 500 && status <= 599) return TRANSPORT.HTTP_5XX;
  return TRANSPORT.HTTP_OTHER_NON_2XX;
}

function outputKey(permission) {
  return `bootstrap_permission_${permission.replaceAll(".", "_")}`;
}

function unknownResult(permissions, transport, exitCode = 0) {
  return {
    exitCode,
    transport,
    states: new Map(permissions.map((permission) => [permission, "UNKNOWN_NO_ACCESS"])),
  };
}

function validateGrantedPermissions(body, requested) {
  if (!body || typeof body !== "object" || Array.isArray(body)) return null;
  const permissions = body.permissions ?? [];
  if (!Array.isArray(permissions)) return null;
  const allowed = new Set(requested);
  const seen = new Set();
  for (const permission of permissions) {
    if (typeof permission !== "string" || !allowed.has(permission) || seen.has(permission)) return null;
    seen.add(permission);
  }
  return seen;
}

async function testResourcePermissions({ accessToken, url, permissions, fetchImpl }) {
  let response;
  try {
    response = await fetchImpl(url, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ permissions: [...permissions] }),
    });
  } catch {
    return unknownResult(permissions, TRANSPORT.NETWORK_ERROR);
  }

  if (!response || typeof response.ok !== "boolean" || !Number.isInteger(response.status)) {
    return unknownResult(permissions, TRANSPORT.INVALID_JSON, 1);
  }
  if (!response.ok) return unknownResult(permissions, classifyHttpStatus(response.status));

  let body;
  try {
    body = await response.json();
  } catch {
    return unknownResult(permissions, TRANSPORT.INVALID_JSON, 1);
  }
  const granted = validateGrantedPermissions(body, permissions);
  if (!granted) return unknownResult(permissions, TRANSPORT.INVALID_JSON, 1);

  return {
    exitCode: 0,
    transport: TRANSPORT.SUCCESS_2XX,
    states: new Map(
      permissions.map((permission) => [permission, granted.has(permission) ? "GRANTED" : "NOT_GRANTED"])
    ),
  };
}

function aggregatePermission(results, permission) {
  const states = results.map((result) => result.states.get(permission));
  if (states.some((state) => state === "UNKNOWN_NO_ACCESS")) return "UNKNOWN_NO_ACCESS";
  return states.every((state) => state === "GRANTED") ? "GRANTED" : "NOT_GRANTED";
}

function aggregateTransport(results) {
  return results.every((result) => result.transport === TRANSPORT.SUCCESS_2XX)
    ? TRANSPORT.SUCCESS_2XX
    : results.find((result) => result.transport !== TRANSPORT.SUCCESS_2XX)?.transport ?? TRANSPORT.INVALID_JSON;
}

export async function probeProductionBootstrapCapabilities({
  accessToken,
  expectedProjectId,
  expectedProjectNumber,
  fetchImpl = globalThis.fetch,
}) {
  const allPermissions = [...PROJECT_PERMISSIONS, ...RUN_PERMISSIONS, ...ARTIFACT_PERMISSIONS];
  if (
    typeof accessToken !== "string" || accessToken.length === 0 ||
    expectedProjectId !== CANONICAL_PROJECT_ID ||
    expectedProjectNumber !== CANONICAL_PROJECT_NUMBER ||
    typeof fetchImpl !== "function"
  ) {
    return {
      exitCode: 1,
      lines: [
        ...allPermissions.map((permission) => `${outputKey(permission)}=UNKNOWN_NO_ACCESS`),
        "bootstrap_project_permission_test_transport=NOT_ATTEMPTED_GUARD_FAILURE",
        "bootstrap_run_permission_test_transport=NOT_ATTEMPTED_GUARD_FAILURE",
        "bootstrap_artifact_permission_test_transport=NOT_ATTEMPTED_GUARD_FAILURE",
      ],
    };
  }

  const project = await testResourcePermissions({
    accessToken,
    url: `${RESOURCE_MANAGER_BASE}/projects/${encodeURIComponent(expectedProjectId)}:testIamPermissions`,
    permissions: PROJECT_PERMISSIONS,
    fetchImpl,
  });

  const runResults = [];
  for (const service of CLOUD_RUN_SERVICES) {
    runResults.push(await testResourcePermissions({
      accessToken,
      url: `${CLOUD_RUN_BASE}/projects/${encodeURIComponent(expectedProjectId)}/locations/europe-west1/services/${service}:testIamPermissions`,
      permissions: RUN_PERMISSIONS,
      fetchImpl,
    }));
  }

  const artifactResults = [];
  for (const region of ARTIFACT_REGIONS) {
    artifactResults.push(await testResourcePermissions({
      accessToken,
      url: `${ARTIFACT_REGISTRY_BASE}/projects/${encodeURIComponent(expectedProjectId)}/locations/${region}/repositories/${ARTIFACT_REPOSITORY}:testIamPermissions`,
      permissions: ARTIFACT_PERMISSIONS,
      fetchImpl,
    }));
  }

  const lines = [];
  for (const permission of PROJECT_PERMISSIONS) lines.push(`${outputKey(permission)}=${project.states.get(permission)}`);
  lines.push(`${outputKey(RUN_PERMISSIONS[0])}=${aggregatePermission(runResults, RUN_PERMISSIONS[0])}`);
  for (const permission of ARTIFACT_PERMISSIONS) {
    lines.push(`${outputKey(permission)}=${aggregatePermission(artifactResults, permission)}`);
  }
  lines.push(`bootstrap_project_permission_test_transport=${project.transport}`);
  lines.push(`bootstrap_run_permission_test_transport=${aggregateTransport(runResults)}`);
  lines.push(`bootstrap_artifact_permission_test_transport=${aggregateTransport(artifactResults)}`);

  return {
    exitCode: Math.max(project.exitCode, ...runResults.map((result) => result.exitCode), ...artifactResults.map((result) => result.exitCode)),
    lines,
  };
}

async function runCli() {
  const result = await probeProductionBootstrapCapabilities({
    accessToken: process.env.BLOCK3F_IAM_TEST_ACCESS_TOKEN ?? "",
    expectedProjectId: process.env.EXPECTED_PROJECT_ID ?? "",
    expectedProjectNumber: process.env.EXPECTED_PROJECT_NUMBER ?? "",
  });
  for (const line of result.lines) console.log(line);
  process.exitCode = result.exitCode;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) await runCli();
