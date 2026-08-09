package com.example

import com.example.ui.CustomerProgressStage
import com.example.ui.TruthfulProgressPresentationPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TruthfulProgressPresentationPolicyTest {
    @Test
    fun everyBackendDrivenStageUsesPlainHebrewTruthLanguage() {
        val expected = mapOf(
            CustomerProgressStage.DETECTED to "זיהינו",
            CustomerProgressStage.CHECKED to "בדקנו",
            CustomerProgressStage.VERIFIED to "אימתנו",
            CustomerProgressStage.STILL_CHECKING to "עדיין בודקים"
        )

        expected.forEach { (stage, phrase) ->
            val message = TruthfulProgressPresentationPolicy.message(stage)
            assertTrue("$stage lost truthful phrase $phrase", message.contains(phrase))
        }
    }

    @Test
    fun progressCopyNeverInventsPercentagesOrAiTheatre() {
        CustomerProgressStage.entries.forEach { stage ->
            val message = TruthfulProgressPresentationPolicy.message(stage)
            assertFalse(message.contains("%"))
            assertFalse(message.contains("AI", ignoreCase = true))
            assertFalse(message.contains("רובוט"))
        }
    }

    @Test
    fun stageIsPresentationOnlyAndNeverAutoAdvances() {
        assertTrue(TruthfulProgressPresentationPolicy.nextStage(CustomerProgressStage.DETECTED) == null)
        assertTrue(TruthfulProgressPresentationPolicy.nextStage(CustomerProgressStage.CHECKED) == null)
        assertTrue(TruthfulProgressPresentationPolicy.nextStage(CustomerProgressStage.VERIFIED) == null)
        assertTrue(TruthfulProgressPresentationPolicy.nextStage(CustomerProgressStage.STILL_CHECKING) == null)
    }
}
