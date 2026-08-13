package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialAuthoritativeProjectionGuardTest {
    @Test
    fun viewModelExposesAuthoritativeFinancialValuesAsNullableRecoveryProjections() {
        val viewModel = File("src/main/java/com/example/ui/MainViewModel.kt").readText()

        assertTrue(viewModel.contains("financialHomeOrNull"))
        assertTrue(viewModel.contains("latestScanOrNull"))
        assertTrue(viewModel.contains("observedRecurringMonthlySpendOrNull"))
        assertTrue(viewModel.contains("recurringServiceCountOrNull"))

        assertTrue(viewModel.contains("val authoritativeFinancialHome: StateFlow<FinancialHomeResult?>"))
        assertTrue(viewModel.contains("val latestRecoveredGmailScan: StateFlow<GmailScanResult?>"))
        assertTrue(viewModel.contains("val observedRecurringMonthlySpend: StateFlow<Double?>"))
        assertTrue(viewModel.contains("val recurringServiceCount: StateFlow<Int?>"))

        val authoritativeBlock = viewModel
            .substringAfter("val authoritativeFinancialHome: StateFlow<FinancialHomeResult?>")
            .substringBefore("val monthlySavingsGoal")

        assertTrue(authoritativeBlock.contains(".map { it.financialHomeOrNull }"))
        assertTrue(authoritativeBlock.contains(".map { it.latestScanOrNull }"))
        assertTrue(authoritativeBlock.contains(".map { it.observedRecurringMonthlySpendOrNull }"))
        assertTrue(authoritativeBlock.contains(".map { it.recurringServiceCountOrNull }"))
        assertTrue(authoritativeBlock.contains(", null)"))
        assertFalse(authoritativeBlock.contains("?: 0.0"))
        assertFalse(authoritativeBlock.contains("?: 0"))
    }
}
