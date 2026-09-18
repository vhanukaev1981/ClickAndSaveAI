"use strict";

const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const {
  validateProviderIntegrationConfig,
  integrationStatus,
} = require("./providerIntegrationFramework");
const { hasProviderAdapter } = require("./providerAdapterRegistry");

const db = getFirestore();
const MAX_INTEGRATIONS = 100;

function requireCommerceOperator(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
  const token = request.auth?.token || {};
  if (token.admin !== true && token.operator !== true) {
    throw new HttpsError("permission-denied", "Commerce operator permission is required.");
  }
  return uid;
}

function publicIntegrationStatus(id, data) {
  const status = integrationStatus(data, {
    adapterRegistered: hasProviderAdapter(data.adapterKey),
    healthVerified: data.lastHealthVerified === true,
  });
  return {
    providerId: String(id),
    providerName: String(data.providerName || ""),
    adapterKey: String(data.adapterKey || ""),
    state: status.state,
    reason: status.reason,
    supportsDeliveryReceipts: data.supportsDeliveryReceipts === true,
    supportsCommissionReconciliation: data.supportsCommissionReconciliation === true,
    lastHealthVerified: data.lastHealthVerified === true,
  };
}

exports.upsertProviderIntegrationConfig = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const operatorUid = requireCommerceOperator(request);
    let config;
    try {
      config = validateProviderIntegrationConfig(request.data);
    } catch (error) {
      throw new HttpsError("invalid-argument", error instanceof Error ? error.message : "Invalid provider integration config");
    }

    const ref = db.collection("providerIntegrations").doc(config.providerId);
    const existing = await ref.get();
    await ref.set({
      ...config,
      lastHealthVerified: false,
      lastHealthReference: FieldValue.delete(),
      lastHealthVerifiedAt: FieldValue.delete(),
      updatedByOperatorUid: operatorUid,
      updatedAt: FieldValue.serverTimestamp(),
      ...(existing.exists ? {} : { createdAt: FieldValue.serverTimestamp() }),
      schemaVersion: 1,
    }, { merge: true });

    return publicIntegrationStatus(config.providerId, {
      ...config,
      lastHealthVerified: false,
    });
  }
);

exports.getProviderIntegrationStatuses = onCall(
  { enforceAppCheck: true },
  async (request) => {
    requireCommerceOperator(request);
    const snapshot = await db.collection("providerIntegrations").limit(MAX_INTEGRATIONS).get();
    return {
      integrations: snapshot.docs.map((doc) => publicIntegrationStatus(doc.id, doc.data() || {})),
      registeredRuntimeAdapters: snapshot.docs
        .map((doc) => String(doc.data()?.adapterKey || ""))
        .filter((key) => key && hasProviderAdapter(key))
        .length,
    };
  }
);

exports._publicIntegrationStatus = publicIntegrationStatus;
