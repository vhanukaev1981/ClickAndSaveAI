# Post-E2E: authoritative realtime Bills sync

Status: queued for Stream A **after** locked staging E2E correction cycle #2. Do not move the locked Stream A baseline to implement this before that validation.

## Problem

The backend Gmail realtime path updates Firestore / Financial Agent and sends an FCM notification. The Android Bills screen is Room-backed. `ClickAndSaveMessagingService` displays notifications but does not refresh Room, and normal authenticated app startup only triggers a full Gmail scan when a parser upgrade is required.

Result: Financial Home/opportunities can be current while the local Bills list lags until another scan.

## Required architecture

Firestore remains authoritative for normalized Gmail-observed bills. Do **not** run the six-month Gmail scan on every app open.

Add a secure callable, tentatively `getObservedBills`, that returns a bounded recent snapshot derived from backend-owned `users/{uid}/gmailInvoices` state.

Suggested response shape:

```text
{
  bills: [
    {
      sourceMessageId,
      providerName,
      category,
      monthlyCost,
      receivedDate,
      verificationStatus
    }
  ],
  sourceIds: [...],
  generatedAt
}
```

Rules:

- App Check + Firebase Auth required.
- No raw subject/body/snippet/PDF text.
- No account number, address, payment instrument or hidden commercial terms.
- Bounded result size; newest-first.
- Backend normalized sources only.
- `sourceMessageId` remains an internal synchronization key and must not be shown in customer UI.

## Android reconciliation

Add a repository method that:

1. fetches `getObservedBills`;
2. upserts each returned Gmail source into Room;
3. removes stale Gmail-only Room rows that are no longer present in the authoritative bounded synchronization set according to a safe reconciliation rule;
4. never deletes manually entered local bills;
5. never changes local user-action state unless the source itself was removed and replacement lineage is explicitly known.

Trigger a lightweight authoritative refresh:

- after authenticated app startup/resume with reasonable throttling;
- after an FCM event indicating `NEW_INVOICE` where practical;
- after parser-upgrade/full Gmail scan.

## Truthfulness / performance gates

- no local invoice-count fallback for recurring-service count;
- no local arithmetic that creates savings claims;
- no six-month Gmail scan as a normal refresh mechanism;
- one backend bill must map to one current Room source;
- body->PDF source migrations must remain duplicate-safe;
- failure to refresh Bills must not make a valid Gmail connection appear disconnected.

## Tests

Backend:
- auth/App Check boundary;
- response contains normalized minimum fields only;
- bounded newest-first list;
- no raw Gmail content;
- stale source not returned after parser reconciliation.

Android:
- new backend bill appears once in Room;
- existing source is updated, not duplicated;
- manual local bill survives authoritative Gmail reconciliation;
- stale Gmail source is removed safely;
- transient refresh failure preserves current UI data and connection state.
