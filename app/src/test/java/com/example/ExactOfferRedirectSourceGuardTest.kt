package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactOfferRedirectSourceGuardTest {
    @Test
    fun externalOfferRedirectIsServerVerifiedAndTracked() {
        val screen = File("src/main/java/com/example/ui/screens/ProvidersScreen.kt").readText()
        val repository = File("src/main/java/com/example/data/repository/OpportunityActionRepository.kt").readText()

        assertTrue(screen.contains("externalRedirectAvailable"))
        assertTrue(screen.contains("createTrackedOfferRedirect"))
        assertTrue(screen.contains("מעבר להצעה באתר הספק"))
        assertTrue(screen.contains("ההצטרפות והתשלום מתבצעים ישירות מול הספק"))
        assertTrue(repository.contains("getHttpsCallable(\"createTrackedOfferRedirect\")"))
        assertFalse(screen.contains("officialSourceUrl"))
        assertFalse(screen.contains("destinationUrl"))
    }
}
