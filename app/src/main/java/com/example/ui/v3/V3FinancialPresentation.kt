package com.example.ui.v3

import com.example.data.repository.FinancialHomeResult
import com.example.data.repository.FinancialOpportunity
import java.util.Locale

data class V3SavingsSummary(
    val realizedMonthly: Double?,
    val potentialMonthly: Double?,
    val nextBestOpportunityId: String?,
    val realizedKnownZero: Boolean = false
)

fun FinancialOpportunity.hasAuthoritativeV3Offer(): Boolean {
    val offer = matchedOffer ?: return false
    return offerVerificationState == "VERIFIED" &&
        offerFreshnessState == "FRESH" &&
        userEligibilityState == "ELIGIBLE" &&
        offer.verificationState == "VERIFIED" &&
        offer.freshnessState == "FRESH" &&
        offer.eligibilityState == "ELIGIBLE"
}

fun FinancialHomeResult.toV3SavingsSummary(): V3SavingsSummary {
    val realizedValues = opportunities
        .filter { it.savingRealizationState == "REALIZED" }
        .mapNotNull { opportunity ->
            opportunity.realizedMonthlySaving?.takeIf { value -> value >= 0.0 }
        }

    val realizedKnownZero = realizedValues.isEmpty() && opportunities.any {
        it.savingRealizationState == "NOT_REALIZED" &&
            it.realizedMonthlySaving == 0.0 &&
            it.completionState == "DEAL_COMPLETED"
    }

    val openOpportunities = opportunities.filter {
        it.savingRealizationState != "REALIZED" &&
            it.hasAuthoritativeV3Offer() &&
            (it.potentialMonthlySaving ?: 0.0) > 0.0
    }

    val potentialValues = openOpportunities.mapNotNull { it.potentialMonthlySaving }
    val nextBest = openOpportunities.maxByOrNull {
        it.potentialMonthlySaving ?: Double.NEGATIVE_INFINITY
    }

    return V3SavingsSummary(
        realizedMonthly = realizedValues.takeIf { it.isNotEmpty() }?.sum(),
        potentialMonthly = potentialValues.takeIf { it.isNotEmpty() }?.sum(),
        nextBestOpportunityId = nextBest?.id,
        realizedKnownZero = realizedKnownZero
    )
}

fun FinancialOpportunity.v3LifecycleLabel(): String = when {
    savingRealizationState == "REALIZED" && realizedMonthlySaving != null -> "מומש"
    completionState == "DEAL_COMPLETED" ||
        providerContactState == "CONTACTED" ||
        deliveryState == "DELIVERY_CONFIRMED" ||
        submissionState == "SUBMITTED" ||
        requestState == "REQUEST_CREATED" -> "בתהליך"
    hasAuthoritativeV3Offer() && actionMode == "IN_APP_PROVIDER_REQUEST" -> "מוכן לפעולה"
    hasAuthoritativeV3Offer() -> "נבדק"
    else -> "נמצא"
}

fun Double.asV3Money(): String = "₪${String.format(Locale.US, "%.2f", this)}"
