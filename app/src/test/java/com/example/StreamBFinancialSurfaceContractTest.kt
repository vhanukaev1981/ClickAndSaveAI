package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamBFinancialSurfaceContractTest {
    private val previewPath = "src/main/java/com/example/ui/screens/ProductPreviewScreens.kt"

    @Test
    fun billsRemainRecognitionFirstAndUseSharedFinancialDesignTokens() {
        val preview = File(previewPath).readText()

        listOf(
            "FinancialDesignTokens.screenHorizontalPadding",
            "FinancialDesignTokens.screenTopPadding",
            "FinancialDesignTokens.screenBottomNavigationClearance",
            "FinancialDesignTokens.sectionSpacing",
            "FinancialDesignTokens.cardRadius",
            "FinancialDesignTokens.cardPadding"
        ).forEach { token ->
            assertTrue("Product Preview lost shared design token $token", preview.contains(token))
        }

        assertTrue(preview.contains("product_bills_screen"))
        assertTrue(preview.contains("חשבונות שזוהו"))
        assertTrue(preview.contains("החשבון זוהה"))
        assertFalse("Primary Bills must not expose manual expense entry", preview.contains("add_manual_bill"))
        assertFalse("Primary Bills must not expose manual expense save", preview.contains("save_manual_bill"))
        assertFalse("Primary Bills must not be spend-first", preview.contains("הוצאה חודשית מזוהה"))
    }

    @Test
    fun billsPaymentHandoffNeverPretendsToProcessPayment() {
        val preview = File(previewPath).readText()
        assertTrue(preview.contains("product_bills_payment_truth"))
        assertTrue(preview.contains("תשלום נשאר אצל הספק"))
        assertTrue(preview.contains("כתובת תשלום רשמית של הספק"))
        assertTrue(preview.contains("Click&SaveAI אינה שומרת כרטיסי אשראי ואינה סולקת תשלומים"))
        assertFalse("Preview must not contain a fabricated provider payment URL", preview.contains("https://pay."))
    }

    @Test
    fun savingsRemainVerifiedOfferFirstAndNeverInferAnnualEconomics() {
        val preview = File(previewPath).readText()

        assertTrue(preview.contains("potentialMonthlySaving?.takeIf(::positiveFinite)"))
        assertTrue(preview.contains("potentialAnnualSaving?.takeIf(::positiveFinite)"))
        assertTrue(preview.contains("EmeraldSavings"))
        assertTrue(preview.contains("מחיר נוכחי:"))
        assertTrue(preview.contains("חיסכון מאומת:"))
        assertFalse(
            "Savings UI must not synthesize annual economics from monthly savings",
            preview.contains("* 12.0") || preview.contains("*12.0")
        )
    }

    @Test
    fun savingsActionKeepsIntentConsentProgressAndDuplicateProtection() {
        val preview = File(previewPath).readText()

        listOf(
            "product_savings_action_starting",
            "product_savings_action_submitting",
            "product_submit_provider_details",
            "product_savings_action_success",
            "product_savings_action_error"
        ).forEach { tag ->
            assertTrue("Savings lost P0 E2E/action hook $tag", preview.contains(tag))
        }

        assertTrue(preview.contains("recordSavingsActionStarted("))
        assertTrue(preview.contains("acceptSavingsOpportunity("))
        assertTrue(preview.contains("enabled = !actionStarting && !actionSubmitting"))
        assertTrue(preview.contains("if (actionSubmitting) return@ProductProviderConsentDialog"))
        assertTrue(preview.contains("תוכן תיבת הדואר ונתוני הוצאות אחרים אינם נשלחים"))
    }
}
