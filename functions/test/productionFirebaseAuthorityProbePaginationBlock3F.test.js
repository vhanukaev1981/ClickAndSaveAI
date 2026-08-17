"use strict";

const path = require("node:path");
const { pathToFileURL } = require("node:url");
const test = require("node:test");
const assert = require("node:assert/strict");

const probePath = path.resolve(
  process.cwd(),
  "../scripts/production-3f-firebase-authority-probe.mjs"
);

const EXTERNAL_AUTHORITY = "EXTERNALLY_AUTHORITATIVE_VERIFIED";
const EXPECTED_PROJECT_ID = "click-save-ai-production";
const EXPECTED_PROJECT_NUMBER = "991489557172";
const EXPECTED_PACKAGE = "com.aistudio.clickandsaveai.app";

function fakeJsonResponse(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    async json() {
      return body;
    },
  };
}

async function loadProbeModule() {
  return import(`${pathToFileURL(probePath).href}?t=${Date.now()}-${Math.random()}`);
}

function canonicalProject() {
  return {
    projectId: EXPECTED_PROJECT_ID,
    projectNumber: EXPECTED_PROJECT_NUMBER,
    state: "ACTIVE",
  };
}

function canonicalApp() {
  return {
    name: `projects/${EXPECTED_PROJECT_ID}/androidApps/1:991489557172:android:target`,
    appId: "1:991489557172:android:target",
    projectId: EXPECTED_PROJECT_ID,
    packageName: EXPECTED_PACKAGE,
    state: "ACTIVE",
  };
}

function canonicalConfig() {
  return {
    project_info: {
      project_id: EXPECTED_PROJECT_ID,
      project_number: EXPECTED_PROJECT_NUMBER,
    },
    client: [
      {
        client_info: {
          mobilesdk_app_id: "1:991489557172:android:target",
          android_client_info: { package_name: EXPECTED_PACKAGE },
        },
      },
    ],
  };
}

function callProbe(probeFirebaseAuthority, fetchImpl) {
  return probeFirebaseAuthority({
    accessToken: "secret",
    expectedProjectId: EXPECTED_PROJECT_ID,
    expectedProjectNumber: EXPECTED_PROJECT_NUMBER,
    expectedPackage: EXPECTED_PACKAGE,
    fetchImpl,
  });
}

test("Android app authority follows every pageToken before matching and emits external authority truth", async () => {
  const { probeFirebaseAuthority } = await loadProbeModule();
  const calls = [];

  const fetchImpl = async (url) => {
    const parsed = new URL(String(url));
    calls.push(parsed);

    if (parsed.pathname.endsWith(`/projects/${EXPECTED_PROJECT_ID}`)) {
      return fakeJsonResponse(200, canonicalProject());
    }

    if (parsed.pathname.endsWith(`/projects/${EXPECTED_PROJECT_ID}/androidApps`)) {
      if (!parsed.searchParams.has("pageToken")) {
        return fakeJsonResponse(200, {
          apps: [
            {
              name: `projects/${EXPECTED_PROJECT_ID}/androidApps/other`,
              projectId: EXPECTED_PROJECT_ID,
              packageName: "com.example.other",
              state: "ACTIVE",
            },
          ],
          nextPageToken: "PAGE_2_TOKEN",
        });
      }
      assert.equal(parsed.searchParams.get("pageToken"), "PAGE_2_TOKEN");
      return fakeJsonResponse(200, { apps: [canonicalApp()] });
    }

    if (parsed.pathname.endsWith("/config")) {
      return fakeJsonResponse(200, {
        configFilename: "google-services.json",
        configFileContents: Buffer.from(JSON.stringify(canonicalConfig()), "utf8").toString("base64"),
      });
    }

    throw new Error(`unexpected URL: ${url}`);
  };

  const result = await callProbe(probeFirebaseAuthority, fetchImpl);

  assert.equal(result.exitCode, 0);
  assert.deepEqual(result.lines, [
    `firebase_project_external_authority=${EXTERNAL_AUTHORITY}`,
    "firebase_project_identity=VERIFIED_MATCH",
    `firebase_android_app_external_authority=${EXTERNAL_AUTHORITY}`,
    "firebase_android_package=VERIFIED_MATCH",
    `firebase_android_config_authority=${EXTERNAL_AUTHORITY}`,
    "firebase_android_config_project=VERIFIED_MATCH",
    "firebase_android_config_package=VERIFIED_MATCH",
  ]);

  const appListCalls = calls.filter((call) =>
    call.pathname.endsWith(`/projects/${EXPECTED_PROJECT_ID}/androidApps`)
  );
  assert.equal(appListCalls.length, 2);
});

test("VERIFIED_ABSENT is allowed only after the final authoritative Android-app page completes", async () => {
  const { probeFirebaseAuthority } = await loadProbeModule();
  let appPage = 0;

  const result = await callProbe(probeFirebaseAuthority, async (url) => {
    const parsed = new URL(String(url));
    if (parsed.pathname.endsWith(`/projects/${EXPECTED_PROJECT_ID}`)) {
      return fakeJsonResponse(200, canonicalProject());
    }
    if (parsed.pathname.endsWith(`/projects/${EXPECTED_PROJECT_ID}/androidApps`)) {
      appPage += 1;
      if (appPage === 1) {
        return fakeJsonResponse(200, {
          apps: [{ packageName: "com.example.one" }],
          nextPageToken: "PAGE_2",
        });
      }
      assert.equal(parsed.searchParams.get("pageToken"), "PAGE_2");
      return fakeJsonResponse(200, { apps: [{ packageName: "com.example.two" }] });
    }
    throw new Error(`unexpected URL: ${url}`);
  });

  assert.equal(appPage, 2);
  assert.equal(result.exitCode, 0);
  assert.deepEqual(result.lines, [
    `firebase_project_external_authority=${EXTERNAL_AUTHORITY}`,
    "firebase_project_identity=VERIFIED_MATCH",
    "firebase_android_app_external_authority=VERIFIED_ABSENT",
    "firebase_android_package=VERIFIED_ABSENT",
    "firebase_android_config_authority=VERIFIED_ABSENT",
    "firebase_android_config_project=VERIFIED_ABSENT",
    "firebase_android_config_package=VERIFIED_ABSENT",
  ]);
});

test("second-page non-2xx is incomplete authority and remains UNKNOWN_NO_ACCESS, never VERIFIED_ABSENT", async () => {
  const { probeFirebaseAuthority } = await loadProbeModule();

  const result = await callProbe(probeFirebaseAuthority, async (url) => {
    const parsed = new URL(String(url));
    if (parsed.pathname.endsWith(`/projects/${EXPECTED_PROJECT_ID}`)) {
      return fakeJsonResponse(200, canonicalProject());
    }
    if (parsed.pathname.endsWith(`/projects/${EXPECTED_PROJECT_ID}/androidApps`)) {
      if (!parsed.searchParams.has("pageToken")) {
        return fakeJsonResponse(200, {
          apps: [{ packageName: "com.example.other" }],
          nextPageToken: "PAGE_2",
        });
      }
      return fakeJsonResponse(503, { error: { status: "UNAVAILABLE" } });
    }
    throw new Error(`unexpected URL: ${url}`);
  });

  assert.equal(result.exitCode, 0);
  assert.deepEqual(result.lines, [
    `firebase_project_external_authority=${EXTERNAL_AUTHORITY}`,
    "firebase_project_identity=VERIFIED_MATCH",
    "firebase_android_app_external_authority=UNKNOWN_NO_ACCESS",
    "firebase_android_package=UNKNOWN_NO_ACCESS",
    "firebase_android_config_authority=UNKNOWN_NO_ACCESS",
    "firebase_android_config_project=UNKNOWN_NO_ACCESS",
    "firebase_android_config_package=UNKNOWN_NO_ACCESS",
  ]);
  assert.doesNotMatch(result.lines.join("\n"), /VERIFIED_ABSENT/);
});

test("repeated Android page token is incomplete authority and remains UNKNOWN_NO_ACCESS", async () => {
  const { probeFirebaseAuthority } = await loadProbeModule();

  const result = await callProbe(probeFirebaseAuthority, async (url) => {
    const parsed = new URL(String(url));
    if (parsed.pathname.endsWith(`/projects/${EXPECTED_PROJECT_ID}`)) {
      return fakeJsonResponse(200, canonicalProject());
    }
    if (parsed.pathname.endsWith(`/projects/${EXPECTED_PROJECT_ID}/androidApps`)) {
      return fakeJsonResponse(200, {
        apps: [{ packageName: "com.example.other" }],
        nextPageToken: "LOOP_TOKEN",
      });
    }
    throw new Error(`unexpected URL: ${url}`);
  });

  assert.equal(result.exitCode, 0);
  assert.deepEqual(result.lines, [
    `firebase_project_external_authority=${EXTERNAL_AUTHORITY}`,
    "firebase_project_identity=VERIFIED_MATCH",
    "firebase_android_app_external_authority=UNKNOWN_NO_ACCESS",
    "firebase_android_package=UNKNOWN_NO_ACCESS",
    "firebase_android_config_authority=UNKNOWN_NO_ACCESS",
    "firebase_android_config_project=UNKNOWN_NO_ACCESS",
    "firebase_android_config_package=UNKNOWN_NO_ACCESS",
  ]);
  assert.doesNotMatch(result.lines.join("\n"), /VERIFIED_ABSENT/);
});

test("page-limit exhaustion preserves MAX_ANDROID_APP_PAGES fail-closed behavior", async () => {
  const { probeFirebaseAuthority } = await loadProbeModule();
  let listCalls = 0;

  const result = await callProbe(probeFirebaseAuthority, async (url) => {
    const parsed = new URL(String(url));
    if (parsed.pathname.endsWith(`/projects/${EXPECTED_PROJECT_ID}`)) {
      return fakeJsonResponse(200, canonicalProject());
    }
    if (parsed.pathname.endsWith(`/projects/${EXPECTED_PROJECT_ID}/androidApps`)) {
      listCalls += 1;
      return fakeJsonResponse(200, {
        apps: [{ packageName: `com.example.${listCalls}` }],
        nextPageToken: `PAGE_${listCalls + 1}`,
      });
    }
    throw new Error(`unexpected URL: ${url}`);
  });

  assert.equal(listCalls, 100);
  assert.equal(result.exitCode, 0);
  assert.deepEqual(result.lines, [
    `firebase_project_external_authority=${EXTERNAL_AUTHORITY}`,
    "firebase_project_identity=VERIFIED_MATCH",
    "firebase_android_app_external_authority=UNKNOWN_NO_ACCESS",
    "firebase_android_package=UNKNOWN_NO_ACCESS",
    "firebase_android_config_authority=UNKNOWN_NO_ACCESS",
    "firebase_android_config_project=UNKNOWN_NO_ACCESS",
    "firebase_android_config_package=UNKNOWN_NO_ACCESS",
  ]);
  assert.doesNotMatch(result.lines.join("\n"), /VERIFIED_ABSENT/);
});
