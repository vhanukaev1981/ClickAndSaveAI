package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailRecoveryDryRunContractTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun debugRecoveryControlIsExplicitVersionedAndAbsentFromNormalRefreshPaths() {
        val recoverySource = source("src/main/java/com/example/data/repository/GmailRecoveryDryRunRepository.kt")
        val profileSource = source("src/main/java/com/example/ui/screens/ProfileScreen.kt")
        val gmailSource = source("src/main/java/com/example/data/repository/GmailRepository.kt")
        val mainViewModelSource = source("src/main/java/com/example/ui/MainViewModel.kt")

        assertTrue(recoverySource.contains("runGmailRecoveryDryRun"))
        assertTrue(recoverySource.contains("staging-controlled-gmail-recovery-dry-run-v1"))
        assertTrue(profileSource.contains("BuildConfig.DEBUG"))
        assertTrue(profileSource.contains("Recovery dry-run"))
        assertFalse(gmailSource.contains("runGmailRecoveryDryRun"))
        assertFalse(mainViewModelSource.contains("runGmailRecoveryDryRun"))
    }

    @Test
    fun recoveryClientDecodesCountOnlyContractWithoutPersonalCandidateFields() {
        val recoverySource = source("src/main/java/com/example/data/repository/GmailRecoveryDryRunRepository.kt")

        for (required in listOf(
            "messagesExamined",
            "candidateMessageCount",
            "pdfCandidateCount",
            "normalizedCandidateCount",
            "recurringBillCount",
            "uniqueRecurringSourceCount",
            "duplicateCount",
            "rejectedOneOffCount",
            "rejectedRefundCount",
            "rejectedReceiptOnlyCount",
            "rejectedContractCount",
            "unknownCount"
        )) {
            assertTrue("Missing sanitized counter: $required", recoverySource.contains(required))
        }

        for (forbidden in listOf(
            "providerName",
            "monthlyCost",
            "receivedDate",
            "subject",
            "sourceMessageId",
            "filename",
            "emailText",
            "attachmentContent"
        )) {
            assertFalse("Recovery client must not expose $forbidden", recoverySource.contains(forbidden))
        }
    }
}
