package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V3ProductionBoundaryGuardTest {
    @Test
    fun v3DoesNotIntroduceWebOrLovableRuntime() {
        val sourceRoot = File("src/main/java")
        val source = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .joinToString("\n") { it.readText() }
        listOf("android.webkit.WebView", "addJavascriptInterface", "com.getcapacitor", "com.facebook.react", "startGmailConnect", "GOOGLE_MAIL_APP_USER_CONNECTOR_CLIENT_API_KEY", "connectAppUser", "com.lovable.", "lovable.runtime")
            .forEach { prohibited -> assertFalse("V3 must not introduce runtime boundary: $prohibited", source.contains(prohibited)) }
        assertTrue(source.contains("https://www.googleapis.com/auth/gmail.readonly"))
    }

    @Test
    fun releaseIdentityRemainsUnchanged() {
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("applicationId = \"com.aistudio.clickandsaveai.app\""))
        assertTrue(gradle.contains("versionCode = 1"))
        assertTrue(gradle.contains("versionName = \"1.0\""))
    }

    @Test
    fun criticalV3ActionsExposeTextOrTestSemantics() {
        val nav = File("src/main/java/com/example/ui/components/BottomNavBar.kt").readText()
        val savings = File("src/main/java/com/example/ui/components/SavingsHero.kt").readText()
        val dashboard = File("src/main/java/com/example/ui/screens/DashboardScreen.kt").readText()
        val profile = File("src/main/java/com/example/ui/screens/ProfileScreen.kt").readText()
        val ai = File("src/main/java/com/example/ui/screens/AiAssistantScreen.kt").readText()
        val shell = File("src/main/java/com/example/MainActivity.kt").readText()
        listOf("nav_home", "nav_savings", "nav_ai", "nav_pay", "nav_profile")
            .forEach { assertTrue(nav.contains("testTag(\"$it\")")) }
        assertTrue(savings.contains("testTag(\"v3_realized_savings\")"))
        assertTrue(savings.contains("testTag(\"v3_potential_savings\")"))
        assertTrue(dashboard.contains("testTag(\"v3_next_best_action\")"))
        assertTrue(profile.contains("testTag(\"connect_gmail\")"))
        assertTrue(profile.contains("testTag(\"disconnect_gmail\")"))
        assertTrue(profile.contains("testTag(\"delete_imported_data\")"))
        assertTrue(profile.contains("testTag(\"delete_account\")"))
        assertTrue(ai.contains("testTag(\"ai_message_input\")"))
        assertTrue(ai.contains("testTag(\"ai_send\")"))
        assertTrue(shell.contains("testTag(\"activity_back\")"))
        assertTrue(shell.contains("contentDescription = \"חזרה\""))
    }

    @Test
    fun rtlAndTask7PolishMatchTheApprovedV3Contract() {
        val home = File("src/main/java/com/example/ui/components/V3HomeComponents.kt").readText()
        val ai = File("src/main/java/com/example/ui/screens/AiAssistantScreen.kt").readText()
        val activity = File("src/main/java/com/example/ui/screens/ActivityScreen.kt").readText()
        val profile = File("src/main/java/com/example/ui/screens/ProfileScreen.kt").readText()
        assertTrue(home.contains("Icons.AutoMirrored.Filled.ArrowBack"))
        assertFalse(home.contains("Icons.Default.ArrowBack"))
        assertTrue(ai.contains("Icons.AutoMirrored.Filled.Login"))
        assertTrue(ai.contains("Icons.AutoMirrored.Filled.Send"))
        assertFalse(ai.contains("Icons.Default.Login"))
        assertFalse(ai.contains("Icons.Default.Send"))
        assertTrue(activity.contains("\"השבוע\""))
        assertTrue(activity.contains("\"פעילות קודמת\""))
        assertTrue(profile.contains("אודות האפליקציה"))
        assertTrue(profile.contains("BuildConfig.VERSION_NAME"))
    }
}
