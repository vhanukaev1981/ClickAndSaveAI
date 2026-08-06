package com.example.data.repository

import android.util.Log
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

class GmailRepository(
    private val shoppingRepository: ShoppingRepository,
    private val backendRepository: BackendRepository = BackendRepository()
) {
    private val _syncState = MutableStateFlow<GmailSyncState>(GmailSyncState.Idle)
    val syncState: StateFlow<GmailSyncState> = _syncState.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectedEmail = MutableStateFlow("")
    val connectedEmail: StateFlow<String> = _connectedEmail.asStateFlow()

    private val _lastScanTime = MutableStateFlow("טרם בוצעה סריקה")
    val lastScanTime: StateFlow<String> = _lastScanTime.asStateFlow()

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
            _syncState.value = GmailSyncState.Success(
                invoicesFound = 0,
                totalSavingsPotential = 0.0,
                message = "Gmail חובר בהרשאת קריאה בלבד. טרם בוצעה סריקה."
            )
            connection
        }.onFailure { error ->
            Log.e("GmailRepository", "Gmail connection failed", error)
            _isConnected.value = false
            _syncState.value = GmailSyncState.Error(
                errorMessage = error.localizedMessage ?: "חיבור Gmail נכשל.",
                isAuthRequired = true
            )
        }
    }

    suspend fun scanInvoices(): Result<GmailScanResult> {
        if (!_isConnected.value) {
            val message = "יש לחבר Gmail ולאשר הרשאת קריאה בלבד לפני הסריקה."
            _syncState.value = GmailSyncState.Error(message, isAuthRequired = true)
            return Result.failure(IllegalStateException(message))
        }

        _syncState.value = GmailSyncState.Syncing("סורק בשרת הודעות עם נושאי חשבונית בלבד...", 45)
        return runCatching {
            val result = backendRepository.scanGmailInvoices()
            result.invoices.forEach { invoice ->
                shoppingRepository.addInvoice(
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
            _lastScanTime.value = "עכשיו"
            _syncState.value = GmailSyncState.Success(
                invoicesFound = result.importedCount,
                totalSavingsPotential = 0.0,
                message = if (result.importedCount == 0) {
                    "הסריקה הסתיימה. לא נמצאו חשבוניות חדשות שניתן לזהות באופן דטרמיניסטי."
                } else {
                    "יובאו ${result.importedCount} חשבוניות חדשות ללא המלצת חיסכון עד לאימות."
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
            backendRepository.disconnectGmail()
            _isConnected.value = false
            _connectedEmail.value = ""
            _lastScanTime.value = "מנותק"
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
