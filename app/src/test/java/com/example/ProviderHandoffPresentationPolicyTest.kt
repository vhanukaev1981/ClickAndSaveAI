package com.example

import com.example.ui.ProviderHandoffStage
import com.example.ui.ProviderHandoffPresentationPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderHandoffPresentationPolicyTest {
    @Test
    fun everyStageUsesCustomerValueLanguageWithoutCommercialInternals() {
        ProviderHandoffStage.entries.forEach { stage ->
            val message = ProviderHandoffPresentationPolicy.message(stage)
            val text = "${message.title} ${message.body}".lowercase()

            assertTrue(message.title.isNotBlank())
            assertTrue(message.body.isNotBlank())
            listOf("lead", "crm", "commission", "attribution", "dispatch").forEach { forbidden ->
                assertFalse("$stage leaked $forbidden", text.contains(forbidden))
            }
        }
    }

    @Test
    fun openedProviderDestinationNeverClaimsSwitchOrActivation() {
        val message = ProviderHandoffPresentationPolicy.message(ProviderHandoffStage.PROVIDER_DESTINATION_OPENED)
        val text = "${message.title} ${message.body}"

        assertTrue(text.contains("הספק"))
        assertFalse(text.contains("המעבר הושלם"))
        assertFalse(text.contains("השירות הופעל"))
        assertFalse(text.contains("העסקה הושלמה"))
    }

    @Test
    fun awaitingProviderOutcomeStaysExplicitlyUnverified() {
        val message = ProviderHandoffPresentationPolicy.message(ProviderHandoffStage.AWAITING_PROVIDER_EVIDENCE)
        val text = "${message.title} ${message.body}"

        assertTrue(text.contains("עדיין"))
        assertTrue(text.contains("אישור"))
        assertFalse(text.contains("הושלם"))
    }

    @Test
    fun verifiedActivationRequiresExplicitVerifiedStage() {
        val verified = ProviderHandoffPresentationPolicy.message(ProviderHandoffStage.ACTIVATION_VERIFIED)
        assertTrue("${verified.title} ${verified.body}".contains("אומת"))
    }
}
