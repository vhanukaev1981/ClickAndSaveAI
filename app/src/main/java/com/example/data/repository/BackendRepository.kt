package com.example.data.repository

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

data class BackendInvoice(
    val sourceMessageId: String,
    val providerName: String,
    val category: String,
    val monthlyCost: Double,
    val receivedDate: String,
    val verificationStatus: String
)

data class GmailConnectionResult(
    val connected: Boolean,
    val email: String,
    val consentVersion: String
)

data class GmailSyncStatusResult(
    val connected: Boolean,
    val storedParserVersion: Int,
    val activeParserVersion: Int,
    val upgradeRequired: Boolean,
    val lookback: String
)

data class GmailWatchResult(
    val watching: Boolean,
    val historyId: String = "",
    val expiration: String = ""
)

data class GmailScanResult(
    val invoices: List<BackendInvoice>,
    val scannedMessages: Int,
    val importedCount: Int,
    val removedSourceMessageIds: List<String>
)

data class ProviderLeadRequest(
    val contactName: String,
    val phone: String,
    val contactEmail: String,
    val currentProvider: String,
    val requestedProvider: String,
    val category: String,
    val invoiceLocalId: String,
    val idempotencyKey: String,
    val consentVersion: String = "provider-lead-v1",
    val notes: String = ""
)

data class ProviderLeadResult(
    val leadId: String,
    val status: String,
    val duplicate: Boolean
)

data class BackendDealAnalysis(
    val summary: String,
    val risks: List<String>,
    val questions: List<String>,
    val requiresVerification: Boolean
)

data class FinancialRecurringService(
    val providerName: String,
    val category: String,
    val latestMonthlyCost: Double,
    val observationCount: Int,
    val observedMonths: Int
)

data class FinancialCategorySummary(
    val category: String,
    val observedMonthlySpend: Double
)

data class FinancialHomeContext(
    val observedRecurringMonthlySpend: Double?,
    val recurringServiceCount: Int?,
    val isCompleteHouseholdSpend: Boolean,
    val sourceCoverage: List<String>,
    val recurringServices: List<FinancialRecurringService>,
    val categories: List<FinancialCategorySummary>
)

data class FinancialInsight(
    val id: String,
    val type: String,
    val providerName: String,
    val category: String,
    val currentMonthlyCost: Double,
    val previousMonthlyCost: Double,
    val monthlyIncrease: Double,
    val percentIncrease: Double,
    val severity: String
)

data class FinancialMatchedOffer(
    val offerId: String,
    val providerName: String,
    val pricingModel: String,
    val monthlyPrice: Double,
    val effectiveMonthlyPrice: Double?,
    val priceGuaranteedMonths: Int?,
    val requiredRecurringFees: Double?,
    val requiredRecurringFeesDescription: String,
    val oneTimeFees: Double?,
    val firstYearCost: Double?,
    val serviceType: String,
    val verifiedAt: String,
    val validUntil: String,
    val userFitScore: Double
)

data class FinancialOpportunity(
    val id: String,
    val type: String,
    val status: String,
    val actionMode: String,
    val providerName: String,
    val category: String,
    val serviceType: String,
    val currentMonthlyCost: Double,
    val previousMonthlyCost: Double,
    val monthlyIncrease: Double,
    val percentIncrease: Double,
    val potentialMonthlySaving: Double?,
    val potentialAnnualSaving: Double?,
    val recommendationAction: String,
    val matchedOffer: FinancialMatchedOffer?
)

data class FinancialHomeResult(
    val context: FinancialHomeContext,
    val insights: List<FinancialInsight>,
    val opportunities: List<FinancialOpportunity>
)

class FinancialHomeUnavailableException(cause: Throwable) : IllegalStateException(
    "הנתונים הפיננסיים עדיין מתעדכנים. ננסה שוב אוטומטית.",
    cause
)

class BackendRepository(
    private val functionsProvider: () -> FirebaseFunctions = {
        FirebaseFunctions.getInstance("europe-west1")
    }
) {
    private val functions: FirebaseFunctions by lazy(functionsProvider)

    suspend fun getGmailConnectionStatus(): GmailConnectionResult {
        val response = functions.getHttpsCallable("getGmailConnectionStatus")
            .call()
            .await()
            .data
            .asStringMap()
        return GmailConnectionResult(
            connected = response["connected"] as? Boolean ?: false,
            email = response["email"] as? String ?: "",
            consentVersion = response["consentVersion"] as? String ?: ""
        )
    }

    suspend fun getGmailSyncStatus(): GmailSyncStatusResult {
        val response = functions.getHttpsCallable("getGmailSyncStatus")
            .call()
            .await()
            .data
            .asStringMap()
        return GmailSyncStatusResult(
            connected = response["connected"] as? Boolean ?: false,
            storedParserVersion = (response["storedParserVersion"] as? Number)?.toInt() ?: 0,
            activeParserVersion = (response["activeParserVersion"] as? Number)?.toInt() ?: 0,
            upgradeRequired = response["upgradeRequired"] as? Boolean ?: false,
            lookback = response["lookback"] as? String ?: ""
        )
    }

    suspend fun connectGmail(
        serverAuthCode: String,
        consentVersion: String = "gmail-readonly-v1"
    ): GmailConnectionResult {
        val data = mapOf(
            "serverAuthCode" to serverAuthCode,
            "consentAccepted" to true,
            "consentVersion" to consentVersion
        )
        val response = functions.getHttpsCallable("connectGmail").call(data).await().data.asStringMap()
        return GmailConnectionResult(
            connected = response["connected"] as? Boolean ?: false,
            email = response["email"] as? String ?: "",
            consentVersion = response["consentVersion"] as? String ?: consentVersion
        )
    }

    suspend fun startGmailWatch(): GmailWatchResult {
        val response = functions.getHttpsCallable("startGmailWatch").call().await().data.asStringMap()
        return GmailWatchResult(
            watching = response["watching"] as? Boolean ?: false,
            historyId = response["historyId"]?.toString() ?: "",
            expiration = response["expiration"]?.toString() ?: ""
        )
    }

    suspend fun stopGmailWatch(): GmailWatchResult {
        val response = functions.getHttpsCallable("stopGmailWatch").call().await().data.asStringMap()
        return GmailWatchResult(watching = response["watching"] as? Boolean ?: false)
    }

    suspend fun scanGmailInvoices(): GmailScanResult {
        val response = functions.getHttpsCallable("scanGmailInvoices").call().await().data.asStringMap()
        val invoices = (response["invoices"] as? List<*>)
            .orEmpty()
            .mapNotNull { item ->
                val map = item.asStringMapOrNull() ?: return@mapNotNull null
                val sourceMessageId = map["sourceMessageId"] as? String ?: return@mapNotNull null
                BackendInvoice(
                    sourceMessageId = sourceMessageId,
                    providerName = map["providerName"] as? String ?: "ספק לא ידוע",
                    category = map["category"] as? String ?: "לא מסווג",
                    monthlyCost = (map["monthlyCost"] as? Number)?.toDouble() ?: return@mapNotNull null,
                    receivedDate = map["receivedDate"] as? String ?: "",
                    verificationStatus = map["verificationStatus"] as? String
                        ?: "UNVERIFIED_GMAIL_IMPORT"
                )
            }
        val removedSourceMessageIds = (response["removedSourceMessageIds"] as? List<*>)
            .orEmpty()
            .mapNotNull { it as? String }
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
        return GmailScanResult(
            invoices = invoices,
            scannedMessages = (response["scannedMessages"] as? Number)?.toInt() ?: 0,
            importedCount = (response["importedCount"] as? Number)?.toInt() ?: 0,
            removedSourceMessageIds = removedSourceMessageIds
        )
    }

    suspend fun getFinancialHome(): FinancialHomeResult {
        val response = try {
            functions.getHttpsCallable("getFinancialHome").call().await().data.asStringMap()
        } catch (error: Exception) {
            throw FinancialHomeUnavailableException(error)
        }
        val contextMap = response["context"].asStringMapOrNull().orEmpty()
        val recurringServices = (contextMap["recurringServices"] as? List<*>)
            .orEmpty()
            .mapNotNull { item ->
                val map = item.asStringMapOrNull() ?: return@mapNotNull null
                FinancialRecurringService(
                    providerName = map["providerName"] as? String ?: return@mapNotNull null,
                    category = map["category"] as? String ?: "",
                    latestMonthlyCost = (map["latestMonthlyCost"] as? Number)?.toDouble()
                        ?: return@mapNotNull null,
                    observationCount = (map["observationCount"] as? Number)?.toInt()
                        ?: return@mapNotNull null,
                    observedMonths = (map["observedMonths"] as? Number)?.toInt()
                        ?: return@mapNotNull null
                )
            }
        val categories = (contextMap["categories"] as? List<*>)
            .orEmpty()
            .mapNotNull { item ->
                val map = item.asStringMapOrNull() ?: return@mapNotNull null
                FinancialCategorySummary(
                    category = map["category"] as? String ?: return@mapNotNull null,
                    observedMonthlySpend = (map["observedMonthlySpend"] as? Number)?.toDouble()
                        ?: return@mapNotNull null
                )
            }
        val context = FinancialHomeContext(
            observedRecurringMonthlySpend = (contextMap["observedRecurringMonthlySpend"] as? Number)?.toDouble(),
            recurringServiceCount = (contextMap["recurringServiceCount"] as? Number)?.toInt(),
            isCompleteHouseholdSpend = contextMap["isCompleteHouseholdSpend"] as? Boolean ?: false,
            sourceCoverage = (contextMap["sourceCoverage"] as? List<*>)?.map { it.toString() }.orEmpty(),
            recurringServices = recurringServices,
            categories = categories
        )
        val insights = (response["insights"] as? List<*>)
            .orEmpty()
            .mapNotNull { item ->
                val map = item.asStringMapOrNull() ?: return@mapNotNull null
                FinancialInsight(
                    id = map["id"] as? String ?: return@mapNotNull null,
                    type = map["type"] as? String ?: "",
                    providerName = map["providerName"] as? String ?: "",
                    category = map["category"] as? String ?: "",
                    currentMonthlyCost = (map["currentMonthlyCost"] as? Number)?.toDouble()
                        ?: return@mapNotNull null,
                    previousMonthlyCost = (map["previousMonthlyCost"] as? Number)?.toDouble()
                        ?: return@mapNotNull null,
                    monthlyIncrease = (map["monthlyIncrease"] as? Number)?.toDouble()
                        ?: return@mapNotNull null,
                    percentIncrease = (map["percentIncrease"] as? Number)?.toDouble()
                        ?: return@mapNotNull null,
                    severity = map["severity"] as? String ?: "INFO"
                )
            }
        val opportunities = (response["opportunities"] as? List<*>)
            .orEmpty()
            .mapNotNull { item ->
                val map = item.asStringMapOrNull() ?: return@mapNotNull null
                val matchedMap = map["matchedOffer"].asStringMapOrNull()
                FinancialOpportunity(
                    id = map["id"] as? String ?: return@mapNotNull null,
                    type = map["type"] as? String ?: "",
                    status = map["status"] as? String ?: "OPEN",
                    actionMode = map["actionMode"] as? String ?: "VIEW_ONLY",
                    providerName = map["providerName"] as? String ?: "",
                    category = map["category"] as? String ?: "",
                    serviceType = map["serviceType"] as? String ?: "",
                    currentMonthlyCost = (map["currentMonthlyCost"] as? Number)?.toDouble()
                        ?: return@mapNotNull null,
                    previousMonthlyCost = (map["previousMonthlyCost"] as? Number)?.toDouble()
                        ?: return@mapNotNull null,
                    monthlyIncrease = (map["monthlyIncrease"] as? Number)?.toDouble()
                        ?: return@mapNotNull null,
                    percentIncrease = (map["percentIncrease"] as? Number)?.toDouble()
                        ?: return@mapNotNull null,
                    potentialMonthlySaving = (map["potentialMonthlySaving"] as? Number)?.toDouble(),
                    potentialAnnualSaving = (map["potentialAnnualSaving"] as? Number)?.toDouble(),
                    recommendationAction = map["recommendationAction"] as? String ?: "",
                    matchedOffer = matchedMap?.let {
                        FinancialMatchedOffer(
                            offerId = it["offerId"] as? String ?: return@let null,
                            providerName = it["providerName"] as? String ?: "",
                            pricingModel = it["pricingModel"] as? String ?: "",
                            monthlyPrice = (it["monthlyPrice"] as? Number)?.toDouble() ?: return@let null,
                            effectiveMonthlyPrice = (it["effectiveMonthlyPrice"] as? Number)?.toDouble(),
                            priceGuaranteedMonths = (it["priceGuaranteedMonths"] as? Number)?.toInt(),
                            requiredRecurringFees = (it["requiredRecurringFees"] as? Number)?.toDouble(),
                            requiredRecurringFeesDescription = it["requiredRecurringFeesDescription"] as? String ?: "",
                            oneTimeFees = (it["oneTimeFees"] as? Number)?.toDouble(),
                            firstYearCost = (it["firstYearCost"] as? Number)?.toDouble(),
                            serviceType = it["serviceType"] as? String ?: "",
                            verifiedAt = it["verifiedAt"] as? String ?: "",
                            validUntil = it["validUntil"] as? String ?: "",
                            userFitScore = (it["userFitScore"] as? Number)?.toDouble() ?: return@let null
                        )
                    }
                )
            }
        return FinancialHomeResult(context, insights, opportunities)
    }

    suspend fun disconnectGmail() {
        functions.getHttpsCallable("disconnectGmail").call().await()
    }

    suspend fun createProviderLead(request: ProviderLeadRequest): ProviderLeadResult {
        val payload = mapOf(
            "contactName" to request.contactName,
            "phone" to request.phone,
            "contactEmail" to request.contactEmail,
            "currentProvider" to request.currentProvider,
            "requestedProvider" to request.requestedProvider,
            "category" to request.category,
            "invoiceLocalId" to request.invoiceLocalId,
            "idempotencyKey" to request.idempotencyKey,
            "consentVersion" to request.consentVersion,
            "consentAccepted" to true,
            "notes" to request.notes
        )
        val response = functions.getHttpsCallable("createProviderLead").call(payload).await().data.asStringMap()
        return ProviderLeadResult(
            leadId = response["leadId"] as? String ?: error("Backend did not return a lead ID"),
            status = response["status"] as? String ?: "NEW",
            duplicate = response["duplicate"] as? Boolean ?: false
        )
    }

    suspend fun analyzeDeal(query: String): BackendDealAnalysis {
        val response = functions.getHttpsCallable("analyzeDeal")
            .call(mapOf("query" to query))
            .await()
            .data
            .asStringMap()
        return BackendDealAnalysis(
            summary = response["summary"] as? String ?: "לא הופק סיכום.",
            risks = (response["risks"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
            questions = (response["questions"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
            requiresVerification = response["requiresVerification"] as? Boolean ?: true
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
