package com.example

import com.example.ui.SavingsPushCandidate
import com.example.ui.SavingsPushTarget
import com.example.ui.SavingsPushTargetStatus
import com.example.ui.resolveSavingsPushTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavingsPushTargetPolicyTest {
    private val current = listOf(
        SavingsPushCandidate(opportunityId = "opp-1", offerId = "offer-1"),
        SavingsPushCandidate(opportunityId = "opp-2", offerId = "offer-2")
    )

    @Test
    fun noPushTargetLeavesSavingsListUntouched() {
        val result = resolveSavingsPushTarget(null, current)

        assertEquals(SavingsPushTargetStatus.NONE, result.status)
        assertNull(result.focusedOpportunityId)
    }

    @Test
    fun exactCurrentOpportunityAndOfferFocusesOnlyThatOpportunity() {
        val result = resolveSavingsPushTarget(
            SavingsPushTarget(opportunityId = "opp-2", offerId = "offer-2"),
            current
        )

        assertEquals(SavingsPushTargetStatus.FOCUSED, result.status)
        assertEquals("opp-2", result.focusedOpportunityId)
    }

    @Test
    fun sameOpportunityWithDifferentCurrentOfferIsStale() {
        val result = resolveSavingsPushTarget(
            SavingsPushTarget(opportunityId = "opp-1", offerId = "old-offer"),
            current
        )

        assertEquals(SavingsPushTargetStatus.STALE, result.status)
        assertNull(result.focusedOpportunityId)
    }

    @Test
    fun missingOpportunityIsStaleAndNeverSubstitutesAnotherOffer() {
        val result = resolveSavingsPushTarget(
            SavingsPushTarget(opportunityId = "missing-opp", offerId = "offer-2"),
            current
        )

        assertEquals(SavingsPushTargetStatus.STALE, result.status)
        assertNull(result.focusedOpportunityId)
    }

    @Test
    fun candidateWithoutCurrentOfferCannotSatisfyExactPushTarget() {
        val result = resolveSavingsPushTarget(
            SavingsPushTarget(opportunityId = "opp-3", offerId = "offer-3"),
            current + SavingsPushCandidate(opportunityId = "opp-3", offerId = null)
        )

        assertEquals(SavingsPushTargetStatus.STALE, result.status)
        assertNull(result.focusedOpportunityId)
    }
}
