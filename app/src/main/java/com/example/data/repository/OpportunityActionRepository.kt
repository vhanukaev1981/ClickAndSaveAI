package com.example.data.repository

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

data class OpportunityActionResult(
    val leadId: String,
    val status: String,
    val duplicate: Boolean,
    val opportunityId: String,
    val offerId: String,
    val potentialMonthlySaving: Double,
    val potentialAnnualSaving: Double
)

class OpportunityActionRepository(
    private val functionsProvider: () -> FirebaseFunctions = {
        FirebaseFunctions.getInstance("europe-west1")
    }
) {
    private val functions: FirebaseFunctions by lazy(functionsProvider)

    suspend fun recordSavingsActionStarted(
        opportunityId: String,
        expectedOfferId: String
    ) {
        functions.getHttpsCallable("recordSavingsActionStarted")
            .call(
                mapOf(
                    "opportunityId" to opportunityId,
                    "expectedOfferId" to expectedOfferId
                )
            )
            .await()
    }

    suspend fun acceptSavingsOpportunity(
        opportunityId: String,
        expectedOfferId: String,
        contactName: String,
        phone: String,
        contactEmail: String
    ): OpportunityActionResult {
        val payload = mapOf(
            "opportunityId" to opportunityId,
            "expectedOfferId" to expectedOfferId,
            "contactName" to contactName,
            "phone" to phone,
            "contactEmail" to contactEmail,
            "consentAccepted" to true,
            "consentVersion" to "opportunity-action-v1"
        )
        val response = functions.getHttpsCallable("acceptSavingsOpportunity")
            .call(payload)
            .await()
            .data
            .asStringMap()
        return OpportunityActionResult(
            leadId = response["leadId"] as? String ?: error("Backend did not return a lead ID"),
            status = response["status"] as? String ?: "NEW",
            duplicate = response["duplicate"] as? Boolean ?: false,
            opportunityId = response["opportunityId"] as? String ?: opportunityId,
            offerId = response["offerId"] as? String ?: expectedOfferId,
            potentialMonthlySaving = (response["potentialMonthlySaving"] as? Number)?.toDouble() ?: 0.0,
            potentialAnnualSaving = (response["potentialAnnualSaving"] as? Number)?.toDouble() ?: 0.0
        )
    }

    private fun Any?.asStringMap(): Map<String, Any?> {
        val raw = this as? Map<*, *> ?: error("Unexpected backend response")
        return raw.entries.associate { (key, value) -> key.toString() to value }
    }
}
