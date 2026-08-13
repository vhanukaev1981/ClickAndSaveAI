package com.example.data.repository

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

data class FinancialActivityEvent(
    val id: String,
    val type: String,
    val timestamp: String,
    val status: String,
    val destination: String,
    val providerName: String?,
    val category: String?,
    val observedAmount: Double?,
    val verificationStatus: String?
)

data class FinancialActivityResult(
    val events: List<FinancialActivityEvent>,
    val sourceCoverage: List<String>,
    val isCompleteHistory: Boolean
)

class FinancialActivityRepository(
    private val functionsProvider: () -> FirebaseFunctions = {
        FirebaseFunctions.getInstance("europe-west1")
    }
) {
    private val functions: FirebaseFunctions by lazy(functionsProvider)

    suspend fun getFinancialActivity(): FinancialActivityResult {
        val response = functions.getHttpsCallable("getFinancialActivity")
            .call()
            .await()
            .data
            .asStringMap()
        val events = (response["events"] as? List<*>)
            .orEmpty()
            .mapNotNull { item ->
                val map = item.asStringMapOrNull() ?: return@mapNotNull null
                FinancialActivityEvent(
                    id = map["id"] as? String ?: return@mapNotNull null,
                    type = map["type"] as? String ?: return@mapNotNull null,
                    timestamp = map["timestamp"] as? String ?: return@mapNotNull null,
                    status = map["status"] as? String ?: return@mapNotNull null,
                    destination = map["destination"] as? String ?: return@mapNotNull null,
                    providerName = (map["providerName"] as? String)?.takeIf(String::isNotBlank),
                    category = (map["category"] as? String)?.takeIf(String::isNotBlank),
                    observedAmount = (map["observedAmount"] as? Number)?.toDouble(),
                    verificationStatus = (map["verificationStatus"] as? String)
                        ?.takeIf(String::isNotBlank)
                )
            }
        return FinancialActivityResult(
            events = events,
            sourceCoverage = (response["sourceCoverage"] as? List<*>)
                .orEmpty()
                .map { it.toString() },
            isCompleteHistory = response["isCompleteHistory"] as? Boolean ?: false
        )
    }

    private fun Any?.asStringMap(): Map<String, Any?> {
        return asStringMapOrNull() ?: error("Unexpected backend response")
    }

    private fun Any?.asStringMapOrNull(): Map<String, Any?>? {
        val raw = this as? Map<*, *> ?: return null
        return raw.entries.associate { (key, value) -> key.toString() to value }
    }
}

sealed interface FinancialSyncState {
    data object Unauthenticated : FinancialSyncState
    data object CheckingConnection : FinancialSyncState
    data object Disconnected : FinancialSyncState
    data object Recovering : FinancialSyncState

    data class Ready(
        val latestScan: GmailScanResult,
        val financialHome: FinancialHomeResult,
        val gmailConnection: GmailConnectionResult? = null,
        val activity: FinancialActivityResult? = null
    ) : FinancialSyncState

    data class Partial(
        val latestScan: GmailScanResult?,
        val financialHome: FinancialHomeResult?,
        val reason: String,
        val gmailConnection: GmailConnectionResult? = null,
        val activity: FinancialActivityResult? = null
    ) : FinancialSyncState

    data class Failed(
        val reason: String,
        val isAuthRequired: Boolean = false
    ) : FinancialSyncState
}

val FinancialSyncState.latestScanOrNull: GmailScanResult?
    get() = when (this) {
        is FinancialSyncState.Ready -> latestScan
        is FinancialSyncState.Partial -> latestScan
        else -> null
    }

val FinancialSyncState.financialHomeOrNull: FinancialHomeResult?
    get() = when (this) {
        is FinancialSyncState.Ready -> financialHome
        is FinancialSyncState.Partial -> financialHome
        else -> null
    }

val FinancialSyncState.gmailConnectionOrNull: GmailConnectionResult?
    get() = when (this) {
        is FinancialSyncState.Ready -> gmailConnection
        is FinancialSyncState.Partial -> gmailConnection
        else -> null
    }

val FinancialSyncState.activityOrNull: FinancialActivityResult?
    get() = when (this) {
        is FinancialSyncState.Ready -> activity
        is FinancialSyncState.Partial -> activity
        else -> null
    }

val FinancialSyncState.observedRecurringMonthlySpendOrNull: Double?
    get() = financialHomeOrNull?.context?.observedRecurringMonthlySpend

val FinancialSyncState.recurringServiceCountOrNull: Int?
    get() = financialHomeOrNull?.context?.recurringServiceCount
