package com.example

import com.example.ui.FinancialUiState
import com.example.ui.FinancialUiStatePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialUiStatePolicyTest {
    @Test
    fun underReviewNeverPromisesUnverifiedSavings() {
        val message = FinancialUiStatePolicy.message(FinancialUiState.UNDER_REVIEW)
        assertTrue(message.body.contains("רק לאחר"))
        assertFalse(message.body.contains("₪0"))
    }

    @Test
    fun everySharedStateUsesCustomerLanguageWithoutInternalTerms() {
        val forbidden = listOf(
            "FIREBASE",
            "APP_CHECK",
            "GMAIL_READONLY",
            "NOT_FOUND",
            "BACKEND",
            "CRM",
            "COMMISSION",
            "ATTRIBUTION"
        )

        FinancialUiState.entries.forEach { state ->
            val message = FinancialUiStatePolicy.message(state)
            val text = "${message.title} ${message.body}".uppercase()
            assertTrue("$state must have a title", message.title.isNotBlank())
            assertTrue("$state must have body copy", message.body.isNotBlank())
            forbidden.forEach { token ->
                assertFalse("$state leaked $token", text.contains(token))
            }
        }
    }

    @Test
    fun loadingStateIsActionlessAndReassuring() {
        val message = FinancialUiStatePolicy.message(FinancialUiState.LOADING)
        assertTrue(message.body.contains("אין צורך לבצע פעולה"))
    }

    @Test
    fun emptyStateDoesNotClaimMissingDataIsZeroSpendOrZeroSaving() {
        val message = FinancialUiStatePolicy.message(FinancialUiState.EMPTY)
        val text = "${message.title} ${message.body}"
        assertFalse(text.contains("₪0"))
        assertFalse(text.contains("אין הוצאות"))
        assertFalse(text.contains("אין חיסכון"))
    }

    @Test
    fun errorStatePreservesExistingDataExpectation() {
        val message = FinancialUiStatePolicy.message(FinancialUiState.ERROR)
        assertTrue(message.body.contains("הנתונים הקיימים נשארים שמורים"))
    }

    @Test
    fun readyStateDescribesAvailableVerifiedPictureWithoutPromise() {
        val message = FinancialUiStatePolicy.message(FinancialUiState.READY)
        assertTrue(message.body.contains("המידע הזמין והמאומת כרגע"))
        assertFalse(message.body.contains("מובטח"))
    }
}
