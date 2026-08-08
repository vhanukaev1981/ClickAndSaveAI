"use strict";

function text(value, maxLength) {
  return String(value || "").trim().slice(0, maxLength);
}

function buildProviderDispatchPayload(lead) {
  if (!lead || typeof lead !== "object") return null;
  const leadId = text(lead.leadId || lead.id, 128);
  const contactName = text(lead.contactName, 120);
  const phone = text(lead.phone, 40);
  const contactEmail = text(lead.contactEmail, 320).toLowerCase();
  const requestedProvider = text(lead.requestedProvider, 160);
  const category = text(lead.category, 80);
  const offerId = text(lead.offerId, 128);

  if (!leadId || !contactName || !phone || !contactEmail || !requestedProvider || !category || !offerId) {
    return null;
  }

  return {
    leadId,
    contactName,
    phone,
    contactEmail,
    requestedProvider,
    category,
    offerId,
    consentVersion: text(lead.consentVersion, 50),
    source: "CLICKANDSAVE_VERIFIED_OPPORTUNITY",
  };
}

module.exports = {
  buildProviderDispatchPayload,
};
