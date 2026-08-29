package com.example

import com.example.data.repository.FinancialCategorySummary
import com.example.data.repository.FinancialHomeContext
import com.example.data.repository.FinancialHomeResult
import com.example.data.repository.FinancialMatchedOffer
import com.example.data.repository.FinancialOpportunity
import com.example.ui.v3.toV3SavingsSummary
import com.example.ui.v3.v3LifecycleLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V3FinancialPresentationTest {
    @Test
    fun realizedAndPotentialAreNeverMerged() {
        val realized = opportunity(
            id = "realized",
            potential = 40.0,
            realized = 35.0,
            savingState = "REALIZED"
        )
        val open = opportunity(
            id = "open",
            potential = 50.0,
            realized = null,
            savingState = "UNKNOWN"
        )

        val summary = home(realized, open).toV3SavingsSummary()

        assertEquals(35.0, summary.realizedMonthly!!, 0.001)
        assertEquals(50.0, summary.potentialMonthly!!, 0.001)
        assertEquals("open", summary.nextBestOpportunityId)
        assertFalse(summary.realizedKnownZero)
    }

    @Test
    fun missingSavingsRemainUnknownInsteadOfZero() {
        val unknown = opportunity(
            id = "unknown",
            potential = null,
            realized = null,
            savingState = "UNKNOWN",
            verifiedOffer = false
        )

        val summary = home(unknown).toV3SavingsSummary()

        assertNull(summary.realizedMonthly)
        assertNull(summary.potentialMonthly)
        assertNull(summary.nextBestOpportunityId)
        assertFalse(summary.realizedKnownZero)
    }

    @Test
    fun explicitNotRealizedZeroRemainsKnownZero() {
        val notRealized = opportunity(
            id = "no-saving",
            potential = 20.0,
            realized = 0.0,
            savingState = "NOT_REALIZED",
            completionState = "DEAL_COMPLETED"
        )

        val summary = home(notRealized).toV3SavingsSummary()

        assertNull(summary.realizedMonthly)
        assertTrue(summary.realizedKnownZero)
    }

    @Test
    fun lifecycleNeverCallsDealCompletionRealizedWithoutSavingEvidence() {
        val completedUnknown = opportunity(
            id = "completed",
            potential = 20.0,
            realized = null,
            savingState = "UNKNOWN",
            completionState = "DEAL_COMPLETED"
        )

        assertEquals("בתהליך", completedUnknown.v3LifecycleLabel())
    }

    private fun home(vararg opportunities: FinancialOpportunity) = FinancialHomeResult(
        context = FinancialHomeContext(
            observedRecurringMonthlySpend = null,
            recurringServiceCount = null,
            isCompleteHouseholdSpend = false,
            sourceCoverage = emptyList(),
            recurringServices = emptyList(),
            categories = emptyList<FinancialCategorySummary>()
        ),
        insights = emptyList(),
        opportunities = opportunities.toList()
    )

    private fun opportunity(
        id: String,
        potential: Double?,
        realized: Double?,
        savingState: String,
        verifiedOffer: Boolean = true,
        completionState: String = "UNKNOWN"
    ): FinancialOpportunity {
        val verification = if (verifiedOffer) "VERIFIED" else "UNKNOWN"
        val freshness = if (verifiedOffer) "FRESH" else "UNKNOWN"
        val eligibility = if (verifiedOffer) "ELIGIBLE" else "UNKNOWN"
        val offer = if (verifiedOffer) FinancialMatchedOffer(
            offerId = "offer-$id",
            providerName = "Provider $id",
            pricingModel = "MONTHLY",
            monthlyPrice = 80.0,
            effectiveMonthlyPrice = 80.0,
            priceGuaranteedMonths = null,
            requiredRecurringFees = null,
            requiredRecurringFeesDescription = "",
            oneTimeFees = null,
            firstYearCost = null,
            serviceType = "internet",
            verificationState = verification,
            freshnessState = freshness,
            eligibilityState = eligibility,
            verificationMethod = "OFFICIAL_SOURCE",
            officialSourceUrl = "https://example.invalid/$id",
            officialSourceName = "Official",
            verifiedAt = "2026-08-19",
            validUntil = "",
            userFitScore = null
        ) else null

        return FinancialOpportunity(
            id = id,
            type = "COMPARE_PROVIDER",
            status = "READY",
            actionMode = "IN_APP_PROVIDER_REQUEST",
            providerName = "Current $id",
            category = "אינטרנט",
            serviceType = "internet",
            currentMonthlyCost = 120.0,
            previousMonthlyCost = null,
            monthlyIncrease = null,
            percentIncrease = null,
            potentialMonthlySaving = potential,
            potentialAnnualSaving = potential?.times(12),
            realizedMonthlySaving = realized,
            realizedAnnualSaving = realized?.times(12),
            currentCostEvidenceState = "VERIFIED",
            offerVerificationState = verification,
            offerFreshnessState = freshness,
            userEligibilityState = eligibility,
            consentState = "UNKNOWN",
            requestState = "UNKNOWN",
            deliveryAttemptState = "UNKNOWN",
            submissionState = "UNKNOWN",
            deliveryState = "UNKNOWN",
            providerContactState = "UNKNOWN",
            completionState = completionState,
            savingRealizationState = savingState,
            recommendationAction = "CHECK",
            matchedOffer = offer
        )
    }
}
