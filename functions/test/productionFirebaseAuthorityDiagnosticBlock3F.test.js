"use strict";

const fs = require("node:fs");
const path = require("node:path");
const { pathToFileURL } = require("node:url");
const test = require("node:test");
const assert = require("node:assert/strict");

const root = path.resolve(process.cwd(), "..");
const probePath = path.join(root, "scripts/production-3f-firebase-authority-probe.mjs");
const workflowPath = path.join(root, ".github/workflows/production-release.yml");
const workflow = fs.readFileSync(workflowPath, "utf8");

const EXPECTED_PROJECT_ID = "click-save-ai-production";
const EXPECTED_PROJECT_NUMBER = "991489557172";
const EXPECTED_PACKAGE = "com.aistudio.clickandsaveai.app";
const EXTERNAL_AUTHORITY = "EXTERNALLY_AUTHORITATIVE_VERIFIED";
const NOT_ATTEMPTED = "NOT_ATTEMPTED_UPSTREAM_UNAVAILABLE";

function fakeJsonResponse(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    async json() {
      return body;
    },
  };
}

function invalidJsonResponse(status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    async json() {
      throw new SyntaxError("SENSITIVE_INVALID_JSON_MESSAGE_MUST_NOT_LEAK");
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

function callProbe(probeFirebaseAuthority, fetchImpl, accessToken = "SENSITIVE_ACCESS_TOKEN") {
  return probeFirebaseAuthority({
    accessToken,
    expectedProjectId: EXPECTED_PROJECT_ID,
    expectedProjectNumber: EXPECTED_PROJECT_NUMBER,
    expectedPackage: EXPECTED_PACKAGE,
    fetchImpl,
  });
}

function expectedDiagnostics(project, apps = NOT_ATTEMPTED, config = NOT_ATTEMPTED) {
  return [
    `firebase_project_read_transport=${project}`,
    `firebase_android_apps_read_transport=${apps}`,
    `firebase_android_config_read_transport=${config}`,
  ];
}

function assertProjectUnavailable(result, transport) {
  assert.equal(result.exitCode, 0);
  assert.deepEqual(result.lines, [
    "firebase_project_external_authority=UNKNOWN_NO_ACCESS",
    "firebase_project_identity=UNKNOWN_NO_ACCESS",
    "firebase_android_app_external_authority=UNKNOWN_NO_ACCESS",
    "firebase_android_package=UNKNOWN_NO_ACCESS",
    "firebase_android_config_authority=UNKNOWN_NO_ACCESS",
    "firebase_android_config_project=UNKNOWN_NO_ACCESS",
    "firebase_android_config_package=UNKNOWN_NO_ACCESS",
  ]);
  assert.deepEqual(result.diagnosticLines, expectedDiagnostics(transport));
  assert.doesNotMatch(result.lines.join("\n"), /VERIFIED_ABSENT/);
}

for (const [status, transport] of [
  [401, "HTTP_401"],
  [403, "HTTP_403"],
  [404, "HTTP_404"],
  [429, "HTTP_429"],
  [500, "HTTP_5XX"],
  [599, "HTTP_5XX"],
  [418, "HTTP_OTHER_NON_2XX"],
]) {
  test(`HTTP ${status} preserves UNKNOWN_NO_ACCESS and emits only ${transport}`, async () => {
    const { probeFirebaseAuthority } = await loadProbeModule();
    const result = await callProbe(probeFirebaseAuthority, async () =>
      fakeJsonResponse(status, {
        error: {
          message: "SENSITIVE_FIREBASE_ERROR_MESSAGE_MUST_NOT_LEAK",
          details: ["SENSITIVE_FIREBASE_ERROR_DETAILS_MUST_NOT_LEAK"],
          status: "SENSITIVE_FIREBASE_ERROR_STATUS_MUST_NOT_LEAK",
        },
      })
    );

    assertProjectUnavailable(result, transport);
    const output = [...result.lines, ...result.diagnosticLines].join("\n");
    assert.doesNotMatch(
      output,
      /SENSITIVE_FIREBASE_ERROR_MESSAGE_MUST_NOT_LEAK|SENSITIVE_FIREBASE_ERROR_DETAILS_MUST_NOT_LEAK|SENSITIVE_FIREBASE_ERROR_STATUS_MUST_NOT_LEAK/
    );
  });
}

test("fetch exception becomes NETWORK_ERROR without leaking exception content", async () => {
  const { probeFirebaseAuthority } = await loadProbeModule();
  const result = await callProbe(probeFirebaseAuthority, async () => {
    throw new Error("SENSITIVE_NETWORK_ERROR_MESSAGE_MUST_NOT_LEAK");
  });

  assertProjectUnavailable(result, "NETWORK_ERROR");
  assert.doesNotMatch(
    [...result.lines, ...result.diagnosticLines].join("\n"),
    /SENSITIVE_NETWORK_ERROR_MESSAGE_MUST_NOT_LEAK/
  );
});

test("unreadable successful JSON becomes INVALID_JSON and downstream reads are not attempted", async () => {
  const { probeFirebaseAuthority } = await loadProbeModule();
  let calls = 0;
  const result = await callProbe(probeFirebaseAuthority, async () => {
    calls += 1;
    return invalidJsonResponse();
  });

  assert.equal(calls, 1);
  assertProjectUnavailable(result, "INVALID_JSON");
  assert.doesNotMatch(
    [...result.lines, ...result.diagnosticLines].join("\n"),
    /SENSITIVE_INVALID_JSON_MESSAGE_MUST_NOT_LEAK/
  );
});

test("fully successful authoritative reads emit SUCCESS_2XX separately from authority truth", async () => {
  const { probeFirebaseAuthority } = await loadProbeModule();
  const result = await callProbe(probeFirebaseAuthority, async (url) => {
    const value = String(url);
    if (value.endsWith(`/projects/${EXPECTED_PROJECT_ID}`)) {
      return fakeJsonResponse(200, canonicalProject());
    }
    if (value.endsWith(`/projects/${EXPECTED_PROJECT_ID}/androidApps`)) {
      return fakeJsonResponse(200, { apps: [canonicalApp()] });
    }
    if (value.endsWith("/config")) {
      return fakeJsonResponse(200, {
        configFilename: "google-services.json",
        configFileContents: Buffer.from(JSON.stringify(canonicalConfig()), "utf8").toString("base64"),
      });
    }
    throw new Error(`unexpected URL: ${url}`);
  });

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
  assert.deepEqual(
    result.diagnosticLines,
    expectedDiagnostics("SUCCESS_2XX", "SUCCESS_2XX", "SUCCESS_2XX")
  );
});

test("invalid decoded Firebase Android config is INVALID_JSON while authority stays UNKNOWN_NO_ACCESS", async () => {
  const { probeFirebaseAuthority } = await loadProbeModule();
  const result = await callProbe(probeFirebaseAuthority, async (url) => {
    const value = String(url);
    if (value.endsWith(`/projects/${EXPECTED_PROJECT_ID}`)) {
      return fakeJsonResponse(200, canonicalProject());
    }
    if (value.endsWith(`/projects/${EXPECTED_PROJECT_ID}/androidApps`)) {
      return fakeJsonResponse(200, { apps: [canonicalApp()] });
    }
    return fakeJsonResponse(200, {
      configFilename: "google-services.json",
      configFileContents: Buffer.from("{not-json", "utf8").toString("base64"),
    });
  });

  assert.equal(result.exitCode, 0);
  assert.match(result.lines.join("\n"), /firebase_android_config_authority=UNKNOWN_NO_ACCESS/);
  assert.deepEqual(
    result.diagnosticLines,
    expectedDiagnostics("SUCCESS_2XX", "SUCCESS_2XX", "INVALID_JSON")
  );
});

test("intermediate pagination HTTP failure preserves UNKNOWN_NO_ACCESS and reports the failing page class", async () => {
  const { probeFirebaseAuthority } = await loadProbeModule();
  let appCalls = 0;
  const result = await callProbe(probeFirebaseAuthority, async (url) => {
    const parsed = new URL(String(url));
    if (parsed.pathname.endsWith(`/projects/${EXPECTED_PROJECT_ID}`)) {
      return fakeJsonResponse(200, canonicalProject());
    }
    if (parsed.pathname.endsWith(`/projects/${EXPECTED_PROJECT_ID}/androidApps`)) {
      appCalls += 1;
      if (appCalls === 1) {
        return fakeJsonResponse(200, {
          apps: [{ packageName: "com.example.other" }],
          nextPageToken: "PAGE_2",
        });
      }
      return fakeJsonResponse(503, { error: { message: "SENSITIVE_PAGE_ERROR" } });
    }
    throw new Error(`unexpected URL: ${url}`);
  });

  assert.equal(appCalls, 2);
  assert.match(result.lines.join("\n"), /firebase_android_app_external_authority=UNKNOWN_NO_ACCESS/);
  assert.doesNotMatch(result.lines.join("\n"), /VERIFIED_ABSENT/);
  assert.deepEqual(
    result.diagnosticLines,
    expectedDiagnostics("SUCCESS_2XX", "HTTP_5XX", NOT_ATTEMPTED)
  );
  assert.doesNotMatch([...result.lines, ...result.diagnosticLines].join("\n"), /SENSITIVE_PAGE_ERROR/);
});

test("repeated page token preserves UNKNOWN_NO_ACCESS and classifies the inventory as INVALID_JSON", async () => {
  const { probeFirebaseAuthority } = await loadProbeModule();
  const result = await callProbe(probeFirebaseAuthority, async (url) => {
    const parsed = new URL(String(url));
    if (parsed.pathname.endsWith(`/projects/${EXPECTED_PROJECT_ID}`)) {
      return fakeJsonResponse(200, canonicalProject());
    }
    return fakeJsonResponse(200, {
      apps: [{ packageName: "com.example.other" }],
      nextPageToken: "LOOP_TOKEN",
    });
  });

  assert.match(result.lines.join("\n"), /firebase_android_app_external_authority=UNKNOWN_NO_ACCESS/);
  assert.doesNotMatch(result.lines.join("\n"), /VERIFIED_ABSENT/);
  assert.deepEqual(
    result.diagnosticLines,
    expectedDiagnostics("SUCCESS_2XX", "INVALID_JSON", NOT_ATTEMPTED)
  );
});

test("page-limit exhaustion preserves UNKNOWN_NO_ACCESS and MAX_ANDROID_APP_PAGES=100", async () => {
  const { probeFirebaseAuthority } = await loadProbeModule();
  let appCalls = 0;
  const result = await callProbe(probeFirebaseAuthority, async (url) => {
    const parsed = new URL(String(url));
    if (parsed.pathname.endsWith(`/projects/${EXPECTED_PROJECT_ID}`)) {
      return fakeJsonResponse(200, canonicalProject());
    }
    appCalls += 1;
    return fakeJsonResponse(200, {
      apps: [{ packageName: `com.example.${appCalls}` }],
      nextPageToken: `PAGE_${appCalls + 1}`,
    });
  });

  assert.equal(appCalls, 100);
  assert.match(result.lines.join("\n"), /firebase_android_app_external_authority=UNKNOWN_NO_ACCESS/);
  assert.doesNotMatch(result.lines.join("\n"), /VERIFIED_ABSENT/);
  assert.deepEqual(
    result.diagnosticLines,
    expectedDiagnostics("SUCCESS_2XX", "INVALID_JSON", NOT_ATTEMPTED)
  );
});

test("complete authoritative inventory is still the only path to VERIFIED_ABSENT", async () => {
  const { probeFirebaseAuthority } = await loadProbeModule();
  let appCalls = 0;
  const result = await callProbe(probeFirebaseAuthority, async (url) => {
    const parsed = new URL(String(url));
    if (parsed.pathname.endsWith(`/projects/${EXPECTED_PROJECT_ID}`)) {
      return fakeJsonResponse(200, canonicalProject());
    }
    appCalls += 1;
    if (appCalls === 1) {
      return fakeJsonResponse(200, {
        apps: [{ packageName: "com.example.one" }],
        nextPageToken: "PAGE_2",
      });
    }
    return fakeJsonResponse(200, { apps: [{ packageName: "com.example.two" }] });
  });

  assert.equal(appCalls, 2);
  assert.match(result.lines.join("\n"), /firebase_android_app_external_authority=VERIFIED_ABSENT/);
  assert.match(result.lines.join("\n"), /firebase_android_config_authority=VERIFIED_ABSENT/);
  assert.deepEqual(
    result.diagnosticLines,
    expectedDiagnostics("SUCCESS_2XX", "SUCCESS_2XX", NOT_ATTEMPTED)
  );
});

test("diagnostic output cannot emit body, message, details, token, header, credential or config material", async () => {
  const { probeFirebaseAuthority } = await loadProbeModule();
  const sentinels = [
    "SENSITIVE_RESPONSE_BODY",
    "SENSITIVE_ERROR_MESSAGE",
    "SENSITIVE_ERROR_DETAILS",
    "SENSITIVE_ACCESS_TOKEN",
    "Authorization",
    "SENSITIVE_CREDENTIAL_FILE",
    "SENSITIVE_GOOGLE_SERVICES_CONFIG",
    "SENSITIVE_API_KEY",
  ];
  const result = await callProbe(probeFirebaseAuthority, async () =>
    fakeJsonResponse(403, {
      response: "SENSITIVE_RESPONSE_BODY",
      error: {
        message: "SENSITIVE_ERROR_MESSAGE",
        details: "SENSITIVE_ERROR_DETAILS",
      },
      Authorization: "Bearer SENSITIVE_ACCESS_TOKEN",
      credential_file: "SENSITIVE_CREDENTIAL_FILE",
      google_services: "SENSITIVE_GOOGLE_SERVICES_CONFIG",
      api_key: "SENSITIVE_API_KEY",
    })
  );

  const output = [...result.lines, ...result.diagnosticLines].join("\n");
  for (const sentinel of sentinels) {
    assert.ok(!output.includes(sentinel), `diagnostic output leaked ${sentinel}`);
  }
  assert.deepEqual(result.diagnosticLines, expectedDiagnostics("HTTP_403"));
});

test("repository diagnostic refinement preserves GET-only, no-build/no-deploy/no-Play/no-IAM/no-API-enable guards", () => {
  const source = fs.readFileSync(probePath, "utf8");
  assert.match(source, /method:\s*["']GET["']/);
  assert.doesNotMatch(source, /method:\s*["'](?:POST|PUT|PATCH|DELETE)["']/i);
  for (const forbidden of [
    /firebase\s+deploy/i,
    /assembleRelease|bundleRelease|gradle\s/i,
    /google\s+play|play.*(?:upload|publish)/i,
    /roles\/firebase\.viewer|roles\/viewer|roles\/owner|roles\/editor/i,
    /add-iam-policy-binding|set-iam-policy|remove-iam-policy-binding/i,
    /services\s+enable/i,
    /service-accounts\s+keys\s+create/i,
  ]) {
    assert.doesNotMatch(source, forbidden);
  }
});

test("Block 3E and Block 3F external-authority modes remain mutually exclusive with the exact authorization phrase", () => {
  const marker = "  production-wif-auth-proof:\n";
  const start = workflow.indexOf(marker);
  assert.notEqual(start, -1);
  const tail = workflow.slice(start + marker.length);
  const nextJob = tail.match(/\n  [A-Za-z0-9_-]+:\n/);
  const wifJob = nextJob ? tail.slice(0, nextJob.index) : tail;

  const probeMarker = "  production-3f-firebase-authority-probe:\n";
  const probeStart = workflow.indexOf(probeMarker);
  assert.notEqual(probeStart, -1);
  const probeTail = workflow.slice(probeStart + probeMarker.length);
  const probeNext = probeTail.match(/\n  [A-Za-z0-9_-]+:\n/);
  const probeJob = probeNext ? probeTail.slice(0, probeNext.index) : probeTail;

  assert.ok(
    wifJob.includes(
      "inputs.authorize_3f_external_authority_probe != 'PROBE_3F_FIREBASE_AUTHORITY_READ_ONLY'"
    )
  );
  assert.ok(
    probeJob.includes("inputs.authorize_wif_auth_proof == 'NO_WIF_PROOF'")
  );
  assert.ok(
    probeJob.includes(
      "inputs.authorize_3f_external_authority_probe == 'PROBE_3F_FIREBASE_AUTHORITY_READ_ONLY'"
    )
  );
  assert.match(probeJob, /environment: production/);
  assert.match(probeJob, /permissions:\n      contents: read\n      id-token: write\n/);
  assert.match(probeJob, /uses: google-github-actions\/auth@v3/);
  assert.match(
    probeJob,
    /access_token_scopes: 'https:\/\/www\.googleapis\.com\/auth\/firebase\.readonly'/
  );
});
