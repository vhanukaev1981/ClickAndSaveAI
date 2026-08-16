"use strict";

const crypto = require("node:crypto");

function accountScopeForUid(uid) {
  return crypto.createHash("sha256").update(String(uid)).digest("hex").slice(0, 32);
}

function safeNotificationEnvelope(uid, type, data = {}) {
  const normalizedType = String(type || "UNKNOWN");
  const display = normalizedType === "NEW_INVOICE"
    ? {
        title: "חיוב חדש זוהה",
        body: "פתח את ClickAndSaveAI לצפייה מאובטחת בפרטי החיוב.",
      }
    : normalizedType === "VERIFIED_SAVINGS_OPPORTUNITY"
      ? {
          title: "נמצאה הזדמנות חיסכון",
          body: "פתח את ClickAndSaveAI לצפייה מאובטחת בהזדמנות.",
        }
      : {
          title: "עדכון חדש ב-ClickAndSaveAI",
          body: "פתח את האפליקציה לצפייה מאובטחת בפרטים.",
        };
  return {
    ...display,
    data: {
      ...data,
      type: normalizedType,
      accountScope: accountScopeForUid(uid),
    },
  };
}

module.exports = { accountScopeForUid, safeNotificationEnvelope };
