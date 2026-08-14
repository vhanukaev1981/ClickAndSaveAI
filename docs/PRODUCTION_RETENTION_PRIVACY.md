# ClickAndSaveAI Production Retention and Privacy Operations

## Status

Repository policy: `REPOSITORY_POLICY_ONLY`

Legal/privacy approval: `LEGAL_APPROVAL_REQUIRED`

The canonical machine-readable classification is `operations/retention/retention-policy.json`. Its existence does not constitute legal approval, regulatory advice, or activation of a production retention scheduler.

## Dispositions

`DELETE` means the data family is intended to be removed at the defined lifecycle trigger, subject to fail-closed provider cleanup and authoritative deletion completion.

`ANONYMIZE` means operational usefulness may remain after direct identity is removed or replaced by a pseudonymous/non-user-identifying representation. Block 2 operational logs use pseudonymous actor references and exclude secrets/direct email identity.

`RETAIN` means the record family may need preservation for operational, financial, dispute, audit, or release traceability. Exact retention duration and lawful basis are owner/legal decisions unless already governed elsewhere.

## Canonical Classifications

- Firebase Authentication identity — `DELETE` after confirmed account deletion completes dependent cleanup.
- Gmail OAuth credentials — `DELETE` after disconnect/account deletion and confirmed provider cleanup; encrypted retry evidence may remain only while provider cleanup is retry-required.
- Gmail imported data — `DELETE` on imported-data deletion or account deletion.
- Derived financial state — `DELETE` with the imported data it derives from and must not be recreated by deletion-triggered processing.
- Push registration tokens — `DELETE` on token/session revocation or account deletion.
- Provider/commerce records — `RETAIN`, with duration/exceptions requiring owner/legal policy.
- Operational logs/metrics — `ANONYMIZE`, with exact retention window requiring owner security/privacy policy.
- Release/recovery evidence — `RETAIN`, without user content or secret values; exact duration requires owner policy.

## Fail-Closed Rules

1. Unclassified data does not receive an invented retention period; escalate it.
2. A deletion request is not complete while required external provider cleanup is unconfirmed.
3. A retention exception must not silently expand product data collection.
4. Operational logs must not contain OAuth tokens, authorization codes, secret values, request bodies, raw email addresses, passwords, private keys, or encryption material.
5. Release/recovery evidence must identify immutable source/artifact/configuration identities, not user content.

## Owner Actions Still Required

- approve lawful retention periods and any jurisdiction-specific exceptions;
- approve deletion/anonymization behavior for financial or dispute records;
- configure actual production log/metric retention;
- verify production deletion evidence and provider cleanup behavior;
- maintain records of legal/privacy review.
