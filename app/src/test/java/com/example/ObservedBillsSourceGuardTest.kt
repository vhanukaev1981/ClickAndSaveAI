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
    fun lightweightSnapshotDoesNotExposeRawGmailContentFields() {
        val backend = File("../../functions/src/observedBillsFunctions.js").readText()
        val normalizedReturn = backend.substringAfter("return {\n    sourceMessageId")
            .substringBefore("  };\n}")

        listOf("rawBody", "subject", "snippet", "accountNumber", "pdfText", "commission").forEach { forbidden ->
            assertFalse("Observed bills response must not expose $forbidden", normalizedReturn.contains(forbidden))
        }
    }
}
