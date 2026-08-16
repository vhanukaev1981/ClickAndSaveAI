"use strict";

const IN_APP_PROVIDER_REQUEST = "IN_APP_PROVIDER_REQUEST";
const VIEW_ONLY = "VIEW_ONLY";

function normalizeCommissionType(value) {
  return String(value || "NONE").trim().toUpperCase() || "NONE";
}

function commercialTerms(source) {
  const direct = source && typeof source === "object" ? source : {};
  const nested = direct.commercial && typeof direct.commercial === "object"
    ? direct.commercial
    : {};
  const agreementActive =
    direct.commercialAgreementActive === true ||
    direct.agreementActive === true ||
    nested.commercialAgreementActive === true ||
    nested.agreementActive === true;
  const commissionType = normalizeCommissionType(
    direct.commissionType !== undefined ? direct.commissionType : nested.commissionType
  );
  const rawValue = direct.commissionValue !== undefined
    ? direct.commissionValue
    : nested.commissionValue;
  const commissionValue = Number(rawValue);
  return {
    agreementActive,
    commissionType,
    commissionValue: Number.isFinite(commissionValue) ? commissionValue : null,
  };
}

function isTrackableCommercialOffer(source) {
  const terms = commercialTerms(source);
  return terms.agreementActive === true &&
    terms.commissionType !== "NONE" &&
    Number.isFinite(terms.commissionValue) &&
    terms.commissionValue > 0;
}

function commercialActionMode(source) {
  return isTrackableCommercialOffer(source)
    ? IN_APP_PROVIDER_REQUEST
    : VIEW_ONLY;
}

module.exports = {
  IN_APP_PROVIDER_REQUEST,
  VIEW_ONLY,
  commercialTerms,
  isTrackableCommercialOffer,
  commercialActionMode,
};
