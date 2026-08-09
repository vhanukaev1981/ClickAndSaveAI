package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamBFinancialSurfaceContractTest {
    private val billsPath = "src/main/java/com/example/ui/screens/InvoicesScreen.kt"
    private val savingsPath = "src/main/java/com/example/ui/screens/ProvidersScreen.kt"

    @Test
    fun billsRemainSpendFirstAndUseSharedFinancialDesignTokens() {
        val bills = File(billsPath).readText()

        listOf(
            "FinancialDesignTokens.screenHorizontalPadding",
            "FinancialDesignTokens.screenTopPadding",
            "FinancialDesignTokens.screenBottomNavigationClearance",
            "FinancialDesignTokens.sectionSpacing",
            "FinancialDesignTokens.cardRadius",
            "FinancialDesignTokens.cardPadding"
        ).forEach { token ->
            assertTrue("Bills lost shared design token $token", bills.contains(token))
        }

        assertTrue(bills.contains("bills_monthly_overview"))
        assertTrue(bills.contains("bills_action_feedback"))
        assertTrue(bills.contains("הוצאה חודשית מזוהה"))
        assertFalse(
            "Bills must not present local potential as verified savings",
            bills.contains("חיסכון פוטנציאלי")
        )
    }

    @Test
    fun manualBillFlowKeepsStableInputsCategoriesAndConfirmationHooks() {
        val bills = File(billsPath).readText()

        listOf(
            "add_manual_bill",
            "manual_bill_provider",
            "manual_bill_amount",
            "save_manual_bill",
            "cancel_manual_bill",
            "confirm_delete_bill",
            "cancel_delete_bill"
        ).forEach { tag ->
            assertTrue("Bills lost E2E hook $tag", bills.contains(tag))
        }

        listOf(
            "manual_bill_category_electricity",
            "manual_bill_category_cellular",
            "manual_bill_category_internet",
            "manual_bill_category_communications",
            "manual_bill_category_insurance",
            "manual_bill_category_television"
        ).forEach { tag ->
            assertTrue("Manual bill category lost stable hook $tag", bills.contains(tag))
        }
    }

    @Test
    fun savingsRemainVerifiedOfferFirstAndNeverInferAnnualEconomics() {
        val savings = File(savingsPath).readText()

        assertTrue(savings.contains("CustomerPresentationPolicy.verifiedSavingsLabel"))
        assertTrue(savings.contains("result.potentialAnnualSaving"))
        assertFalse(
            "Savings UI must not synthesize annual economics from monthly savings",
            savings.contains("result.potentialMonthlySaving * 12.0")
        )
        assertTrue(savings.contains("VIEW_ONLY").not() || savings.contains("פעולה ישירה מתוך האפליקציה עדיין אינה זמינה"))
    }

    @Test
    fun savingsActionKeepsIntentConsentProgressRetryAndDuplicateProtection() {
        val savings = File(savingsPath).readText()

        listOf(
            "savings_action_starting",
            "savings_action_submitting",
            "savings_retry_refresh",
            "savings_contact_name",
            "savings_contact_phone",
            "savings_contact_email",
            "savings_contact_consent",
            "submit_savings_request",
            "cancel_savings_request",
            "savings_action_success"
        ).forEach { tag ->
            assertTrue("Savings lost E2E/action hook $tag", savings.contains(tag))
        }

        assertTrue(savings.contains("recordSavingsActionStarted("))
        assertTrue(savings.contains("actionEnabled = !actionIntentStarting && !actionSubmitting"))
        assertTrue(savings.contains("if (actionSubmitting) return@SavingsActionDialog"))
        assertTrue(savings.contains("תוכן תיבת הדואר ותמונת ההוצאות המלאה שלך אינם נשלחים"))
    }
}
