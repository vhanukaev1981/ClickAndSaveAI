package com.example.data.repository

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

data class SavingsMatchedOffer(
    val offerId: String,
    val providerName: String,
    val pricingModel: String,
    val monthlyPrice: Double?,
    val priceGuaranteedMonths: Int?,
    val oneTimeFees: Double?,
    val firstYearCost: Double?,
    val serviceType: String,
    val verifiedAt: String,
    val validUntil: String
)

data class SavingsOpportunityView(
    val id: String,
    val type: String,
    val status: String,
    val providerName: String,
    val category: String,
    val serviceType: String,
    val currentMonthlyCost: Double?,
    val previousMonthlyCost: Double?,
    val monthlyIncrease: Double?,
    val percentIncrease: Double?,
    val potentialMonthlySaving: Double?,
    val potentialAnnualSaving: Double?,
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
            providerName = map["providerName"] as? String ?: "",
            category = map["category"] as? String ?: "",
            serviceType = map["serviceType"] as? String ?: "",
            currentMonthlyCost = (map["currentMonthlyCost"] as? Number)?.toDouble(),
            previousMonthlyCost = (map["previousMonthlyCost"] as? Number)?.toDouble(),
            monthlyIncrease = (map["monthlyIncrease"] as? Number)?.toDouble(),
            percentIncrease = (map["percentIncrease"] as? Number)?.toDouble(),
            potentialMonthlySaving = (map["potentialMonthlySaving"] as? Number)?.toDouble(),
            potentialAnnualSaving = (map["potentialAnnualSaving"] as? Number)?.toDouble(),
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
            priceGuaranteedMonths = (map["priceGuaranteedMonths"] as? Number)?.toInt(),
            oneTimeFees = (map["oneTimeFees"] as? Number)?.toDouble(),
            firstYearCost = (map["firstYearCost"] as? Number)?.toDouble(),
            serviceType = map["serviceType"] as? String ?: "",
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
