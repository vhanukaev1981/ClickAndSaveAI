package com.example.ui

object CustomerPresentationPolicy {
    private val internalTokens = listOf(
        "NOT_FOUND",
        "GMAIL_READONLY",
        "FIREBASE",
        "APP_CHECK",
        "SECRET_MANAGER",
        "CRM",
        "LEAD_ID",
        "PROVIDER_REFERENCE"
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
        if (upper.contains("UNVERIFIED") || upper.contains("PENDING")) return "נמצא בבדיקה"
        return raw.take(120)
    }

    fun underReviewLabel(): String = "נבדקת עבורך חלופה מתאימה. סכום חיסכון יוצג רק לאחר אימות."

    private fun money(value: Double): String = "₪${String.format("%.2f", value)}"
}
