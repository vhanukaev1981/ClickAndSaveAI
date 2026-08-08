"use strict";

const crypto = require("node:crypto");

const IMPORT_RESULTS = Object.freeze({
  ACCEPTED: "ACCEPTED",
  DUPLICATE: "DUPLICATE",
  QUARANTINED: "QUARANTINED",
});

function requiredText(value, field, maxLength = 200) {
  const text = typeof value === "string" ? value.trim() : "";
  if (!text) throw new TypeError(`${field} is required`);
  if (text.length > maxLength) throw new TypeError(`${field} exceeds ${maxLength} characters`);
  return text;
}

function deriveImportEventId(input) {
  const material = [
    requiredText(input.providerId, "providerId", 128),
    requiredText(input.reportId, "reportId", 200),
    requiredText(input.externalEventId, "externalEventId", 200),
    requiredText(input.eventType, "eventType", 64).toUpperCase(),
  ].join(":");
  return crypto.createHash("sha256").update(material).digest("hex");
}

function planProviderReportImport(events, existingEventIds = []) {
  if (!Array.isArray(events)) throw new TypeError("provider report events must be an array");
  if (!Array.isArray(existingEventIds)) throw new TypeError("existingEventIds must be an array");

  const seen = new Set(existingEventIds.filter((value) => typeof value === "string" && value));
  const results = [];

  for (const raw of events) {
    try {
      if (!raw || typeof raw !== "object" || Array.isArray(raw)) throw new TypeError("report event must be an object");
      const eventType = requiredText(raw.eventType, "eventType", 64).toUpperCase();
      if (!["CONVERSION", "COMMISSION", "SETTLEMENT"].includes(eventType)) {
        throw new TypeError(`unsupported report event type: ${eventType}`);
      }
      const importEventId = deriveImportEventId({ ...raw, eventType });
      if (seen.has(importEventId)) {
        results.push({ result: IMPORT_RESULTS.DUPLICATE, importEventId, eventType });
        continue;
      }
      seen.add(importEventId);
      results.push({
        result: IMPORT_RESULTS.ACCEPTED,
        importEventId,
        eventType,
        providerId: requiredText(raw.providerId, "providerId", 128),
        reportId: requiredText(raw.reportId, "reportId", 200),
        externalEventId: requiredText(raw.externalEventId, "externalEventId", 200),
        payload: raw.payload && typeof raw.payload === "object" && !Array.isArray(raw.payload) ? raw.payload : {},
      });
    } catch (error) {
      results.push({
        result: IMPORT_RESULTS.QUARANTINED,
        reason: error instanceof Error ? error.message : "invalid report event",
      });
    }
  }

  return results;
}

module.exports = {
  IMPORT_RESULTS,
  deriveImportEventId,
  planProviderReportImport,
};
