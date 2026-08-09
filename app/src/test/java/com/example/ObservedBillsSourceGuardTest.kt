package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservedBillsSourceGuardTest {
    @Test
    fun authenticatedStartupKeepsParserUpgradeAndLightweightSnapshotPathsSeparate() {
        val repository = File("src/main/java/com/example/data/repository/GmailRepository.kt").readText()
        val activity = File("src/main/java/com/example/MainActivity.kt").readText()

        assertTrue(activity.contains("viewModel.gmailRepository.refreshConnectionStatusAndUpgradeIfNeeded()"))
        assertTrue(repository.contains("if (syncStatus?.upgradeRequired == true)"))
        assertTrue(repository.contains("scanInvoices()"))
        assertTrue(repository.contains("observedBillsRepository.refreshObservedBills()"))
        assertTrue(repository.contains("Authoritative observed bills refresh unavailable"))
    }

    @Test
    fun lightweightSnapshotFailureDoesNotMarkValidGmailConnectionDisconnected() {
        val repository = File("src/main/java/com/example/data/repository/GmailRepository.kt").readText()
        val normalRefreshSection = repository.substringAfter("} else {\n            // Normal app startup")
            .substringBefore("        }\n        return connectionResult")

        assertTrue(normalRefreshSection.contains("observedBillsRepository.refreshObservedBills()"))
        assertFalse(normalRefreshSection.contains("_isConnected.value = false"))
        assertFalse(normalRefreshSection.contains("GmailSyncState.Error"))
    }

    @Test
    fun authoritativeDeletionRequiresCompleteServerSourceSet() {
        val observedRepository = File("src/main/java/com/example/data/repository/ObservedBillsRepository.kt").readText()
        assertTrue(observedRepository.contains("if (!sourceSetComplete) return emptyList()"))
        assertTrue(observedRepository.contains("deleteObservedGmailInvoicesBySourceIds(stale)"))
    }

    @Test
    fun newInvoicePushUsesOnlyLightweightObservedBillsRefresh() {
        val service = File("src/main/java/com/example/ClickAndSaveMessagingService.kt").readText()
        assertTrue(service.contains("PUSH_TYPE_NEW_INVOICE = \"NEW_INVOICE\""))
        assertTrue(service.contains("message.data[\"type\"] == PUSH_TYPE_NEW_INVOICE"))
        assertTrue(service.contains("observedBillsRepository.refreshObservedBills()"))
        assertTrue(service.contains("Authenticated app startup retries it"))
        assertFalse("FCM must never trigger the six-month Gmail scan", service.contains("scanInvoices()"))
        assertFalse("FCM refresh failure must not disconnect Gmail", service.contains("_isConnected.value = false"))
    }

    @Test
    fun lightweightSnapshotDoesNotExposeRawGmailContentFields() {
        // Android unit tests execute from the app module directory.
        val backend = File("../functions/src/observedBillsFunctions.js").readText()
        val normalizedReturn = backend.substringAfter("return {\n    sourceMessageId")
            .substringBefore("  };\n}")

        listOf("rawBody", "subject", "snippet", "accountNumber", "pdfText", "commission").forEach { forbidden ->
            assertFalse("Observed bills response must not expose $forbidden", normalizedReturn.contains(forbidden))
        }
    }
}
