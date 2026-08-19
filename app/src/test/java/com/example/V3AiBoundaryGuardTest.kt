package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V3AiBoundaryGuardTest {
    @Test
    fun v3AiUsesExistingBackendViewModelOnly() {
        val source = File("src/main/java/com/example/ui/screens/AiAssistantScreen.kt").readText()
        assertTrue(source.contains("viewModel.analyzeDeal"))
        assertTrue(source.contains("viewModel.sendChatMessage"))
        assertTrue(source.contains("viewModel.aiDealAnalysis.collectAsState()"))
        assertTrue(source.contains("viewModel.chatMessages.collectAsState()"))
        assertFalse(source.contains("WebView"))
        assertFalse(source.contains("GOOGLE_MAIL_APP_USER_CONNECTOR_CLIENT_API_KEY"))
        assertFalse(source.contains("startGmailConnect"))
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
}
