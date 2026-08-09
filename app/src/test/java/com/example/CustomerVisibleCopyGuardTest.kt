package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class CustomerVisibleCopyGuardTest {
    private val customerScreens = listOf(
        "src/main/java/com/example/ui/screens/DashboardScreen.kt",
        "src/main/java/com/example/ui/screens/InvoicesScreen.kt",
        "src/main/java/com/example/ui/screens/ProvidersScreen.kt",
        "src/main/java/com/example/ui/screens/ProfileScreen.kt",
        "src/main/java/com/example/ui/screens/SettingsScreen.kt"
    )

    private val forbiddenVisibleTerms = listOf(
        "lead",
        "crm",
        "firebase auth",
        "app check",
        "secret manager",
        "backend error",
        "dispatch id",
        "commission id",
        "attribution id",
        "providerreference",
        "clickid",
        "opportunityid",
        "gmail_readonly",
        "not_found",
        "stack trace"
    )

    @Test
    fun customerFacingStringLiteralsContainNoInternalProductTerminology() {
        customerScreens.forEach { path ->
            val source = File(path).readText()
            val literals = quotedStringLiterals(source)
                .joinToString("\n")
                .lowercase()

            forbiddenVisibleTerms.forEach { forbidden ->
                assertFalse(
                    "$path exposes internal customer-visible term: $forbidden",
                    literals.contains(forbidden)
                )
            }
        }
    }

    private fun quotedStringLiterals(source: String): List<String> {
        val tripleQuoted = Regex("\\\"\\\"\\\"([\\s\\S]*?)\\\"\\\"\\\"")
            .findAll(source)
            .map { it.groupValues[1] }
            .toList()
        val withoutTripleQuoted = source.replace(Regex("\\\"\\\"\\\"[\\s\\S]*?\\\"\\\"\\\""), "")
        val regularQuoted = Regex("\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"")
            .findAll(withoutTripleQuoted)
            .map { it.groupValues[1] }
            .toList()
        return tripleQuoted + regularQuoted
    }
}
