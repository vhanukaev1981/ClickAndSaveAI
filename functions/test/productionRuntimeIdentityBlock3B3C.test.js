"use strict";

const fs = require("node:fs");
const path = require("node:path");
const { spawnSync } = require("node:child_process");
const test = require("node:test");
const assert = require("node:assert/strict");

const root = path.resolve(__dirname, "..", "..");
const file = (p) => fs.readFileSync(path.join(root, p), "utf8");
const pkg = JSON.parse(file("functions/package.json"));
const entry = file("functions/src/entry.js");
const index = file("functions/src/index.js");
const cleanup = file("functions/src/pushAccountCleanup.js");
const bootstrap = file("scripts/bootstrap-production-runtime-build-actas.sh");
const verifier = file("scripts/verify-production-runtime-build-actas.sh");
const matrixPath = path.join(root, "docs/PRODUCTION_V2_RUNTIME_PERMISSION_MATRIX.md");
const matrix = fs.existsSync(matrixPath) ? fs.readFileSync(matrixPath, "utf8") : "";
const changed = () => [index, cleanup, bootstrap, verifier, matrix, fs.readFileSync(__filename, "utf8")].join("\n");

const prod = "click-save-ai-production";
const projectNumber = "991489557172";
const repoId = "1314210715";
const v1 = "clicksave-auth-cleanup@click-save-ai-production.iam.gserviceaccount.com";
const v2 = "clicksave-v2-runtime@click-save-ai-production.iam.gserviceaccount.com";
const deploy = "clickandsaveai-github-deployer@click-save-ai-production.iam.gserviceaccount.com";
const buildDeferred = "DEFERRED_UNTIL_BUILD_SERVICE_INITIALIZATION";
const cloudBuildService = "cloudbuild.googleapis.com";
const defaultCloudBuild = `${projectNumber}@${["cloudbuild", "gserviceaccount.com"].join(".")}`;
const defaultComputeRuntime = `${projectNumber}-${["compute", "developer.gserviceaccount.com"].join("@")}`;
const esc = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const has = (s, r) => assert.match(s, r);
const no = (s, r) => assert.doesNotMatch(s, r);

test("1 configured Functions entry module is src/entry.js", () => {
  assert.equal(pkg.main, "src/entry.js");
});

test("2 entry loads common index before every other exported module", () => {
  const firstLocalRequire = entry.match(/require\(["'](\.\/[^"']+)["']\)/);
  assert.equal(firstLocalRequire?.[1], "./index");
  const indexRequire = entry.indexOf('require("./index")');
  assert.ok(indexRequire >= 0);
  for (const match of entry.matchAll(/const\s+[\w$]+\s*=\s*require\(["'](\.\/[^"']+)["']\)/g)) {
    if (match[1] !== "./index") assert.ok(indexRequire < match.index, `index must load before ${match[1]}`);
  }
});

test("3 common v2 global options bind exact Production SA with non-Production default", () => {
  has(index, /firebase-functions\/params/);
  has(index, /\bprojectID\b/);
  has(index, new RegExp(esc(v2)));
  has(index, new RegExp(`projectID\\s*\\.\\s*equals\\(\\s*["']${esc(prod)}["']\\s*\\)`));
  has(index, /thenElse\(\s*PRODUCTION_V2_SERVICE_ACCOUNT\s*,\s*["']default["']\s*\)/);
  has(index, /setGlobalOptions\s*\(\s*\{[\s\S]*?serviceAccount\s*:\s*productionV2ServiceAccount[\s\S]*?\}\s*\)/);
});

test("4 common v2 options are established before entry imports remaining v2 modules", () => {
  assert.ok(index.indexOf("setGlobalOptions") >= 0);
  assert.ok(entry.indexOf('require("./index")') < entry.indexOf('require("./pushFunctions")'));
});

test("5 v1 cleanup identity and trigger semantics remain exact", () => {
  has(cleanup, /require\(["']firebase-functions\/v1["']\)/);
  has(cleanup, new RegExp(esc(v1)));
  has(cleanup, new RegExp(`projectID\\s*\\.\\s*equals\\(\\s*["']${esc(prod)}["']\\s*\\)`));
  has(cleanup, /thenElse\(PRODUCTION_AUTH_CLEANUP_SERVICE_ACCOUNT,\s*["']default["']\)/);
  has(cleanup, /runWith\(\{\s*serviceAccount:\s*authCleanupServiceAccount\s*\}\)/);
  has(cleanup, /exports\.onPushAccountDeleted\s*=\s*functions[\s\S]*?\.auth\.user\(\)\.onDelete/);
});

test("6 bootstrap creates only the two exact dedicated runtime service accounts when absent", () => {
  has(bootstrap, /EXPECTED_RUNTIME_SA_ID="clicksave-auth-cleanup"/);
  has(bootstrap, /EXPECTED_V2_RUNTIME_SA_ID="clicksave-v2-runtime"/);
  has(bootstrap, new RegExp(esc(v1)));
  has(bootstrap, new RegExp(esc(v2)));
  assert.equal((bootstrap.match(/iam service-accounts create/g) || []).length, 2);
  has(bootstrap, /service-accounts create "\$EXPECTED_RUNTIME_SA_ID"/);
  has(bootstrap, /service-accounts create "\$EXPECTED_V2_RUNTIME_SA_ID"/);
});

test("7 v2 runtime bootstrap verifies zero user-managed keys and zero project roles", () => {
  has(bootstrap, /v2_runtime_roles\(\)/);
  has(bootstrap, /--iam-account="\$EXPECTED_V2_RUNTIME_SA"[\s\S]*?--managed-by=user/);
  has(bootstrap, /v2 runtime SA has unexpected project role/);
  has(bootstrap, /\[\[ "\$\{#v2_roles\[@\]\}" -eq 0 \]\]/);
  has(verifier, /dedicated v2 runtime SA has user-managed keys/);
  has(verifier, /dedicated v2 runtime SA must have zero project roles/);
});

test("8 v1 runtime keeps exactly roles/datastore.user", () => {
  has(bootstrap, /EXPECTED_RUNTIME_ROLE="roles\/datastore\.user"/);
  has(verifier, /V1_RUNTIME_ROLE="roles\/datastore\.user"/);
  has(verifier, /dedicated v1 runtime SA project roles must equal \$V1_RUNTIME_ROLE/);
});

test("9 deployer actAs is individual-SA only and covers v1/v2 runtime identities", () => {
  has(bootstrap, new RegExp(esc(deploy)));
  has(bootstrap, /iam service-accounts add-iam-policy-binding "\$sa"/);
  has(bootstrap, /--role="roles\/iam\.serviceAccountUser"/);
  no(bootstrap, /gcloud projects add-iam-policy-binding[\s\S]{0,300}roles\/iam\.serviceAccountUser/);
  has(verifier, /project-wide roles\/iam\.serviceAccountUser exists/);
});

test("10 Cloud Build enabled-service state is established before default identity discovery", () => {
  has(verifier, new RegExp(`CLOUD_BUILD_SERVICE=["']${esc(cloudBuildService)}["']`));
  has(verifier, /gcloud services list --project="\$P" --enabled --filter="config\.name:\$CLOUD_BUILD_SERVICE" --format='value\(config\.name\)'/);
  has(verifier, /gcloud builds get-default-service-account --project="\$P" --region="\$REGION" --format='value\(serviceAccountEmail\)'/);
  assert.ok(
    verifier.indexOf("gcloud services list --project=\"$P\" --enabled") <
      verifier.indexOf("gcloud builds get-default-service-account"),
    "Cloud Build enabled-service query must occur before default service-account discovery"
  );
});

test("11 disabled Cloud Build service deterministically defers without prose matching", () => {
  has(verifier, new RegExp(buildDeferred));
  has(verifier, /CLOUD_BUILD_SERVICE_ENABLED=false/);
  has(verifier, /BUILD_IDENTITY_DISCOVERY_ATTEMPTED=false/);
  has(verifier, /Cloud Build service is not enabled; build identity deferred without API enablement/);
  no(verifier, /is_build_service_uninitialized/);
  no(verifier, /SERVICE_DISABLED|has not been used in project|not initialized/i);
  has(bootstrap, new RegExp(buildDeferred));
});

test("12 empty Cloud Build identity after enabled-service discovery defers without substitution", () => {
  has(verifier, /\[\[ -z "\$BUILD_SA" \]\]/);
  has(verifier, new RegExp(buildDeferred));
  no(verifier, new RegExp(`BUILD_SA=.*${esc(defaultCloudBuild)}`));
  no(verifier, new RegExp(`BUILD_SA=.*${esc(defaultComputeRuntime)}`));
});

test("13 enabled-service Cloud Build discovery errors are hard failures independent of error prose", () => {
  has(verifier, /Cloud Build default service-account discovery failed after enabled-service verification/);
  no(verifier, /is_build_service_uninitialized/);
});

test("14 build actAs is skipped when identity status is deferred", () => {
  has(bootstrap, /PRODUCTION_BUILD_IDENTITY_STATUS/);
  has(bootstrap, /\[\[ "\$PRODUCTION_BUILD_IDENTITY_STATUS" == "READY" \]\]/);
  has(verifier, /\[\[ "\$PRODUCTION_BUILD_IDENTITY_STATUS" == "READY" \]\]/);
});

test("15 runtime target lock remains exact", () => {
  has(verifier, new RegExp(`P=${esc(prod)}`));
  has(verifier, new RegExp(`N=${projectNumber}`));
  has(verifier, new RegExp(`RID=${repoId}`));
  has(bootstrap, /PROJECT_ID must be exactly \$EXPECTED_PROJECT_ID/);
});

test("16 absent default Compute runtime identity is no longer a dependency", () => {
  no(changed(), new RegExp(esc(defaultComputeRuntime)));
});

test("17 no API enablement or App Engine initialization", () => {
  no(changed(), /gcloud\s+services\s+enable/);
  no(changed(), /gcloud\s+app\s+create/);
});

test("18 no deployment or service-account-key creation", () => {
  no(changed(), /firebase\s+deploy/);
  no(changed(), /gcloud\s+functions\s+deploy/);
  no(changed(), /service-accounts\s+keys\s+create/);
});

test("19 permission matrix records zero-role identity bootstrap and scoped Secret Manager policy", () => {
  has(matrix, new RegExp(esc(v2)));
  has(matrix, /zero project-level application roles/i);
  has(matrix, /roles\/secretmanager\.secretAccessor/);
  has(matrix, /individual secret|per-secret/i);
  no(matrix, /GRANT NOW/i);
});

test("20 changed security files contain no obvious embedded credentials", () => {
  const s = changed();
  no(s, /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/);
  no(s, /AIza[0-9A-Za-z_-]{30,}/);
  no(s, /gh[pousr]_[A-Za-z0-9]{20,}/);
});

test("21 runtime/build shell scripts remain syntactically valid", () => {
  for (const p of ["scripts/bootstrap-production-runtime-build-actas.sh", "scripts/verify-production-runtime-build-actas.sh"]) {
    const r = spawnSync("bash", ["-n", path.join(root, p)], { encoding: "utf8" });
    assert.equal(r.status, 0, r.stderr);
  }
});

test("22 service and discovery truth flags are exported and printed", () => {
  has(verifier, /printf 'CLOUD_BUILD_SERVICE_ENABLED=%q\\n' "\$CLOUD_BUILD_SERVICE_ENABLED"/);
  has(verifier, /printf 'BUILD_IDENTITY_DISCOVERY_ATTEMPTED=%q\\n' "\$BUILD_IDENTITY_DISCOVERY_ATTEMPTED"/);
  has(verifier, /productionCloudBuildServiceEnabled=%s/);
  has(verifier, /productionBuildIdentityDiscoveryAttempted=%s/);
});
