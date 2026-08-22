package com.example.data.repository

import com.example.BuildConfig
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

private const val RECOVERY_DRY_RUN_VERSION = "staging-controlled-gmail-recovery-dry-run-v1"

data class GmailRecoveryDryRunResult(
    val result: String,
    val credentialPreflight: String,
    val failureStage: String,
    val recoveryDryRunVersion: String,
    val messagesExamined: Int,
    val candidateMessageCount: Int,
    val pdfCandidateCount: Int,
    val normalizedCandidateCount: Int,
    val recurringBillCount: Int,
    val uniqueRecurringSourceCount: Int,
    val duplicateCount: Int,
    val rejectedOneOffCount: Int,
    val rejectedRefundCount: Int,
    val rejectedReceiptOnlyCount: Int,
    val rejectedContractCount: Int,
    val unknownCount: Int
) {
    fun countOnlySummary(): String = buildString {
        append(result)
        append(" · preflight=")
        append(credentialPreflight)
        if (failureStage.isNotBlank()) {
            append(" · stage=")
            append(failureStage)
        }
        append(" · messages=")
        append(messagesExamined)
        append(" · candidates=")
        append(candidateMessageCount)
        append(" · pdf=")
        append(pdfCandidateCount)
        append(" · normalized=")
        append(normalizedCandidateCount)
        append(" · recurring=")
        append(recurringBillCount)
        append(" · uniqueSources=")
        append(uniqueRecurringSourceCount)
        append(" · duplicates=")
        append(duplicateCount)
        append(" · oneOff=")
        append(rejectedOneOffCount)
        append(" · refunds=")
        append(rejectedRefundCount)
        append(" · receipts=")
        append(rejectedReceiptOnlyCount)
        append(" · contracts=")
        append(rejectedContractCount)
        append(" · unknown=")
        append(unknownCount)
    }
}

class GmailRecoveryDryRunRepository(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("europe-west1")
) {
    suspend fun run(): GmailRecoveryDryRunResult {
        check(BuildConfig.DEBUG) { "Recovery dry-run is DEBUG-only." }
        val raw = functions
            .getHttpsCallable("runGmailRecoveryDryRun")
            .call(mapOf("recoveryDryRunVersion" to RECOVERY_DRY_RUN_VERSION))
            .await()
            .data as? Map<*, *> ?: error("Recovery dry-run returned an invalid count-only response.")

        fun text(key: String): String = raw[key]?.toString().orEmpty()
        fun count(key: String): Int = (raw[key] as? Number)?.toInt() ?: 0

        return GmailRecoveryDryRunResult(
            result = text("result"),
            credentialPreflight = text("credentialPreflight"),
            failureStage = text("failureStage"),
            recoveryDryRunVersion = text("recoveryDryRunVersion"),
            messagesExamined = count("messagesExamined"),
            candidateMessageCount = count("candidateMessageCount"),
            pdfCandidateCount = count("pdfCandidateCount"),
            normalizedCandidateCount = count("normalizedCandidateCount"),
            recurringBillCount = count("recurringBillCount"),
            uniqueRecurringSourceCount = count("uniqueRecurringSourceCount"),
            duplicateCount = count("duplicateCount"),
            rejectedOneOffCount = count("rejectedOneOffCount"),
            rejectedRefundCount = count("rejectedRefundCount"),
            rejectedReceiptOnlyCount = count("rejectedReceiptOnlyCount"),
            rejectedContractCount = count("rejectedContractCount"),
            unknownCount = count("unknownCount")
        )
    }
}
