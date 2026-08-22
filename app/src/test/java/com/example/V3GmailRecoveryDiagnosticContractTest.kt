package com.example

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class V3GmailRecoveryDiagnosticContractTest {
    @Test
    fun backendDecodesOnlySanitizedRecoveryMetadataOnExplicitDebugRequest() {
        val backend = File("src/main/java/com/example/data/repository/BackendRepository.kt").readText()
        val gmail = File("src/main/java/com/example/data/repository/GmailRepository.kt").readText()
        val profile = File("src/main/java/com/example/ui/screens/ProfileScreen.kt").readText()

        assertTrue(backend.contains("data class GmailRecoveryDiagnosticResult"))
        for (field in listOf(
            "initialBackfillCompleted",
            "initialBackfillCompletedAt",
            "storedParserVersion",
            "activeParserVersion",
            "authoritativeInvoiceCount",
            "gmailMessageImportCount",
            "gmailMessageImportsParserVersionDistribution",
            "storedCandidateCount",
            "importsTruncated"
        )) {
            assertTrue("missing diagnostic field $field", backend.contains(field))
        }
        assertTrue(backend.contains("recoveryState: GmailRecoveryDiagnosticResult?"))
        assertTrue(backend.contains("getGmailSyncStatus(includeRecoveryDiagnostics: Boolean = false)"))
        assertTrue(backend.contains("\"includeRecoveryDiagnostics\" to includeRecoveryDiagnostics"))

        assertTrue(gmail.contains("BuildConfig.DEBUG"))
        assertTrue(gmail.contains("getGmailSyncStatus(includeRecoveryDiagnostics = true)"))
        assertTrue(gmail.contains("recoveryDiagnostic"))
        assertTrue(profile.contains("gmail_recovery_diagnostic"))

        for (forbidden in listOf(
            "refreshToken",
            "serverAuthCode",
            "attachmentContent",
            "messageBody"
        )) {
            assertTrue("diagnostic surface must not add $forbidden", !profile.contains(forbidden))
        }
    }
}
