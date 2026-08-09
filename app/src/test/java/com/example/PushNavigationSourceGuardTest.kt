package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushNavigationSourceGuardTest {
    @Test
    fun messagingServiceUsesTypedPushDestinationInsteadOfLegacyBoolean() {
        val service = File("src/main/java/com/example/ClickAndSaveMessagingService.kt").readText()

        assertTrue(service.contains("message.data[PUSH_TYPE_EXTRA]"))
        assertTrue(service.contains("destinationTabForPushType(pushType)"))
        assertTrue(service.contains("putExtra(PUSH_TYPE_EXTRA, pushType)"))
        assertFalse(service.contains("openSavingsOpportunity"))
    }

    @Test
    fun activityConsumesOnlyAllowlistedPushType() {
        val activity = File("src/main/java/com/example/MainActivity.kt").readText()
        val handler = activity
            .substringAfter("private fun applyPushDestination(intent: Intent?)")
            .substringBefore("private fun maybeTriggerDebugTestPush")

        assertTrue(handler.contains("getStringExtra(PUSH_TYPE_EXTRA)"))
        assertTrue(handler.contains("destinationTabForPushType(pushType) ?: return"))
        assertTrue(handler.contains("viewModel.setTab(destinationTab)"))
        assertTrue(handler.contains("removeExtra(PUSH_TYPE_EXTRA)"))
        assertFalse(handler.contains("getIntExtra"))
        assertFalse(handler.contains("openSavingsOpportunity"))
    }
}
