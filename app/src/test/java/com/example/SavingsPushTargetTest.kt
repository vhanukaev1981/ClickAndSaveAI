package com.example

import com.example.ui.SavingsPushTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class SavingsPushTargetTest {
    @Test
    fun exactTargetPreservesOpportunityAndOfferIds() {
        assertEquals(
            SavingsPushTarget(opportunityId = "opp-1", offerId = "offer-1"),
            SavingsPushTarget(opportunityId = "opp-1", offerId = "offer-1")
        )
    }
}
