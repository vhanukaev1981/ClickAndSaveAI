"use strict";

const { getAuth } = require("firebase-admin/auth");
const { FieldPath, FieldValue, getFirestore } = require("firebase-admin/firestore");
const { defineSecret } = require("firebase-functions/params");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { assertActiveAccount, lifecycleRef } = require("./accountAuthorization");
const { _disconnectGmailForUid: disconnectGmailForUid } = require("./gmailDisconnectFunctions");
const { emitOperationalEvent } = require("./operationalTelemetry");

const db = getFirestore();
const googleOAuthClientSecret = defineSecret("GOOGLE_OAUTH_CLIENT_SECRET");
const oauthTokenEncryptionKey = defineSecret("OAUTH_TOKEN_ENCRYPTION_KEY");
const DELETE_IMPORTED_CONFIRMATION = "DELETE_IMPORTED_FINANCIAL_DATA";
const DELETE_ACCOUNT_CONFIRMATION = "DELETE_ACCOUNT";
const MAX_DELETE_BATCH = 400;

// recursiveDelete(users/{uid}) also removes the nested pushTokens collection.
const ACCOUNT_USER_SUBCOLLECTIONS = ["pushTokens"];
const IMPORTED_USER_SUBCOLLECTIONS = [
  "gmailInvoices",
  "gmailMessageImports",
  "financialContext",
  "financialInsights",
  "opportunities",
];
const IMPORTED_TOP_LEVEL_UID_COLLECTIONS = ["commerceMatches", "commerceEvents"];
const ACCOUNT_TOP_LEVEL_UID_COLLECTIONS = [
  "providerLeads",
  "providerDispatchQueue",
  "commerceMatches",
  "commerceEvents",
];

function requireAuth(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
  return uid;
}

function requireConfirmation(request, expected) {
  if (String(request.data?.confirmation || "") !== expected) {
    throw new HttpsError("invalid-argument", `Explicit confirmation ${expected} is required.`);
  }
}

function authErrorCode(error) {
  return String(error?.code || error?.errorInfo?.code || "");
}

async function deleteQueryByUid(collectionName, uid) {
  let deleted = 0;
  while (true) {
    const snapshot = await db
      .collection(collectionName)
      .where("uid", "==", uid)
      .limit(MAX_DELETE_BATCH)
      .get();
    if (snapshot.empty) return deleted;
    const batch = db.batch();
    snapshot.docs.forEach((doc) => batch.delete(doc.ref));
    await batch.commit();
    deleted += snapshot.size;
  }
}

async function markGmailImportsForPrivacyDeletion(collectionRef) {
  let lastDocumentId = null;
  while (true) {
    let query = collectionRef.orderBy(FieldPath.documentId()).limit(MAX_DELETE_BATCH);
    if (lastDocumentId !== null) query = query.startAfter(lastDocumentId);
    const snapshot = await query.get();
    if (snapshot.empty) return;
    const batch = db.batch();
    snapshot.docs.forEach((doc) => batch.set(doc.ref, {
      privacyDeletionRequested: true,
      privacyDeletionRequestedAt: FieldValue.serverTimestamp(),
    }, { merge: true }));
    await batch.commit();
    lastDocumentId = snapshot.docs[snapshot.docs.length - 1].id;
  }
}

async function deleteImportedFinancialState(uid) {
  const userRef = db.collection("users").doc(uid);
  // Mark import audit records first so both the marker update events and the subsequent
  // delete events carry explicit privacy intent. The financial trigger suppresses those
  // events instead of recreating derived state while the privacy deletion is in flight.
  await markGmailImportsForPrivacyDeletion(userRef.collection("gmailMessageImports"));
  for (const collectionName of IMPORTED_USER_SUBCOLLECTIONS) {
    await db.recursiveDelete(userRef.collection(collectionName));
  }
  const topLevelDeleted = {};
  for (const collectionName of IMPORTED_TOP_LEVEL_UID_COLLECTIONS) {
    topLevelDeleted[collectionName] = await deleteQueryByUid(collectionName, uid);
  }
  return topLevelDeleted;
}

async function deleteAuthUserIdempotently(uid) {
  try {
    await getAuth().deleteUser(uid);
    return false;
  } catch (error) {
    if (authErrorCode(error) === "auth/user-not-found") return true;
    throw error;
  }
}

exports.deleteImportedFinancialData = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const uid = requireAuth(request);
    try {
      requireConfirmation(request, DELETE_IMPORTED_CONFIRMATION);
      await assertActiveAccount(uid);
      const topLevelDeleted = await deleteImportedFinancialState(uid);
      emitOperationalEvent({
        event: "privacy.imported_data.delete",
        subsystem: "privacy",
        outcome: "success",
        severity: "INFO",
        code: "IMPORTED_DATA_DELETION_COMPLETED",
        uid,
        details: { operation: DELETE_IMPORTED_CONFIRMATION, topLevelDeleted },
      });
      return {
        deleted: true,
        operation: DELETE_IMPORTED_CONFIRMATION,
        accountPreserved: true,
        gmailConnectionPreserved: true,
        providerHandoffRecordsPreserved: true,
        futureGmailIngestionMayCreateNewData: true,
        topLevelDeleted,
      };
    } catch (error) {
      emitOperationalEvent({
        event: "privacy.imported_data.delete",
        subsystem: "privacy",
        outcome: "failure",
        severity: "ERROR",
        code: "IMPORTED_DATA_DELETION_FAILED",
        uid,
        details: {
          errorName: error instanceof Error ? error.name : typeof error,
          errorCode: error?.code || "UNKNOWN",
        },
      });
      throw error;
    }
  }
);

exports.deleteAccount = onCall(
  {
    enforceAppCheck: true,
    secrets: [googleOAuthClientSecret, oauthTokenEncryptionKey],
  },
  async (request) => {
    const uid = requireAuth(request);
    try {
      requireConfirmation(request, DELETE_ACCOUNT_CONFIRMATION);
      const lifecycle = lifecycleRef(uid);

      await lifecycle.set({
        state: "DELETING",
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });

      const gmailCleanup = await disconnectGmailForUid(uid);
      if (!gmailCleanup.externalCleanupConfirmed) {
        await lifecycle.set({
          state: "DELETE_RETRY_REQUIRED",
          retryReason: "GMAIL_PROVIDER_CLEANUP",
          gmailCleanup,
          updatedAt: FieldValue.serverTimestamp(),
        }, { merge: true });
        emitOperationalEvent({
          event: "privacy.account.delete",
          subsystem: "privacy",
          outcome: "retry_required",
          severity: "CRITICAL",
          code: "ACCOUNT_DELETE_RETRY_REQUIRED",
          uid,
          details: {
            retryReason: "GMAIL_PROVIDER_CLEANUP",
            externalCleanupConfirmed: false,
          },
        });
        throw new HttpsError(
          "unavailable",
          "Account deletion is paused until Gmail provider cleanup can be confirmed. Retry deletion."
        );
      }

      // Provider authorization is now safe to discard. Re-enter DELETING before any account data
      // is removed so active-account gates cannot recreate protected state during the destructive phase.
      await lifecycle.set({
        state: "DELETING",
        retryReason: FieldValue.delete(),
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });

      const topLevelDeleted = {};
      for (const collectionName of ACCOUNT_TOP_LEVEL_UID_COLLECTIONS) {
        topLevelDeleted[collectionName] = await deleteQueryByUid(collectionName, uid);
      }

      await db.recursiveDelete(db.collection("users").doc(uid));
      const authAlreadyMissing = await deleteAuthUserIdempotently(uid);

      let lifecycleMarkerDeleted = true;
      try {
        await lifecycle.delete();
      } catch (error) {
        lifecycleMarkerDeleted = false;
        emitOperationalEvent({
          event: "privacy.account.delete",
          subsystem: "privacy",
          outcome: "partial",
          severity: "WARNING",
          code: "ACCOUNT_DELETE_LIFECYCLE_MARKER_CLEANUP_FAILED",
          uid,
          details: { errorName: error instanceof Error ? error.name : typeof error },
        });
      }

      emitOperationalEvent({
        event: "privacy.account.delete",
        subsystem: "privacy",
        outcome: lifecycleMarkerDeleted ? "success" : "partial",
        severity: lifecycleMarkerDeleted ? "INFO" : "WARNING",
        code: lifecycleMarkerDeleted ? "ACCOUNT_DELETION_COMPLETED" : "ACCOUNT_DELETION_COMPLETED_WITH_MARKER_RETRY",
        uid,
        details: {
          authAlreadyMissing,
          externalCleanupConfirmed: gmailCleanup.externalCleanupConfirmed,
          lifecycleMarkerDeleted,
          topLevelDeleted,
        },
      });

      return {
        accountDeleted: true,
        authAlreadyMissing,
        gmailCleanup,
        topLevelDeleted,
        userTreeDeleted: true,
        pushRegistrationsDeleted: true,
        lifecycleMarkerDeleted,
      };
    } catch (error) {
      const retryRequired = error instanceof HttpsError && error.code === "unavailable";
      if (!retryRequired) {
        emitOperationalEvent({
          event: "privacy.account.delete",
          subsystem: "privacy",
          outcome: "failure",
          severity: "CRITICAL",
          code: "ACCOUNT_DELETION_FAILED",
          uid,
          details: {
            errorName: error instanceof Error ? error.name : typeof error,
            errorCode: error?.code || "UNKNOWN",
          },
        });
      }
      throw error;
    }
  }
);

Object.defineProperties(module.exports, {
  _deleteImportedFinancialState: { value: deleteImportedFinancialState, enumerable: false },
  _deleteQueryByUid: { value: deleteQueryByUid, enumerable: false },
  _deleteAuthUserIdempotently: { value: deleteAuthUserIdempotently, enumerable: false },
  _markGmailImportsForPrivacyDeletion: { value: markGmailImportsForPrivacyDeletion, enumerable: false },
  _documentedAccountSubcollections: { value: ACCOUNT_USER_SUBCOLLECTIONS, enumerable: false },
});
