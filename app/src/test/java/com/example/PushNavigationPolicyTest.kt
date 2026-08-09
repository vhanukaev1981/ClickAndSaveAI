package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushNavigationPolicyTest {
    @Test
    fun newInvoiceOpensBills() {
        assertEquals(1, destinationTabForPushType(PUSH_TYPE_NEW_INVOICE))
    }

    @Test
    fun verifiedSavingsOpensSavings() {
        assertEquals(2, destinationTabForPushType(PUSH_TYPE_VERIFIED_SAVINGS_OPPORTUNITY))
    }

    @Test
    fun testPushOpensDashboard() {
        assertEquals(0, destinationTabForPushType(PUSH_TYPE_TEST))
    }

    @Test
    fun unknownPushCannotChooseArbitraryTab() {
        assertNull(destinationTabForPushType("OPEN_PROFILE"))
        assertNull(destinationTabForPushType("99"))
        assertNull(destinationTabForPushType(null))
    }
}
