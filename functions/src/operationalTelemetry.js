"use strict";

const crypto = require("node:crypto");
const logger = require("firebase-functions/logger");

const SCHEMA_VERSION = 1;
const MAX_STRING_LENGTH = 512;
const MAX_ARRAY_LENGTH = 20;
const MAX_OBJECT_KEYS = 40;
const MAX_DEPTH = 5;
const EVENT_PATTERN = /^[a-z][a-z0-9_-]*(?:\.[a-z0-9_-]+)+$/;
const SUBSYSTEM_PATTERN = /^[a-z][a-z0-9_-]{0,31}$/;
const CODE_PATTERN = /^[A-Z][A-Z0-9_]{2,95}$/;
const VALID_SEVERITIES = new Set(["DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"]);
const VALID_OUTCOMES = new Set([
  "success",
  "failure",
  "retry_required",
  "skipped",
  "partial",
  "blocked",
  "no_op",
  "queued",
  "delivered",
  "not_delivered",
  "degraded",
  "unknown",
]);

function normalizedKey(key) {
  return String(key || "").replace(/[^a-z0-9]/gi, "").toLowerCase();
}

function isForbiddenDetailKey(key) {
  const normalized = normalizedKey(key);
  if (!normalized) return false;
  return [
    "token",
    "secret",
    "authorizationcode",
    "authcode",
    "password",
    "email",
    "credential",
    "encryptionkey",
    "apikey",
    "requestbody",
    "requestheaders",
    "authorizationheader",
  ].some((term) => normalized.includes(term));
}

function boundedString(value) {
  return String(value).slice(0, MAX_STRING_LENGTH);
}

function sanitizeOperationalDetails(value, depth = 0, seen = new WeakSet()) {
  if (value === null || value === undefined) return value;
  if (depth >= MAX_DEPTH) return "[TRUNCATED]";
  if (typeof value === "string") return boundedString(value);
  if (typeof value === "number" || typeof value === "boolean") return value;
  if (typeof value === "bigint") return boundedString(value);
  if (typeof value !== "object") return boundedString(value);
  if (seen.has(value)) return "[CIRCULAR]";
  seen.add(value);

  if (Array.isArray(value)) {
    return value
      .slice(0, MAX_ARRAY_LENGTH)
      .map((item) => sanitizeOperationalDetails(item, depth + 1, seen));
  }

  const output = {};
  for (const [key, item] of Object.entries(value).slice(0, MAX_OBJECT_KEYS)) {
    if (isForbiddenDetailKey(key)) continue;
    const sanitized = sanitizeOperationalDetails(item, depth + 1, seen);
    if (sanitized !== undefined) output[key] = sanitized;
  }
  return output;
}

function actorRef(uid) {
  const normalized = String(uid || "").trim();
  if (!normalized) return null;
  return crypto
    .createHash("sha256")
    .update(`clickandsaveai:operations:v1:${normalized}`, "utf8")
    .digest("hex")
    .slice(0, 24);
}

function requireField(value, name, pattern) {
  const normalized = String(value || "").trim();
  if (!normalized || (pattern && !pattern.test(normalized))) {
    throw new TypeError(`Invalid operational telemetry ${name}.`);
  }
  return normalized;
}

function buildOperationalPayload(input = {}) {
  const event = requireField(input.event, "event", EVENT_PATTERN);
  const subsystem = requireField(input.subsystem, "subsystem", SUBSYSTEM_PATTERN);
  const outcome = requireField(input.outcome, "outcome");
  const severity = requireField(input.severity, "severity");
  const code = requireField(input.code, "code", CODE_PATTERN);

  if (!VALID_OUTCOMES.has(outcome)) {
    throw new TypeError("Invalid operational telemetry outcome.");
  }
  if (!VALID_SEVERITIES.has(severity)) {
    throw new TypeError("Invalid operational telemetry severity.");
  }

  const payload = {
    schemaVersion: SCHEMA_VERSION,
    event,
    subsystem,
    outcome,
    severity,
    code,
  };

  const correlationId = String(input.correlationId || "").trim();
  if (correlationId) payload.correlationId = correlationId.slice(0, 128);

  const pseudonymousActor = actorRef(input.uid);
  if (pseudonymousActor) payload.actorRef = pseudonymousActor;

  if (input.details !== undefined) {
    payload.details = sanitizeOperationalDetails(input.details);
  }

  return payload;
}

function emitOperationalEvent(input) {
  const payload = buildOperationalPayload(input);
  const message = `Operational event: ${payload.event}`;

  switch (payload.severity) {
    case "DEBUG":
      logger.debug(message, payload);
      break;
    case "WARNING":
      logger.warn(message, payload);
      break;
    case "ERROR":
    case "CRITICAL":
      logger.error(message, payload);
      break;
    case "INFO":
    default:
      logger.info(message, payload);
      break;
  }

  return payload;
}

module.exports = {
  actorRef,
  buildOperationalPayload,
  emitOperationalEvent,
  _sanitizeOperationalDetails: sanitizeOperationalDetails,
};
