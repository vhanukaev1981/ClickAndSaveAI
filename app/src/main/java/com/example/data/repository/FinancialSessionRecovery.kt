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
        } catch (error: Exception) {
            return emit(preservePreviousOrFail(previous, safeReason(error)))
        }

        if (!connection.connected) {
            return emit(FinancialSyncState.Disconnected)
        }

        emit(FinancialSyncState.Recovering)

        val scan = try {
            recoverInvoices()
        } catch (error: Exception) {
            return emit(preservePreviousOrFail(previous, safeReason(error)))
        }

        val financialHome = try {
            getFinancialHome()
        } catch (error: Exception) {
            val previousHome = when (previous) {
                is FinancialSyncState.Ready -> previous.financialHome
                is FinancialSyncState.Partial -> previous.financialHome
                else -> null
            }
            return emit(
                FinancialSyncState.Partial(
                    latestScan = scan,
                    financialHome = previousHome,
                    reason = safeReason(error)
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

    private fun safeReason(error: Exception): String {
        return error.localizedMessage?.takeIf(String::isNotBlank)
            ?: "הסנכרון הפיננסי אינו זמין כרגע."
    }
}
