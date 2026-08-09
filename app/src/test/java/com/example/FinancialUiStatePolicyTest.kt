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
    fun errorStateDoesNotExposeTechnicalBackendLanguage() {
        val message = FinancialUiStatePolicy.message(FinancialUiState.ERROR)
        val text = "${message.title} ${message.body}".uppercase()
        assertFalse(text.contains("FIREBASE"))
        assertFalse(text.contains("APP_CHECK"))
        assertFalse(text.contains("GMAIL_READONLY"))
        assertFalse(text.contains("NOT_FOUND"))
    }

    @Test
    fun loadingStateIsActionlessAndReassuring() {
        val message = FinancialUiStatePolicy.message(FinancialUiState.LOADING)
        assertTrue(message.body.contains("אין צורך לבצע פעולה"))
    }
}
