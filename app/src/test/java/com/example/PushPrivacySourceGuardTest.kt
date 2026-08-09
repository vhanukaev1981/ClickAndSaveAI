package com.example

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PushPrivacySourceGuardTest {
    @Test
    fun localFinancialNotificationsAreMarkedPrivate() {
        val service = File("src/main/java/com/example/ClickAndSaveMessagingService.kt").readText()
        assertTrue(service.contains("lockscreenVisibility = Notification.VISIBILITY_PRIVATE"))
        assertTrue(service.contains("setVisibility(NotificationCompat.VISIBILITY_PRIVATE)"))
    }
}
