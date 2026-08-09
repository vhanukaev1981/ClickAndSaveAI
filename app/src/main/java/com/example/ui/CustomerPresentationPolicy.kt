package com.example.ui

object CustomerPresentationPolicy {
    private val internalTokens = listOf(
        "NOT_FOUND",
        "GMAIL_READONLY",
        "FIREBASE",
        "FIRESTORE",
        "APP_CHECK",
        "SECRET_MANAGER",
        "BACKEND",
        "CLOUD_FUNCTION",
        "CLOUD_RUN",
        "PUBSUB",
        "WEBHOOK",
        "CREDENTIAL",
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
    private val hebrewCharacterPattern = Regex("[\u0590-\u05FF]")

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

        if (upper.contains("UNVERIFIED") || upper.contains("PENDING") || upper.contains("PROCESSING")) {
            return "נמצא בבדיקה"
        }
        if (upper.contains("FAILED") || upper.contains("ERROR") || upper.contains("EXCEPTION")) {
            return "לא הצלחנו לעדכן כרגע"
        }
        if (upper in setOf("VERIFIED", "VALIDATED", "READY")) {
            return "המידע אומת"
        }
        if (containsInternalSignal(raw, upper)) return "המידע מתעדכן"

        // Closed-world presentation: unknown backend/domain text is never passed through
        // to the customer just because it does not look technical yet.
        return "המידע מתעדכן"
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
            upper.contains("PERMISSION_DENIED") ||
            !hebrewCharacterPattern.containsMatchIn(raw)
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
