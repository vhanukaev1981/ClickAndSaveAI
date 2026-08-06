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

data class GmailScanResult(
    val invoices: List<BackendInvoice>,
    val scannedMessages: Int,
    val importedCount: Int
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

class BackendRepository(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("europe-west1")
) {
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
        return GmailScanResult(
            invoices = invoices,
            scannedMessages = (response["scannedMessages"] as? Number)?.toInt() ?: 0,
            importedCount = (response["importedCount"] as? Number)?.toInt() ?: invoices.size
        )
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
