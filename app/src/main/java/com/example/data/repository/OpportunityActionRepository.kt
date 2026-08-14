package com.example.data.repository

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

data class OpportunityActionResult(
    val leadId: String,
    val status: String,
    val duplicate: Boolean,
    val opportunityId: String,
    val offerId: String,
    val potentialMonthlySaving: Double?,
    val potentialAnnualSaving: Double?,
    val consentState: String,
    val requestState: String,
    val deliveryAttemptState: String,
    val submissionState: String,
    val deliveryState: String,
    val providerContactState: String,
    val completionState: String,
    val savingRealizationState: String,
    val realizedMonthlySaving: Double?,
    val realizedAnnualSaving: Double?
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
        contactEmail: String,
        consentAccepted: Boolean
    ): OpportunityActionResult {
        require(consentAccepted) { "Explicit provider-contact consent is required" }
        val payload = mapOf(
            "opportunityId" to opportunityId,
            "expectedOfferId" to expectedOfferId,
            "contactName" to contactName,
            "phone" to phone,
            "contactEmail" to contactEmail,
            "consentAccepted" to consentAccepted,
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
            potentialMonthlySaving = (response["potentialMonthlySaving"] as? Number)?.toDouble(),
            potentialAnnualSaving = (response["potentialAnnualSaving"] as? Number)?.toDouble(),
            consentState = response["consentState"] as? String ?: "UNKNOWN",
            requestState = response["requestState"] as? String ?: "NOT_CREATED",
            deliveryAttemptState = response["deliveryAttemptState"] as? String ?: "NOT_ATTEMPTED",
            submissionState = response["submissionState"] as? String ?: "NOT_SUBMITTED",
            deliveryState = response["deliveryState"] as? String ?: "NOT_CONFIRMED",
            providerContactState = response["providerContactState"] as? String ?: "UNKNOWN",
            completionState = response["completionState"] as? String ?: "NOT_COMPLETED",
            savingRealizationState = response["savingRealizationState"] as? String ?: "UNKNOWN",
            realizedMonthlySaving = (response["realizedMonthlySaving"] as? Number)?.toDouble(),
            realizedAnnualSaving = (response["realizedAnnualSaving"] as? Number)?.toDouble()
        )
    }

    private fun Any?.asStringMap(): Map<String, Any?> {
        val raw = this as? Map<*, *> ?: error("Unexpected backend response")
        return raw.entries.associate { (key, value) -> key.toString() to value }
    }
}
