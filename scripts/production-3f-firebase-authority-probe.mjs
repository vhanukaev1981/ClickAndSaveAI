import { pathToFileURL } from "node:url";

const FIREBASE_MANAGEMENT_BASE = "https://firebase.googleapis.com/v1beta1";
const CANONICAL_PROJECT_ID = "click-save-ai-production";
const CANONICAL_PROJECT_NUMBER = "991489557172";
const CANONICAL_PACKAGE = "com.aistudio.clickandsaveai.app";
const UNKNOWN = "UNKNOWN_NO_ACCESS";
const EXTERNAL_AUTHORITY = "EXTERNALLY_AUTHORITATIVE_VERIFIED";
const MAX_ANDROID_APP_PAGES = 100;

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
  NOT_ATTEMPTED_UPSTREAM_UNAVAILABLE: "NOT_ATTEMPTED_UPSTREAM_UNAVAILABLE",
});

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

function diagnosticLinesFor({
  projectTransport = TRANSPORT.NOT_ATTEMPTED_UPSTREAM_UNAVAILABLE,
  appsTransport = TRANSPORT.NOT_ATTEMPTED_UPSTREAM_UNAVAILABLE,
  configTransport = TRANSPORT.NOT_ATTEMPTED_UPSTREAM_UNAVAILABLE,
} = {}) {
  return [
    `firebase_project_read_transport=${projectTransport}`,
    `firebase_android_apps_read_transport=${appsTransport}`,
    `firebase_android_config_read_transport=${configTransport}`,
  ];
}

function resultFor(exitCode, authority = {}, diagnostic = {}) {
  return {
    exitCode,
    lines: linesFor(authority),
    diagnosticLines: diagnosticLinesFor(diagnostic),
  };
}

function classifyHttpStatus(status) {
  if (status === 401) return TRANSPORT.HTTP_401;
  if (status === 403) return TRANSPORT.HTTP_403;
  if (status === 404) return TRANSPORT.HTTP_404;
  if (status === 429) return TRANSPORT.HTTP_429;
  if (status >= 500 && status <= 599) return TRANSPORT.HTTP_5XX;
  return TRANSPORT.HTTP_OTHER_NON_2XX;
}

async function readJson(url, accessToken, fetchImpl) {
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
    return {
      available: false,
      transport: TRANSPORT.NETWORK_ERROR,
      body: null,
    };
  }

  if (
    !response ||
    typeof response.ok !== "boolean" ||
    !Number.isInteger(response.status)
  ) {
    return {
      available: false,
      transport: TRANSPORT.INVALID_JSON,
      body: null,
    };
  }

  if (!response.ok) {
    return {
      available: false,
      transport: classifyHttpStatus(response.status),
      body: null,
    };
  }

  try {
    const body = await response.json();
    return {
      available: true,
      transport: TRANSPORT.SUCCESS_2XX,
      body,
    };
  } catch {
    return {
      available: false,
      transport: TRANSPORT.INVALID_JSON,
      body: null,
    };
  }
}

function invalidInventoryResult() {
  return {
    available: false,
    apps: [],
    transport: TRANSPORT.INVALID_JSON,
  };
}

async function listAllAndroidApps(projectPath, accessToken, fetchImpl) {
  const baseUrl = `${FIREBASE_MANAGEMENT_BASE}${projectPath}/androidApps`;
  const apps = [];
  const seenPageTokens = new Set();
  let pageToken = "";

  for (let page = 0; page < MAX_ANDROID_APP_PAGES; page += 1) {
    const url = pageToken
      ? `${baseUrl}?pageToken=${encodeURIComponent(pageToken)}`
      : baseUrl;
    const pageRead = await readJson(url, accessToken, fetchImpl);
    if (!pageRead.available) {
      return {
        available: false,
        apps: [],
        transport: pageRead.transport,
      };
    }

    if (!pageRead.body || typeof pageRead.body !== "object") {
      return invalidInventoryResult();
    }
    if (pageRead.body.apps !== undefined && !Array.isArray(pageRead.body.apps)) {
      return invalidInventoryResult();
    }
    if (
      pageRead.body.nextPageToken !== undefined &&
      typeof pageRead.body.nextPageToken !== "string"
    ) {
      return invalidInventoryResult();
    }

    if (Array.isArray(pageRead.body.apps)) {
      apps.push(...pageRead.body.apps);
    }

    const nextPageToken =
      typeof pageRead.body.nextPageToken === "string"
        ? pageRead.body.nextPageToken.trim()
        : "";
    if (!nextPageToken) {
      return {
        available: true,
        apps,
        transport: TRANSPORT.SUCCESS_2XX,
      };
    }
    if (seenPageTokens.has(nextPageToken)) {
      return invalidInventoryResult();
    }
    seenPageTokens.add(nextPageToken);
    pageToken = nextPageToken;
  }

  return invalidInventoryResult();
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
    return resultFor(1);
  }

  const projectPath = `/projects/${encodeURIComponent(expectedProjectId)}`;
  const projectRead = await readJson(
    `${FIREBASE_MANAGEMENT_BASE}${projectPath}`,
    accessToken,
    fetchImpl
  );

  if (!projectRead.available) {
    return resultFor(0, {}, { projectTransport: projectRead.transport });
  }

  if (!projectIdentityMatches(projectRead.body, expectedProjectId, expectedProjectNumber)) {
    return resultFor(
      1,
      { projectAuthority: "MISMATCH", projectIdentity: "MISMATCH" },
      { projectTransport: TRANSPORT.SUCCESS_2XX }
    );
  }

  const projectVerified = {
    projectAuthority: EXTERNAL_AUTHORITY,
    projectIdentity: "VERIFIED_MATCH",
  };
  const projectDiagnostic = {
    projectTransport: TRANSPORT.SUCCESS_2XX,
  };

  const appsRead = await listAllAndroidApps(projectPath, accessToken, fetchImpl);
  if (!appsRead.available) {
    return resultFor(0, projectVerified, {
      ...projectDiagnostic,
      appsTransport: appsRead.transport,
    });
  }

  const appsDiagnostic = {
    ...projectDiagnostic,
    appsTransport: TRANSPORT.SUCCESS_2XX,
  };
  const matches = appsRead.apps.filter((app) => app?.packageName === expectedPackage);
  if (matches.length === 0) {
    return resultFor(
      0,
      {
        ...projectVerified,
        appAuthority: "VERIFIED_ABSENT",
        packageIdentity: "VERIFIED_ABSENT",
        configAuthority: "VERIFIED_ABSENT",
        configProject: "VERIFIED_ABSENT",
        configPackage: "VERIFIED_ABSENT",
      },
      appsDiagnostic
    );
  }

  if (matches.length !== 1) {
    return resultFor(
      1,
      {
        ...projectVerified,
        appAuthority: "MISMATCH",
        packageIdentity: "MISMATCH",
      },
      appsDiagnostic
    );
  }

  const app = matches[0];
  const expectedNamePrefix = `projects/${expectedProjectId}/androidApps/`;
  if (
    typeof app.name !== "string" ||
    !app.name.startsWith(expectedNamePrefix) ||
    app.name.slice(expectedNamePrefix.length).includes("/") ||
    (app.projectId !== undefined && app.projectId !== expectedProjectId)
  ) {
    return resultFor(
      1,
      {
        ...projectVerified,
        appAuthority: "MISMATCH",
        packageIdentity: "MISMATCH",
      },
      appsDiagnostic
    );
  }

  const appVerified = {
    ...projectVerified,
    appAuthority: EXTERNAL_AUTHORITY,
    packageIdentity: "VERIFIED_MATCH",
  };
  const configRead = await readJson(
    `${FIREBASE_MANAGEMENT_BASE}/${app.name}/config`,
    accessToken,
    fetchImpl
  );
  if (!configRead.available) {
    return resultFor(0, appVerified, {
      ...appsDiagnostic,
      configTransport: configRead.transport,
    });
  }

  const config = safeDecodeConfig(configRead.body?.configFileContents);
  if (!config) {
    return resultFor(0, appVerified, {
      ...appsDiagnostic,
      configTransport: TRANSPORT.INVALID_JSON,
    });
  }

  const configDiagnostic = {
    ...appsDiagnostic,
    configTransport: TRANSPORT.SUCCESS_2XX,
  };
  const configProjectId = config?.project_info?.project_id;
  const configProjectNumber = String(config?.project_info?.project_number ?? "");
  const clients = Array.isArray(config?.client) ? config.client : [];
  const configPackagePresent = clients.some(
    (client) => client?.client_info?.android_client_info?.package_name === expectedPackage
  );
  const configProjectMatches =
    configProjectId === expectedProjectId && configProjectNumber === expectedProjectNumber;

  if (!configProjectMatches || !configPackagePresent) {
    return resultFor(
      1,
      {
        ...appVerified,
        configAuthority: EXTERNAL_AUTHORITY,
        configProject: configProjectMatches ? "VERIFIED_MATCH" : "MISMATCH",
        configPackage: configPackagePresent ? "VERIFIED_MATCH" : "MISMATCH",
      },
      configDiagnostic
    );
  }

  return resultFor(
    0,
    {
      ...appVerified,
      configAuthority: EXTERNAL_AUTHORITY,
      configProject: "VERIFIED_MATCH",
      configPackage: "VERIFIED_MATCH",
    },
    configDiagnostic
  );
}

async function runCli() {
  const result = await probeFirebaseAuthority({
    accessToken: process.env.BLOCK3F_FIREBASE_ACCESS_TOKEN ?? "",
    expectedProjectId: process.env.EXPECTED_PROJECT_ID ?? "",
    expectedProjectNumber: process.env.EXPECTED_PROJECT_NUMBER ?? "",
    expectedPackage: process.env.EXPECTED_PACKAGE ?? "",
  });
  for (const line of result.lines) console.log(line);
  for (const line of result.diagnosticLines) console.log(line);
  process.exitCode = result.exitCode;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  await runCli();
}
