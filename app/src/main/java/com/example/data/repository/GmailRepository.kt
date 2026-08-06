package com.example.data.repository

import android.util.Log
import com.example.data.local.InvoiceItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

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

data class ParsedGmailInvoice(
    val providerName: String,
    val category: String,
    val monthlyCost: Double,
    val recommendedAlternative: String,
    val alternativeMonthlyCost: Double,
    val potentialMonthlySavings: Double,
    val emailSnippet: String,
    val dateReceived: String
)

class GmailRepository(
    private val shoppingRepository: ShoppingRepository,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val _syncState = MutableStateFlow<GmailSyncState>(GmailSyncState.Idle)
    val syncState: StateFlow<GmailSyncState> = _syncState.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectedEmail = MutableStateFlow("")
    val connectedEmail: StateFlow<String> = _connectedEmail.asStateFlow()

    private val _lastScanTime = MutableStateFlow("טרם בוצעה סריקה")
    val lastScanTime: StateFlow<String> = _lastScanTime.asStateFlow()

    suspend fun fetchAndParseInvoices(
        accessToken: String?,
        userEmail: String = ""
    ): Result<List<ParsedGmailInvoice>> = withContext(Dispatchers.IO) {
        if (accessToken.isNullOrBlank() || userEmail.isBlank()) {
            val message = "נדרשת התחברות Google והרשאת Gmail אמיתית לפני סריקת חשבוניות."
            _isConnected.value = false
            _connectedEmail.value = ""
            _syncState.value = GmailSyncState.Error(message, isAuthRequired = true)
            return@withContext Result.failure(IllegalStateException(message))
        }

        _syncState.value = GmailSyncState.Syncing("מתחבר ל-Gmail API...", 10)

        try {
            val query = "subject:(חשבונית OR קבלה OR הודעת תשלום)"
            val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
            val listUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages?q=$encodedQuery&maxResults=15"
            val request = Request.Builder()
                .url(listUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Accept", "application/json")
                .build()

            val messageIds = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val authRequired = response.code == 401 || response.code == 403
                    val message = if (authRequired) {
                        "הרשאת Gmail חסרה או פגה. יש להתחבר מחדש."
                    } else {
                        "Gmail API returned HTTP ${response.code}."
                    }
                    throw GmailSyncException(message, authRequired)
                }

                val body = response.body?.string().orEmpty()
                val messages = JSONObject(body).optJSONArray("messages")
                    ?: return@use emptyList<String>()
                buildList {
                    for (index in 0 until messages.length()) {
                        messages.optJSONObject(index)?.optString("id")
                            ?.takeIf { it.isNotBlank() }
                            ?.let(::add)
                    }
                }
            }

            _syncState.value = GmailSyncState.Syncing("מנתח הודעות חשבונית...", 60)
            val parsedInvoices = mutableListOf<ParsedGmailInvoice>()

            messageIds.forEach { messageId ->
                val detailUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages/$messageId?format=metadata"
                val detailRequest = Request.Builder()
                    .url(detailUrl)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("Accept", "application/json")
                    .build()

                okHttpClient.newCall(detailRequest).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string().orEmpty()
                    val snippet = JSONObject(body).optString("snippet", "")
                    parseInvoiceFromSnippet(snippet)?.let(parsedInvoices::add)
                }
            }

            saveInvoicesToDatabase(parsedInvoices)
            _isConnected.value = true
            _connectedEmail.value = userEmail
            _lastScanTime.value = "עכשיו"
            _syncState.value = GmailSyncState.Success(
                invoicesFound = parsedInvoices.size,
                totalSavingsPotential = 0.0,
                message = if (parsedInvoices.isEmpty()) {
                    "הסריקה הסתיימה, אך לא נמצאו חשבוניות שניתן לאמת."
                } else {
                    "נמצאו ${parsedInvoices.size} חשבוניות. הן נשמרו ללא המלצת חיסכון עד לאימות."
                }
            )
            Result.success(parsedInvoices)
        } catch (e: GmailSyncException) {
            Log.e("GmailRepository", "Gmail sync failed", e)
            _isConnected.value = false
            _syncState.value = GmailSyncState.Error(e.message.orEmpty(), e.authRequired)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("GmailRepository", "Gmail sync failed", e)
            val message = "סריקת Gmail נכשלה. לא הוזנו נתוני דמה."
            _isConnected.value = false
            _syncState.value = GmailSyncState.Error(message, isAuthRequired = false)
            Result.failure(e)
        }
    }

    private fun parseInvoiceFromSnippet(snippet: String): ParsedGmailInvoice? {
        if (snippet.isBlank()) return null

        val amountMatcher = Pattern
            .compile("(?:₪|ש\\\"ח|ILS)\\s*(\\d+(?:[.,]\\d{1,2})?)")
            .matcher(snippet)
        if (!amountMatcher.find()) return null
        val amount = amountMatcher.group(1)
            ?.replace(',', '.')
            ?.toDoubleOrNull()
            ?.takeIf { it > 0.0 }
            ?: return null

        val category = when {
            snippet.contains("חשמל", ignoreCase = true) || snippet.contains("iec", ignoreCase = true) -> "חשמל"
            snippet.contains("סלולר", ignoreCase = true) ||
                snippet.contains("פלאפון", ignoreCase = true) ||
                snippet.contains("סלקום", ignoreCase = true) ||
                snippet.contains("פרטנר", ignoreCase = true) -> "סלולר"
            snippet.contains("אינטרנט", ignoreCase = true) ||
                snippet.contains("סיבים", ignoreCase = true) -> "אינטרנט"
            snippet.contains("ביטוח", ignoreCase = true) -> "ביטוח"
            else -> return null
        }

        return ParsedGmailInvoice(
            providerName = "ספק שזוהה מהודעת Gmail",
            category = category,
            monthlyCost = amount,
            recommendedAlternative = "טרם בוצעה השוואה מאומתת",
            alternativeMonthlyCost = amount,
            potentialMonthlySavings = 0.0,
            emailSnippet = snippet,
            dateReceived = "לא ידוע"
        )
    }

    private suspend fun saveInvoicesToDatabase(invoices: List<ParsedGmailInvoice>) {
        invoices.forEach { invoice ->
            shoppingRepository.addInvoice(
                InvoiceItem(
                    providerName = invoice.providerName,
                    category = invoice.category,
                    monthlyCost = invoice.monthlyCost,
                    recommendedAlternative = invoice.recommendedAlternative,
                    alternativeMonthlyCost = invoice.alternativeMonthlyCost,
                    potentialMonthlySavings = invoice.potentialMonthlySavings,
                    status = "נמצא ב-Gmail - ממתין לאימות",
                    isSwitchRequested = false,
                    accountNumber = "",
                    billDate = invoice.dateReceived
                )
            )
        }
    }

    fun disconnectGmail() {
        _isConnected.value = false
        _connectedEmail.value = ""
        _syncState.value = GmailSyncState.Idle
        _lastScanTime.value = "מנותק"
    }

    private class GmailSyncException(
        message: String,
        val authRequired: Boolean
    ) : Exception(message)
}
