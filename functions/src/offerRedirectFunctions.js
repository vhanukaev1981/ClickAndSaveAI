"use strict";

const crypto = require("node:crypto");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { normalizeOffer } = require("./commerceEngine");
const { _verifiedActionSnapshot: verifiedActionSnapshot } = require("./opportunityActionFunctions");
const { isTrackableCommercialOffer } = require("./commercialPolicy");
const {
  assertActiveAccount,
  lifecycleRef,
  BLOCKED_ACCOUNT_LIFECYCLE_STATES,
} = require("./accountAuthorization");
const { requiredString } = require("./validation");

const db = getFirestore();

function requireAuth(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
  return uid;
}

function parseDestinationUrl(value) {
  const text = String(value || "").trim();
  if (!text) return "";
  try {
    const parsed = new URL(text);
    if (parsed.protocol !== "https:" || !parsed.hostname) return "";
    parsed.hash = "";
    return parsed.toString();
  } catch {
    return "";
  }
}

function offerVersionHash(offer, destinationUrl, attribution = {}) {
  const payload = [
    String(offer.offerId || ""),
    String(offer.verifiedAt || ""),
    String(offer.validUntil || ""),
    String(offer.monthlyPrice ?? ""),
    String(offer.requiredRecurringFees ?? ""),
    String(offer.oneTimeFees ?? ""),
    destinationUrl,
    attribution.commercialAgreementActive === true ? "1" : "0",
    String(attribution.commissionType || "NONE"),
    String(attribution.commissionValue ?? ""),
    String(attribution.partnerSubIdParam || ""),
  ].join("|");
  return crypto.createHash("sha256").update(payload).digest("hex");
}

function clickIdentity(uid, opportunityId, offerId, versionHash) {
  return crypto
    .createHash("sha256")
    .update(["OFFER_CLICK", uid, opportunityId, offerId, versionHash].join("|"))
    .digest("hex");
}

function buildRedirectUrl(destinationUrl, clickId, partnerSubIdParam = "") {
  const parsed = new URL(destinationUrl);
  if (partnerSubIdParam) parsed.searchParams.set(partnerSubIdParam, clickId);
  return parsed.toString();
}

function assertWritableLifecycle(snapshot) {
  const state = String(snapshot.data()?.state || "").toUpperCase();
  if (BLOCKED_ACCOUNT_LIFECYCLE_STATES.has(state)) {
    throw new HttpsError(
      "permission-denied",
      state === "DELETE_RETRY_REQUIRED"
        ? "This account deletion requires retry before account writes can resume."
        : "This account is being deleted."
    );
  }
}

exports.createTrackedOfferRedirect = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const uid = requireAuth(request);
    await assertActiveAccount(uid);

    let opportunityId;
    let expectedOfferId;
    try {
      opportunityId = requiredString(request.data?.opportunityId, "opportunityId", 128);
      expectedOfferId = requiredString(request.data?.expectedOfferId, "expectedOfferId", 128);
    } catch (error) {
      throw new HttpsError("invalid-argument", error instanceof Error ? error.message : "Invalid request");
    }

    const userRef = db.collection("users").doc(uid);
    const opportunityRef = userRef.collection("opportunities").doc(opportunityId);
    const offerRef = db.collection("providerOffers").doc(expectedOfferId);
    const accountLifecycleRef = lifecycleRef(uid);

    let result = null;

    await db.runTransaction(async (transaction) => {
      const [lifecycleSnapshot, opportunitySnapshot, offerSnapshot] = await Promise.all([
        transaction.get(accountLifecycleRef),
        transaction.get(opportunityRef),
        transaction.get(offerRef),
      ]);
      assertWritableLifecycle(lifecycleSnapshot);

      if (!opportunitySnapshot.exists || !offerSnapshot.exists) {
        throw new HttpsError("failed-precondition", "The exact offer is no longer available.");
      }

      const opportunity = { id: opportunitySnapshot.id, ...opportunitySnapshot.data() };
      const rawOffer = { id: offerSnapshot.id, ...offerSnapshot.data() };
      const action = verifiedActionSnapshot(opportunity, rawOffer, expectedOfferId);
      const normalizedOffer = normalizeOffer(rawOffer);
      if (!action || !normalizedOffer || normalizedOffer.offerId !== expectedOfferId) {
        throw new HttpsError(
          "failed-precondition",
          "The offer changed, expired, became ineligible, or can no longer be verified."
        );
      }

      const offerData = offerSnapshot.data() || {};
      const destinationUrl = offerData.destinationVerified === true
        ? parseDestinationUrl(offerData.destinationUrl)
        : "";
      if (!destinationUrl) {
        throw new HttpsError(
          "failed-precondition",
          "An exact verified provider destination is not available for this offer."
        );
      }

      const attributable = isTrackableCommercialOffer({
        commercialAgreementActive: normalizedOffer.commercialAgreementActive === true,
        commissionType: normalizedOffer.commissionType,
        commissionValue: normalizedOffer.commissionValue,
      });
      const partnerSubIdParam = attributable
        ? String(offerData.partnerSubIdParam || "").trim()
        : "";
      const versionHash = offerVersionHash(normalizedOffer, destinationUrl, {
        commercialAgreementActive: normalizedOffer.commercialAgreementActive === true,
        commissionType: normalizedOffer.commissionType,
        commissionValue: normalizedOffer.commissionValue,
        partnerSubIdParam,
      });
      const clickId = clickIdentity(uid, opportunityId, expectedOfferId, versionHash);
      const redirectUrl = buildRedirectUrl(destinationUrl, clickId, partnerSubIdParam);
      const clickRef = db.collection("offerClicks").doc(clickId);
      const eventRef = db.collection("commerceEvents").doc(`offer-click-${clickId}`);
      const existing = await transaction.get(clickRef);
      const duplicate = existing.exists;
      const attributionMode = attributable ? "PARTNER_ATTRIBUTABLE" : "DIRECT_UNATTRIBUTED";

      if (!duplicate) {
        transaction.create(clickRef, {
          clickId,
          uid,
          opportunityId,
          offerId: expectedOfferId,
          providerName: normalizedOffer.providerName,
          category: normalizedOffer.category,
          offerVersionHash: versionHash,
          exactDestinationUrl: destinationUrl,
          attributionMode,
          partnerSubIdParam: partnerSubIdParam || null,
          createdAt: FieldValue.serverTimestamp(),
          schemaVersion: 1,
        });
        transaction.create(eventRef, {
          eventType: "OFFER_CLICK",
          clickId,
          uid,
          opportunityId,
          offerId: expectedOfferId,
          providerName: normalizedOffer.providerName,
          attributionMode,
          createdAt: FieldValue.serverTimestamp(),
          schemaVersion: 1,
        });
      }

      result = {
        clickId,
        opportunityId,
        offerId: expectedOfferId,
        providerName: normalizedOffer.providerName,
        redirectUrl,
        duplicate,
        attributionMode,
      };
    });

    if (!result) {
      throw new HttpsError("internal", "The exact offer redirect could not be resolved.");
    }
    return result;
  }
);

exports._parseDestinationUrl = parseDestinationUrl;
exports._offerVersionHash = offerVersionHash;
exports._clickIdentity = clickIdentity;
exports._buildRedirectUrl = buildRedirectUrl;
exports._assertWritableLifecycle = assertWritableLifecycle;
