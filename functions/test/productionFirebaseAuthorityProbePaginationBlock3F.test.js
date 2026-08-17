"use strict";

const path = require("node:path");
const { pathToFileURL } = require("node:url");
const test = require("node:test");
const assert = require("node:assert/strict");

const probePath = path.resolve(
  process.cwd(),
  "../scripts/production-3f-firebase-authority-probe.mjs"
);

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

test("Android app authority follows every pageToken before matching or declaring VERIFIED_ABSENT", async () => {
  const { probeFirebaseAuthority } = await loadProbeModule();
  const calls = [];
  const config = {
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

  const fetchImpl = async (url) => {
    const parsed = new URL(String(url));
    calls.push(parsed);

    if (parsed.pathname.endsWith(`/projects/${EXPECTED_PROJECT_ID}`)) {
      return fakeJsonResponse(200, {
        projectId: EXPECTED_PROJECT_ID,
        projectNumber: EXPECTED_PROJECT_NUMBER,
        state: "ACTIVE",
      });
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
      return fakeJsonResponse(200, {
        apps: [
          {
            name: `projects/${EXPECTED_PROJECT_ID}/androidApps/1:991489557172:android:target`,
            appId: "1:991489557172:android:target",
            projectId: EXPECTED_PROJECT_ID,
            packageName: EXPECTED_PACKAGE,
            state: "ACTIVE",
          },
        ],
      });
    }

    if (parsed.pathname.endsWith("/config")) {
      return fakeJsonResponse(200, {
        configFilename: "google-services.json",
        configFileContents: Buffer.from(JSON.stringify(config), "utf8").toString("base64"),
      });
    }

    throw new Error(`unexpected URL: ${url}`);
  };

  const result = await probeFirebaseAuthority({
    accessToken: "secret",
    expectedProjectId: EXPECTED_PROJECT_ID,
    expectedProjectNumber: EXPECTED_PROJECT_NUMBER,
    expectedPackage: EXPECTED_PACKAGE,
    fetchImpl,
  });

  assert.equal(result.exitCode, 0);
  assert.deepEqual(result.lines, [
    "firebase_project_external_authority=VERIFIED_PRESENT",
    "firebase_project_identity=VERIFIED_MATCH",
    "firebase_android_app_external_authority=VERIFIED_PRESENT",
    "firebase_android_package=VERIFIED_MATCH",
    "firebase_android_config_authority=VERIFIED_PRESENT",
    "firebase_android_config_project=VERIFIED_MATCH",
    "firebase_android_config_package=VERIFIED_MATCH",
  ]);

  const appListCalls = calls.filter((call) =>
    call.pathname.endsWith(`/projects/${EXPECTED_PROJECT_ID}/androidApps`)
  );
  assert.equal(appListCalls.length, 2);
});
