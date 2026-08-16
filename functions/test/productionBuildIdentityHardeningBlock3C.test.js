"use strict";
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { spawnSync } = require("node:child_process");
const test = require("node:test");
const assert = require("node:assert/strict");

const root = path.resolve(__dirname, "..", "..");
const read = (p) => { const f = path.join(root, p); return fs.existsSync(f) ? fs.readFileSync(f, "utf8") : ""; };
const hardener = read("scripts/harden-production-build-identity.sh");
const bootstrap = read("scripts/bootstrap-production-build-identity.sh");
const P = "click-save-ai-production";
const N = "991489557172";
const BUILD = `${N}-compute@developer.gserviceaccount.com`;
const LEGACY = `${N}@cloudbuild.gserviceaccount.com`;
const CUSTOM = `custom-build@${P}.iam.gserviceaccount.com`;
const V1 = `clicksave-auth-cleanup@${P}.iam.gserviceaccount.com`;
const V2 = `clicksave-v2-runtime@${P}.iam.gserviceaccount.com`;
const DEPLOY = `clickandsaveai-github-deployer@${P}.iam.gserviceaccount.com`;
const EDITOR = "roles/editor";
const BUILDER = "roles/cloudbuild.builds.builder";
const VERIFIER_BLOB = "1a60a70dba55eff3423b2599c8a30810aecb79a8";
const RUNTIME_BOOTSTRAP_BLOB = "53ecc26c2842df891699c4b3e2446dc5bd406354";
const exe = (p, s) => fs.writeFileSync(p, s, { mode: 0o755 });
const set = (p, v) => fs.writeFileSync(p, String(v));
const get = (p) => Number(fs.readFileSync(p, "utf8"));

function hardeningScenario(o = {}) {
  assert.ok(hardener, "dedicated build-identity hardener is absent");
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "block3c-hard-"));
  const scripts = path.join(dir, "scripts"), bin = path.join(dir, "bin"), state = path.join(dir, "state");
  fs.mkdirSync(scripts); fs.mkdirSync(bin); fs.mkdirSync(state);
  exe(path.join(scripts, "harden-production-build-identity.sh"), hardener);
  exe(path.join(scripts, "verify-production-runtime-build-actas.sh"), "#!/usr/bin/env bash\nexit 0\n");
  exe(path.join(scripts, "bootstrap-production-runtime-build-actas.sh"), "#!/usr/bin/env bash\nexit 0\n");
  fs.writeFileSync(path.join(state, "roles"), (o.roles || [EDITOR]).join("\n") + ((o.roles || [EDITOR]).length ? "\n" : ""));
  fs.writeFileSync(path.join(state, "policy"), JSON.stringify(o.policy || { bindings: [] }));
  fs.writeFileSync(path.join(state, "deployments"), JSON.stringify(o.deployments || []));
  fs.writeFileSync(path.join(state, "events"), "");
  set(path.join(state, "add"), 0); set(path.join(state, "remove"), 0);
  exe(path.join(bin, "git"), `#!/usr/bin/env bash\ncase "$*" in *verify-production-runtime-build-actas.sh*) echo ${VERIFIER_BLOB};; *bootstrap-production-runtime-build-actas.sh*) echo ${RUNTIME_BOOTSTRAP_BLOB};; *) exit 2;; esac\n`);
  exe(path.join(bin, "curl"), '#!/usr/bin/env bash\ncat "$FAKE_STATE/deployments"\n');
  exe(path.join(bin, "gcloud"), `#!/usr/bin/env bash
set -euo pipefail
S="$FAKE_STATE"; cmd="\${1:-} \${2:-}"; BUILD="${o.buildSa || BUILD}"
if [[ "$cmd" == "auth list" ]]; then echo test@example.com; exit 0; fi
if [[ "$cmd" == "projects describe" ]]; then [[ "$*" == *"value(projectId)"* ]] && echo "${o.projectId || P}" || echo "${o.projectNumber || N}"; exit 0; fi
if [[ "$cmd" == "services list" ]]; then [[ "${o.cloudBuildEnabled === false ? 0 : 1}" == 1 ]] && echo cloudbuild.googleapis.com; exit 0; fi
if [[ "$cmd" == "builds get-default-service-account" ]]; then [[ "${o.discoveryExit || 0}" == 0 ]] || exit ${o.discoveryExit || 0}; echo "$BUILD"; exit 0; fi
if [[ "$cmd" == "iam service-accounts" && "\${3:-}" == describe ]]; then echo "\${4:-}"; exit 0; fi
if [[ "$cmd" == "iam service-accounts" && "\${3:-}" == keys && "\${4:-}" == list ]]; then
  [[ "$*" == *"--iam-account=$BUILD"* && "${o.buildKeys || 0}" != 0 ]] && echo key-1
  exit 0
fi
if [[ "$cmd" == "iam service-accounts" && "\${3:-}" == get-iam-policy ]]; then
  acct="\${4:-}"
  if [[ "$*" == *"roles/iam.serviceAccountUser"* && ( "$acct" == "${V1}" || "$acct" == "${V2}" ) ]]; then echo "serviceAccount:${DEPLOY}"; else echo '{}'; fi
  exit 0
fi
if [[ "$cmd" == "projects get-iam-policy" ]]; then
  if [[ "$*" == *"--format=json"* ]]; then cat "$S/policy"; exit 0; fi
  if [[ "$*" == *"bindings.members=serviceAccount:$BUILD"* ]]; then cat "$S/roles"; exit 0; fi
  if [[ "$*" == *"bindings.members=serviceAccount:${V1}"* ]]; then echo roles/datastore.user; exit 0; fi
  if [[ "$*" == *"bindings.members=serviceAccount:${V2}"* ]]; then exit 0; fi
  exit 0
fi
if [[ "$cmd" == "projects add-iam-policy-binding" ]]; then
  [[ "$*" == *"--member=serviceAccount:${BUILD}"* && "$*" == *"--role=${BUILDER}"* ]] || exit 81
  n=$(cat "$S/add"); echo $((n+1)) >"$S/add"; echo add-builder >>"$S/events"
  if [[ "${o.addInvisible ? 1 : 0}" == 0 ]]; then { cat "$S/roles"; echo "${BUILDER}"; } | sed '/^$/d' | sort -u >"$S/r2"; mv "$S/r2" "$S/roles"; fi
  exit 0
fi
if [[ "$cmd" == "projects remove-iam-policy-binding" ]]; then
  [[ "$*" == *"--member=serviceAccount:${BUILD}"* && "$*" == *"--role=${EDITOR}"* ]] || exit 82
  n=$(cat "$S/remove"); echo $((n+1)) >"$S/remove"; echo remove-editor >>"$S/events"
  [[ "${o.removeExit || 0}" == 0 ]] || exit ${o.removeExit || 0}
  if [[ "${o.editorPersists ? 1 : 0}" == 0 ]]; then grep -Fvx "${EDITOR}" "$S/roles" >"$S/r2" || true; mv "$S/r2" "$S/roles"; fi
  exit 0
fi
exit 92
`);
  const run = (args = [], project = o.envProject || P) => spawnSync("bash", [path.join(scripts, "harden-production-build-identity.sh"), ...args], { encoding: "utf8", env: { ...process.env, PATH: `${bin}:${process.env.PATH}`, FAKE_STATE: state, PROJECT_ID: project } });
  return { run, state, n: (f) => get(path.join(state, f)), text: (f) => fs.readFileSync(path.join(state, f), "utf8") };
}

function bootstrapFailureScenario(o = {}) {
  assert.ok(bootstrap, "Block 3C bootstrap is absent");
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "block3c-orch-"));
  const scripts = path.join(dir, "scripts"), bin = path.join(dir, "bin"), state = path.join(dir, "state");
  fs.mkdirSync(scripts); fs.mkdirSync(bin); fs.mkdirSync(state);
  exe(path.join(scripts, "bootstrap-production-build-identity.sh"), bootstrap);
  set(path.join(state, "verify"), 0); set(path.join(state, "apply"), 0); set(path.join(state, "runtime"), 0); set(path.join(state, "closure"), 0); fs.writeFileSync(path.join(state, "events"), "");
  exe(path.join(scripts, "verify-production-build-identity.sh"), '#!/usr/bin/env bash\nn=$(cat "$FAKE_STATE/closure"); echo $((n+1)) >"$FAKE_STATE/closure"; echo closure >>"$FAKE_STATE/events"\n');
  exe(path.join(scripts, "bootstrap-production-runtime-build-actas.sh"), '#!/usr/bin/env bash\nn=$(cat "$FAKE_STATE/runtime"); echo $((n+1)) >"$FAKE_STATE/runtime"; echo runtime >>"$FAKE_STATE/events"\n');
  exe(path.join(scripts, "verify-production-runtime-build-actas.sh"), `#!/usr/bin/env bash
n=$(cat "$FAKE_STATE/verify"); n=$((n+1)); echo "$n" >"$FAKE_STATE/verify"; echo "verifier-$n" >>"$FAKE_STATE/events"
if [[ "$n" == 1 ]]; then echo 'FAIL discovered build identity ${BUILD} holds forbidden role: roles/editor' >&2; exit ${o.verifierExit || 1}; fi
cat >"$DISCOVERY_OUTPUT" <<STATE
RUNTIME_SAS=("${V1}" "${V2}")
BUILD_SA="${BUILD}"
PRODUCTION_BUILD_IDENTITY_STATUS="READY"
CLOUD_BUILD_SERVICE_ENABLED=true
BUILD_IDENTITY_DISCOVERY_ATTEMPTED=true
STATE
echo productionRuntimeActAsConfigured=true
`);
  exe(path.join(scripts, "harden-production-build-identity.sh"), `#!/usr/bin/env bash
if [[ "\${1:-}" == --preflight ]]; then echo harden-preflight >>"$FAKE_STATE/events"; echo 'productionBuildIdentityHardeningState=${o.preflightState || "EDITOR_ONLY"}'; exit ${o.preflightExit || 0}; fi
n=$(cat "$FAKE_STATE/apply"); echo $((n+1)) >"$FAKE_STATE/apply"; echo harden-apply >>"$FAKE_STATE/events"; exit ${o.applyExit || 0}
`);
  exe(path.join(bin, "git"), `#!/usr/bin/env bash\ncase "$*" in *verify-production-runtime-build-actas.sh*) echo ${VERIFIER_BLOB};; *bootstrap-production-runtime-build-actas.sh*) echo ${RUNTIME_BOOTSTRAP_BLOB};; *) exit 2;; esac\n`);
  exe(path.join(bin, "date"), '#!/usr/bin/env bash\necho 0\n'); exe(path.join(bin, "sleep"), '#!/usr/bin/env bash\nexit 0\n');
  exe(path.join(bin, "gcloud"), `#!/usr/bin/env bash
set -euo pipefail
if [[ "$1 $2" == "projects describe" ]]; then [[ "$*" == *"value(projectId)"* ]] && echo '${P}' || echo '${N}'; exit 0; fi
if [[ "$1 $2" == "services list" ]]; then echo cloudbuild.googleapis.com; exit 0; fi
if [[ "$1 $2" == "builds get-default-service-account" ]]; then echo '${BUILD}'; exit 0; fi
exit 92
`);
  const run = () => spawnSync("bash", [path.join(scripts, "bootstrap-production-build-identity.sh")], { encoding: "utf8", timeout: 10000, env: { ...process.env, PATH: `${bin}:${process.env.PATH}`, FAKE_STATE: state, PROJECT_ID: P } });
  return { run, n: (f) => get(path.join(state, f)), events: () => fs.readFileSync(path.join(state, "events"), "utf8") };
}

test("hardening static contract is narrowly bounded", () => {
  assert.ok(hardener, "dedicated hardener missing");
  for (const token of [P, N, BUILD, BUILDER, EDITOR, VERIFIER_BLOB, RUNTIME_BOOTSTRAP_BLOB, "--preflight"]) assert.match(hardener, new RegExp(token.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  assert.match(bootstrap, /harden-production-build-identity\.sh/);
  assert.match(bootstrap, /productionBuildIdentityHardeningState/);
  assert.doesNotMatch(hardener, /services enable|firebase deploy|gcloud functions deploy|gcloud run deploy|gcloud app create|service-accounts keys (?:create|delete)/);
  assert.doesNotMatch(hardener, /--role="roles\/(?:owner|iam\.serviceAccountTokenCreator|[^\"]+\.serviceAgent)"/);
});

test("editor only adds builder before removing editor", () => {
  const s = hardeningScenario({ roles: [EDITOR] }); const r = s.run(); assert.equal(r.status, 0, `${r.stdout}\n${r.stderr}`); assert.equal(s.n("add"), 1); assert.equal(s.n("remove"), 1); assert.match(s.text("events"), /add-builder[\s\S]*remove-editor/); assert.equal(s.text("roles").trim(), BUILDER);
});

test("editor plus builder removes editor without duplicate builder", () => { const s = hardeningScenario({ roles: [EDITOR, BUILDER] }); const r = s.run(); assert.equal(r.status, 0, r.stderr); assert.equal(s.n("add"), 0); assert.equal(s.n("remove"), 1); assert.equal(s.text("roles").trim(), BUILDER); });
test("builder only is an idempotent no-op", () => { const s = hardeningScenario({ roles: [BUILDER] }); let r = s.run(); assert.equal(r.status, 0, r.stderr); r = s.run(); assert.equal(r.status, 0, r.stderr); assert.equal(s.n("add"), 0); assert.equal(s.n("remove"), 0); });
test("missing editor and builder fails without mutation", () => { const s = hardeningScenario({ roles: [] }); const r = s.run(); assert.equal(r.status, 1); assert.equal(s.n("add"), 0); assert.equal(s.n("remove"), 0); });
test("unknown extra role fails before mutation", () => { const s = hardeningScenario({ roles: [EDITOR, "roles/viewer"] }); const r = s.run(); assert.equal(r.status, 1); assert.equal(s.n("add"), 0); assert.equal(s.n("remove"), 0); });
test("custom and legacy build identities never enter compute editor remediation", () => { for (const buildSa of [CUSTOM, LEGACY]) { const s = hardeningScenario({ buildSa, roles: [EDITOR] }); const r = s.run(); assert.equal(r.status, 1); assert.equal(s.n("add"), 0); assert.equal(s.n("remove"), 0); } });
test("wrong project, project-wide serviceAccountUser, and build key fail before mutation", () => {
  let s = hardeningScenario(); let r = s.run([], "other-project"); assert.equal(r.status, 1); assert.equal(s.n("add"), 0);
  s = hardeningScenario({ policy: { bindings: [{ role: "roles/iam.serviceAccountUser", members: [`serviceAccount:${DEPLOY}`] }] } }); r = s.run(); assert.equal(r.status, 1); assert.equal(s.n("add"), 0);
  s = hardeningScenario({ buildKeys: 1 }); r = s.run(); assert.equal(r.status, 1); assert.equal(s.n("add"), 0);
});
test("builder must be visible before editor removal", () => { const s = hardeningScenario({ roles: [EDITOR], addInvisible: true }); const r = s.run(); assert.equal(r.status, 1); assert.equal(s.n("add"), 1); assert.equal(s.n("remove"), 0); });
test("remove failure and post-remove editor visibility hard fail", () => {
  let s = hardeningScenario({ roles: [EDITOR, BUILDER], removeExit: 9 }); let r = s.run(); assert.notEqual(r.status, 0); assert.equal(s.n("remove"), 1);
  s = hardeningScenario({ roles: [EDITOR, BUILDER], editorPersists: true }); r = s.run(); assert.notEqual(r.status, 0); assert.equal(s.n("remove"), 1);
});
test("preflight reports exact state without mutation", () => { const s = hardeningScenario({ roles: [EDITOR] }); const r = s.run(["--preflight"]); assert.equal(r.status, 0, r.stderr); assert.match(r.stdout, /productionBuildIdentityHardeningState=EDITOR_ONLY/); assert.equal(s.n("add"), 0); assert.equal(s.n("remove"), 0); });
test("live editor verifier failure remediates only after explicit preflight and reruns verifier before runtime", () => {
  const s = bootstrapFailureScenario(); const r = s.run(); assert.equal(r.status, 0, `${r.stdout}\n${r.stderr}`); assert.equal(s.n("apply"), 1); assert.equal(s.n("runtime"), 1); assert.equal(s.n("closure"), 1); assert.match(s.events(), /verifier-1[\s\S]*harden-preflight[\s\S]*harden-apply[\s\S]*verifier-2[\s\S]*runtime[\s\S]*closure/);
});
test("arbitrary verifier failure is not treated as remediable", () => { const s = bootstrapFailureScenario({ preflightState: "BUILDER_ONLY" }); const r = s.run(); assert.equal(r.status, 1); assert.equal(s.n("apply"), 0); assert.equal(s.n("runtime"), 0); });
test("hardening apply failure prevents later runtime/bootstrap execution", () => { const s = bootstrapFailureScenario({ applyExit: 9 }); const r = s.run(); assert.equal(r.status, 1); assert.equal(s.n("runtime"), 0); assert.equal(s.n("closure"), 0); });
test("no Owner/Editor grants, service-account key mutation, deployment, App Engine init, or extra API enablement", () => {
  assert.doesNotMatch(hardener, /projects add-iam-policy-binding[\s\S]{0,300}--role="roles\/(?:owner|editor)"/);
  assert.doesNotMatch(hardener, /service-accounts keys (?:create|delete)|firebase deploy|functions deploy|run deploy|app create|services enable/);
});
test("affected shell syntax passes", () => { for (const p of ["scripts/harden-production-build-identity.sh", "scripts/bootstrap-production-build-identity.sh"]) { const r = spawnSync("bash", ["-n", path.join(root, p)], { encoding: "utf8" }); assert.equal(r.status, 0, r.stderr); } });
