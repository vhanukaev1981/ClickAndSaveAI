package com.example.data.repository

sealed interface FinancialSyncState {
    data object Unauthenticated : FinancialSyncState
    data object CheckingConnection : FinancialSyncState
    data object Disconnected : FinancialSyncState
    data object Recovering : FinancialSyncState

    data class Ready(
        val latestScan: GmailScanResult,
        val financialHome: FinancialHomeResult
    ) : FinancialSyncState

    data class Partial(
        val latestScan: GmailScanResult?,
        val financialHome: FinancialHomeResult?,
        val reason: String
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

val FinancialSyncState.observedRecurringMonthlySpendOrNull: Double?
    get() = financialHomeOrNull?.context?.observedRecurringMonthlySpend

val FinancialSyncState.recurringServiceCountOrNull: Int?
    get() = financialHomeOrNull?.context?.recurringServiceCount
