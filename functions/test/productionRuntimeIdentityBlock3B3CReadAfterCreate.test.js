"use strict";

const crypto = require("node:crypto");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { spawnSync } = require("node:child_process");
const test = require("node:test");
const assert = require("node:assert/strict");

const root = path.resolve(__dirname, "..", "..");
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");
const bootstrap = read("scripts/bootstrap-production-runtime-build-actas.sh");
const verifier = read("scripts/verify-production-runtime-build-actas.sh");

const prod = "click-save-ai-production";
const v1 = "clicksave-auth-cleanup@click-save-ai-production.iam.gserviceaccount.com";
const v2 = "clicksave-v2-runtime@click-save-ai-production.iam.gserviceaccount.com";
const deploy = "clickandsaveai-github-deployer@click-save-ai-production.iam.gserviceaccount.com";
const role = "roles/datastore.user";
const deferred = "DEFERRED_UNTIL_BUILD_SERVICE_INITIALIZATION";
const acceptedVerifierBlob = "1a60a70dba55eff3423b2599c8a30810aecb79a8";

const gitBlobSha = (content) => {
  const body = Buffer.from(content, "utf8");
  return crypto.createHash("sha1").update(`blob ${body.length}\0`).update(body).digest("hex");
};

const helperBody = () => {
  const match = bootstrap.match(/wait_for_exact_service_account_visibility\(\) \{([\s\S]*?)\n\}/);
  assert.ok(match, "bounded service-account visibility helper must exist");
  return match[1];
};

function makeScenario(options = {}) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "clicksave-bootstrap-"));
  const scriptsDir = path.join(dir, "scripts");
  const binDir = path.join(dir, "bin");
  fs.mkdirSync(scriptsDir, { recursive: true });
  fs.mkdirSync(binDir, { recursive: true });

  const bootstrapPath = path.join(scriptsDir, "bootstrap-production-runtime-build-actas.sh");
  fs.writeFileSync(bootstrapPath, bootstrap, { mode: 0o755 });

  const verifierPath = path.join(scriptsDir, "verify-production-runtime-build-actas.sh");
  fs.writeFileSync(
    verifierPath,
    `#!/usr/bin/env bash\nset -euo pipefail\nif [[ -n "\${DISCOVERY_OUTPUT:-}" ]]; then\n  cat >"$DISCOVERY_OUTPUT" <<'STATE'\nRUNTIME_SAS=("${v1}" "${v2}")\nPRODUCTION_BUILD_IDENTITY_STATUS="${deferred}"\nBUILD_SA=""\nSTATE\nfi\nexit 0\n`,
    { mode: 0o755 }
  );

  const statePath = path.join(dir, "state.json");
  const state = {
    clock: 0,
    events: [],
    accounts: {
      [v1]: {
        exists: options.v1Exists ?? true,
        created: false,
        visibilityReadsRemaining: options.v1VisibilityReads ?? 0,
        mismatch: options.v1Mismatch ?? "",
        roles: [...(options.v1Roles ?? [role])],
        actAs: options.v1ActAs ?? false,
      },
      [v2]: {
        exists: options.v2Exists ?? true,
        created: false,
        visibilityReadsRemaining: options.v2VisibilityReads ?? 0,
        mismatch: options.v2Mismatch ?? "",
        roles: [...(options.v2Roles ?? [])],
        actAs: options.v2ActAs ?? false,
      },
    },
  };
  fs.writeFileSync(statePath, JSON.stringify(state));

  const gcloudPath = path.join(binDir, "gcloud");
  fs.writeFileSync(
    gcloudPath,
    `#!/usr/bin/env node\n"use strict";\nconst fs=require("node:fs");\nconst statePath=process.env.FAKE_GCLOUD_STATE;\nconst a=process.argv.slice(2);\nconst s=JSON.parse(fs.readFileSync(statePath,"utf8"));\nconst save=()=>fs.writeFileSync(statePath,JSON.stringify(s));\nconst event=(x)=>{s.events.push(x);};\nconst val=(prefix)=>{const x=a.find((v)=>v.startsWith(prefix+"="));return x?x.slice(prefix.length+1):"";};\nconst out=(x)=>{if(x)process.stdout.write(x+"\\n");};\nconst die=(m)=>{event("unexpected "+a.join(" "));save();console.error(m);process.exit(2);};\nif(a[0]==="iam"&&a[1]==="service-accounts"&&a[2]==="describe") {\n  const email=a[3]; const acct=s.accounts[email]; if(!acct) die("unknown account");\n  let r="";\n  if(acct.exists){\n    if(acct.created&&acct.visibilityReadsRemaining>0){acct.visibilityReadsRemaining-=1;}\n    else if(acct.mismatch){r=acct.mismatch;}\n    else r=email;\n  }\n  event("describe "+email+" -> "+(r||"empty")); save(); out(r); process.exit(0);\n}\nif(a[0]==="iam"&&a[1]==="service-accounts"&&a[2]==="create") {\n  const id=a[3]; const email=Object.keys(s.accounts).find((x)=>x.startsWith(id+"@")); if(!email) die("unknown create id");\n  const acct=s.accounts[email]; if(acct.exists) {event("create-existing "+email); save(); process.exit(1);}\n  acct.exists=true; acct.created=true; event("create "+email); save(); process.exit(0);\n}\nif(a[0]==="iam"&&a[1]==="service-accounts"&&a[2]==="keys"&&a[3]==="list") {\n  const email=val("--iam-account"); event("keys-list "+email); save(); process.exit(0);\n}\nif(a[0]==="projects"&&a[1]==="get-iam-policy") {\n  const filter=val("--filter"); const email=Object.keys(s.accounts).find((x)=>filter.includes("serviceAccount:"+x)); if(!email) die("unknown project policy filter");\n  event("project-roles "+email); save(); for(const r of s.accounts[email].roles) out(r); process.exit(0);\n}\nif(a[0]==="projects"&&a[1]==="add-iam-policy-binding") {\n  const member=val("--member"); const email=member.replace(/^serviceAccount:/,""); const r=val("--role"); if(!s.accounts[email]) die("unknown project role target");\n  if(!s.accounts[email].roles.includes(r)) s.accounts[email].roles.push(r); event("project-role-add "+email+" "+r); save(); process.exit(0);\n}\nif(a[0]==="iam"&&a[1]==="service-accounts"&&a[2]==="get-iam-policy") {\n  const email=a[3]; if(!s.accounts[email]) die("unknown actAs check target"); event("actas-check "+email); save(); if(s.accounts[email].actAs) out("serviceAccount:${deploy}"); process.exit(0);\n}\nif(a[0]==="iam"&&a[1]==="service-accounts"&&a[2]==="add-iam-policy-binding") {\n  const email=a[3]; if(!s.accounts[email]) die("unknown actAs target"); s.accounts[email].actAs=true; event("actas-add "+email); save(); process.exit(0);\n}\ndie("unsupported fake gcloud command");\n`,
    { mode: 0o755 }
  );

  fs.writeFileSync(
    path.join(binDir, "date"),
    `#!/usr/bin/env node\nconst fs=require("node:fs");const p=process.env.FAKE_GCLOUD_STATE;const s=JSON.parse(fs.readFileSync(p,"utf8"));process.stdout.write(String(s.clock)+"\\n");\n`,
    { mode: 0o755 }
  );
  fs.writeFileSync(
    path.join(binDir, "sleep"),
    `#!/usr/bin/env node\nconst fs=require("node:fs");const p=process.env.FAKE_GCLOUD_STATE;const s=JSON.parse(fs.readFileSync(p,"utf8"));const n=Number(process.argv[2]||0);s.clock+=n;s.events.push("sleep "+n);fs.writeFileSync(p,JSON.stringify(s));\n`,
    { mode: 0o755 }
  );

  const run = () => spawnSync("bash", [bootstrapPath], {
    encoding: "utf8",
    timeout: 5000,
    env: {
      ...process.env,
      PATH: `${binDir}:${process.env.PATH}`,
      FAKE_GCLOUD_STATE: statePath,
      PROJECT_ID: prod,
    },
  });
  const getState = () => JSON.parse(fs.readFileSync(statePath, "utf8"));
  return { dir, run, getState };
}

const countEvents = (events, prefix) => events.filter((x) => x.startsWith(prefix)).length;
const indexOfEvent = (events, exact) => events.findIndex((x) => x === exact);

test("v1 create tolerates one empty post-create read and becomes visible without a second create", () => {
  const s = makeScenario({ v1Exists: false, v1Roles: [], v1VisibilityReads: 1, v2Exists: true });
  const r = s.run();
  assert.equal(r.status, 0, r.stderr || r.stdout);
  const events = s.getState().events;
  assert.equal(countEvents(events, `create ${v1}`), 1);
  assert.ok(events.includes(`describe ${v1} -> empty`));
  assert.ok(events.includes(`describe ${v1} -> ${v1}`));
});

test("v1 visibility timeout is deterministic and hard-fails with the exact SA", () => {
  const s = makeScenario({ v1Exists: false, v1Roles: [], v1VisibilityReads: 999, v2Exists: true });
  const r = s.run();
  assert.equal(r.status, 1);
  assert.match(r.stderr, /Timed out waiting for newly created runtime service account to become visible/);
  assert.match(r.stderr, new RegExp(v1.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  const events = s.getState().events;
  assert.equal(countEvents(events, `create ${v1}`), 1);
  assert.equal(countEvents(events, `project-role-add ${v1}`), 0);
  assert.equal(countEvents(events, `actas-add ${v1}`), 0);
});

test("v2 create propagation uses the same bounded waiter and create occurs once", () => {
  const s = makeScenario({ v1Exists: true, v1Roles: [role], v2Exists: false, v2VisibilityReads: 1 });
  const r = s.run();
  assert.equal(r.status, 0, r.stderr || r.stdout);
  const events = s.getState().events;
  assert.equal(countEvents(events, `create ${v2}`), 1);
  assert.ok(events.includes(`describe ${v2} -> empty`));
  assert.ok(events.includes(`describe ${v2} -> ${v2}`));
});

test("non-empty mismatched identity during post-create visibility fails immediately before writes", () => {
  const wrong = "wrong-runtime@click-save-ai-production.iam.gserviceaccount.com";
  const s = makeScenario({ v1Exists: false, v1Roles: [], v1Mismatch: wrong, v2Exists: true });
  const r = s.run();
  assert.equal(r.status, 1);
  assert.match(r.stderr, /Runtime service account visibility mismatch/);
  assert.match(r.stderr, new RegExp(wrong.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  const events = s.getState().events;
  assert.equal(countEvents(events, `create ${v1}`), 1);
  assert.equal(countEvents(events, `project-role-add ${v1}`), 0);
  assert.equal(countEvents(events, `actas-add ${v1}`), 0);
});

test("v2 visibility timeout uses the same hard 30-second deadline", () => {
  const s = makeScenario({ v1Exists: true, v1Roles: [role], v2Exists: false, v2VisibilityReads: 999 });
  const r = s.run();
  assert.equal(r.status, 1);
  assert.match(r.stderr, /Timed out waiting for newly created runtime service account to become visible/);
  assert.match(r.stderr, new RegExp(v2.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  const events = s.getState().events;
  assert.equal(countEvents(events, `create ${v2}`), 1);
  assert.equal(countEvents(events, `actas-add ${v2}`), 0);
});

test("current live partial state resumes: existing v1, missing role, missing v2, build deferred", () => {
  const s = makeScenario({ v1Exists: true, v1Roles: [], v2Exists: false, v2VisibilityReads: 1 });
  const r = s.run();
  assert.equal(r.status, 0, r.stderr || r.stdout);
  assert.match(r.stdout, new RegExp(deferred));
  const state = s.getState();
  const events = state.events;
  assert.equal(countEvents(events, `create ${v1}`), 0);
  assert.equal(countEvents(events, `project-role-add ${v1} ${role}`), 1);
  assert.equal(countEvents(events, `create ${v2}`), 1);
  assert.equal(state.accounts[v1].roles.length, 1);
  assert.deepEqual(state.accounts[v1].roles, [role]);
  assert.deepEqual(state.accounts[v2].roles, []);
  assert.equal(state.accounts[v1].actAs, true);
  assert.equal(state.accounts[v2].actAs, true);
  assert.equal(events.some((x) => x.includes("get-default-service-account")), false);
});

test("role and actAs writes happen only after exact visibility", () => {
  const s = makeScenario({ v1Exists: false, v1Roles: [], v1VisibilityReads: 1, v2Exists: false, v2VisibilityReads: 1 });
  const r = s.run();
  assert.equal(r.status, 0, r.stderr || r.stdout);
  const events = s.getState().events;
  assert.ok(indexOfEvent(events, `describe ${v1} -> ${v1}`) < indexOfEvent(events, `project-role-add ${v1} ${role}`));
  assert.ok(indexOfEvent(events, `describe ${v1} -> ${v1}`) < indexOfEvent(events, `actas-add ${v1}`));
  assert.ok(indexOfEvent(events, `describe ${v2} -> ${v2}`) < indexOfEvent(events, `actas-add ${v2}`));
});

test("visibility helper is bounded to 30 seconds, polls about every 2 seconds, and contains no write", () => {
  assert.match(bootstrap, /SERVICE_ACCOUNT_VISIBILITY_TIMEOUT_SECONDS=30/);
  assert.match(bootstrap, /SERVICE_ACCOUNT_VISIBILITY_POLL_SECONDS=2/);
  const body = helperBody();
  assert.match(body, /gcloud iam service-accounts describe|describe_exact_service_account_email/);
  assert.match(body, /sleep "\$SERVICE_ACCOUNT_VISIBILITY_POLL_SECONDS"/);
  assert.doesNotMatch(body, /service-accounts create|add-iam-policy-binding|delete|services enable|keys create/);
});

test("bootstrap contains no forbidden delete, API enablement, App Engine init, deployment, or key creation", () => {
  assert.doesNotMatch(bootstrap, /gcloud\s+iam\s+service-accounts\s+delete/);
  assert.doesNotMatch(bootstrap, /gcloud\s+services\s+enable/);
  assert.doesNotMatch(bootstrap, /gcloud\s+app\s+create/);
  assert.doesNotMatch(bootstrap, /firebase\s+deploy|gcloud\s+functions\s+deploy/);
  assert.doesNotMatch(bootstrap, /service-accounts\s+keys\s+create/);
});

test("accepted verifier blob and its Cloud Build timeout implementation remain untouched", () => {
  assert.equal(gitBlobSha(verifier), acceptedVerifierBlob);
  assert.equal((verifier.match(/timeout 30s gcloud builds get-default-service-account/g) || []).length, 1);
  assert.match(verifier, /\[\[ \$BS -eq 124 \]\]/);
});

test("bootstrap syntax remains valid", () => {
  const r = spawnSync("bash", ["-n", path.join(root, "scripts/bootstrap-production-runtime-build-actas.sh")], { encoding: "utf8" });
  assert.equal(r.status, 0, r.stderr);
});

test("bootstrap remains idempotent after successful completion", () => {
  const s = makeScenario({ v1Exists: false, v1Roles: [], v1VisibilityReads: 1, v2Exists: false, v2VisibilityReads: 1 });
  const first = s.run();
  assert.equal(first.status, 0, first.stderr || first.stdout);
  const second = s.run();
  assert.equal(second.status, 0, second.stderr || second.stdout);
  const events = s.getState().events;
  assert.equal(countEvents(events, `create ${v1}`), 1);
  assert.equal(countEvents(events, `create ${v2}`), 1);
  assert.equal(countEvents(events, `project-role-add ${v1} ${role}`), 1);
  assert.equal(countEvents(events, `actas-add ${v1}`), 1);
  assert.equal(countEvents(events, `actas-add ${v2}`), 1);
});
