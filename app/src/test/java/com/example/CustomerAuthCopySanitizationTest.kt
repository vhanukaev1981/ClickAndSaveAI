package com.example

import com.example.ui.CustomerPresentationPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerAuthCopySanitizationTest {
    private val genericError = "לא הצלחנו לעדכן כרגע. ננסה שוב אוטומטית."

    @Test
    fun mixedHebrewAndAuthInfrastructureTextIsNeverPassedThrough() {
        val samples = listOf(
            "שגיאת Google OAuth בעת החיבור",
            "חסר client_id להגדרת החשבון",
            "ה־scope gmail.readonly לא אושר",
            "Google API החזיר שגיאה זמנית",
            "לא התקבל server auth code",
            "נכשל OAuth token exchange"
        )

        samples.forEach { raw ->
            assertEquals(genericError, CustomerPresentationPolicy.safeError(raw))
        }
    }
}
