package com.example

import com.example.data.repository.GmailRecoveryDiagnosticResult
import com.example.data.repository.GmailRepository
import com.example.data.repository.decodeGmailRecoveryDiagnostic
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V3GmailRecoveryDiagnosticContractTest {
    @Test
    fun replayFieldsDecodeCorrectlyAndMissingNumericValuesDefaultToZero() {
        val decoded = decodeGmailRecoveryDiagnostic(
            mapOf(
                "initialBackfillCompleted" to true,
                "initialBackfillCompletedAt" to "2026-08-22T00:00:00Z",
                "storedParserVersion" to 2L,
                "activeParserVersion" to 3L,
                "authoritativeInvoiceCount" to 4L,
                "gmailMessageImportCount" to 5L,
                "gmailMessageImportsParserVersionDistribution" to mapOf("2" to 5L),
                "storedCandidateCount" to 6L,
                "normalizedCandidateCount" to 7L,
                "replayableCandidateCount" to 8L,
                "replayableRecurringCount" to 9L,
                "uniqueReplayableSourceCount" to 10L,
                "duplicateCandidateCount" to 11L,
                "importsTruncated" to true
            )
        )

        assertNotNull(decoded)
        decoded!!
        assertEquals(7, decoded.normalizedCandidateCount)
        assertEquals(8, decoded.replayableCandidateCount)
        assertEquals(9, decoded.replayableRecurringCount)
        assertEquals(10, decoded.uniqueReplayableSourceCount)
        assertEquals(11, decoded.duplicateCandidateCount)

        val missing = decodeGmailRecoveryDiagnostic(mapOf("initialBackfillCompleted" to false))!!
        assertEquals(0, missing.normalizedCandidateCount)
        assertEquals(0, missing.replayableCandidateCount)
        assertEquals(0, missing.replayableRecurringCount)
        assertEquals(0, missing.uniqueReplayableSourceCount)
        assertEquals(0, missing.duplicateCandidateCount)
    }

    @Test
    fun debugDiagnosticShowsReplayDecisionCountsWithoutDatesOrPersonalCandidateData() {
        val state = GmailRecoveryDiagnosticResult(
            initialBackfillCompleted = true,
            initialBackfillCompletedAt = "2026-08-22T00:00:00Z",
            storedParserVersion = 2,
            activeParserVersion = 3,
            authoritativeInvoiceCount = 4,
            gmailMessageImportCount = 5,
            gmailMessageImportsParserVersionDistribution = mapOf("2" to 5),
            storedCandidateCount = 6,
            normalizedCandidateCount = 7,
            replayableCandidateCount = 8,
            replayableRecurringCount = 9,
            uniqueReplayableSourceCount = 10,
            duplicateCandidateCount = 11,
            importsTruncated = true
        )

        val line = GmailRepository.formatRecoveryDiagnostic(state)
        for (token in listOf(
            "completed=true",
            "parser=2→3",
            "invoices=4",
            "imports=5",
            "storedCandidates=6",
            "normalized=7",
            "replayable=8",
            "recurring=9",
            "uniqueSources=10",
            "duplicates=11",
            "truncated=true"
        )) {
            assertTrue("missing diagnostic token $token", line.contains(token))
        }
        assertFalse(line.contains(state.initialBackfillCompletedAt))

        val gmail = File("src/main/java/com/example/data/repository/GmailRepository.kt").readText()
        val formatter = gmail.substringAfter("formatRecoveryDiagnostic").substringBefore("refreshRecoveryDiagnosticIfAvailable")
        for (forbidden in listOf(
            "providerName",
            "sourceMessageId",
            "subject",
            "attachment",
            "email",
            "token",
            "credential",
            "monthlyCost",
            "receivedDate"
        )) {
            assertFalse("diagnostic formatter must not render $forbidden", formatter.contains(forbidden, ignoreCase = true))
        }
    }

    @Test
    fun recoveryDiagnosticIsReadOnlyAndDoesNotChangeGmailConnectionBehavior() {
        val gmail = File("src/main/java/com/example/data/repository/GmailRepository.kt").readText()
        val refresh = gmail.substringAfter("private suspend fun refreshRecoveryDiagnosticIfAvailable")
            .substringBefore("private suspend fun ensureGmailWatch")

        assertTrue(refresh.contains("getGmailSyncStatus(includeRecoveryDiagnostics = true)"))
        assertFalse(refresh.contains("scanInvoices("))
        assertFalse(refresh.contains("scanGmailInvoices("))
        assertFalse(refresh.contains("connectGmail("))
        assertFalse(refresh.contains("disconnectGmail"))
        assertFalse(refresh.contains("_isConnected.value"))

        assertTrue(gmail.contains("_isConnected.value = connection.connected"))
        assertTrue(gmail.contains("if (connection.connected) ensureGmailWatch()"))
    }

    @Test
    fun diagnosticRemainsDebugOnlyAndExplicitlyRequested() {
        val backend = File("src/main/java/com/example/data/repository/BackendRepository.kt").readText()
        val gmail = File("src/main/java/com/example/data/repository/GmailRepository.kt").readText()
        val profile = File("src/main/java/com/example/ui/screens/ProfileScreen.kt").readText()

        assertTrue(backend.contains("recoveryState: GmailRecoveryDiagnosticResult?"))
        assertTrue(backend.contains("getGmailSyncStatus(includeRecoveryDiagnostics: Boolean = false)"))
        assertTrue(backend.contains("\"includeRecoveryDiagnostics\" to includeRecoveryDiagnostics"))
        assertTrue(gmail.contains("BuildConfig.DEBUG"))
        assertTrue(gmail.contains("getGmailSyncStatus(includeRecoveryDiagnostics = true)"))
        assertTrue(profile.contains("gmail_recovery_diagnostic"))
    }
}
