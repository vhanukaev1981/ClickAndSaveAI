package com.example

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamBProviderHandoffContractTest {
    private val contractPath = "../docs/STREAM_B_PROVIDER_HANDOFF_CONTRACT.md"

    @Test
    fun handoffPreservesExactOfferIntentConsentAndRevalidation() {
        val contract = File(contractPath).readText()

        listOf(
            "exact verified alternative",
            "never synthesize annual savings in Android",
            "ACTION_STARTED",
            "Ask explicit consent",
            "Revalidate the exact offer",
            "Only after successful intent/consent/revalidation"
        ).forEach { rule ->
            assertTrue("Provider handoff lost Core invariant: $rule", contract.contains(rule))
        }
    }

    @Test
    fun customerHandoffLanguageNeverClaimsAutomaticSwitchOrUnprovenOutcome() {
        val contract = File(contractPath).readText()

        listOf(
            "does not perform the provider switch itself",
            "Do not expose CRM/lead/commission/attribution terminology",
            "do not claim activation/conversion/sale without provider evidence",
            "mailbox content and the full internal spending picture are not sent"
        ).forEach { rule ->
            assertTrue("Provider handoff lost customer trust rule: $rule", contract.contains(rule))
        }
    }

    @Test
    fun streamBDoesNotTakeCoreCommercialOwnership() {
        val contract = File(contractPath).readText()

        listOf(
            "exact offer ID",
            "action mode / commercial eligibility",
            "acceptance-time revalidation",
            "provider dispatch/referral mechanics",
            "attribution/lifecycle/evidence"
        ).forEach { coreOwned ->
            assertTrue("Provider handoff lost Core boundary: $coreOwned", contract.contains(coreOwned))
        }
    }
}
