package com.example.data.repository

/**
 * Coordinates the server-authoritative financial recovery sequence for one authenticated session.
 *
 * The pipeline is deliberately independent of Android UI so its ordering and truth semantics can
 * be verified with unit tests: connection status -> Gmail recovery -> Financial Home.
 */
class FinancialSessionRecovery(
    private val getConnectionStatus: suspend () -> GmailConnectionResult,
    private val recoverInvoices: suspend () -> GmailScanResult,
    private val getFinancialHome: suspend () -> FinancialHomeResult
) {
    suspend fun refresh(
        previous: FinancialSyncState?,
        publish: (FinancialSyncState) -> Unit = {}
    ): FinancialSyncState {
        fun emit(state: FinancialSyncState): FinancialSyncState {
            publish(state)
            return state
        }

        emit(FinancialSyncState.CheckingConnection)

        val connection = try {
            getConnectionStatus()
        } catch (_: Exception) {
            return emit(
                preservePreviousOrFail(
                    previous,
                    safeReason("GMAIL_CONNECTION_STATUS_FAILED")
                )
            )
        }

        if (!connection.connected) {
            return emit(FinancialSyncState.Disconnected)
        }

        emit(FinancialSyncState.Recovering)

        val scan = try {
            recoverInvoices()
        } catch (_: Exception) {
            return emit(
                preservePreviousOrFail(
                    previous,
                    safeReason("GMAIL_SCAN_FAILED")
                )
            )
        }

        val financialHome = try {
            getFinancialHome()
        } catch (_: Exception) {
            val previousHome = when (previous) {
                is FinancialSyncState.Ready -> previous.financialHome
                is FinancialSyncState.Partial -> previous.financialHome
                else -> null
            }
            return emit(
                FinancialSyncState.Partial(
                    latestScan = scan,
                    financialHome = previousHome,
                    reason = safeReason("FINANCIAL_HOME_FAILED")
                )
            )
        }

        return emit(
            FinancialSyncState.Ready(
                latestScan = scan,
                financialHome = financialHome
            )
        )
    }

    private fun preservePreviousOrFail(
        previous: FinancialSyncState?,
        reason: String
    ): FinancialSyncState {
        return when (previous) {
            is FinancialSyncState.Ready -> FinancialSyncState.Partial(
                latestScan = previous.latestScan,
                financialHome = previous.financialHome,
                reason = reason
            )
            is FinancialSyncState.Partial -> FinancialSyncState.Partial(
                latestScan = previous.latestScan,
                financialHome = previous.financialHome,
                reason = reason
            )
            else -> FinancialSyncState.Failed(
                reason = reason,
                isAuthRequired = false
            )
        }
    }

    private fun safeReason(code: String): String {
        return when (code) {
            "GMAIL_CONNECTION_STATUS_FAILED" ->
                "בדיקת החיבור ל-Gmail נכשלה. קוד: GMAIL_CONNECTION_STATUS_FAILED"
            "GMAIL_SCAN_FAILED" ->
                "סריקת Gmail נכשלה. קוד: GMAIL_SCAN_FAILED"
            "FINANCIAL_HOME_FAILED" ->
                "טעינת הנתונים הפיננסיים נכשלה. קוד: FINANCIAL_HOME_FAILED"
            else -> "הסנכרון הפיננסי אינו זמין כרגע. קוד: FINANCIAL_REFRESH_FAILED"
        }
    }
}
