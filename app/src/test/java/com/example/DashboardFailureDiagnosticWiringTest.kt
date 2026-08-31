package com.example

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardFailureDiagnosticWiringTest {
    @Test
    fun failedFinancialRefreshSurfacesSanitizedStageReason() {
        val source = Files.readString(
            Path.of("src/main/java/com/example/ui/screens/DashboardScreen.kt")
        )
        val failedStart = source.indexOf("is FinancialSyncState.Failed ->")
        assertTrue("missing Failed financial sync branch", failedStart >= 0)
        val readyStart = source.indexOf("is FinancialSyncState.Ready ->", failedStart)
        assertTrue("missing Ready branch after Failed branch", readyStart > failedStart)
        val failedBlock = source.substring(failedStart, readyStart)

        assertTrue(
            "Failed dashboard state must surface the sanitized recovery reason",
            failedBlock.contains("state.reason")
        )
    }
}
