package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushNavigationSourceGuardTest {
    @Test
    fun messagingServicePreservesOnlyAllowlistedExactPushRoute() {
        val service = File("src/main/java/com/example/ClickAndSaveMessagingService.kt").readText()

        assertTrue(service.contains("message.data[PUSH_TYPE_EXTRA]"))
        assertTrue(service.contains("message.data[PUSH_OPPORTUNITY_ID_EXTRA]"))
        assertTrue(service.contains("message.data[PUSH_OFFER_ID_EXTRA]"))
        assertTrue(service.contains("navigationTargetForPush(pushType, opportunityId, offerId)"))
        assertTrue(service.contains("putExtra(PUSH_TYPE_EXTRA, pushType)"))
        assertTrue(service.contains("putExtra(PUSH_OPPORTUNITY_ID_EXTRA, navigationTarget.opportunityId)"))
        assertTrue(service.contains("putExtra(PUSH_OFFER_ID_EXTRA, navigationTarget.offerId)"))
        assertTrue(service.contains("pendingIntentRequestCodeForPushTarget(navigationTarget)"))
        assertFalse(service.contains("destinationTabForPushType(pushType)"))
        assertFalse(service.contains("openSavingsOpportunity"))
        assertFalse(service.contains("message.data.forEach"))
        assertFalse(service.contains("putExtras("))
    }

    @Test
    fun activityConsumesTypedExactPushTargetForAuthenticatedUser() {
        val activity = File("src/main/java/com/example/MainActivity.kt").readText()
        val handler = activity
            .substringAfter("private fun applyPushDestination(intent: Intent?)")
            .substringBefore("private fun maybeTriggerDebugTestPush")

        assertTrue(handler.contains("FirebaseAuth.getInstance().currentUser == null"))
        assertTrue(handler.contains("getStringExtra(PUSH_TYPE_EXTRA)"))
        assertTrue(handler.contains("getStringExtra(PUSH_OPPORTUNITY_ID_EXTRA)"))
        assertTrue(handler.contains("getStringExtra(PUSH_OFFER_ID_EXTRA)"))
        assertTrue(handler.contains("navigationTargetForPush(pushType, opportunityId, offerId) ?: return"))
        assertTrue(handler.contains("viewModel.setTab(navigationTarget.tab)"))
        assertTrue(handler.contains("viewModel.setSavingsPushTarget("))
        assertTrue(handler.contains("navigationTarget.opportunityId"))
        assertTrue(handler.contains("navigationTarget.offerId"))
        assertTrue(handler.contains("removeExtra(PUSH_TYPE_EXTRA)"))
        assertTrue(handler.contains("removeExtra(PUSH_OPPORTUNITY_ID_EXTRA)"))
        assertTrue(handler.contains("removeExtra(PUSH_OFFER_ID_EXTRA)"))
        assertFalse(handler.contains("getIntExtra"))
        assertFalse(handler.contains("openSavingsOpportunity"))
    }

    @Test
    fun viewModelStoresSavingsPushTargetOnlyInMemoryAsOneShotState() {
        val viewModel = File("src/main/java/com/example/ui/MainViewModel.kt").readText()

        assertTrue(viewModel.contains("data class SavingsPushTarget("))
        assertTrue(viewModel.contains("private val _savingsPushTarget = MutableStateFlow<SavingsPushTarget?>(null)"))
        assertTrue(viewModel.contains("val savingsPushTarget: StateFlow<SavingsPushTarget?> = _savingsPushTarget.asStateFlow()"))
        assertTrue(viewModel.contains("fun setSavingsPushTarget("))
        assertTrue(viewModel.contains("fun clearSavingsPushTarget()"))
        assertFalse(viewModel.contains("SharedPreferences"))
        assertFalse(viewModel.contains("savedStateHandle"))
    }
}
