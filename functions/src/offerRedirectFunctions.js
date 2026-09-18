"use strict";

const crypto = require("node:crypto");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { normalizeOffer } = require("./commerceEngine");
const { _verifiedActionSnapshot: verifiedActionSnapshot } = require("./opportunityActionFunctions");
const { isTrackableCommercialOffer } = require("./commercialPolicy");
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

function offerVersionHash(offer, destinationUrl) {
  const payload = [
    String(offer.offerId || ""),
    String(offer.verifiedAt || ""),
    String(offer.validUntil || ""),
    String(offer.monthlyPrice ?? ""),
    String(offer.requiredRecurringFees ?? ""),
    String(offer.oneTimeFees ?? ""),
    destinationUrl,
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

exports.createTrackedOfferRedirect = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const uid = requireAuth(request);
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

    const [opportunitySnapshot, offerSnapshot] = await Promise.all([
      opportunityRef.get(),
      offerRef.get(),
    ]);
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

    const destinationUrl = offerSnapshot.data()?.destinationVerified === true
      ? parseDestinationUrl(offerSnapshot.data()?.destinationUrl)
      : "";
    if (!destinationUrl) {
      throw new HttpsError(
        "failed-precondition",
        "An exact verified provider destination is not available for this offer."
      );
    }

    const versionHash = offerVersionHash(normalizedOffer, destinationUrl);
    const clickId = clickIdentity(uid, opportunityId, expectedOfferId, versionHash);
    const attributable = isTrackableCommercialOffer({
      commercialAgreementActive: normalizedOffer.commercialAgreementActive === true,
      commissionType: normalizedOffer.commissionType,
      commissionValue: normalizedOffer.commissionValue,
    });
    const partnerSubIdParam = attributable
      ? String(offerSnapshot.data()?.partnerSubIdParam || "").trim()
      : "";
    const redirectUrl = buildRedirectUrl(destinationUrl, clickId, partnerSubIdParam);
    const clickRef = db.collection("offerClicks").doc(clickId);
    const eventRef = db.collection("commerceEvents").doc(`offer-click-${clickId}`);
    let duplicate = false;

    await db.runTransaction(async (transaction) => {
      const existing = await transaction.get(clickRef);
      if (existing.exists) {
        duplicate = true;
        return;
      }
      transaction.create(clickRef, {
        clickId,
        uid,
        opportunityId,
        offerId: expectedOfferId,
        providerName: normalizedOffer.providerName,
        category: normalizedOffer.category,
        offerVersionHash: versionHash,
        exactDestinationUrl: destinationUrl,
        attributionMode: attributable ? "PARTNER_ATTRIBUTABLE" : "DIRECT_UNATTRIBUTED",
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
        attributionMode: attributable ? "PARTNER_ATTRIBUTABLE" : "DIRECT_UNATTRIBUTED",
        createdAt: FieldValue.serverTimestamp(),
        schemaVersion: 1,
      });
    });

    return {
      clickId,
      opportunityId,
      offerId: expectedOfferId,
      providerName: normalizedOffer.providerName,
      redirectUrl,
      duplicate,
      attributionMode: attributable ? "PARTNER_ATTRIBUTABLE" : "DIRECT_UNATTRIBUTED",
    };
  }
);

exports._parseDestinationUrl = parseDestinationUrl;
exports._offerVersionHash = offerVersionHash;
exports._clickIdentity = clickIdentity;
exports._buildRedirectUrl = buildRedirectUrl;
