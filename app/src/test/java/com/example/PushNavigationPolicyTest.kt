package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushNavigationPolicyTest {
    @Test
    fun newInvoiceOpensBills() {
        assertEquals(1, destinationTabForPushType(PUSH_TYPE_NEW_INVOICE))
        assertEquals(
            PushNavigationTarget(tab = 1),
            navigationTargetForPush(PUSH_TYPE_NEW_INVOICE, null, null)
        )
    }

    @Test
    fun verifiedSavingsRequiresAndPreservesExactOpportunityAndOffer() {
        assertEquals(2, destinationTabForPushType(PUSH_TYPE_VERIFIED_SAVINGS_OPPORTUNITY))
        assertEquals(
            PushNavigationTarget(
                tab = 2,
                opportunityId = "opp-1",
                offerId = "offer-1"
            ),
            navigationTargetForPush(
                PUSH_TYPE_VERIFIED_SAVINGS_OPPORTUNITY,
                "opp-1",
                "offer-1"
            )
        )
    }

    @Test
    fun verifiedSavingsWithoutExactIdsCannotNavigate() {
        assertNull(
            navigationTargetForPush(
                PUSH_TYPE_VERIFIED_SAVINGS_OPPORTUNITY,
                "",
                "offer-1"
            )
        )
        assertNull(
            navigationTargetForPush(
                PUSH_TYPE_VERIFIED_SAVINGS_OPPORTUNITY,
                "opp-1",
                ""
            )
        )
        assertNull(
            navigationTargetForPush(
                PUSH_TYPE_VERIFIED_SAVINGS_OPPORTUNITY,
                null,
                null
            )
        )
    }

    @Test
    fun testPushOpensDashboard() {
        assertEquals(0, destinationTabForPushType(PUSH_TYPE_TEST))
        assertEquals(
            PushNavigationTarget(tab = 0),
            navigationTargetForPush(PUSH_TYPE_TEST, null, null)
        )
    }

    @Test
    fun unknownPushCannotChooseArbitraryTab() {
        assertNull(destinationTabForPushType("OPEN_PROFILE"))
        assertNull(destinationTabForPushType("99"))
        assertNull(destinationTabForPushType(null))
        assertNull(navigationTargetForPush("OPEN_PROFILE", "opp-1", "offer-1"))
        assertNull(navigationTargetForPush("99", "opp-1", "offer-1"))
        assertNull(navigationTargetForPush(null, "opp-1", "offer-1"))
    }
}
