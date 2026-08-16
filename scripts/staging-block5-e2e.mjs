import crypto from "node:crypto";
import fs from "node:fs/promises";
import { createRequire } from "node:module";
import { pathToFileURL } from "node:url";
import { mintSmokeTokensWithAdmin } from "./staging-core-smoke.mjs";

const STAGING_PROJECT_ID = "clickandsaveai-staging";
const FUNCTIONS_REGION = "europe-west1";
const E2E_APP_NAME = "clickandsaveai-staging-block5-e2e";
const requireFromFunctions = createRequire(new URL("../functions/package.json", import.meta.url));
const TOP_LEVEL_UID_COLLECTIONS = [
  "providerLeads",
  "providerDispatchQueue",
  "commerceMatches",
  "commerceEvents",
];
const IMPORTED_SUBCOLLECTIONS = [
  "gmailInvoices",
  "gmailMessageImports",
  "financialContext",
  "financialInsights",
  "opportunities",
];

function exactSha(value) {
  const sha = String(value || "").trim();
  if (!/^[0-9a-f]{40}$/.test(sha)) {
    throw new Error("SOURCE_SHA must be an exact lowercase 40-character commit SHA.");
  }
  return sha;
}

function lifecycleDocumentId(uid) {
  return crypto.createHash("sha256").update(String(uid)).digest("hex");
}

function legacyLeadId(uid, idempotencyKey) {
  return crypto.createHash("sha256").update(`${uid}:${idempotencyKey}`).digest("hex");
}

async function adminContext(env) {
  const { applicationDefault, getApps, initializeApp } = requireFromFunctions("firebase-admin/app");
  const { getAuth } = requireFromFunctions("firebase-admin/auth");
  const { getFirestore } = requireFromFunctions("firebase-admin/firestore");
  const serviceAccountId = String(env.GCP_DEPLOY_SERVICE_ACCOUNT || "").trim();
  if (!serviceAccountId) throw new Error("GCP_DEPLOY_SERVICE_ACCOUNT is required.");
  const existing = getApps().find((app) => app.name === E2E_APP_NAME);
  const app = existing || initializeApp(
    {
      credential: applicationDefault(),
      projectId: STAGING_PROJECT_ID,
      serviceAccountId,
    },
    E2E_APP_NAME
  );
  return { app, auth: getAuth(app), db: getFirestore(app), serviceAccountId };
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
    const code = String(error?.status || error?.code || `HTTP_${response.status}`);
    return { ok: false, code, result: null };
  }
  const result = payload?.result ?? payload?.data;
  if (!result || typeof result !== "object") {
    return { ok: false, code: "NO_RESULT", result: null };
  }
  return { ok: true, code: "OK", result };
}

async function mustCall(functionName, options) {
  const response = await callable(functionName, options);
  if (!response.ok) throw new Error(`${functionName} failed with ${response.code}.`);
  return response.result;
}

async function deleteUidQuery(db, collectionName, uid) {
  while (true) {
    const snapshot = await db.collection(collectionName).where("uid", "==", uid).limit(400).get();
    if (snapshot.empty) return;
    const batch = db.batch();
    snapshot.docs.forEach((doc) => batch.delete(doc.ref));
    await batch.commit();
  }
}

async function cleanupEphemeral({ auth, db }, uid) {
  await Promise.allSettled([
    db.recursiveDelete(db.collection("users").doc(uid)),
    db.collection("gmailConnections").doc(uid).delete(),
    db.collection("accountLifecycle").doc(lifecycleDocumentId(uid)).delete(),
  ]);
  for (const collectionName of TOP_LEVEL_UID_COLLECTIONS) {
    await deleteUidQuery(db, collectionName, uid).catch(() => undefined);
  }
  await auth.deleteUser(uid).catch((error) => {
    const code = String(error?.code || error?.errorInfo?.code || "");
    if (code !== "auth/user-not-found") throw error;
  });
}

async function seedImportedState(db, uid, suffix) {
  const userRef = db.collection("users").doc(uid);
  await userRef.set({ block5E2E: true });
  await Promise.all([
    userRef.collection("gmailInvoices").doc(`invoice-${suffix}`).set({
      sourceMessageId: `message-${suffix}`,
      providerName: "E2E Provider",
      monthlyCost: 100,
      verificationStatus: "UNVERIFIED_GMAIL_IMPORT",
    }),
    userRef.collection("gmailMessageImports").doc(`message-${suffix}`).set({ parserVersion: 6 }),
    userRef.collection("financialContext").doc("current").set({ sourceCoverage: ["GMAIL_READONLY"] }),
    userRef.collection("financialInsights").doc(`insight-${suffix}`).set({ type: "E2E" }),
    userRef.collection("opportunities").doc(`opportunity-${suffix}`).set({ type: "E2E" }),
    userRef.collection("pushTokens").doc(`push-${suffix}`).set({
      token: `e2e-token-${suffix}-abcdefghijklmnopqrstuvwxyz`,
      enabled: true,
    }),
    db.collection("commerceMatches").doc(`${uid}_${suffix}`).set({ uid, opportunityId: suffix }),
    db.collection("commerceEvents").doc(`${uid}_${suffix}`).set({ uid, type: "E2E" }),
    db.collection("providerLeads").doc(`${uid}_${suffix}`).set({
      uid,
      status: "NEW",
      consentAccepted: true,
      source: "E2E",
    }),
    db.collection("providerDispatchQueue").doc(`${uid}_${suffix}`).set({
      uid,
      status: "PENDING",
      source: "E2E",
    }),
  ]);
}

async function seedGmailConnection(db, uid, email, watchEnabled, extra = {}) {
  await db.collection("gmailConnections").doc(uid).set({
    uid,
    email,
    scopes: ["https://www.googleapis.com/auth/gmail.readonly"],
    consentVersion: "gmail-readonly-v1",
    watchEnabled,
    block5E2ENoCredential: true,
    ...extra,
  });
}

async function exists(ref) {
  return (await ref.get()).exists;
}

async function assertImportedStateDeleted(db, uid) {
  const userRef = db.collection("users").doc(uid);
  for (const collectionName of IMPORTED_SUBCOLLECTIONS) {
    const snapshot = await userRef.collection(collectionName).limit(1).get();
    if (!snapshot.empty) throw new Error(`${collectionName} survived imported-data deletion.`);
  }
  for (const collectionName of ["commerceMatches", "commerceEvents"]) {
    const snapshot = await db.collection(collectionName).where("uid", "==", uid).limit(1).get();
    if (!snapshot.empty) throw new Error(`${collectionName} survived imported-data deletion.`);
  }
}

async function authUserExists(auth, uid) {
  try {
    await auth.getUser(uid);
    return true;
  } catch (error) {
    const code = String(error?.code || error?.errorInfo?.code || "");
    if (code === "auth/user-not-found") return false;
    throw error;
  }
}

async function mintE2ETokens({ uid, env, serviceAccountId, fetchImpl }) {
  return mintSmokeTokensWithAdmin({
    uid,
    projectId: STAGING_PROJECT_ID,
    appId: String(env.STAGING_APPCHECK_APP_ID || "").trim(),
    apiKey: String(env.STAGING_FIREBASE_API_KEY || "").trim(),
    serviceAccountId,
    fetchImpl,
  });
}

export async function runBlock5StagingE2E({
  sourceSha,
  env = process.env,
  fetchImpl = fetch,
}) {
  const sha = exactSha(sourceSha);
  if (String(env.GCLOUD_PROJECT || env.GOOGLE_CLOUD_PROJECT || STAGING_PROJECT_ID) !== STAGING_PROJECT_ID) {
    throw new Error("Block 5 E2E may target clickandsaveai-staging only.");
  }

  const { auth, db, serviceAccountId } = await adminContext(env);
  const suffix = `${sha.slice(0, 8)}-${crypto.randomBytes(5).toString("hex")}`;
  const subjectUid = `block5-subject-${suffix}`;
  const controlUid = `block5-control-${suffix}`;
  const retryUid = `block5-retry-${suffix}`;
  const subjectEmail = `block5-subject-${suffix}@example.invalid`;
  const controlEmail = `block5-control-${suffix}@example.invalid`;
  const retryEmail = `block5-retry-${suffix}@example.invalid`;

  try {
    await Promise.all([
      auth.createUser({ uid: subjectUid, email: subjectEmail }),
      auth.createUser({ uid: controlUid, email: controlEmail }),
      auth.createUser({ uid: retryUid, email: retryEmail }),
    ]);
    await seedImportedState(db, subjectUid, suffix);
    await seedImportedState(db, controlUid, `control-${suffix}`);
    await seedImportedState(db, retryUid, `retry-${suffix}`);
    await seedGmailConnection(db, subjectUid, subjectEmail, true);
    await seedGmailConnection(db, controlUid, controlEmail, true);
    await seedGmailConnection(db, retryUid, retryEmail, true, {
      encryptedRefreshToken: "block5-e2e-intentionally-invalid-encrypted-token",
      block5E2ENoCredential: false,
    });

    const [tokens, retryTokens] = await Promise.all([
      mintE2ETokens({ uid: subjectUid, env, serviceAccountId, fetchImpl }),
      mintE2ETokens({ uid: retryUid, env, serviceAccountId, fetchImpl }),
    ]);
    const callOptions = { ...tokens, fetchImpl };
    const retryCallOptions = { ...retryTokens, fetchImpl };

    // Prove lifecycle authorization before destructive work: an authenticated user in DELETING
    // state must not be able to recreate a top-level provider lead.
    const lifecycleRef = db.collection("accountLifecycle").doc(lifecycleDocumentId(subjectUid));
    await lifecycleRef.set({ state: "DELETING", block5E2E: true });
    const blockedLeadKey = `blocked-${suffix}`;
    const blockedLeadId = legacyLeadId(subjectUid, blockedLeadKey);
    const blockedLeadAttempt = await callable("createProviderLead", {
      ...callOptions,
      data: {
        contactName: "Block 5 E2E",
        phone: "+972501234567",
        contactEmail: subjectEmail,
        currentProvider: "E2E Provider",
        requestedProvider: "",
        category: "אינטרנט",
        invoiceLocalId: "block5-e2e",
        idempotencyKey: blockedLeadKey,
        consentAccepted: true,
        consentVersion: "provider-lead-v1",
      },
    });
    const deletingWriteRejected = !blockedLeadAttempt.ok &&
      blockedLeadAttempt.code === "PERMISSION_DENIED" &&
      !(await exists(db.collection("providerLeads").doc(blockedLeadId)));
    if (!deletingWriteRejected) {
      throw new Error("DELETING lifecycle did not block provider-lead recreation.");
    }
    await lifecycleRef.delete();

    // Prove recovery semantics with an intentionally unreadable encrypted credential. No real
    // Google credential is used; provider cleanup must remain unconfirmed and retryable.
    const retryDisconnectResult = await mustCall("disconnectGmail", retryCallOptions);
    const retryConnection = await db.collection("gmailConnections").doc(retryUid).get();
    const retryConnectionData = retryConnection.data() || {};
    const retryStateRetained = retryConnection.exists &&
      retryConnectionData.disconnectState === "RETRY_REQUIRED" &&
      retryConnectionData.watchEnabled === false &&
      Array.isArray(retryConnectionData.scopes) && retryConnectionData.scopes.length === 0 &&
      Boolean(retryConnectionData.encryptedRefreshToken);
    if (retryDisconnectResult.externalCleanupConfirmed || !retryStateRetained) {
      throw new Error("Unconfirmed Gmail provider cleanup was not retained for safe retry.");
    }

    const retryScan = await callable("scanGmailInvoices", retryCallOptions);
    const retryReconnect = await callable("connectGmail", {
      ...retryCallOptions,
      data: {
        serverAuthCode: "block5-e2e-reconnect-code",
        consentAccepted: true,
        consentVersion: "gmail-readonly-v1",
      },
    });
    if (retryScan.ok || retryReconnect.ok) {
      throw new Error("Pending Gmail cleanup did not block ingestion/reconnect.");
    }

    const secondRetryDisconnect = await mustCall("disconnectGmail", retryCallOptions);
    if (!secondRetryDisconnect.idempotent || secondRetryDisconnect.externalCleanupConfirmed) {
      throw new Error("Gmail disconnect retry semantics are not stable.");
    }

    const retryDeleteAccount = await callable("deleteAccount", {
      ...retryCallOptions,
      data: { confirmation: "DELETE_ACCOUNT" },
    });
    const retryAuthPreserved = await authUserExists(auth, retryUid);
    const retryUserTreePreserved = await exists(db.collection("users").doc(retryUid));
    const retryLifecycle = await db.collection("accountLifecycle")
      .doc(lifecycleDocumentId(retryUid))
      .get();
    const retryDeletePaused = !retryDeleteAccount.ok &&
      retryDeleteAccount.code === "UNAVAILABLE" &&
      retryAuthPreserved &&
      retryUserTreePreserved &&
      retryLifecycle.data()?.state === "DELETE_RETRY_REQUIRED";
    if (!retryDeletePaused) {
      throw new Error("Account deletion did not pause safely on unconfirmed Gmail cleanup.");
    }

    const disconnectResult = await mustCall("disconnectGmail", callOptions);
    const connectionDeleted = !(await exists(db.collection("gmailConnections").doc(subjectUid)));
    const importedDataPreservedAfterDisconnect = await exists(
      db.collection("users").doc(subjectUid).collection("gmailInvoices").doc(`invoice-${suffix}`)
    );
    const providerLeadPreservedAfterDisconnect = await exists(
      db.collection("providerLeads").doc(`${subjectUid}_${suffix}`)
    );
    if (!disconnectResult.ingestionStopped || !connectionDeleted ||
        !importedDataPreservedAfterDisconnect || !providerLeadPreservedAfterDisconnect) {
      throw new Error("Gmail disconnect boundary verification failed.");
    }

    // Re-create only the connection marker so DELETE IMPORTED DATA can prove it does not disconnect Gmail.
    await seedGmailConnection(db, subjectUid, subjectEmail, false);
    const deleteImportedResult = await mustCall("deleteImportedFinancialData", {
      ...callOptions,
      data: { confirmation: "DELETE_IMPORTED_FINANCIAL_DATA" },
    });
    await assertImportedStateDeleted(db, subjectUid);
    const gmailPreserved = await exists(db.collection("gmailConnections").doc(subjectUid));
    const providerLeadPreserved = await exists(db.collection("providerLeads").doc(`${subjectUid}_${suffix}`));
    const providerQueuePreserved = await exists(
      db.collection("providerDispatchQueue").doc(`${subjectUid}_${suffix}`)
    );
    const subjectAuthPreserved = await authUserExists(auth, subjectUid);
    if (!deleteImportedResult.deleted || !gmailPreserved || !providerLeadPreserved ||
        !providerQueuePreserved || !subjectAuthPreserved) {
      throw new Error("Imported-data deletion boundary verification failed.");
    }

    const retryResult = await mustCall("deleteImportedFinancialData", {
      ...callOptions,
      data: { confirmation: "DELETE_IMPORTED_FINANCIAL_DATA" },
    });
    if (!retryResult.deleted) throw new Error("Imported-data idempotent retry failed.");

    // Seed fresh state so DELETE ACCOUNT proves full cleanup independently of prior data deletion.
    await seedImportedState(db, subjectUid, `account-${suffix}`);
    await seedGmailConnection(db, subjectUid, subjectEmail, false);
    const deleteAccountResult = await mustCall("deleteAccount", {
      ...callOptions,
      data: { confirmation: "DELETE_ACCOUNT" },
    });

    const accountAuthDeleted = !(await authUserExists(auth, subjectUid));
    const userTreeDeleted = !(await exists(db.collection("users").doc(subjectUid)));
    const gmailDeleted = !(await exists(db.collection("gmailConnections").doc(subjectUid)));
    const providerLeadsGone = (await db.collection("providerLeads").where("uid", "==", subjectUid).get()).empty;
    const providerQueueGone = (
      await db.collection("providerDispatchQueue").where("uid", "==", subjectUid).get()
    ).empty;
    if (!deleteAccountResult.accountDeleted || !accountAuthDeleted || !userTreeDeleted ||
        !gmailDeleted || !providerLeadsGone || !providerQueueGone) {
      throw new Error("Account deletion verification failed.");
    }

    const staleMutation = await callable("registerPushToken", {
      ...callOptions,
      data: { token: `stale-${suffix}-abcdefghijklmnopqrstuvwxyz` },
    });
    const staleLeadMutation = await callable("createProviderLead", {
      ...callOptions,
      data: {
        contactName: "Deleted Block 5 E2E",
        phone: "+972501234567",
        contactEmail: subjectEmail,
        currentProvider: "E2E Provider",
        requestedProvider: "",
        category: "אינטרנט",
        invoiceLocalId: "deleted-block5-e2e",
        idempotencyKey: `deleted-${suffix}`,
        consentAccepted: true,
        consentVersion: "provider-lead-v1",
      },
    });
    const staleTokenRejected = !staleMutation.ok;
    const staleLeadRejected = !staleLeadMutation.ok;
    const noPushRecreated = (
      await db.collection("users").doc(subjectUid).collection("pushTokens").limit(1).get()
    ).empty;
    const noLeadRecreated = (
      await db.collection("providerLeads").where("uid", "==", subjectUid).limit(1).get()
    ).empty;
    if (!staleTokenRejected || !staleLeadRejected || !noPushRecreated || !noLeadRecreated) {
      throw new Error("Deleted-account stale token recreated server state.");
    }

    const controlAccountUntouched = await authUserExists(auth, controlUid) &&
      await exists(db.collection("gmailConnections").doc(controlUid)) &&
      await exists(db.collection("providerLeads").doc(`${controlUid}_control-${suffix}`)) &&
      await exists(
        db.collection("users").doc(controlUid).collection("gmailInvoices").doc(`invoice-control-${suffix}`)
      );
    if (!controlAccountUntouched) throw new Error("Cross-account isolation verification failed.");

    return {
      projectId: STAGING_PROJECT_ID,
      sourceSha: sha,
      authorizationIsolation: {
        deletingWriteRejected,
        staleTokenRejected,
        staleLeadRejected,
      },
      recovery: {
        retryStateRetained,
        scanBlockedDuringRetry: !retryScan.ok,
        reconnectBlockedDuringRetry: !retryReconnect.ok,
        disconnectRetryIdempotent: secondRetryDisconnect.idempotent === true,
        accountDeletionPaused: retryDeletePaused,
        authPreservedUntilRetry: retryAuthPreserved,
      },
      disconnect: {
        ingestionStopped: disconnectResult.ingestionStopped === true,
        connectionDeleted,
        importedDataPreserved: importedDataPreservedAfterDisconnect,
        providerHandoffPreserved: providerLeadPreservedAfterDisconnect,
        watchStopStatus: String(disconnectResult.watchStopStatus || ""),
        oauthRevocationStatus: String(disconnectResult.oauthRevocationStatus || ""),
        realGoogleCredentialUsed: false,
      },
      deleteImportedData: {
        deleted: deleteImportedResult.deleted === true,
        accountPreserved: subjectAuthPreserved,
        gmailConnectionPreserved: gmailPreserved,
        providerHandoffRecordsPreserved: providerLeadPreserved && providerQueuePreserved,
        idempotentRetry: retryResult.deleted === true,
      },
      deleteAccount: {
        accountDeleted: deleteAccountResult.accountDeleted === true,
        authDeleted: accountAuthDeleted,
        userTreeDeleted,
        gmailConnectionDeleted: gmailDeleted,
        providerHandoffDeleted: providerLeadsGone && providerQueueGone,
        pushCleanupConfirmed: deleteAccountResult.pushRegistrationsDeleted === true && noPushRecreated,
        staleTokenRejected,
        staleLeadRejected,
      },
      isolation: {
        controlAccountUntouched,
      },
    };
  } finally {
    await cleanupEphemeral({ auth, db }, subjectUid).catch(() => undefined);
    await cleanupEphemeral({ auth, db }, controlUid).catch(() => undefined);
    await cleanupEphemeral({ auth, db }, retryUid).catch(() => undefined);
  }
}

async function main() {
  const sourceSha = exactSha(process.env.SOURCE_SHA);
  const outputPath = String(process.env.STAGING_BLOCK5_E2E_OUTPUT || "staging-block5-e2e.json").trim();
  const summary = await runBlock5StagingE2E({ sourceSha });
  await fs.writeFile(outputPath, `${JSON.stringify(summary, null, 2)}\n`, {
    encoding: "utf8",
    mode: 0o600,
  });
  process.stdout.write(`${JSON.stringify(summary)}\n`);
}

const invokedPath = process.argv[1] ? pathToFileURL(process.argv[1]).href : "";
if (import.meta.url === invokedPath) {
  main().catch((error) => {
    const message = error instanceof Error ? error.message : "Unknown Block 5 staging E2E failure.";
    process.stderr.write(`STAGING_BLOCK5_E2E_FAILED: ${message}\n`);
    process.exitCode = 1;
  });
}
