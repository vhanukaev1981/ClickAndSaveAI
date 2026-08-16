"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const root = path.resolve(__dirname, "..", "..");
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");

const design = read("docs/superpowers/specs/2026-08-15-production-enablement-block3b3c-v2-runtime-identity-design.md");
const plan = read("docs/superpowers/plans/2026-08-15-production-enablement-block3b3c-v2-runtime-identity.md");
const verifier = read("scripts/verify-production-runtime-build-actas.sh");
const docs = `${design}\n${plan}`;

const serviceQuery = "gcloud services list";
const identityDiscovery = "gcloud builds get-default-service-account";
const boundedIdentityDiscovery = "timeout 30s gcloud builds get-default-service-account";
const deferred = "DEFERRED_UNTIL_BUILD_SERVICE_INITIALIZATION";

const getServiceStateSections = () => {
  const stateCase = verifier.indexOf('case "$CLOUD_BUILD_SERVICE_STATE" in');
  const disabledBranch = verifier.indexOf("DISABLED)", stateCase);
  const enabledBranch = verifier.indexOf("ENABLED)", disabledBranch);
  const caseEnd = verifier.indexOf("\nesac", enabledBranch);
  return {
    stateCase,
    disabledBranch,
    enabledBranch,
    caseEnd,
    disabledSection: verifier.slice(disabledBranch, enabledBranch),
    enabledSection: verifier.slice(enabledBranch, caseEnd),
  };
};

test("Block 3B.3C contract documents require service-state-before-build-identity discovery", () => {
  for (const source of [design, plan]) {
    assert.match(source, /cloudbuild\.googleapis\.com/);
    assert.match(source, /gcloud services list/);
    assert.match(source, /gcloud builds get-default-service-account/);
    assert.ok(
      source.indexOf(serviceQuery) < source.indexOf(identityDiscovery),
      "Cloud Build service-state query must be documented before identity discovery"
    );
  }
});

test("disabled Cloud Build service is deferred without invoking identity discovery", () => {
  assert.match(docs, /service (?:is )?not enabled/i);
  assert.match(docs, new RegExp(deferred));
  assert.match(docs, /discovery (?:is )?not attempted|must not be invoked|is not invoked/i);
  assert.match(docs, /productionCloudBuildServiceEnabled=false/);
  assert.match(docs, /productionBuildIdentityDiscoveryAttempted=false/);
});

test("enabled-service discovery failure is a hard failure independent of gcloud prose", () => {
  assert.match(docs, /enabled-service identity discovery command fails[^\n]*hard FAIL/i);
  assert.doesNotMatch(docs, /stderr clearly indicates/i);
  assert.doesNotMatch(docs, /SERVICE_DISABLED|has not been used in project/i);
  assert.doesNotMatch(docs, /fails specifically because Cloud Build is not initialized\/enabled/i);
});

test("verifier implementation and contract documents reject the retired prose classifier", () => {
  for (const source of [verifier, design, plan]) {
    assert.doesNotMatch(source, /is_build_service_uninitialized/);
  }
});

test("runtime verifier uses an explicit Cloud Build service-state machine", () => {
  assert.match(verifier, /CLOUD_BUILD_SERVICE_STATE="UNKNOWN"/);
  assert.match(verifier, /--filter="config\.name=\$CLOUD_BUILD_SERVICE"/);
  assert.match(verifier, /CLOUD_BUILD_SERVICE_STATE="DISABLED"/);
  assert.match(verifier, /CLOUD_BUILD_SERVICE_STATE="ENABLED"/);
  assert.match(verifier, /case "\$CLOUD_BUILD_SERVICE_STATE" in/);
  assert.match(verifier, /DISABLED\)/);
  assert.match(verifier, /ENABLED\)/);
});

test("runtime verifier keeps build identity discovery inside the ENABLED state only", () => {
  const { stateCase, disabledBranch, enabledBranch, caseEnd } = getServiceStateSections();
  const discovery = verifier.indexOf(identityDiscovery, stateCase);

  assert.ok(stateCase >= 0, "explicit service-state case is required");
  assert.ok(disabledBranch > stateCase, "DISABLED branch is required");
  assert.ok(enabledBranch > disabledBranch, "ENABLED branch must follow DISABLED");
  assert.ok(discovery > enabledBranch, "build identity discovery must occur only after entering ENABLED");
  assert.ok(caseEnd > discovery, "build identity discovery must remain inside the service-state case");
});

test("build identity discovery has exactly one hard 30 second timeout and only in ENABLED", () => {
  const { disabledSection, enabledSection } = getServiceStateSections();
  assert.equal((verifier.match(/timeout 30s gcloud builds get-default-service-account/g) || []).length, 1);
  assert.doesNotMatch(disabledSection, /\btimeout\b|get-default-service-account/);
  assert.match(enabledSection, /timeout 30s gcloud builds get-default-service-account/);
  assert.match(enabledSection, /BS=\$\?/);
  assert.match(enabledSection, /\[\[ \$BS -eq 124 \]\]/);
  assert.match(enabledSection, /fatal ['"]Cloud Build default service-account discovery timed out after 30 seconds['"]/);
  assert.match(enabledSection, /Cloud Build default service-account discovery failed with exit code \$BS/);
  assert.match(enabledSection, /BUILD_DISCOVERY_ERROR=/);
});

test("verifier emits all five exact boolean truth keys on successful output paths", () => {
  for (const key of [
    "productionRuntimeIdentityConfigured",
    "productionRuntimeActAsConfigured",
    "productionBuildIdentityConfigured",
    "productionBuildActAsConfigured",
    "productionRuntimeBuildActAsConfigured",
  ]) {
    assert.match(verifier, new RegExp(`printf '${key}=%s\\\\n'`));
  }
  assert.match(verifier, /productionRuntimeIdentityStatus=%s/);
});

test("runtime identity truth is false by default and true only after both exact runtimes validated", () => {
  assert.match(verifier, /PRODUCTION_RUNTIME_IDENTITY_CONFIGURED=false/);
  assert.match(
    verifier,
    /if \[\[ -n "\$V1_EMAIL" && -n "\$V2_EMAIL" \]\]; then\s+PRODUCTION_RUNTIME_IDENTITY_CONFIGURED=true\s+fi/
  );
});

test("runtime actAs truth requires configured runtimes and both exact individual bindings", () => {
  assert.match(verifier, /PRODUCTION_RUNTIME_ACTAS_CONFIGURED=false/);
  assert.match(
    verifier,
    /if \[\[ "\$PRODUCTION_RUNTIME_IDENTITY_CONFIGURED" == true \]\] && has_actas "\$V1_RUNTIME_SA" && has_actas "\$V2_RUNTIME_SA"; then\s+PRODUCTION_RUNTIME_ACTAS_CONFIGURED=true\s+fi/
  );
});

test("build identity and build actAs truth remain false while deferred and require READY plus exact binding", () => {
  assert.match(verifier, /PRODUCTION_BUILD_IDENTITY_CONFIGURED=false/);
  assert.match(
    verifier,
    /if \[\[ "\$PRODUCTION_BUILD_IDENTITY_STATUS" == "READY" && -n "\$BUILD_SA" \]\]; then\s+PRODUCTION_BUILD_IDENTITY_CONFIGURED=true\s+fi/
  );
  assert.match(verifier, /PRODUCTION_BUILD_ACTAS_CONFIGURED=false/);
  assert.match(
    verifier,
    /if \[\[ "\$PRODUCTION_BUILD_IDENTITY_CONFIGURED" == true \]\] && has_actas "\$BUILD_SA"; then\s+PRODUCTION_BUILD_ACTAS_CONFIGURED=true\s+fi/
  );
});

test("aggregate runtime/build actAs truth is the logical AND of the four component booleans", () => {
  assert.match(verifier, /PRODUCTION_RUNTIME_BUILD_ACTAS_CONFIGURED=false/);
  assert.match(
    verifier,
    /if \[\[ "\$PRODUCTION_RUNTIME_IDENTITY_CONFIGURED" == true &&\s+"\$PRODUCTION_RUNTIME_ACTAS_CONFIGURED" == true &&\s+"\$PRODUCTION_BUILD_IDENTITY_CONFIGURED" == true &&\s+"\$PRODUCTION_BUILD_ACTAS_CONFIGURED" == true \]\]; then\s+PRODUCTION_RUNTIME_BUILD_ACTAS_CONFIGURED=true\s+fi/
  );
});

test("verifier implementation performs no API enablement, App Engine initialization, deployment, or key creation", () => {
  assert.doesNotMatch(verifier, /gcloud\s+services\s+enable/);
  assert.doesNotMatch(verifier, /gcloud\s+app\s+create/);
  assert.doesNotMatch(verifier, /firebase\s+deploy|gcloud\s+functions\s+deploy/);
  assert.doesNotMatch(verifier, /service-accounts\s+keys\s+create/);
});
