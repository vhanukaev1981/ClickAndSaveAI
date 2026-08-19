package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V3AiBoundaryGuardTest {
    @Test
    fun v3AiUsesExistingBackendViewModelOnly() {
        val source = File("src/main/java/com/example/ui/screens/AiAssistantScreen.kt").readText()

        assertTrue(
            Regex("""fun\s+AiAssistantScreen\s*\(\s*viewModel\s*:\s*MainViewModel\s*\)""")
                .containsMatchIn(source)
        )
        assertTrue(viewModelCallback(source, "analyzeDeal"))
        assertTrue(viewModelCallback(source, "sendChatMessage"))
        assertTrue(viewModelFlowCollection(source, "aiDealAnalysis"))
        assertTrue(viewModelFlowCollection(source, "chatMessages"))

        val imports = source.lineSequence()
            .map(String::trim)
            .filter { it.startsWith("import ") }
            .toList()
        val prohibitedImportPrefixes = listOf(
            "import android.webkit.",
            "import com.getcapacitor.",
            "import com.facebook.react.",
            "import lovable.",
            "import com.lovable."
        )
        prohibitedImportPrefixes.forEach { prefix ->
            assertFalse(
                "AI surface must not introduce runtime import: $prefix",
                imports.any { it.startsWith(prefix) }
            )
        }

        val prohibitedConnectorSymbols = listOf(
            "WebView",
            "GOOGLE_MAIL_APP_USER_CONNECTOR_CLIENT_API_KEY",
            "startGmailConnect",
            "GmailConnector"
        )
        prohibitedConnectorSymbols.forEach { symbol ->
            assertFalse(
                "AI surface must not introduce connector/runtime symbol: $symbol",
                source.contains(symbol)
            )
        }
    }

    @Test
    fun v3AiHasTheApprovedSavingsAssistantExperience() {
        val source = File("src/main/java/com/example/ui/screens/AiAssistantScreen.kt").readText()
        assertTrue(source.contains("עוזר החיסכון שלך"))
        assertTrue(source.contains("איפה אני משלם יותר מדי?"))
        assertTrue(source.contains("מה אפשר לבטל?"))
        assertTrue(source.contains("מה כדאי לבדוק השבוע?"))
        assertTrue(source.contains("איפה החיסכון הגדול ביותר?"))
        assertTrue(source.contains("תסביר לי את החשבון הזה"))
        assertTrue(source.contains("AiAssistantContent"))
        assertTrue(source.contains("ai_message_input"))
        assertTrue(source.contains("ai_send"))
    }

    private fun viewModelCallback(source: String, callback: String): Boolean =
        Regex("""viewModel\s*(?:::|\.)\s*${Regex.escape(callback)}\b""").containsMatchIn(source)

    private fun viewModelFlowCollection(source: String, flow: String): Boolean =
        Regex(
            """viewModel\s*\.\s*${Regex.escape(flow)}\s*\.\s*collectAsState\s*\(\s*\)"""
        ).containsMatchIn(source)
}
