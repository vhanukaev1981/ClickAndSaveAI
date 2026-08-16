import crypto from "node:crypto";
import { createRequire } from "node:module";

const STAGING_PROJECT_ID = "clickandsaveai-staging";
const FUNCTIONS_REGION = "europe-west1";
const ACCEPTANCE_APP_NAME = "clickandsaveai-staging-block6-acceptance";
const requireFromFunctions = createRequire(new URL("../functions/package.json", import.meta.url));

function exactSha(value) {
  const sha = String(value || "").trim();
  if (!/^[0-9a-f]{40}$/.test(sha)) {
    throw new Error("SOURCE_SHA must be an exact lowercase 40-character commit SHA.");
  }
  return sha;
}

async function callable(functionName, { idToken, appCheckToken, data = {}, fetchImpl = fetch }) {
  const response = await fetchImpl(
    `https://${FUNCTIONS_REGION}-${STAGING_PROJECT_ID}.cloudfunctions.net/${encodeURIComponent(functionName)}`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${idToken}`,
        "X-Firebase-AppCheck": appCheckToken,
      },
      body: JSON.stringify({ data }),
    }
  );
  const payload = await response.json().catch(() => null);
  const error = payload?.error || null;
  if (!response.ok || error) {
    return {
      ok: false,
      code: String(error?.status || error?.code || `HTTP_${response.status}`),
      result: null,
    };
  }
  const result = payload?.result ?? payload?.data;
  return result && typeof result === "object"
    ? { ok: true, code: "OK", result }
    : { ok: false, code: "NO_RESULT", result: null };
}

async function adminContext(env) {
  const { applicationDefault, getApps, initializeApp } = requireFromFunctions("firebase-admin/app");
  const { getAuth } = requireFromFunctions("firebase-admin/auth");
  const { getFirestore } = requireFromFunctions("firebase-admin/firestore");
  const serviceAccountId = String(env.GCP_DEPLOY_SERVICE_ACCOUNT || "").trim();
  if (!serviceAccountId) throw new Error("GCP_DEPLOY_SERVICE_ACCOUNT is required.");
  const existing = getApps().find((app) => app.name === ACCEPTANCE_APP_NAME);
  const app = existing || initializeApp(
    {
      credential: applicationDefault(),
      projectId: STAGING_PROJECT_ID,
      serviceAccountId,
    },
    ACCEPTANCE_APP_NAME
  );
  return { auth: getAuth(app), db: getFirestore(app), serviceAccountId };
}

async function waitForDoc(ref, attempts = 40, delayMs = 500) {
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    const snapshot = await ref.get();
    if (snapshot.exists) return snapshot;
    await new Promise((resolve) => setTimeout(resolve, delayMs));
  }
  return null;
}

async function deleteUidQuery(db, collectionName, uid) {
  while (true) {
    const snapshot = await db.collection(collectionName).where("uid", "==", uid).limit(200).get();
    if (snapshot.empty) return;
    const batch = db.batch();
    snapshot.docs.forEach((doc) => batch.delete(doc.ref));
    await batch.commit();
  }
}

function acceptedTruth(result) {
  return result?.consentState === "CONSENTED" &&
    result?.requestState === "REQUEST_CREATED" &&
    result?.deliveryAttemptState === "NOT_ATTEMPTED" &&
    result?.submissionState === "NOT_SUBMITTED" &&
    result?.deliveryState === "NOT_CONFIRMED" &&
    result?.providerContactState === "UNKNOWN" &&
    result?.completionState === "NOT_COMPLETED" &&
    result?.savingRealizationState === "UNKNOWN" &&
    result?.realizedMonthlySaving == null &&
    result?.realizedAnnualSaving == null;
}

export async function runBlock6HandoffAcceptance({
  sourceSha,
  env = process.env,
  fetchImpl = fetch,
  mintTokens,
}) {
  const sha = exactSha(sourceSha);
  if (typeof mintTokens !== "function") throw new Error("mintTokens callback is required.");
  if (String(env.GCLOUD_PROJECT || env.GOOGLE_CLOUD_PROJECT || STAGING_PROJECT_ID) !== STAGING_PROJECT_ID) {
    throw new Error("Block 6 acceptance may target clickandsaveai-staging only.");
  }

  const { auth, db, serviceAccountId } = await adminContext(env);
  const suffix = `${sha.slice(0, 8)}-${crypto.randomBytes(5).toString("hex")}`;
  const uid = `block6-accept-${suffix}`;
  const email = `block6-accept-${suffix}@example.invalid`;
  const opportunityId = `block6-opportunity-${suffix}`;
  const offerId = `block6-offer-${suffix}`;
  const offerRef = db.collection("providerOffers").doc(offerId);
  const userRef = db.collection("users").doc(uid);
  const opportunityRef = userRef.collection("opportunities").doc(opportunityId);
  let leadId = "";

  const now = new Date();
  const validUntil = new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000).toISOString();
  const expiredAt = new Date(now.getTime() - 24 * 60 * 60 * 1000).toISOString();
  const verifiedAt = now.toISOString();

  const offer = {
    providerName: "Block 6 Synthetic Provider",
    category: "אינטרנט",
    pricingModel: "FIXED_MONTHLY",
    country: "IL",
    monthlyPrice: 89,
    priceGuaranteedMonths: 12,
    oneTimeFees: 0,
    serviceType: "ANY",
    verifiedAt,
    validUntil,
    officialSourceVerified: true,
    officialSourceUrl: "https://provider.example.invalid/block6-offer",
    officialSourceName: "Block 6 synthetic staging offer",
    availabilityStatus: "AVAILABLE",
    availabilityMode: "NATIONWIDE",
    consumerPriceIncludesVat: true,
    requiredRecurringFees: 0,
    requiredRecurringFeesDescription: "",
    userFitScore: 0.9,
    commercialAgreementActive: true,
    commissionType: "CPA",
    commissionValue: 180,
    block6Synthetic: true,
  };
  const opportunity = {
    providerName: "Block 6 Current Provider",
    category: "אינטרנט",
    currentMonthlyCost: 129,
    potentialMonthlySaving: 40,
    potentialAnnualSaving: 480,
    currentCostEvidenceState: "OBSERVED",
    matchedOffer: {
      offerId,
      providerName: offer.providerName,
      monthlyPrice: 89,
      firstYearCost: 1068,
    },
    block6Synthetic: true,
  };

  try {
    await auth.createUser({ uid, email });
    await userRef.set({ block6Synthetic: true });
    await Promise.all([offerRef.set(offer), opportunityRef.set(opportunity)]);

    const tokens = await mintTokens({
      uid,
      projectId: STAGING_PROJECT_ID,
      appId: String(env.STAGING_APPCHECK_APP_ID || "").trim(),
      apiKey: String(env.STAGING_FIREBASE_API_KEY || "").trim(),
      serviceAccountId,
      fetchImpl,
    });
    const callOptions = { ...tokens, fetchImpl };
    const requestData = {
      opportunityId,
      expectedOfferId: offerId,
      contactName: "Block 6 Acceptance",
      phone: "+972501234567",
      contactEmail: email,
      consentAccepted: true,
      consentVersion: "opportunity-action-v1",
    };

    const wrongIdentity = await callable("acceptSavingsOpportunity", {
      ...callOptions,
      data: { ...requestData, expectedOfferId: `${offerId}-wrong` },
    });
    if (wrongIdentity.ok || wrongIdentity.code !== "FAILED_PRECONDITION") {
      throw new Error("Exact-offer identity mismatch was not rejected.");
    }

    await offerRef.set({ validUntil: expiredAt }, { merge: true });
    const staleOffer = await callable("acceptSavingsOpportunity", { ...callOptions, data: requestData });
    if (staleOffer.ok || staleOffer.code !== "FAILED_PRECONDITION") {
      throw new Error("Expired offer was not rejected during authoritative revalidation.");
    }

    await offerRef.set({ validUntil }, { merge: true });
    const accepted = await callable("acceptSavingsOpportunity", { ...callOptions, data: requestData });
    if (!accepted.ok || !acceptedTruth(accepted.result)) {
      throw new Error("Fresh exact offer did not create truthful consent/request state.");
    }
    if (accepted.result.offerId !== offerId || accepted.result.opportunityId !== opportunityId) {
      throw new Error("Accepted request identity does not match the exact offer/opportunity.");
    }
    leadId = String(accepted.result.leadId || "");
    if (!leadId) throw new Error("Accepted request did not return a lead ID.");

    const duplicate = await callable("acceptSavingsOpportunity", { ...callOptions, data: requestData });
    if (!duplicate.ok || duplicate.result?.duplicate !== true || duplicate.result?.leadId !== leadId) {
      throw new Error("Exact opportunity acceptance is not idempotent.");
    }

    const queueSnapshot = await waitForDoc(db.collection("providerDispatchQueue").doc(leadId));
    if (!queueSnapshot) throw new Error("Internal provider dispatch queue was not created.");
    const queue = queueSnapshot.data() || {};
    const queueTruth = queue.status === "PENDING" &&
      queue.offerId === offerId &&
      queue.opportunityId === opportunityId &&
      queue.consentState === "CONSENTED" &&
      queue.requestState === "REQUEST_CREATED" &&
      queue.deliveryAttemptState === "NOT_ATTEMPTED" &&
      queue.submissionState === "NOT_SUBMITTED" &&
      queue.deliveryState === "NOT_CONFIRMED" &&
      queue.providerContactState === "UNKNOWN" &&
      queue.completionState === "NOT_COMPLETED" &&
      queue.savingRealizationState === "UNKNOWN";
    if (!queueTruth) throw new Error("Internal provider queue overstated external delivery truth.");

    const activity = await callable("getFinancialActivity", callOptions);
    if (!activity.ok || !Array.isArray(activity.result?.events) || activity.result?.isCompleteHistory !== false) {
      throw new Error("Authoritative Activity did not restore for the disposable acceptance account.");
    }

    return {
      sourceSha: sha,
      exactOfferIdentityRejected: true,
      expiredOfferRejected: true,
      freshOfferRevalidated: true,
      explicitConsentRecorded: true,
      requestCreated: true,
      acceptanceIdempotent: true,
      internalDispatchQueue: "PENDING",
      deliveryAttemptState: "NOT_ATTEMPTED",
      submissionState: "NOT_SUBMITTED",
      deliveryState: "NOT_CONFIRMED",
      providerContactState: "UNKNOWN",
      completionState: "NOT_COMPLETED",
      savingRealizationState: "UNKNOWN",
      activityAuthoritative: true,
      activityCompleteHistoryClaimed: false,
      externalProviderTransportUsed: false,
    };
  } finally {
    await Promise.allSettled([
      db.recursiveDelete(userRef),
      offerRef.delete(),
    ]);
    for (const collectionName of ["providerLeads", "providerDispatchQueue", "commerceMatches", "commerceEvents"]) {
      await deleteUidQuery(db, collectionName, uid).catch(() => undefined);
    }
    if (leadId) await db.collection("providerDispatchQueue").doc(leadId).delete().catch(() => undefined);
    await auth.deleteUser(uid).catch((error) => {
      const code = String(error?.code || error?.errorInfo?.code || "");
      if (code !== "auth/user-not-found") throw error;
    });
  }
}
