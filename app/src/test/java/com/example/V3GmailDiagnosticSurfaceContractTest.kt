package com.example

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class V3GmailDiagnosticSurfaceContractTest {
    @Test
    fun profileSurfacesGmailConnectionFailureMessage() {
        val source = File("src/main/java/com/example/ui/screens/ProfileScreen.kt").readText()
        assertTrue(source.contains("viewModel.gmailSyncStep.collectAsState()"))
        assertTrue(source.contains("gmailSyncStep.isNotBlank()"))
        assertTrue(Regex("Text\\(\\s*gmailSyncStep\\s*,").containsMatchIn(source))
        assertTrue(source.contains("gmail_connection_message"))
    }
}
