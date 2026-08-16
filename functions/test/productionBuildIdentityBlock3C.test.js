"use strict";

const crypto = require("node:crypto");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { spawnSync } = require("node:child_process");
const test = require("node:test");
const assert = require("node:assert/strict");

const root = path.resolve(__dirname, "..", "..");
const read = (p) => { const f = path.join(root, p); return fs.existsSync(f) ? fs.readFileSync(f, "utf8") : ""; };
const bootstrap = read("scripts/bootstrap-production-build-identity.sh");
const closure = read("scripts/verify-production-build-identity.sh");
const acceptedVerifier = read("scripts/verify-production-runtime-build-actas.sh");
const acceptedBootstrap = read("scripts/bootstrap-production-runtime-build-actas.sh");
const prod = "click-save-ai-production", number = "991489557172", region = "europe-west1";
const service = "cloudbuild.googleapis.com", deferred = "DEFERRED_UNTIL_BUILD_SERVICE_INITIALIZATION";
const compute = `${number}-compute@developer.gserviceaccount.com`, legacy = `${number}@cloudbuild.gserviceaccount.com`;
const deployer = `clickandsaveai-github-deployer@${prod}.iam.gserviceaccount.com`;
const acceptedBlob = "1a60a70dba55eff3423b2599c8a30810aecb79a8";
const esc = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const blobSha = (s) => { const b = Buffer.from(s); return crypto.createHash("sha1").update(`blob ${b.length}\0`).update(b).digest("hex"); };
const exe = (p, s) => fs.writeFileSync(p, s, { mode: 0o755 });
const get = (p) => Number(fs.readFileSync(p, "utf8"));
const set = (p, n) => fs.writeFileSync(p, String(n));

function fakeGit(bin) {
  exe(path.join(bin, "git"), `#!/usr/bin/env bash\n[[ "$*" == *"hash-object scripts/verify-production-runtime-build-actas.sh"* ]] || exit 2\nprintf '%s\\n' '${acceptedBlob}'\n`);
}

function bootScenario(o = {}) {
  assert.ok(bootstrap, "Block 3C bootstrap implementation is absent");
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "block3c-b-")), scripts = path.join(dir, "scripts"), bin = path.join(dir, "bin"), state = path.join(dir, "state");
  fs.mkdirSync(scripts); fs.mkdirSync(bin); fs.mkdirSync(state);
  exe(path.join(scripts, "bootstrap-production-build-identity.sh"), bootstrap);
  exe(path.join(scripts, "verify-production-build-identity.sh"), "#!/usr/bin/env bash\nset -euo pipefail\nn=$(cat \"$FAKE_STATE/closure\"); echo $((n+1)) >\"$FAKE_STATE/closure\"\n");
  exe(path.join(scripts, "bootstrap-production-runtime-build-actas.sh"), "#!/usr/bin/env bash\nset -euo pipefail\nn=$(cat \"$FAKE_STATE/runtime\"); echo $((n+1)) >\"$FAKE_STATE/runtime\"\nprintf 'runtime-bootstrap %s\\n' \"$(cat \"$FAKE_STATE/last\")\" >>\"$FAKE_STATE/events\"\n");
  exe(path.join(scripts, "verify-production-runtime-build-actas.sh"), `#!/usr/bin/env bash\nset -euo pipefail\ncat >"$DISCOVERY_OUTPUT" <<STATE\nRUNTIME_SAS=("v1@${prod}.iam.gserviceaccount.com" "v2@${prod}.iam.gserviceaccount.com")\nBUILD_SA="${o.preSa || ""}"\nPRODUCTION_BUILD_IDENTITY_STATUS="${o.preStatus || ((o.enabled && o.preSa) ? "READY" : deferred)}"\nCLOUD_BUILD_SERVICE_ENABLED=${o.enabled ? "true" : "false"}\nBUILD_IDENTITY_DISCOVERY_ATTEMPTED=${o.enabled ? "true" : "false"}\nSTATE\n`);
  for (const [f, v] of [["enabled", o.enabled ? 1 : 0], ["enable", 0], ["init", 0], ["runtime", 0], ["closure", 0], ["clock", 0]]) set(path.join(state, f), v);
  fs.writeFileSync(path.join(state, "last"), ""); fs.writeFileSync(path.join(state, "events"), "");
  fs.writeFileSync(path.join(state, "queue"), (o.reads || [`SA:${compute}`]).join("\n") + "\n");
  fs.writeFileSync(path.join(state, "init-exit"), String(o.initExit || 0));
  fakeGit(bin);
  exe(path.join(bin, "date"), '#!/usr/bin/env bash\ncat "$FAKE_STATE/clock"\n');
  exe(path.join(bin, "sleep"), '#!/usr/bin/env bash\nn=$(cat "$FAKE_STATE/clock"); echo $((n+$1)) >"$FAKE_STATE/clock"\n');
  exe(path.join(bin, "gcloud"), `#!/usr/bin/env bash
set -euo pipefail
S="$FAKE_STATE"; cmd="\${1:-} \${2:-}"
if [[ "$cmd" == "projects describe" ]]; then [[ "$*" == *"value(projectId)"* ]] && echo "${o.projectId || prod}" || echo "${o.projectNumber || number}"; exit 0; fi
if [[ "$cmd" == "services list" ]]; then [[ "$(cat "$S/enabled")" == 1 ]] && echo "${service}"; exit 0; fi
if [[ "$cmd" == "services enable" ]]; then [[ "\${3:-}" == "${service}" ]] || exit 88; n=$(cat "$S/enable"); echo $((n+1)) >"$S/enable"; echo 1 >"$S/enabled"; echo "enable ${service}" >>"$S/events"; exit 0; fi
if [[ "$cmd" == "builds get-default-service-account" ]]; then
  echo discovery >>"$S/events"; token=$(head -1 "$S/queue" || true); tail -n +2 "$S/queue" >"$S/q2"; mv "$S/q2" "$S/queue"
  if [[ -z "$token" ]]; then last=$(cat "$S/last"); [[ "$last" == EMPTY ]] && token=EMPTY || token="SA:$last"; fi; case "$token" in EMPTY) echo EMPTY >"$S/last"; exit 0;; ERR:*) exit "\${token#ERR:}";; SA:*) v="\${token#SA:}"; echo "$v" >"$S/last"; echo "$v"; exit 0;; PREFIX:*) v="\${token#PREFIX:}"; echo "$v" >"$S/last"; echo "projects/${prod}/serviceAccounts/$v"; exit 0;; *) exit 91;; esac
fi
if [[ "$cmd" == "builds submit" ]]; then
  n=$(cat "$S/init"); echo $((n+1)) >"$S/init"; printf '%s\\n' "$*" >"$S/init-args"; cfg=""; for a in "$@"; do [[ "$a" == --config=* ]] && cfg="\${a#--config=}"; done; [[ -z "$cfg" ]] || cp "$cfg" "$S/init-config"; echo submit >>"$S/events"; rc=$(cat "$S/init-exit"); [[ "$rc" == 0 ]] || exit "$rc"; echo 'init-1 SUCCESS'; exit 0
fi
exit 92
`);
  const run = (project = o.envProject || prod) => spawnSync("bash", [path.join(scripts, "bootstrap-production-build-identity.sh")], { encoding: "utf8", timeout: 10000, env: { ...process.env, PATH: `${bin}:${process.env.PATH}`, FAKE_STATE: state, PROJECT_ID: project } });
  return { dir, state, run, n: (f) => get(path.join(state, f)), text: (f) => fs.existsSync(path.join(state, f)) ? fs.readFileSync(path.join(state, f), "utf8") : "" };
}

function verifierScenario(o = {}) {
  assert.ok(closure, "Block 3C closure verifier implementation is absent");
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "block3c-v-")), scripts = path.join(dir, "scripts"), bin = path.join(dir, "bin"), state = path.join(dir, "state");
  fs.mkdirSync(scripts); fs.mkdirSync(bin); fs.mkdirSync(state); exe(path.join(scripts, "verify-production-build-identity.sh"), closure); fakeGit(bin);
  exe(path.join(scripts, "verify-production-runtime-build-actas.sh"), `#!/usr/bin/env bash\ncat >"$DISCOVERY_OUTPUT" <<STATE\nBUILD_SA="${o.sa === undefined ? compute : o.sa}"\nPRODUCTION_BUILD_IDENTITY_STATUS="${o.status || "READY"}"\nCLOUD_BUILD_SERVICE_ENABLED=${o.enabled === false ? "false" : "true"}\nBUILD_IDENTITY_DISCOVERY_ATTEMPTED=${o.attempted === false ? "false" : "true"}\nSTATE\n`);
  fs.writeFileSync(path.join(state, "policy"), JSON.stringify(o.policy || { bindings: [] })); fs.writeFileSync(path.join(state, "deployments"), JSON.stringify(o.deployments || []));
  exe(path.join(bin, "gcloud"), `#!/usr/bin/env bash\nset -euo pipefail\nif [[ "$1 $2" == "projects describe" ]]; then [[ "$*" == *"value(projectId)"* ]] && echo '${o.projectId || prod}' || echo '${o.projectNumber || number}'; exit 0; fi\n[[ "$1 $2" == "projects get-iam-policy" ]] && cat "$FAKE_STATE/policy" && exit 0\nexit 2\n`);
  exe(path.join(bin, "curl"), '#!/usr/bin/env bash\ncat "$FAKE_STATE/deployments"\n');
  const run = (project = o.envProject || prod) => spawnSync("bash", [path.join(scripts, "verify-production-build-identity.sh")], { encoding: "utf8", env: { ...process.env, PATH: `${bin}:${process.env.PATH}`, FAKE_STATE: state, PROJECT_ID: project } });
  return { run };
}

const empties = () => Array.from({ length: 31 }, () => "EMPTY");

test("accepted Block 3B.3C verifier blob is locked", { skip: process.env.BLOCK3C_LOCAL_PLACEHOLDER === "1" }, () => assert.equal(blobSha(acceptedVerifier), acceptedBlob));

test("static Block 3C contract locks exact target, dynamic discovery, and one-shot no-source initialization", () => {
  assert.match(bootstrap, /EXPECTED_PROJECT_ID="click-save-ai-production"/); assert.match(bootstrap, /EXPECTED_PROJECT_NUMBER="991489557172"/); assert.match(bootstrap, /REGION="europe-west1"/);
  assert.match(bootstrap, /CLOUD_BUILD_SERVICE="cloudbuild\.googleapis\.com"/); assert.equal((bootstrap.match(/gcloud services enable/g) || []).length, 1); assert.match(bootstrap, /gcloud builds get-default-service-account/);
  assert.match(bootstrap, /MAX_INITIALIZATION_BUILDS=1/); assert.match(bootstrap, /--no-source/); assert.match(bootstrap, /args:\s*\["-ceu",\s*"true"\]/);
  assert.doesNotMatch(`${bootstrap}\n${closure}`, /firebase deploy|gcloud functions deploy|gcloud run deploy|gcloud app create|service-accounts keys (?:create|delete)|roles\/(?:owner|editor)|roles\/iam\.serviceAccountTokenCreator/);
  assert.doesNotMatch(`${bootstrap}\n${closure}`, /add-iam-policy-binding/);
});

test("accepted boundary supports both Google-selected build SAs and validates before exact per-SA actAs", () => {
  assert.match(acceptedVerifier, new RegExp(esc(compute))); assert.match(acceptedVerifier, new RegExp(esc(legacy))); assert.match(acceptedVerifier, /identity is not owned by Production/); assert.match(acceptedVerifier, /holds forbidden role/);
  assert.ok(acceptedVerifier.indexOf("holds forbidden role") < acceptedVerifier.indexOf("missing deploy-SA roles/iam.serviceAccountUser on intended build identity"));
  assert.match(acceptedBootstrap, /iam service-accounts add-iam-policy-binding "\$sa"/); assert.match(acceptedBootstrap, /roles\/iam\.serviceAccountUser/); assert.match(acceptedBootstrap, /actAs already present on intended identity/);
  assert.doesNotMatch(acceptedBootstrap, /gcloud projects add-iam-policy-binding[\s\S]{0,300}roles\/iam\.serviceAccountUser/);
});

test("non-Production targets and verifier-blob mismatch fail before mutation", () => {
  for (const p of ["clickandsaveai-staging", "clickandsaveai", "other-project"]) { const s = bootScenario(); const r = s.run(p); assert.equal(r.status, 1); assert.equal(s.n("enable"), 0); assert.equal(s.n("init"), 0); assert.equal(s.n("runtime"), 0); }
  const s = bootScenario(); exe(path.join(s.dir, "bin", "git"), "#!/usr/bin/env bash\necho deadbeef\n"); const r = s.run(); assert.equal(r.status, 1); assert.equal(s.n("enable"), 0); assert.equal(s.n("init"), 0);
});

test("Cloud Build API enables exactly once when disabled and never duplicates when enabled", () => {
  let s = bootScenario({ reads: [`SA:${compute}`] }); let r = s.run(); assert.equal(r.status, 0, r.stderr); assert.equal(s.n("enable"), 1);
  s = bootScenario({ enabled: true, preSa: compute, reads: [`SA:${compute}`] }); r = s.run(); assert.equal(r.status, 0, r.stderr); assert.equal(s.n("enable"), 0);
});

test("immediate identity skips initialization; empty identity polls bounded then submits at most one no-source build", () => {
  let s = bootScenario({ enabled: true, preSa: compute, reads: [`PREFIX:${compute}`] }); let r = s.run(); assert.equal(r.status, 0, r.stderr); assert.equal(s.n("init"), 0); assert.equal(s.n("runtime"), 1);
  s = bootScenario({ enabled: true, reads: [...empties(), `SA:${compute}`] }); r = s.run(); assert.equal(r.status, 0, r.stderr); assert.equal(s.n("init"), 1); assert.equal(s.n("runtime"), 1); assert.match(s.text("init-args"), /--no-source/); assert.match(s.text("init-config"), /args:\s*\["-ceu",\s*"true"\]/); assert.doesNotMatch(s.text("init-config"), /images:|artifacts:|secrets:|firebase|deploy/i);
});

test("discovery command error or timeout hard-fails without initialization fallback", () => {
  for (const token of ["ERR:7", "ERR:124"]) { const s = bootScenario({ enabled: true, reads: [token] }); const r = s.run(); assert.equal(r.status, 1); assert.equal(s.n("init"), 0); assert.equal(s.n("runtime"), 0); }
});

test("initialization failure fails with empty identity but may continue if identity becomes discoverable", () => {
  let s = bootScenario({ enabled: true, reads: [...empties(), "EMPTY"], initExit: 9 }); let r = s.run(); assert.equal(r.status, 1); assert.equal(s.n("init"), 1); assert.equal(s.n("runtime"), 0);
  s = bootScenario({ enabled: true, reads: [...empties(), `SA:${legacy}`], initExit: 9 }); r = s.run(); assert.equal(r.status, 0, r.stderr); assert.equal(s.n("init"), 1); assert.equal(s.n("runtime"), 1); assert.match(s.text("events"), new RegExp(`runtime-bootstrap ${esc(legacy)}`));
});

test("fully configured rerun is idempotent at Block 3C orchestration boundary", () => {
  const s = bootScenario({ enabled: true, preSa: compute, reads: [`SA:${compute}`] }); let r = s.run(); assert.equal(r.status, 0, r.stderr); r = s.run(); assert.equal(r.status, 0, r.stderr); assert.equal(s.n("enable"), 0); assert.equal(s.n("init"), 0); assert.equal(s.n("runtime"), 2);
});

test("closure verifier requires enabled + attempted + READY + non-empty build SA and exact Production target", () => {
  for (const o of [{ enabled: false }, { attempted: false }, { status: deferred }, { sa: "" }]) { const r = verifierScenario(o).run(); assert.equal(r.status, 1, `${JSON.stringify(o)} ${r.stdout} ${r.stderr}`); }
  assert.equal(verifierScenario({ envProject: "clickandsaveai-staging" }).run().status, 1); assert.equal(verifierScenario({ sa: legacy }).run().status, 0);
});

test("closure verifier independently rejects project-wide serviceAccountUser and Production deployments", () => {
  let r = verifierScenario({ policy: { bindings: [{ role: "roles/iam.serviceAccountUser", members: [`serviceAccount:${deployer}`] }] } }).run(); assert.equal(r.status, 1); assert.match(r.stderr, /project-wide roles\/iam\.serviceAccountUser/);
  r = verifierScenario({ deployments: [{ id: 1 }] }).run(); assert.equal(r.status, 1); assert.match(r.stderr, /deployment inventory/i);
});

test("closure truth is build-ready while WIF/release/overall/deployed remain false", () => {
  const r = verifierScenario().run(); assert.equal(r.status, 0, r.stderr); for (const x of ["productionBuildIdentityReady=true", "productionBuildIdentityStatus=READY", "productionBuildIdentityConfigured=true", "productionBuildActAsConfigured=true", "productionRuntimeBuildActAsConfigured=true", "productionWifEndToEndVerified=false", "productionDeployEndToEndReady=false", "productionIdentityReady=false", "productionDeployed=false"]) assert.match(r.stdout, new RegExp(esc(x)));
});

test("Block 3C shell syntax passes", () => { for (const p of ["scripts/bootstrap-production-build-identity.sh", "scripts/verify-production-build-identity.sh"]) { const r = spawnSync("bash", ["-n", path.join(root, p)], { encoding: "utf8" }); assert.equal(r.status, 0, r.stderr); } });
