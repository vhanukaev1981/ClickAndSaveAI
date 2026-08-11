package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamBConsentPrivacyContractTest {
    private val previewPath = "src/main/java/com/example/ui/screens/ProductPreviewScreens.kt"

    @Test
    fun providerActionRequiresExplicitContactConsent() {
        val preview = File(previewPath).readText()
        assertTrue(preview.contains("product_submit_provider_details"))
        assertTrue(preview.contains("accepted && name.isNotBlank() && phone.isNotBlank() && email.isNotBlank()"))
        assertTrue(preview.contains("תוכן תיבת הדואר ונתוני הוצאות אחרים אינם נשלחים"))
        assertTrue(preview.contains("אני מאשר/ת במפורש את העברת פרטי הקשר לספק עבור ההצעה הזו"))
    }

    @Test
    fun customerCopyDoesNotClaimAutomaticProviderSwitch() {
        val preview = File(previewPath).readText()
        assertFalse(preview.contains("נעביר אותך אוטומטית"))
        assertFalse(preview.contains("המעבר יתבצע אוטומטית"))
        assertTrue(preview.contains("Click&SaveAI לא מבצעת את המעבר"))
        assertTrue(preview.contains("אישור מפורש"))
    }

    @Test
    fun savingsActionStaysBoundToDisplayedOfferBeforeConsent() {
        val preview = File(previewPath).readText()
        assertTrue(preview.contains("val offerId = opportunity.matchedOffer?.offerId.orEmpty()"))
        assertTrue(preview.contains("expectedOfferId = offerId"))
        assertTrue(preview.contains("recordSavingsActionStarted("))
        assertTrue(preview.contains("acceptSavingsOpportunity("))
    }

    @Test
    fun providerPayloadPromiseExcludesMailboxAndUnrelatedSpendData() {
        val preview = File(previewPath).readText()
        assertTrue(preview.contains("תוכן תיבת הדואר ונתוני הוצאות אחרים אינם נשלחים"))
        assertTrue(preview.contains("נעביר רק שם, טלפון, אימייל ומזהה ההצעה שאישרת"))
    }
}
