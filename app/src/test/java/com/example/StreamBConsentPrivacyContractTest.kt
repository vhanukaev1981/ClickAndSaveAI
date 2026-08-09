package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamBConsentPrivacyContractTest {
    private val savingsPath = "src/main/java/com/example/ui/screens/ProvidersScreen.kt"
    private val profilePath = "src/main/java/com/example/ui/screens/ProfileScreen.kt"
    private val settingsPath = "src/main/java/com/example/ui/screens/SettingsScreen.kt"

    @Test
    fun providerActionRequiresExplicitContactConsent() {
        val savings = File(savingsPath).readText()
        assertTrue(savings.contains("savings_contact_consent"))
        assertTrue(savings.contains("accepted && name.isNotBlank() && phone.isNotBlank() && email.isNotBlank()"))
        assertTrue(savings.contains("תוכן תיבת הדואר ותמונת ההוצאות המלאה שלך אינם נשלחים"))
        assertTrue(savings.contains("אני מאשר/ת להעביר את פרטי הקשר לצורך קבלת ההצעה שבחרתי"))
    }

    @Test
    fun customerCopyDoesNotClaimAutomaticProviderSwitch() {
        val combined = listOf(savingsPath, profilePath, settingsPath)
            .joinToString("\n") { File(it).readText() }
        assertFalse(combined.contains("נעביר אותך אוטומטית"))
        assertFalse(combined.contains("המעבר יתבצע אוטומטית"))
        assertTrue(combined.contains("כל פעולה מול נותן שירות דורשת אישור מפורש שלך"))
    }

    @Test
    fun savingsActionStaysBoundToDisplayedOfferBeforeConsent() {
        val savings = File(savingsPath).readText()
        assertTrue(savings.contains("displayedOfferId"))
        assertTrue(savings.contains("expectedOfferId = displayedOfferId"))
        assertTrue(savings.contains("recordSavingsActionStarted("))
        assertTrue(savings.contains("acceptSavingsOpportunity("))
    }

    @Test
    fun privacyCopyKeepsMailboxAndFullSpendOutOfProviderPayloadPromise() {
        val profile = File(profilePath).readText()
        assertTrue(profile.contains("אינה מעבירה לנותן שירות את תוכן תיבת הדואר או את תמונת ההוצאות המלאה שלך"))
    }
}
