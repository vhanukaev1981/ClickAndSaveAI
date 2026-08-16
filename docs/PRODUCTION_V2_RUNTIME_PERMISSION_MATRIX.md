# Production v2 Runtime Permission Matrix — Block 3B.3C

## Boundary decision

Production v2 runtime identity:

`clicksave-v2-runtime@click-save-ai-production.iam.gserviceaccount.com`

Block 3B.3C is an identity-boundary block. The v2 runtime service account intentionally starts with **zero project-level application roles**. Every application runtime capability below is deferred to a later Production runtime/configuration approval. This document does not authorize an IAM grant.

The exported surface is defined by `functions/src/entry.js`. `functions/src/pushAccountCleanup.js` is the dedicated v1 exception and is not part of this v2 permission matrix.

## Reviewed exported v2 surface

The common entry module exports the following v2 modules before/around the preserved v1 cleanup module:

- `functions/src/index.js`
- `functions/src/pushFunctions.js`
- `functions/src/gmailWatchFunctions.js`
- `functions/src/gmailWatchRenewal.js`
- `functions/src/financialAgentFunctions.js`
- `functions/src/financialActivityFunctions.js`
- `functions/src/opportunityNotificationFunctions.js`
- `functions/src/opportunityActionFunctions.js`
- `functions/src/opportunityEngagementFunctions.js`
- `functions/src/commerceOperationsFunctions.js`
- `functions/src/providerOfferCatalogFunctions.js`
- `functions/src/providerDispatchFunctions.js`
- `functions/src/commerceFunnelFunctions.js`
- `functions/src/gmailSyncStatusFunctions.js`
- `functions/src/gmailScanV5Functions.js`
- `functions/src/gmailIncrementalReconciliation.js`
- `functions/src/gmailReliableScanFunctions.js`
- `functions/src/gmailReliabilityGuard.js`
- `functions/src/gmailInvoiceNotificationFunctions.js`
- `functions/src/guardedUserWriteFunctions.js`
- `functions/src/gmailConnectFunctions.js`
- `functions/src/gmailDisconnectFunctions.js`
- `functions/src/privacyLifecycleFunctions.js`

The repository-level verifier independently proves that the exported surface is mixed v1/v2, that the only deployed v1 module is `pushAccountCleanup.js`, and that the common v2 service-account expression is confined to `index.js`.

## Permission matrix

| Capability | Exact code/module requiring it | Exact permission(s) | Candidate role | Scope | Block 3B.3C action |
|---|---|---|---|---|---|
| Firestore document/query reads | `index.js`; `pushFunctions.js`; Gmail ingestion/watch/reliability modules; `financialAgentFunctions.js`; `financialActivityFunctions.js`; opportunity/commerce/provider modules; `gmailInvoiceNotificationFunctions.js`; `privacyLifecycleFunctions.js` call `getFirestore()`, `.get()`, `.where()`, `.limit()`, transactions and queries | `datastore.entities.get`, `datastore.entities.list`; transactions also require `datastore.databases.get` | `roles/datastore.user` | Project. Google documents this as the lowest grant level for the predefined Cloud Datastore User role. | **DEFERRED** — do not grant in this identity bootstrap block. |
| Firestore writes/deletes/transactions | The same Firestore modules use `.set()`, `.create()`, `.delete()`, batches, transactions and `recursiveDelete()` | `datastore.entities.create`, `datastore.entities.update`, `datastore.entities.delete`, plus `datastore.databases.get` for transaction begin/rollback | `roles/datastore.user` | Project | **DEFERRED** — exact runtime data access must be approved in the later runtime/configuration block. |
| Firebase Cloud Messaging sends | `pushFunctions.js` calls `getMessaging().sendEachForMulticast()`. `gmailWatchFunctions.js`, `opportunityNotificationFunctions.js`, and `gmailInvoiceNotificationFunctions.js` call the shared push helper | `cloudmessaging.messages.create` | `roles/firebasecloudmessaging.admin` is the predefined FCM API role containing the send permission | Project | **DEFERRED** — no FCM role is granted here. A narrower custom role containing only the proven send permission should be considered before using the predefined admin role. |
| Firebase Authentication user existence checks | `opportunityNotificationFunctions.js`, `gmailReliabilityGuard.js`, and `gmailInvoiceNotificationFunctions.js` call `getAuth().getUser(uid)` | `firebaseauth.users.get` | `roles/firebaseauth.viewer` contains the required user-read permission | Project | **DEFERRED** — no Auth role is granted here. |
| Firebase Authentication account deletion | `privacyLifecycleFunctions.js` calls `getAuth().deleteUser(uid)` and handles `auth/user-not-found` idempotently | `firebaseauth.users.delete`; the same surface also needs `firebaseauth.users.get` elsewhere | Predefined candidate `roles/firebaseauth.admin` contains both permissions but is broader than this runtime needs; preferred later candidate is a custom project role limited to the proven user permissions if supported by the approved IAM policy | Project | **DEFERRED** — do not grant broad Firebase Authentication Admin in this block. |
| Secret `GOOGLE_OAUTH_CLIENT_SECRET` | Bound with `defineSecret()` in `index.js`, `gmailWatchFunctions.js`, `gmailWatchRenewal.js`, `gmailScanV5Functions.js`, Gmail connection/reliability modules, and `privacyLifecycleFunctions.js`; functions list it in `secrets:` options where used | `secretmanager.versions.access` | `roles/secretmanager.secretAccessor` | **Individual secret only:** `GOOGLE_OAUTH_CLIENT_SECRET` | **DEFERRED** — project-wide Secret Manager Secret Accessor is forbidden. |
| Secret `OAUTH_TOKEN_ENCRYPTION_KEY` | Bound in `index.js`, Gmail connection/watch/scan/reconciliation/reliability modules, and `privacyLifecycleFunctions.js` | `secretmanager.versions.access` | `roles/secretmanager.secretAccessor` | **Individual secret only:** `OAUTH_TOKEN_ENCRYPTION_KEY` | **DEFERRED** — per-secret binding only after runtime approval. |
| Secret `GEMINI_API_KEY` | Bound in `index.js`, `gmailWatchFunctions.js`, `gmailScanV5Functions.js`, `gmailIncrementalReconciliation.js`, and `gmailReliabilityGuard.js`; code passes `geminiApiKey.value()` to `GoogleGenAI` | `secretmanager.versions.access` | `roles/secretmanager.secretAccessor` | **Individual secret only:** `GEMINI_API_KEY` | **DEFERRED** — per-secret binding only after runtime approval. |
| Pub/Sub event consumption | `gmailWatchFunctions.js`, `gmailIncrementalReconciliation.js`, and `gmailReliabilityGuard.js` define `onMessagePublished` handlers for `gmail-notifications` | No application ADC call to Pub/Sub is present in the reviewed handlers. No `pubsub.topics.publish` or `pubsub.subscriptions.consume` requirement is proven for the runtime service account by application code. | Runtime role: none proven. Trigger provisioning/delivery IAM belongs to deployment/Eventarc/Pub/Sub infrastructure identities and must be evaluated separately. | Topic/trigger resources, not project-wide runtime application access | **DEFERRED / INFRASTRUCTURE** — do not grant Pub/Sub Publisher/Subscriber to the v2 runtime SA from this audit. |
| Gmail watch target topic | `gmailWatchFunctions.js` and `gmailWatchRenewal.js` call Gmail `users.watch` with `projects/<project>/topics/gmail-notifications` using a user OAuth access token | No v2-runtime ADC permission; Gmail API authorization is the user's `gmail.readonly` OAuth token. Gmail's ability to publish to the topic is a separate topic IAM concern for the Gmail publishing service identity. | Runtime role: none | Pub/Sub topic IAM for the external publisher, handled separately | **DEFERRED / INFRASTRUCTURE**. |
| Scheduled function execution | `gmailWatchRenewal.js`, `financialAgentFunctions.js`, `gmailIncrementalReconciliation.js`, and `gmailReliabilityGuard.js` define `onSchedule` functions | No Cloud Scheduler API call through ADC is present in the reviewed runtime code | Runtime role: none proven. Scheduler invocation/service-agent permissions are deployment infrastructure concerns. | Scheduler job / function invocation resources | **DEFERRED / INFRASTRUCTURE** — do not grant Cloud Scheduler roles to the v2 runtime SA. |
| Firestore event triggers | `financialAgentFunctions.js`, `opportunityNotificationFunctions.js`, `providerDispatchFunctions.js`, `gmailInvoiceNotificationFunctions.js` and other Firestore-trigger modules use `onDocumentWritten`/`onDocumentCreated` | No Eventarc API call through ADC is present in handler code. Handler Firestore reads/writes are covered by the Firestore rows above. | Runtime role: no additional trigger role proven by application code | Trigger infrastructure | **DEFERRED / INFRASTRUCTURE**. |
| Gmail REST API | `index.js` and Gmail modules call `gmail.googleapis.com` using `Authorization: Bearer <user OAuth access token>` | Not a Google Cloud IAM/ADC permission for the runtime service account; OAuth scope is `https://www.googleapis.com/auth/gmail.readonly` | None for runtime IAM | Per-user OAuth grant | No runtime IAM grant. Secret access for OAuth client/token encryption remains separately deferred above. |
| Google OAuth token/revoke endpoints | `index.js`, `gmailConnectFunctions.js`, Gmail watch/scan/reliability modules call `oauth2.googleapis.com` using client metadata and user refresh tokens | Not a runtime ADC permission | None for runtime IAM | OAuth protocol | No runtime IAM grant. |
| Gemini API calls | `index.js`, `gmailWatchFunctions.js`, `gmailScanV5Functions.js` and reliability paths instantiate `GoogleGenAI` with `apiKey: geminiApiKey.value()` | Not an ADC permission in the current code path; authorization is explicit API-key based | None for runtime IAM | API key secret | No Google AI IAM role is justified by current code; only per-secret access is deferred above. |

## Runtime role conclusion

For **Block 3B.3C**, the correct v2 project-role set is empty:

```text
clicksave-v2-runtime@click-save-ai-production.iam.gserviceaccount.com
project roles = []
```

The deploy service account may receive `roles/iam.serviceAccountUser` only on this individual service account so deployment can select it as runtime identity. That is service-account-resource IAM, not an application project role.

The later Production runtime/configuration block must approve the minimum application privileges before a real v2 deployment can be considered runtime-ready. At minimum, that later review must resolve Firestore, FCM send, Firebase Authentication user operations, and the three individual Secret Manager bindings. Pub/Sub/Eventarc/Scheduler deployment identities must be treated separately from the application runtime identity.

## Explicit prohibitions retained

- Do not grant project-wide `roles/secretmanager.secretAccessor`.
- Do not grant `roles/editor`, `roles/owner`, `roles/firebase.admin`, or another broad convenience role to the v2 runtime service account.
- Do not grant Pub/Sub or Cloud Scheduler roles to the runtime service account merely because functions are triggered by those services.
- Do not enable an API in order to manufacture a default identity.
- Do not reuse the v2 runtime identity as GitHub deploy, WIF, Cloud Build, v1 cleanup, staging, or development identity.
