import { pathToFileURL } from "node:url";

const FIREBASE_MANAGEMENT_BASE = "https://firebase.googleapis.com/v1beta1";
const CANONICAL_PROJECT_ID = "click-save-ai-production";
const CANONICAL_PROJECT_NUMBER = "991489557172";
const CANONICAL_PACKAGE = "com.aistudio.clickandsaveai.app";
const UNKNOWN = "UNKNOWN_NO_ACCESS";

function linesFor({
  projectAuthority = UNKNOWN,
  projectIdentity = UNKNOWN,
  appAuthority = UNKNOWN,
  packageIdentity = UNKNOWN,
  configAuthority = UNKNOWN,
  configProject = UNKNOWN,
  configPackage = UNKNOWN,
} = {}) {
  return [
    `firebase_project_external_authority=${projectAuthority}`,
    `firebase_project_identity=${projectIdentity}`,
    `firebase_android_app_external_authority=${appAuthority}`,
    `firebase_android_package=${packageIdentity}`,
    `firebase_android_config_authority=${configAuthority}`,
    `firebase_android_config_project=${configProject}`,
    `firebase_android_config_package=${configPackage}`,
  ];
}

async function readJson(url, accessToken, fetchImpl) {
  try {
    const response = await fetchImpl(url, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        Accept: "application/json",
      },
    });
    if (!response?.ok) {
      return { available: false, status: response?.status ?? 0, body: null };
    }
    const body = await response.json();
    return { available: true, status: response.status, body };
  } catch {
    return { available: false, status: 0, body: null };
  }
}

function safeDecodeConfig(encoded) {
  if (typeof encoded !== "string" || encoded.length === 0) return null;
  try {
    const text = Buffer.from(encoded, "base64").toString("utf8");
    const parsed = JSON.parse(text);
    return parsed && typeof parsed === "object" ? parsed : null;
  } catch {
    return null;
  }
}

function projectIdentityMatches(project, expectedProjectId, expectedProjectNumber) {
  return (
    project &&
    project.projectId === expectedProjectId &&
    String(project.projectNumber ?? "") === expectedProjectNumber
  );
}

function validateExpectedInputs(expectedProjectId, expectedProjectNumber, expectedPackage) {
  return (
    expectedProjectId === CANONICAL_PROJECT_ID &&
    expectedProjectNumber === CANONICAL_PROJECT_NUMBER &&
    expectedPackage === CANONICAL_PACKAGE
  );
}

export async function probeFirebaseAuthority({
  accessToken,
  expectedProjectId,
  expectedProjectNumber,
  expectedPackage,
  fetchImpl = globalThis.fetch,
}) {
  if (
    typeof accessToken !== "string" ||
    accessToken.length === 0 ||
    typeof fetchImpl !== "function" ||
    !validateExpectedInputs(expectedProjectId, expectedProjectNumber, expectedPackage)
  ) {
    return { exitCode: 1, lines: linesFor() };
  }

  const projectPath = `/projects/${encodeURIComponent(expectedProjectId)}`;
  const projectRead = await readJson(
    `${FIREBASE_MANAGEMENT_BASE}${projectPath}`,
    accessToken,
    fetchImpl
  );

  if (!projectRead.available) {
    return { exitCode: 0, lines: linesFor() };
  }

  if (!projectIdentityMatches(projectRead.body, expectedProjectId, expectedProjectNumber)) {
    return {
      exitCode: 1,
      lines: linesFor({ projectAuthority: "MISMATCH", projectIdentity: "MISMATCH" }),
    };
  }

  const projectVerified = {
    projectAuthority: "VERIFIED_PRESENT",
    projectIdentity: "VERIFIED_MATCH",
  };

  const appsRead = await readJson(
    `${FIREBASE_MANAGEMENT_BASE}${projectPath}/androidApps`,
    accessToken,
    fetchImpl
  );
  if (!appsRead.available) {
    return { exitCode: 0, lines: linesFor(projectVerified) };
  }

  const apps = Array.isArray(appsRead.body?.apps) ? appsRead.body.apps : [];
  const matches = apps.filter((app) => app?.packageName === expectedPackage);
  if (matches.length === 0) {
    return {
      exitCode: 0,
      lines: linesFor({
        ...projectVerified,
        appAuthority: "VERIFIED_ABSENT",
        packageIdentity: "VERIFIED_ABSENT",
        configAuthority: "VERIFIED_ABSENT",
        configProject: "VERIFIED_ABSENT",
        configPackage: "VERIFIED_ABSENT",
      }),
    };
  }

  if (matches.length !== 1) {
    return {
      exitCode: 1,
      lines: linesFor({
        ...projectVerified,
        appAuthority: "MISMATCH",
        packageIdentity: "MISMATCH",
      }),
    };
  }

  const app = matches[0];
  const expectedNamePrefix = `projects/${expectedProjectId}/androidApps/`;
  if (
    typeof app.name !== "string" ||
    !app.name.startsWith(expectedNamePrefix) ||
    app.name.slice(expectedNamePrefix.length).includes("/") ||
    (app.projectId !== undefined && app.projectId !== expectedProjectId)
  ) {
    return {
      exitCode: 1,
      lines: linesFor({
        ...projectVerified,
        appAuthority: "MISMATCH",
        packageIdentity: "MISMATCH",
      }),
    };
  }

  const appVerified = {
    ...projectVerified,
    appAuthority: "VERIFIED_PRESENT",
    packageIdentity: "VERIFIED_MATCH",
  };
  const configRead = await readJson(
    `${FIREBASE_MANAGEMENT_BASE}/${app.name}/config`,
    accessToken,
    fetchImpl
  );
  if (!configRead.available) {
    return { exitCode: 0, lines: linesFor(appVerified) };
  }

  const config = safeDecodeConfig(configRead.body?.configFileContents);
  if (!config) {
    return { exitCode: 0, lines: linesFor(appVerified) };
  }

  const configProjectId = config?.project_info?.project_id;
  const configProjectNumber = String(config?.project_info?.project_number ?? "");
  const clients = Array.isArray(config?.client) ? config.client : [];
  const configPackagePresent = clients.some(
    (client) => client?.client_info?.android_client_info?.package_name === expectedPackage
  );
  const configProjectMatches =
    configProjectId === expectedProjectId && configProjectNumber === expectedProjectNumber;

  if (!configProjectMatches || !configPackagePresent) {
    return {
      exitCode: 1,
      lines: linesFor({
        ...appVerified,
        configAuthority: "VERIFIED_PRESENT",
        configProject: configProjectMatches ? "VERIFIED_MATCH" : "MISMATCH",
        configPackage: configPackagePresent ? "VERIFIED_MATCH" : "MISMATCH",
      }),
    };
  }

  return {
    exitCode: 0,
    lines: linesFor({
      ...appVerified,
      configAuthority: "VERIFIED_PRESENT",
      configProject: "VERIFIED_MATCH",
      configPackage: "VERIFIED_MATCH",
    }),
  };
}

async function runCli() {
  const result = await probeFirebaseAuthority({
    accessToken: process.env.BLOCK3F_FIREBASE_ACCESS_TOKEN ?? "",
    expectedProjectId: process.env.EXPECTED_PROJECT_ID ?? "",
    expectedProjectNumber: process.env.EXPECTED_PROJECT_NUMBER ?? "",
    expectedPackage: process.env.EXPECTED_PACKAGE ?? "",
  });
  for (const line of result.lines) console.log(line);
  process.exitCode = result.exitCode;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  await runCli();
}
