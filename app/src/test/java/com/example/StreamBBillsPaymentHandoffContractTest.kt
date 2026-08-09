package com.example

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamBBillsPaymentHandoffContractTest {
    private val contractPath = "../docs/STREAM_B_BILLS_PAYMENT_HANDOFF_CONTRACT.md"

    @Test
    fun paymentCtaRequiresCoreVerifiedOfficialProviderDestination() {
        val contract = File(contractPath).readText()

        listOf(
            "only when Core supplies a payment-handoff candidate",
            "verified against trusted provider configuration",
            "official payment URL",
            "do not present the destination as an official payment CTA"
        ).forEach { rule ->
            assertTrue("Bills payment handoff lost verification rule: $rule", contract.contains(rule))
        }
    }

    @Test
    fun clickAndSaveNeverProcessesOrStoresPaymentCredentials() {
        val contract = File(contractPath).readText()

        listOf(
            "does not process customer payments",
            "does not store card details",
            "does not receive/store card details",
            "No card number, CVV, expiry, wallet token or payment credential"
        ).forEach { rule ->
            assertTrue("Bills payment boundary lost safety rule: $rule", contract.contains(rule))
        }
    }

    @Test
    fun openingProviderPageNeverProvesPaymentCompletion() {
        val contract = File(contractPath).readText()

        assertTrue(contract.contains("never mark the bill paid solely because the URL was opened"))
        assertTrue(contract.contains("reliable downstream evidence exists"))
        assertTrue(contract.contains("may not create its own provider/payment URLs"))
    }
}
