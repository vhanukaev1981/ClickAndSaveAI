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

    private val internalCodePattern = Regex("^[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+$")

    fun verifiedSavingsLabel(monthlySaving: Double?, annualSaving: Double?): String? {
        val monthly = monthlySaving?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val annual = annualSaving?.takeIf { it.isFinite() && it > 0.0 }
        return if (annual != null) {
            "אפשר לחסוך ${money(monthly)} בחודש • ${money(annual)} בשנה"
        } else {
            "אפשר לחסוך ${money(monthly)} בחודש"
        }
    }

    fun safeStatus(rawStatus: String?): String {
        val raw = rawStatus.orEmpty().trim()
        if (raw.isBlank()) return "המידע מתעדכן"
        val upper = raw.uppercase()
        if (containsInternalSignal(raw, upper)) return "המידע מתעדכן"
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
        if (raw.isBlank()) return genericError()
        val upper = raw.uppercase()
        return if (
            containsInternalSignal(raw, upper) ||
            upper.contains("EXCEPTION") ||
            upper.contains("HTTP") ||
            upper.contains("STACK") ||
            upper.contains("TOKEN") ||
            upper.contains("PERMISSION_DENIED")
        ) {
            genericError()
        } else {
            raw.take(160)
        }
    }

    fun underReviewLabel(): String =
        "נבדקת עבורך חלופה מתאימה. סכום חיסכון יוצג רק לאחר אימות."

    private fun containsInternalSignal(raw: String, upper: String): Boolean {
        if (internalTokens.any(upper::contains)) return true
        if (internalCodePattern.matches(raw)) return true
        if (raw.contains("://")) return true
        if (raw.contains("com.") || raw.contains("java.") || raw.contains("kotlin.")) return true
        if (raw.contains("{") && raw.contains("}")) return true
        return false
    }

    private fun genericError(): String = "לא הצלחנו לעדכן כרגע. ננסה שוב אוטומטית."

    private fun money(value: Double): String = "₪${String.format("%.2f", value)}"
}
