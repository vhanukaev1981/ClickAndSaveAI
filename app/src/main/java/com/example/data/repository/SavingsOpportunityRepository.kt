package com.example.data.repository

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

data class SavingsMatchedOffer(
    val offerId: String,
    val providerName: String,
    val pricingModel: String,
    val monthlyPrice: Double?,
    val effectiveMonthlyPrice: Double?,
    val priceGuaranteedMonths: Int?,
    val requiredRecurringFees: Double?,
    val requiredRecurringFeesDescription: String,
    val oneTimeFees: Double?,
    val firstYearCost: Double?,
    val serviceType: String,
    val verificationState: String,
    val freshnessState: String,
    val eligibilityState: String,
    val verificationMethod: String,
    val officialSourceUrl: String,
    val officialSourceName: String,
    val verifiedAt: String,
    val validUntil: String
)

data class SavingsOpportunityView(
    val id: String,
    val type: String,
    val status: String,
    val actionMode: String,
    val providerName: String,
    val category: String,
    val serviceType: String,
    val currentMonthlyCost: Double?,
    val previousMonthlyCost: Double?,
    val monthlyIncrease: Double?,
    val percentIncrease: Double?,
    val potentialMonthlySaving: Double?,
    val potentialAnnualSaving: Double?,
    val realizedMonthlySaving: Double?,
    val realizedAnnualSaving: Double?,
    val currentCostEvidenceState: String,
    val offerVerificationState: String,
    val offerFreshnessState: String,
    val userEligibilityState: String,
    val consentState: String,
    val requestState: String,
    val deliveryAttemptState: String,
    val submissionState: String,
    val deliveryState: String,
    val providerContactState: String,
    val completionState: String,
    val savingRealizationState: String,
    val matchedOffer: SavingsMatchedOffer?
)

class SavingsOpportunityRepository(
    private val functionsProvider: () -> FirebaseFunctions = {
        FirebaseFunctions.getInstance("europe-west1")
    }
) {
    private val functions: FirebaseFunctions by lazy(functionsProvider)

    suspend fun getSavingsOpportunities(): List<SavingsOpportunityView> {
        val response = functions.getHttpsCallable("getSavingsOpportunities")
            .call()
            .await()
            .data
            .asStringMap()

        return (response["opportunities"] as? List<*>)
            .orEmpty()
            .mapNotNull { item -> parseOpportunity(item.asStringMapOrNull()) }
    }

    private fun parseOpportunity(map: Map<String, Any?>?): SavingsOpportunityView? {
        map ?: return null
        val id = map["id"] as? String ?: return null
        return SavingsOpportunityView(
            id = id,
            type = map["type"] as? String ?: "",
            status = map["status"] as? String ?: "OPEN",
            actionMode = map["actionMode"] as? String ?: "VIEW_ONLY",
            providerName = map["providerName"] as? String ?: "",
            category = map["category"] as? String ?: "",
            serviceType = map["serviceType"] as? String ?: "",
            currentMonthlyCost = (map["currentMonthlyCost"] as? Number)?.toDouble(),
            previousMonthlyCost = (map["previousMonthlyCost"] as? Number)?.toDouble(),
            monthlyIncrease = (map["monthlyIncrease"] as? Number)?.toDouble(),
            percentIncrease = (map["percentIncrease"] as? Number)?.toDouble(),
            potentialMonthlySaving = (map["potentialMonthlySaving"] as? Number)?.toDouble(),
            potentialAnnualSaving = (map["potentialAnnualSaving"] as? Number)?.toDouble(),
            realizedMonthlySaving = (map["realizedMonthlySaving"] as? Number)?.toDouble(),
            realizedAnnualSaving = (map["realizedAnnualSaving"] as? Number)?.toDouble(),
            currentCostEvidenceState = map["currentCostEvidenceState"] as? String ?: "UNKNOWN",
            offerVerificationState = map["offerVerificationState"] as? String ?: "UNKNOWN",
            offerFreshnessState = map["offerFreshnessState"] as? String ?: "UNKNOWN",
            userEligibilityState = map["userEligibilityState"] as? String ?: "UNKNOWN",
            consentState = map["consentState"] as? String ?: "NOT_CONSENTED",
            requestState = map["requestState"] as? String ?: "NOT_CREATED",
            deliveryAttemptState = map["deliveryAttemptState"] as? String ?: "NOT_ATTEMPTED",
            submissionState = map["submissionState"] as? String ?: "NOT_SUBMITTED",
            deliveryState = map["deliveryState"] as? String ?: "NOT_CONFIRMED",
            providerContactState = map["providerContactState"] as? String ?: "UNKNOWN",
            completionState = map["completionState"] as? String ?: "NOT_COMPLETED",
            savingRealizationState = map["savingRealizationState"] as? String ?: "UNKNOWN",
            matchedOffer = parseOffer(map["matchedOffer"].asStringMapOrNull())
        )
    }

    private fun parseOffer(map: Map<String, Any?>?): SavingsMatchedOffer? {
        map ?: return null
        val offerId = map["offerId"] as? String ?: return null
        return SavingsMatchedOffer(
            offerId = offerId,
            providerName = map["providerName"] as? String ?: "",
            pricingModel = map["pricingModel"] as? String ?: "",
            monthlyPrice = (map["monthlyPrice"] as? Number)?.toDouble(),
            effectiveMonthlyPrice = (map["effectiveMonthlyPrice"] as? Number)?.toDouble(),
            priceGuaranteedMonths = (map["priceGuaranteedMonths"] as? Number)?.toInt(),
            requiredRecurringFees = (map["requiredRecurringFees"] as? Number)?.toDouble(),
            requiredRecurringFeesDescription = map["requiredRecurringFeesDescription"] as? String ?: "",
            oneTimeFees = (map["oneTimeFees"] as? Number)?.toDouble(),
            firstYearCost = (map["firstYearCost"] as? Number)?.toDouble(),
            serviceType = map["serviceType"] as? String ?: "",
            verificationState = map["verificationState"] as? String ?: "UNKNOWN",
            freshnessState = map["freshnessState"] as? String ?: "UNKNOWN",
            eligibilityState = map["eligibilityState"] as? String ?: "UNKNOWN",
            verificationMethod = map["verificationMethod"] as? String ?: "",
            officialSourceUrl = map["officialSourceUrl"] as? String ?: "",
            officialSourceName = map["officialSourceName"] as? String ?: "",
            verifiedAt = map["verifiedAt"] as? String ?: "",
            validUntil = map["validUntil"] as? String ?: ""
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
