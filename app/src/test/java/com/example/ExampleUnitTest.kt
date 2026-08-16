package com.example

import com.example.data.local.InvoiceItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun annualSavingsUsesMonthlySavings() {
        val invoice = InvoiceItem(
            providerName = "Test provider",
            category = "Test",
            monthlyCost = 100.0,
            recommendedAlternative = "Unverified",
            alternativeMonthlyCost = 90.0,
            potentialMonthlySavings = 10.0
        )

        assertEquals(120.0, invoice.potentialAnnualSavings, 0.0)
    }
}
