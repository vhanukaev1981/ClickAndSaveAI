#!/usr/bin/env node

import fs from "node:fs";

function fail(message, classification) {
  process.stderr.write(`CLASSIFICATION=${classification}\n${message}\n`);
  process.exit(1);
}

function parseArgs(argv) {
  const out = {};
  for (let i = 0; i < argv.length; i += 2) {
    const key = argv[i];
    const value = argv[i + 1];
    if (!key?.startsWith("--") || value === undefined) {
      fail("Invalid classifier arguments.", "FATAL_UNKNOWN");
    }
    out[key.slice(2)] = value;
  }
  return out;
}

const args = parseArgs(process.argv.slice(2));
const rawExit = args["exit-code"];
const logPath = args.log;
const functionsPath = args["write-functions"];

if (!/^\d+$/.test(rawExit || "") || !logPath || !functionsPath) {
  fail("Required arguments: --exit-code <n> --log <path> --write-functions <path>.", "FATAL_UNKNOWN");
}

const firebaseExit = Number(rawExit);
let log;
try {
  log = fs.readFileSync(logPath, "utf8");
} catch {
  fail("Firebase deploy log is unavailable.", "FATAL_UNKNOWN");
}

const successPattern = /functions\[([^\]]+)\]\s+Successful (?:create|update) operation\./g;
const successfulFunctions = [];
for (const match of log.matchAll(successPattern)) {
  const qualified = match[1];
  const name = qualified.replace(/\([^()]+\)$/, "");
  if (name && !successfulFunctions.includes(name)) successfulFunctions.push(name);
}

try {
  fs.writeFileSync(functionsPath, `${successfulFunctions.join("\n")}${successfulFunctions.length ? "\n" : ""}`, "utf8");
} catch {
  fail("Unable to write successful-function evidence.", "FATAL_UNKNOWN");
}

if (firebaseExit === 0) {
  process.stdout.write(`CLASSIFICATION=SUCCESS\nSUCCESSFUL_FUNCTION_COUNT=${successfulFunctions.length}\n`);
  process.exit(0);
}

const cleanupProof =
  /Failed to set up cleanup policy for repositories? in region us-central1\./.test(log) &&
  /Functions successfully deployed but could not set up cleanup policy in region us-central1\./.test(log);

const fatalPatterns = [
  /Functions deploy had errors with the following functions:/i,
  /Error:\s+There was an error deploying functions/i,
  /Could not create or update Cloud Run service/i,
  /Failed to (?:create|update|delete) function/i,
  /Build failed(?: with status)?/i,
  /PERMISSION_DENIED/i,
  /Permission .* denied/i,
  /Authentication Error/i,
  /invalid_grant/i,
  /Secret Manager.*(?:denied|error|failed)/i,
  /Cannot access secret/i,
  /Function failed on loading user code/i,
  /Container Healthcheck failed/i,
  /Deployment error/i,
];

if (fatalPatterns.some((pattern) => pattern.test(log))) {
  fail(
    `Firebase exited ${firebaseExit}; deploy/auth/IAM/build/secret failure evidence is present, so the failure remains fatal.`,
    "FATAL_DEPLOY_ERROR",
  );
}

if (!cleanupProof || successfulFunctions.length === 0) {
  fail(
    `Firebase exited ${firebaseExit}; cleanup-only post-success proof is incomplete.`,
    "FATAL_INSUFFICIENT_PROOF",
  );
}

const nonCleanupWarningOrError = log
  .split(/\r?\n/)
  .filter((line) => /(?:\bERROR\b|Error:|\bfailed\b|\bFailure\b)/i.test(line))
  .filter((line) => !/cleanup policy/i.test(line))
  .filter((line) => !/Functions successfully deployed but could not set up cleanup policy/i.test(line));

if (nonCleanupWarningOrError.length > 0) {
  fail(
    `Firebase exited ${firebaseExit}; non-cleanup error text is present, so the failure remains fatal.`,
    "FATAL_DEPLOY_ERROR",
  );
}

process.stdout.write(
  `CLASSIFICATION=CLEANUP_ONLY_POST_SUCCESS\nSUCCESSFUL_FUNCTION_COUNT=${successfulFunctions.length}\n`,
);
process.exit(0);
