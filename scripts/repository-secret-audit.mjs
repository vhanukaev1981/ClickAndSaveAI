import { execFileSync } from "node:child_process";
import fs from "node:fs";

const mode = process.argv[2] || "current";
if (!new Set(["current", "history"]).has(mode)) {
  console.error("Usage: node scripts/repository-secret-audit.mjs <current|history>");
  process.exit(64);
}

const exclusions = [
  ":!*.md",
  ":!*.example",
  ":!functions/test/**",
  ":!app/src/test/**",
  ":!app/src/androidTest/**",
  ":!scripts/repository-secret-audit.mjs",
];

const detectors = [
  { type: "PRIVATE_KEY", severity: "CRITICAL", pattern: "-----BEGIN (RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----" },
  { type: "SERVICE_ACCOUNT_JSON", severity: "CRITICAL", pattern: "\"type\"[[:space:]]*:[[:space:]]*\"service_account\"" },
  { type: "OAUTH_CLIENT_SECRET", severity: "HIGH", pattern: "\"client_secret\"[[:space:]]*:[[:space:]]*\"[^\"]{8,}\"" },
  { type: "REFRESH_TOKEN", severity: "CRITICAL", pattern: "\"refresh_token\"[[:space:]]*:[[:space:]]*\"[^\"]{8,}\"" },
  { type: "ACCESS_TOKEN", severity: "HIGH", pattern: "\"access_token\"[[:space:]]*:[[:space:]]*\"[^\"]{8,}\"" },
  { type: "GITHUB_TOKEN", severity: "CRITICAL", pattern: "(gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})" },
  { type: "GOOGLE_API_KEY", severity: "HIGH", pattern: "AIza[0-9A-Za-z_-]{30,}" },
  { type: "HARDCODED_PASSWORD", severity: "HIGH", pattern: "(password|passwd|pwd)[A-Za-z0-9_]*[[:space:]]*[:=][[:space:]]*[\"'][^\"'$<{][^\"']{7,}[\"']" },
];

function runGit(args, allowNoMatch = false) {
  try {
    return execFileSync("git", args, { encoding: "utf8", maxBuffer: 64 * 1024 * 1024 }).trim();
  } catch (error) {
    if (allowNoMatch && error.status === 1) return "";
    throw error;
  }
}

const commits = mode === "history"
  ? runGit(["rev-list", "--all", "--reverse"]).split(/\s+/).filter(Boolean)
  : [runGit(["rev-parse", "HEAD"])];

const findings = new Map();
for (const commit of commits) {
  for (const detector of detectors) {
    const output = runGit([
      "grep", "-I", "-l", "-E", "-e", detector.pattern, commit, "--", ".", ...exclusions,
    ], true);
    for (const path of output.split("\n").map((value) => value.trim()).filter(Boolean)) {
      const key = `${detector.type}|${path}`;
      if (!findings.has(key)) findings.set(key, { ...detector, commit, path });
    }
  }

  const trackedNames = runGit(["ls-tree", "-r", "--name-only", commit]).split("\n").filter(Boolean);
  for (const path of trackedNames) {
    if (/\.(jks|keystore|p12|pfx)$/i.test(path)) {
      const key = `KEYSTORE_FILE|${path}`;
      if (!findings.has(key)) findings.set(key, { type: "KEYSTORE_FILE", severity: "CRITICAL", commit, path });
    }
    if (/(^|\/)(service[-_]?account|credentials?)[^/]*\.json$/i.test(path) && !/\.example$/i.test(path)) {
      const key = `CREDENTIAL_FILE|${path}`;
      if (!findings.has(key)) findings.set(key, { type: "CREDENTIAL_FILE", severity: "CRITICAL", commit, path });
    }
    if (/(^|\/)\.env$/i.test(path)) {
      const key = `DOTENV_FILE|${path}`;
      if (!findings.has(key)) findings.set(key, { type: "DOTENV_FILE", severity: "HIGH", commit, path });
    }
  }
}

const result = {
  mode,
  commitsScanned: commits.length,
  findings: [...findings.values()].map(({ pattern: _pattern, ...safe }) => safe),
};
const outputPath = process.env.SECRET_AUDIT_OUTPUT || `secret-audit-${mode}.json`;
fs.writeFileSync(outputPath, `${JSON.stringify(result, null, 2)}\n`, { mode: 0o600 });

console.log(`Secret audit mode=${mode} commits=${result.commitsScanned} findings=${result.findings.length}`);
for (const finding of result.findings) {
  console.error(`${finding.severity} ${finding.type} ${finding.commit} ${finding.path}`);
}
if (result.findings.length > 0) process.exit(2);
