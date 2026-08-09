package com.example.ui

object CustomerPresentationPolicy {
    private val internalTokens = listOf(
        "NOT_FOUND",
        "GMAIL_READONLY",
        "FIREBASE",
        "APP_CHECK",
        "SECRET_MANAGER",
        "BACKEND",
        "CLOUD_FUNCTION",
        "FUNCTIONS_ERROR",
        "CRM",
        "LEAD_ID",
        "LEAD_STATUS",
        "PROVIDER_REFERENCE",
        "DISPATCH_ID",
        "COMMISSION",
        "ATTRIBUTION_ID"
    )

    fun verifiedSavingsLabel(monthlySaving: Double?, annualSaving: Double?): String? {
        val monthly = monthlySaving?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val annual = annualSaving?.takeIf { it.isFinite() && it > 0.0 } ?: (monthly * 12.0)
        return "אפשר לחסוך ${money(monthly)} בחודש • ${money(annual)} בשנה"
    }

    fun safeStatus(rawStatus: String?): String {
        val raw = rawStatus.orEmpty().trim()
        if (raw.isBlank()) return "המידע מתעדכן"
        val upper = raw.uppercase()
        if (internalTokens.any(upper::contains)) return "המידע מתעדכן"
        if (upper.contains("UNVERIFIED") || upper.contains("PENDING") || upper.contains("PROCESSING")) {
            return "נמצא בבדיקה"
        }
        if (upper.contains("FAILED") || upper.contains("ERROR") || upper.contains("EXCEPTION")) {
            return "לא הצלחנו לעדכן כרגע"
        }
        return raw.take(120)
    }

    fun safeError(rawMessage: String?): String {
        val raw = rawMessage.orEmpty().trim()
        if (raw.isBlank()) return "לא הצלחנו לעדכן כרגע. ננסה שוב אוטומטית."
        val upper = raw.uppercase()
        return if (
            internalTokens.any(upper::contains) ||
            upper.contains("EXCEPTION") ||
            upper.contains("HTTP") ||
            upper.contains("STACK") ||
            upper.contains("TOKEN") ||
            upper.contains("PERMISSION_DENIED")
        ) {
            "לא הצלחנו לעדכן כרגע. ננסה שוב אוטומטית."
        } else {
            raw.take(160)
        }
    }

    fun underReviewLabel(): String =
        "נבדקת עבורך חלופה מתאימה. סכום חיסכון יוצג רק לאחר אימות."

    private fun money(value: Double): String = "₪${String.format("%.2f", value)}"
}
