package com.example.data.local

data class MarketProviderOption(
    val category: String,
    val providerName: String,
    val planName: String,
    val highlights: String,
    val priceRange: String,
    val discountDetails: String
)

/**
 * Local provider directory only.
 *
 * This file intentionally contains no prices, discounts, savings estimates or claims about
 * current plans. Commercial terms change frequently and must be verified against a dated,
 * official source before they are shown as facts or used in a recommendation.
 */
object IsraeliMarketData {
    private fun provider(category: String, name: String) = MarketProviderOption(
        category = category,
        providerName = name,
        planName = "מידע מסחרי דורש אימות",
        highlights = "יש לבדוק זמינות, מחיר, תקופת מבצע ותנאים באתר הרשמי של הספק.",
        priceRange = "מחיר לא מאומת בקוד",
        discountDetails = "לא נשמרת בקוד טענת הנחה או חיסכון."
    )

    val electricityProviders = listOf(
        provider("חשמל", "חברת החשמל (IEC)"),
        provider("חשמל", "אלקטרה פאוור"),
        provider("חשמל", "פזגז חשמל"),
        provider("חשמל", "סלקום אנרגיה"),
        provider("חשמל", "פרטנר Power"),
        provider("חשמל", "אמישראגז חשמל")
    )

    val cellularProviders = listOf(
        provider("סלולר", "פרטנר"),
        provider("סלולר", "סלקום"),
        provider("סלולר", "פלאפון"),
        provider("סלולר", "HOT mobile"),
        provider("סלולר", "גולן טלקום"),
        provider("סלולר", "019 מובייל"),
        provider("סלולר", "Wecom"),
        provider("סלולר", "רמי לוי תקשורת")
    )

    val internetProviders = listOf(
        provider("אינטרנט", "בזק"),
        provider("אינטרנט", "סלקום"),
        provider("אינטרנט", "פרטנר"),
        provider("אינטרנט", "HOT"),
        provider("אינטרנט", "אנלימיטד (Unlimited)"),
        provider("אינטרנט", "019")
    )

    val insuranceProviders = listOf(
        provider("ביטוח", "הראל"),
        provider("ביטוח", "הפניקס"),
        provider("ביטוח", "מגדל"),
        provider("ביטוח", "כלל"),
        provider("ביטוח", "מנורה מבטחים"),
        provider("ביטוח", "ביטוח ישיר"),
        provider("ביטוח", "AIG"),
        provider("ביטוח", "ליברה"),
        provider("ביטוח", "weSure")
    )

    val tvSubscriptions = listOf(
        provider("טלוויזיה ומנויים", "yes / STING"),
        provider("טלוויזיה ומנויים", "HOT / NEXT"),
        provider("טלוויזיה ומנויים", "סלקום TV"),
        provider("טלוויזיה ומנויים", "פרטנר TV"),
        provider("טלוויזיה ומנויים", "FreeTV"),
        provider("טלוויזיה ומנויים", "Netflix"),
        provider("טלוויזיה ומנויים", "Spotify"),
        provider("טלוויזיה ומנויים", "Apple")
    )

    val allCategories = listOf(
        "הכל",
        "חשמל",
        "סלולר",
        "אינטרנט",
        "ביטוח",
        "טלוויזיה ומנויים"
    )

    fun getOptionsForCategory(category: String): List<MarketProviderOption> {
        return when (category) {
            "חשמל" -> electricityProviders
            "סלולר" -> cellularProviders
            "אינטרנט" -> internetProviders
            "ביטוח" -> insuranceProviders
            "טלוויזיה ומנויים" -> tvSubscriptions
            else -> electricityProviders + cellularProviders + internetProviders + insuranceProviders + tvSubscriptions
        }
    }

    /**
     * Legacy helper retained for source compatibility. Current UI does not navigate directly to
     * payment pages because such URLs must be re-verified before production use.
     */
    fun getPaymentUrl(providerName: String): String {
        @Suppress("UNUSED_VARIABLE")
        val ignored = providerName
        return ""
    }
}
