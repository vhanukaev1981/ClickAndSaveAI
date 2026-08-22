package com.example.data.repository

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

data class GmailDisconnectResult(
    val connected: Boolean,
    val ingestionStopped: Boolean,
    val watchStopStatus: String,
    val oauthRevocationStatus: String,
    val externalCleanupConfirmed: Boolean,
    val idempotent: Boolean
)

data class ImportedFinancialDataDeletionResult(
    val deleted: Boolean,
    val accountPreserved: Boolean,
    val gmailConnectionPreserved: Boolean,
    val providerHandoffRecordsPreserved: Boolean,
    val futureGmailIngestionMayCreateNewData: Boolean
)

data class AccountDeletionResult(
    val accountDeleted: Boolean,
    val userTreeDeleted: Boolean,
    val pushRegistrationsDeleted: Boolean,
    val externalGmailCleanupConfirmed: Boolean
)

private fun privacyFunctions(): FirebaseFunctions = FirebaseFunctions.getInstance("europe-west1")

private fun Any?.privacyMap(): Map<String, Any?> {
    val raw = this as? Map<*, *> ?: error("Unexpected privacy backend response")
    return raw.entries.associate { (key, value) -> key.toString() to value }
}

suspend fun BackendRepository.disconnectGmailAuthoritatively(): GmailDisconnectResult {
    val response = privacyFunctions()
        .getHttpsCallable("disconnectGmail")
        .call()
        .await()
        .data
        .privacyMap()
    val result = GmailDisconnectResult(
        connected = response["connected"] as? Boolean ?: false,
        ingestionStopped = response["ingestionStopped"] as? Boolean ?: false,
        watchStopStatus = response["watchStopStatus"] as? String ?: "UNKNOWN",
        oauthRevocationStatus = response["oauthRevocationStatus"] as? String ?: "UNKNOWN",
        externalCleanupConfirmed = response["externalCleanupConfirmed"] as? Boolean ?: false,
        idempotent = response["idempotent"] as? Boolean ?: false
    )
    if (!result.externalCleanupConfirmed) {
        throw IllegalStateException(
            "Gmail provider cleanup is still pending. " +
                "watchStopStatus=${result.watchStopStatus}; " +
                "oauthRevocationStatus=${result.oauthRevocationStatus}"
        )
    }
    return result
}

suspend fun BackendRepository.deleteImportedFinancialData(): ImportedFinancialDataDeletionResult {
    val response = privacyFunctions()
        .getHttpsCallable("deleteImportedFinancialData")
        .call(mapOf("confirmation" to "DELETE_IMPORTED_FINANCIAL_DATA"))
        .await()
        .data
        .privacyMap()
    return ImportedFinancialDataDeletionResult(
        deleted = response["deleted"] as? Boolean ?: false,
        accountPreserved = response["accountPreserved"] as? Boolean ?: false,
        gmailConnectionPreserved = response["gmailConnectionPreserved"] as? Boolean ?: false,
        providerHandoffRecordsPreserved = response["providerHandoffRecordsPreserved"] as? Boolean ?: false,
        futureGmailIngestionMayCreateNewData =
            response["futureGmailIngestionMayCreateNewData"] as? Boolean ?: false
    )
}

suspend fun BackendRepository.deleteAccount(): AccountDeletionResult {
    val response = privacyFunctions()
        .getHttpsCallable("deleteAccount")
        .call(mapOf("confirmation" to "DELETE_ACCOUNT"))
        .await()
        .data
        .privacyMap()
    val gmailCleanup = response["gmailCleanup"].privacyMap()
    return AccountDeletionResult(
        accountDeleted = response["accountDeleted"] as? Boolean ?: false,
        userTreeDeleted = response["userTreeDeleted"] as? Boolean ?: false,
        pushRegistrationsDeleted = response["pushRegistrationsDeleted"] as? Boolean ?: false,
        externalGmailCleanupConfirmed =
            gmailCleanup["externalCleanupConfirmed"] as? Boolean ?: false
    )
}
