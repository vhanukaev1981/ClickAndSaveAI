package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamBBoundaryContractTest {
    private val customerUiRoots = listOf(
        "src/main/java/com/example/ui/screens/DashboardScreen.kt",
        "src/main/java/com/example/ui/screens/InvoicesScreen.kt",
        "src/main/java/com/example/ui/screens/ProvidersScreen.kt",
        "src/main/java/com/example/ui/screens/ProfileScreen.kt",
        "src/main/java/com/example/ui/screens/SettingsScreen.kt",
        "src/main/java/com/example/ui/components/BottomNavBar.kt"
    )

    @Test
    fun customerUiDoesNotImportFirebaseFirestoreOrCloudFunctionsDirectly() {
        val forbiddenImports = listOf(
            "com.google.firebase",
            "firebase.firestore",
            "firebase.functions",
            "FirebaseFirestore",
            "FirebaseFunctions"
        )

        customerUiRoots.forEach { path ->
            val source = File(path).readText()
            forbiddenImports.forEach { forbidden ->
                assertFalse("$path crossed Stream B boundary with $forbidden", source.contains(forbidden))
            }
        }
    }

    @Test
    fun streamBCoordinationStillProtectsCoreOwnedAreas() {
        val workstreams = File("../WORKSTREAMS.md").readText()

        listOf(
            "backend Functions",
            "Firestore schema/rules",
            "Gmail/Auth mechanics",
            "financial calculations",
            "offer ranking",
            "commission/attribution logic",
            "BackendRepository.kt"
        ).forEach { protectedArea ->
            assertTrue("WORKSTREAMS lost protected Stream A area: $protectedArea", workstreams.contains(protectedArea))
        }
    }

    @Test
    fun streamBUiDoesNotDefineCommercialOrRankingLogic() {
        val forbiddenDefinitions = listOf(
            "calculateCommission(",
            "commissionRate",
            "rankingScore",
            "rankOffers(",
            "attributionId =",
            "FirestoreRules"
        )

        customerUiRoots.forEach { path ->
            val source = File(path).readText()
            forbiddenDefinitions.forEach { forbidden ->
                assertFalse("$path contains core/commercial logic marker $forbidden", source.contains(forbidden))
            }
        }
    }
}
