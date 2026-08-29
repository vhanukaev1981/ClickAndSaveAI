package com.example.ui.v3

import com.example.data.repository.BackendInvoice
import com.example.data.repository.FinancialHomeResult
import com.example.data.repository.FinancialOpportunity
import java.util.Locale

data class V3SavingsSummary(
    val realizedMonthly: Double?,
    val realizedAnnual: Double?,
    val potentialMonthly: Double?,
    val potentialAnnual: Double?,
    val nextBestOpportunityId: String?,
    val realizedKnownZero: Boolean = false
)

enum class V3SavingsActionMode {
    DIRECT_PLAN_JOIN,
    PROVIDER_LEAD_FLOW,
    VIEW_ONLY,
    NO_VERIFIED_ACTION_TARGET
}

enum class V3InvoicePaymentMode {
    DIRECT_INVOICE_PAYMENT,
    PROVIDER_PAYMENT_PORTAL,
    NO_VERIFIED_PAYMENT_TARGET
}

data class V3AiSuggestion(
    val id: String,
    val label: String,
    val prompt: String,
    val rankScore: Double
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

fun FinancialOpportunity.hasQualifiedBillIncrease(): Boolean {
    val monthlyIncrease = monthlyIncrease ?: return false
    val percentIncrease = percentIncrease ?: return false
    return monthlyIncrease >= 5.0 && percentIncrease >= 5.0
}

fun FinancialOpportunity.v3SavingsActionMode(): V3SavingsActionMode = when {
    actionMode == "VIEW_ONLY" -> V3SavingsActionMode.VIEW_ONLY
    hasAuthoritativeV3Offer() &&
        (actionMode == "IN_APP_PROVIDER_REQUEST" || actionMode == "PROVIDER_LEAD_FLOW") ->
        V3SavingsActionMode.PROVIDER_LEAD_FLOW
    else -> V3SavingsActionMode.NO_VERIFIED_ACTION_TARGET
}

fun FinancialOpportunity.hasVerifiedSavingsActionTarget(): Boolean =
    v3SavingsActionMode() == V3SavingsActionMode.PROVIDER_LEAD_FLOW

fun BackendInvoice.v3PaymentMode(): V3InvoicePaymentMode =
    V3InvoicePaymentMode.NO_VERIFIED_PAYMENT_TARGET

fun FinancialHomeResult.toV3SavingsSummary(): V3SavingsSummary {
    val realized = opportunities.filter { it.savingRealizationState == "REALIZED" }
    val realizedMonthlyValues = realized.mapNotNull { it.realizedMonthlySaving?.takeIf { value -> value >= 0.0 } }
    val realizedAnnualValues = realized.mapNotNull { it.realizedAnnualSaving?.takeIf { value -> value >= 0.0 } }

    val realizedKnownZero = realizedMonthlyValues.isEmpty() && opportunities.any {
        it.savingRealizationState == "NOT_REALIZED" &&
            it.realizedMonthlySaving == 0.0 &&
            it.completionState == "DEAL_COMPLETED"
    }

    val openOpportunities = opportunities.filter {
        it.savingRealizationState != "REALIZED" &&
            it.hasAuthoritativeV3Offer() &&
            (it.potentialMonthlySaving ?: 0.0) > 0.0
    }
    val potentialMonthlyValues = openOpportunities.mapNotNull { it.potentialMonthlySaving }
    val potentialAnnualValues = openOpportunities.mapNotNull { it.potentialAnnualSaving?.takeIf { value -> value >= 0.0 } }
    val nextBest = openOpportunities.maxByOrNull { it.potentialMonthlySaving ?: Double.NEGATIVE_INFINITY }

    return V3SavingsSummary(
        realizedMonthly = realizedMonthlyValues.takeIf { it.isNotEmpty() }?.sum(),
        realizedAnnual = realizedAnnualValues.takeIf { it.isNotEmpty() }?.sum(),
        potentialMonthly = potentialMonthlyValues.takeIf { it.isNotEmpty() }?.sum(),
        potentialAnnual = potentialAnnualValues.takeIf { it.isNotEmpty() }?.sum(),
        nextBestOpportunityId = nextBest?.id,
        realizedKnownZero = realizedKnownZero
    )
}

fun FinancialHomeResult?.v3RankedAiSuggestions(): List<V3AiSuggestion> {
    if (this == null) return emptyList()
    val suggestions = mutableListOf<V3AiSuggestion>()

    opportunities
        .filter { it.savingRealizationState != "REALIZED" && it.hasAuthoritativeV3Offer() }
        .forEach { opportunity ->
            val monthly = opportunity.potentialMonthlySaving?.takeIf { it > 0.0 } ?: return@forEach
            val annual = opportunity.potentialAnnualSaving?.takeIf { it > 0.0 }
            suggestions += V3AiSuggestion(
                id = "saving:${opportunity.id}",
                label = buildString {
                    append("${opportunity.providerName}: ${monthly.asV3Money()} פוטנציאל בחודש")
                    if (annual != null) append(" · ${annual.asV3Money()} בשנה")
                },
                prompt = "בדוק את הזדמנות החיסכון המאומתת אצל ${opportunity.providerName} והסבר מה ידוע ומה עדיין דורש פעולה.",
                rankScore = 10_000.0 + monthly
            )
        }

    opportunities
        .filter { it.hasQualifiedBillIncrease() }
        .forEach { opportunity ->
            val delta = opportunity.monthlyIncrease ?: return@forEach
            val percent = opportunity.percentIncrease ?: return@forEach
            suggestions += V3AiSuggestion(
                id = "bill-increase:${opportunity.id}",
                label = "החשבון אצל ${opportunity.providerName} עלה ב-${delta.asV3Money()} (${String.format(Locale.US, "%.1f", percent)}%)",
                prompt = "בדוק את ההשוואה בין החיובים אצל ${opportunity.providerName}. אל תניח שהתעריף התייקר בלי ראיה לכך.",
                rankScore = 5_000.0 + delta
            )
        }

    return suggestions.distinctBy { it.id }.sortedByDescending { it.rankScore }.take(3)
}

fun FinancialOpportunity.v3LifecycleLabel(): String = when {
    savingRealizationState == "REALIZED" && realizedMonthlySaving != null -> "מומש"
    completionState == "DEAL_COMPLETED" ||
        providerContactState == "CONTACTED" ||
        deliveryState == "DELIVERY_CONFIRMED" ||
        submissionState == "SUBMITTED" ||
        requestState == "REQUEST_CREATED" -> "בתהליך"
    hasAuthoritativeV3Offer() && hasVerifiedSavingsActionTarget() -> "מוכן לפעולה"
    hasAuthoritativeV3Offer() -> "נבדק"
    else -> "נמצא"
}

fun Double.asV3Money(): String = "₪${String.format(Locale.US, "%.2f", this)}"
