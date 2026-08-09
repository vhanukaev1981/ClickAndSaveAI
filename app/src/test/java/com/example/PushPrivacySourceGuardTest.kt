package com.example

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PushPrivacySourceGuardTest {
    @Test
    fun financialChannelIsCreatedPrivateBeforePushDelivery() {
        val channels = File("src/main/java/com/example/FinancialNotificationChannels.kt").readText()
        val service = File("src/main/java/com/example/ClickAndSaveMessagingService.kt").readText()
        val activity = File("src/main/java/com/example/MainActivity.kt").readText()
        val backendPush = File("../functions/src/pushFunctions.js").readText()

        assertTrue(channels.contains("FINANCIAL_PUSH_CHANNEL_ID = \"savings_opportunities\""))
        assertTrue(channels.contains("lockscreenVisibility = Notification.VISIBILITY_PRIVATE"))
        assertTrue(service.contains("FinancialNotificationChannels.ensureCreated(this)"))
        assertTrue(service.contains("NotificationCompat.Builder(this, FINANCIAL_PUSH_CHANNEL_ID)"))
        assertTrue(service.contains("setVisibility(NotificationCompat.VISIBILITY_PRIVATE)"))
        assertTrue(activity.contains("FinancialNotificationChannels.ensureCreated(this)"))
        assertTrue(backendPush.contains("channelId: \"savings_opportunities\""))
        assertTrue(backendPush.contains("visibility: \"private\""))
    }
}
