#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const TARGETS = new Set(["functions", "firestore-rules", "configuration", "android"]);
const SHA_PATTERN = /^[0-9a-f]{40}$/;
const FORBIDDEN_KEY_TERMS = [
  "token",
  "secretvalue",
  "password",
  "privatekey",
  "credential",
  "apikey",
  "authorizationcode",
];

function parseArgs(argv) {
  const args = {};
  for (let index = 0; index < argv.length; index += 1) {
    const value = argv[index];
    if (!value.startsWith("--")) throw new Error(`Unexpected argument: ${value}`);
    const next = argv[index + 1];
    if (!next || next.startsWith("--")) throw new Error(`Missing value for ${value}`);
    args[value.slice(2)] = next;
    index += 1;
  }
  return args;
}

function normalizedKey(key) {
  return String(key || "").replace(/[^a-z0-9]/gi, "").toLowerCase();
}

function assertNoSecretBearingKeys(value, trail = "manifest") {
  if (!value || typeof value !== "object") return;
  if (Array.isArray(value)) {
    value.forEach((item, index) => assertNoSecretBearingKeys(item, `${trail}[${index}]`));
    return;
  }
  for (const [key, item] of Object.entries(value)) {
    const normalized = normalizedKey(key);
    if (FORBIDDEN_KEY_TERMS.some((term) => normalized.includes(term)) && key !== "secretValuesStored") {
      throw new Error(`Secret-bearing recovery metadata key is prohibited: ${trail}.${key}`);
    }
    assertNoSecretBearingKeys(item, `${trail}.${key}`);
  }
}

function requireSha(value, field) {
  if (!SHA_PATTERN.test(String(value || ""))) throw new Error(`${field} must be an exact 40-character lowercase Git SHA.`);
  return String(value);
}

function validateManifest(manifest) {
  if (!manifest || typeof manifest !== "object" || Array.isArray(manifest)) {
    throw new Error("Recovery manifest must be a JSON object.");
  }
  assertNoSecretBearingKeys(manifest);
  if (manifest.manifestVersion !== 1) throw new Error("Unsupported recovery manifestVersion.");
  const sourceSha = requireSha(manifest.sourceSha, "sourceSha");
  const surfaces = manifest.surfaces || {};
  for (const name of ["functions", "firestoreRules", "firestoreIndexes"]) {
    const surfaceSha = requireSha(surfaces?.[name]?.sourceSha, `surfaces.${name}.sourceSha`);
    if (surfaceSha !== sourceSha) {
      throw new Error(`surfaces.${name}.sourceSha must match the immutable release sourceSha.`);
    }
  }
  if (surfaces?.configuration?.secretValuesStored !== false) {
    throw new Error("Recovery manifest must state secretValuesStored=false.");
  }
  if (!Number.isInteger(surfaces?.android?.versionCode) || surfaces.android.versionCode < 1) {
    throw new Error("Android versionCode must be a positive integer.");
  }
  if (!String(surfaces?.android?.versionName || "").trim()) {
    throw new Error("Android versionName is required.");
  }
  return { ...manifest, sourceSha };
}

function sourceRestoreSteps(manifest, deployTarget) {
  return [
    `Check out exact immutable source SHA ${manifest.sourceSha}.`,
    "Verify the checked-out SHA, release manifest, environment identity, and operator authorization before any environment action.",
    `Review the source-controlled ${deployTarget} diff against the currently deployed production state.`,
    `After owner approval, redeploy only ${deployTarget} from that exact SHA using the governed production deployment workflow.`,
    "Capture deployed revision/ruleset identity and post-recovery verification evidence.",
  ];
}

function buildPlan(manifest, target) {
  const common = {
    plannerVersion: 1,
    mode: "plan",
    executionState: "NOT_EXECUTED",
    verificationState: "PRODUCTION_VERIFICATION_REQUIRED",
    sourceSha: manifest.sourceSha,
    target,
  };

  if (target === "functions") {
    return {
      ...common,
      strategy: "KNOWN_GOOD_SOURCE_REDEPLOY",
      steps: sourceRestoreSteps(manifest, "Firebase Functions"),
    };
  }

  if (target === "firestore-rules") {
    return {
      ...common,
      strategy: "KNOWN_GOOD_RULESET_RESTORE",
      steps: [
        ...sourceRestoreSteps(manifest, "Firestore Rules"),
        "Where owner policy permits, the Firebase Rules release history may also be used to select the previously verified ruleset; record the exact ruleset identity.",
      ],
    };
  }

  if (target === "configuration") {
    return {
      ...common,
      strategy: "DECLARED_CONFIGURATION_REBIND",
      steps: [
        "Resolve the known-good non-secret configuration version and secret-version references from authorized production records.",
        "Never copy secret values into the repository, manifest, logs, issue comments, or recovery output.",
        "Compare current and known-good configuration identifiers before change.",
        "Apply the approved configuration binding through the governed production workflow.",
        "Restart or redeploy only components that require the restored binding, then verify behavior and capture evidence.",
      ],
    };
  }

  return {
    ...common,
    strategy: "PLAY_HALT_PLUS_FORWARD_FIX",
    currentVersionCode: manifest.surfaces.android.versionCode,
    steps: [
      "If Google Play rollout controls permit, halt the affected staged or fully rolled-out release to reduce further exposure.",
      "Do not claim that halting the rollout downgrades devices that already installed the affected build.",
      `Build a corrective signed production artifact from a reviewed source revision using a higher versionCode than ${manifest.surfaces.android.versionCode}.`,
      "Verify production signing identity, production Firebase/OAuth configuration, App Check/Play Integrity readiness, and release artifact digest before upload.",
      "Publish only after explicit owner approval through the protected production release process.",
      "Verify rollout health and retain the corrective release as a new known-good candidate only after production evidence is captured.",
    ],
  };
}

export function createRecoveryPlan({ manifestPath, target, mode }) {
  if (mode !== "plan") throw new Error("Only --mode plan is supported. This script never executes production recovery.");
  if (!TARGETS.has(target)) throw new Error(`--target must be one of: ${[...TARGETS].join(", ")}`);
  if (!manifestPath) throw new Error("--manifest is required.");
  const absolute = path.resolve(manifestPath);
  const manifest = validateManifest(JSON.parse(fs.readFileSync(absolute, "utf8")));
  return buildPlan(manifest, target);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  try {
    const args = parseArgs(process.argv.slice(2));
    const plan = createRecoveryPlan({
      manifestPath: args.manifest,
      target: args.target,
      mode: args.mode,
    });
    process.stdout.write(`${JSON.stringify(plan, null, 2)}\n`);
  } catch (error) {
    process.stderr.write(`Production recovery planner refused request: ${error instanceof Error ? error.message : String(error)}\n`);
    process.exitCode = 1;
  }
}
