package com.example

import com.example.ui.OnboardingPresentationPolicy
import com.example.ui.OnboardingStage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPresentationPolicyTest {
    @Test
    fun onboardingNeverInventsProgressOrTechnicalOAuthCopy() {
        OnboardingStage.entries.forEach { stage ->
            val message = OnboardingPresentationPolicy.message(stage)
            val text = "${message.title} ${message.body}"
            assertFalse(text.contains("%"))
            assertFalse(text.contains("OAuth", ignoreCase = true))
            assertFalse(text.contains("scope", ignoreCase = true))
            assertFalse(text.contains("client_id", ignoreCase = true))
        }
    }

    @Test
    fun permissionExplanationIsReadOnlyAndDoesNotAuthorizeProviderAction() {
        val message = OnboardingPresentationPolicy.message(OnboardingStage.PERMISSION_EXPLANATION)
        val text = "${message.title} ${message.body}"
        assertTrue(text.contains("קריאה בלבד"))
        assertTrue(text.contains("לא"))
        assertTrue(text.contains("ספק"))
    }

    @Test
    fun processingStateRemainsTruthfulAndNonBlocking() {
        val message = OnboardingPresentationPolicy.message(OnboardingStage.STILL_PROCESSING)
        val text = "${message.title} ${message.body}"
        assertTrue(text.contains("עדיין"))
        assertFalse(text.contains("100%"))
        assertFalse(text.contains("הסתיים"))
    }

    @Test
    fun policyNeverAutoAdvances() {
        OnboardingStage.entries.forEach { stage ->
            assertTrue(OnboardingPresentationPolicy.nextStage(stage) == null)
        }
    }
}
