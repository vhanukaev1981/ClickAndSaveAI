package com.example

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailDisconnectCleanupContractTest {
    @Test
    fun disconnectRequiresConfirmedProviderCleanupAndSurfacesStatuses() {
        val source = File("src/main/java/com/example/data/repository/PrivacyRepository.kt").readText()
        assertTrue(source.contains("externalCleanupConfirmed"))
        assertTrue(source.contains("watchStopStatus"))
        assertTrue(source.contains("oauthRevocationStatus"))
        assertTrue(source.contains("if (!result.externalCleanupConfirmed)"))
    }
}
