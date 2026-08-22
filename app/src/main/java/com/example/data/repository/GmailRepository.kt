package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.data.local.InvoiceItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class GmailSyncState {
    data object Idle : GmailSyncState()
    data class Syncing(val stepMessage: String, val progressPercent: Int) : GmailSyncState()
    data class Success(
        val invoicesFound: Int,
        val totalSavingsPotential: Double,
        val message: String
    ) : GmailSyncState()
    data class Error(val errorMessage: String, val isAuthRequired: Boolean) : GmailSyncState()
}

internal suspend fun projectGmailScanToLocalCache(
    shoppingRepository: ShoppingRepository,
    result: GmailScanResult
) {
    // Only explicit backend removals are deletion evidence. Never infer stale Gmail rows
    // merely because a source is absent from one scan response, and never touch manual bills.
    shoppingRepository.deleteObservedGmailInvoicesBySourceIds(result.removedSourceMessageIds)

    result.invoices.forEach { invoice ->
        shoppingRepository.upsertObservedGmailInvoice(
            InvoiceItem(
                providerName = invoice.providerName,
                category = invoice.category,
                monthlyCost = invoice.monthlyCost,
                recommendedAlternative = "טרם בוצעה השוואה מאומתת",
                alternativeMonthlyCost = 0.0,
                potentialMonthlySavings = 0.0,
                status = "יובא מ-Gmail - ממתין לאימות",
                isSwitchRequested = false,
                accountNumber = "",
                billDate = invoice.receivedDate,
                sourceMessageId = invoice.sourceMessageId,
                sourceType = "GMAIL_READONLY",
                verificationStatus = invoice.verificationStatus
            )
        )
    }
}

class GmailRepository(
    private val shoppingRepository: ShoppingRepository,
    private val backendRepository: BackendRepository = BackendRepository()
) {
    companion object {
        internal fun formatRecoveryDiagnostic(state: GmailRecoveryDiagnosticResult): String =
            buildString {
                append("Recovery diagnostic · completed=")
                append(state.initialBackfillCompleted)
                append(" · parser=")
                append(state.storedParserVersion)
                append("→")
                append(state.activeParserVersion)
                append(" · invoices=")
                append(state.authoritativeInvoiceCount)
                append(" · imports=")
                append(state.gmailMessageImportCount)
                append(" · storedCandidates=")
                append(state.storedCandidateCount)
                append(" · normalized=")
                append(state.normalizedCandidateCount)
                append(" · replayable=")
                append(state.replayableCandidateCount)
                append(" · recurring=")
                append(state.replayableRecurringCount)
                append(" · uniqueSources=")
                append(state.uniqueReplayableSourceCount)
                append(" · duplicates=")
                append(state.duplicateCandidateCount)
                append(" · truncated=")
                append(state.importsTruncated)
            }
    }

    private val _syncState = MutableStateFlow<GmailSyncState>(GmailSyncState.Idle)
    val syncState: StateFlow<GmailSyncState> = _syncState.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectedEmail = MutableStateFlow("")
    val connectedEmail: StateFlow<String> = _connectedEmail.asStateFlow()

    private val _lastScanTime = MutableStateFlow("טרם בוצעה סריקה")
    val lastScanTime: StateFlow<String> = _lastScanTime.asStateFlow()

    private val _recoveryDiagnostic = MutableStateFlow("")
    val recoveryDiagnostic: StateFlow<String> = _recoveryDiagnostic.asStateFlow()

    private suspend fun refreshRecoveryDiagnosticIfAvailable(connected: Boolean) {
        if (!BuildConfig.DEBUG || !connected) {
            _recoveryDiagnostic.value = ""
            return
        }
        runCatching { backendRepository.getGmailSyncStatus(includeRecoveryDiagnostics = true) }
            .onSuccess { status ->
                _recoveryDiagnostic.value = status.recoveryState?.let { formatRecoveryDiagnostic(it) }.orEmpty()
            }
            .onFailure { error ->
                // Diagnostic availability must never alter Gmail connection truth.
                _recoveryDiagnostic.value = ""
                Log.w("GmailRepository", "Sanitized Gmail recovery diagnostic unavailable", error)
            }
    }

    private suspend fun ensureGmailWatch() {
        runCatching { backendRepository.startGmailWatch() }
            .onFailure { error ->
                // Gmail read-only access and manual scans remain usable while Pub/Sub is being configured.
                Log.w("GmailRepository", "Gmail push watch is not active yet", error)
            }
    }

    suspend fun refreshConnectionStatus(): Result<GmailConnectionResult> {
        return runCatching {
            val connection = backendRepository.getGmailConnectionStatus()
            _isConnected.value = connection.connected
            _connectedEmail.value = if (connection.connected) connection.email else ""
            if (connection.connected) ensureGmailWatch()
            refreshRecoveryDiagnosticIfAvailable(connection.connected)
            connection
        }.onFailure { error ->
            Log.e("GmailRepository", "Gmail status refresh failed", error)
            _isConnected.value = false
            _recoveryDiagnostic.value = ""
        }
    }

    suspend fun refreshConnectionStatusAndUpgradeIfNeeded(): Result<GmailConnectionResult> {
        val connectionResult = refreshConnectionStatus()
        val connection = connectionResult.getOrNull() ?: return connectionResult
        if (!connection.connected) return connectionResult

        val syncStatus = runCatching { backendRepository.getGmailSyncStatus() }
            .onFailure { error ->
                // A sync-status lookup failure must never make a valid Gmail connection appear disconnected.
                Log.w("GmailRepository", "Gmail parser upgrade status unavailable", error)
            }
            .getOrNull()

        if (syncStatus?.upgradeRequired == true) {
            Log.i(
                "GmailRepository",
                "Running one-time Gmail parser upgrade ${syncStatus.storedParserVersion} -> ${syncStatus.activeParserVersion}"
            )
            scanInvoices()
        }
        return connectionResult
    }

    suspend fun connectWithAuthorizationCode(
        serverAuthCode: String,
        userEmail: String
    ): Result<GmailConnectionResult> {
        if (serverAuthCode.isBlank()) {
            val message = "Google לא החזיר קוד הרשאה תקף."
            _syncState.value = GmailSyncState.Error(message, isAuthRequired = true)
            return Result.failure(IllegalArgumentException(message))
        }

        _syncState.value = GmailSyncState.Syncing("שומר הרשאת Gmail מוצפנת בשרת...", 25)
        return runCatching {
            val connection = backendRepository.connectGmail(serverAuthCode)
            _isConnected.value = connection.connected
            _connectedEmail.value = connection.email.ifBlank { userEmail }
            if (connection.connected) ensureGmailWatch()
            refreshRecoveryDiagnosticIfAvailable(connection.connected)
            _syncState.value = GmailSyncState.Success(
                invoicesFound = 0,
                totalSavingsPotential = 0.0,
                message = "Gmail חובר בהרשאת קריאה בלבד. מתחיל סריקה ראשונית של 6 חודשים."
            )
            connection
        }.onFailure { error ->
            Log.e("GmailRepository", "Gmail connection failed", error)
            _isConnected.value = false
            _recoveryDiagnostic.value = ""
            _syncState.value = GmailSyncState.Error(
                errorMessage = error.localizedMessage ?: "חיבור Gmail נכשל.",
                isAuthRequired = true
            )
        }
    }

    suspend fun scanInvoices(): Result<GmailScanResult> {
        if (!_isConnected.value) {
            val refreshed = refreshConnectionStatus().getOrNull()
            if (refreshed?.connected != true) {
                val message = "יש לחבר Gmail ולאשר הרשאת קריאה בלבד לפני הסריקה."
                _syncState.value = GmailSyncState.Error(message, isAuthRequired = true)
                return Result.failure(IllegalStateException(message))
            }
        }

        _syncState.value = GmailSyncState.Syncing("סורק בשרת חשבוניות וחיובים רלוונטיים...", 45)
        return runCatching {
            val result = backendRepository.scanGmailInvoices()
            projectGmailScanToLocalCache(shoppingRepository, result)

            _lastScanTime.value = "עכשיו"
            val removedCount = result.removedSourceMessageIds.size
            val recoveredCount = (result.invoices.size - result.importedCount).coerceAtLeast(0)
            _syncState.value = GmailSyncState.Success(
                invoicesFound = result.invoices.size,
                totalSavingsPotential = 0.0,
                message = when {
                    removedCount > 0 ->
                        "הסריקה עודכנה: הוחלפו $removedCount רשומות ישנות בנתונים מדויקים יותר ללא כפילות."
                    result.importedCount > 0 && recoveredCount > 0 ->
                        "יובאו ${result.importedCount} חשבוניות חדשות ועודכנו $recoveredCount רשומות קיימות. כולן ממתינות לאימות."
                    result.importedCount > 0 ->
                        "יובאו ${result.importedCount} חשבוניות חדשות ללא המלצת חיסכון עד לאימות."
                    recoveredCount > 0 ->
                        "לא נמצאו חשבוניות חדשות; עודכנו $recoveredCount רשומות שכבר נקלטו בשרת."
                    result.scannedMessages > 0 ->
                        "נבדקו ${result.scannedMessages} הודעות מועמדות, אך לא נמצא חיוב שניתן לזהות באופן דטרמיניסטי."
                    else ->
                        "לא נמצאו ב-Gmail הודעות מועמדות שתואמות לחיפוש החשבוניות."
                }
            )
            result
        }.onFailure { error ->
            Log.e("GmailRepository", "Server-side Gmail scan failed", error)
            _syncState.value = GmailSyncState.Error(
                errorMessage = error.localizedMessage ?: "סריקת Gmail נכשלה.",
                isAuthRequired = false
            )
        }
    }

    suspend fun disconnectGmail(): Result<Unit> {
        return runCatching {
            val result = backendRepository.disconnectGmailAuthoritatively()
            check(result.ingestionStopped) { "Server did not confirm Gmail ingestion was stopped." }
            _isConnected.value = false
            _connectedEmail.value = ""
            _lastScanTime.value = "מנותק"
            _recoveryDiagnostic.value = ""
            _syncState.value = GmailSyncState.Idle
        }.onFailure { error ->
            Log.e("GmailRepository", "Gmail disconnect failed", error)
            _syncState.value = GmailSyncState.Error(
                errorMessage = error.localizedMessage ?: "ניתוק Gmail נכשל.",
                isAuthRequired = false
            )
        }
    }
}
