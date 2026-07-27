package com.example.data.local

data class MarketProviderOption(
    val category: String,
    val providerName: String,
    val planName: String,
    val highlights: String,
    val priceRange: String,
    val discountDetails: String
)

object IsraeliMarketData {
    val electricityProviders = listOf(
        MarketProviderOption(
            category = "חשמל",
            providerName = "אלקטרה פאוור (Electra Power)",
            planName = "מסלול הייטק / יום / לילה / קבוע",
            highlights = "מסלול הייטק 8%-10% ביום, מסלול לילה 20% הנחה, מסלול קבוע 5%-7%",
            priceRange = "חיסכון של ₪35 - ₪120 בחודש",
            discountDetails = "הנחה קבועה מחשבון החשמל ללא צורך בהחלפת מונה בחלק מהמסלולים"
        ),
        MarketProviderOption(
            category = "חשמל",
            providerName = "פזגז חשמל",
            planName = "מסלול קבוע / סופ\"ש",
            highlights = "הנחה קבועה 5%-7%, מסלול סופ\"ש מוזל",
            priceRange = "חיסכון 5%-7% מחשבון החשמל",
            discountDetails = "הנחה ישירה מהתעריף של חברת החשמל"
        ),
        MarketProviderOption(
            category = "חשמל",
            providerName = "סלקום אלקטריק",
            planName = "משולב סלולר וחשמל",
            highlights = "6%-10% הנחה בחשמל + הטבות בחבילת הסלולר",
            priceRange = "חיסכון משולב של עד ₪180 בחודש",
            discountDetails = "הטבות ייחודיות למצטרפים לחשמלית של סלקום"
        ),
        MarketProviderOption(
            category = "חשמל",
            providerName = "פרטנר power",
            planName = "מסלול יום וקבוע",
            highlights = "5%-8% הנחה, התאמה מיוחדת לעובדים מהבית",
            priceRange = "חיסכון ממוצע של ₪50/חודש",
            discountDetails = "ניוד מהיר בלחיצת כפתור אחת"
        ),
        MarketProviderOption(
            category = "חשמל",
            providerName = "אמישראגז חשמל",
            planName = "מסלול יציב",
            highlights = "6.5% הנחה קבועה 24/7",
            priceRange = "חיסכון 6.5% מכל חשבון",
            discountDetails = "ללא אותיות קטנות"
        ),
        MarketProviderOption(
            category = "חשמל",
            providerName = "חברת החשמל (IEC)",
            planName = "תעריף בסיס מפוקח",
            highlights = "תעריף רגיל ללא הנחה מיוחדת",
            priceRange = "תעריף רגיל (ברירת מחדל)",
            discountDetails = "ניתן לניוד בקליק לכל ספק פרטי"
        )
    )

    val cellularProviders = listOf(
        MarketProviderOption(
            category = "סלולר",
            providerName = "פרטנר (Partner)",
            planName = "חבילות 5G יחיד / משפחתי",
            highlights = "דור 5, נפחי גלישה 500GB-1000GB, שיחות לחו\"ל",
            priceRange = "₪29 - ₪49 לקו בחודש",
            discountDetails = "כולל סים כפול ושיחות ללא הגבלה"
        ),
        MarketProviderOption(
            category = "סלולר",
            providerName = "סלקום (Cellcom)",
            planName = "חבילות דור 5 מורחבות",
            highlights = "גלישה חופשית, 500GB, שירותי גיבוי ענן",
            priceRange = "₪29 - ₪49 לקו",
            discountDetails = "הנחה משפחתית מ-3 קווים ומעלה"
        ),
        MarketProviderOption(
            category = "סלולר",
            providerName = "פלאפון (Pelephone)",
            planName = "Pelephone MAX 5G",
            highlights = "מהירות גלישה מירבית 5G, חבילות חו\"ל מובנות",
            priceRange = "₪35 - ₪59 לקו",
            discountDetails = "כולל סטינג פלאפון ואבטחת סייבר"
        ),
        MarketProviderOption(
            category = "סלולר",
            providerName = "019 מובייל",
            planName = "מסלול חיסכון מושלם",
            highlights = "חבילת 100GB-200GB במחיר רצפה",
            priceRange = "₪19 - ₪29 לקו",
            discountDetails = "המחיר הזול בישראל לקו בודד"
        ),
        MarketProviderOption(
            category = "סלולר",
            providerName = "הוט מובייל / גולן / רמי לוי / WEcom",
            planName = "חבילות תחרותיות 5G",
            highlights = "דור 5, 200GB-1000GB, ללא דמי חיבור",
            priceRange = "₪19 - ₪39 לקו",
            discountDetails = "ניוד מספרים אוטומטי ב-5 דקות"
        )
    )

    val internetProviders = listOf(
        MarketProviderOption(
            category = "אינטרנט",
            providerName = "סלקום פייבר (Cellcom Fiber)",
            planName = "מהירות 1000Mb (1Gb)",
            highlights = "סיבים אופטיים מהירים + נתב Wi-Fi 6 מתקדם",
            priceRange = "₪89 - ₪99 בחודש",
            discountDetails = "חודשיים ראשונים בהנחה מיוחדת"
        ),
        MarketProviderOption(
            category = "אינטרנט",
            providerName = "פרטנר סיבים (Partner Fiber)",
            planName = "מהירות 600Mb / 1000Mb",
            highlights = "חיבור יציב ללא ניתוקים, כולל מגדיל טווח",
            priceRange = "₪89 - ₪109 בחודש",
            discountDetails = "מתאים לגיימינג ועבודה מהבית"
        ),
        MarketProviderOption(
            category = "אינטרנט",
            providerName = "בזק סיבים (Bezeq)",
            planName = "Bfiber 1Gb / 2.5Gb",
            highlights = "תשתית בפריסה ארצית רחבה, נתב Be Ultra",
            priceRange = "₪99 - ₪129 בחודש",
            discountDetails = "התקנה מוזלת דרך מנוע Click & Save"
        ),
        MarketProviderOption(
            category = "אינטרנט",
            providerName = "אנלימיטד (Unlimited) / 019 / NEXT",
            planName = "סיבים 300Mb - 1000Mb",
            highlights = "אינטרנט סיבים במחיר מוזל, תשתית פתוחה",
            priceRange = "₪69 - ₪89 בחודש",
            discountDetails = "מסלולים ללא התחייבות"
        )
    )

    val insuranceProviders = listOf(
        MarketProviderOption(
            category = "ביטוח",
            providerName = "ביטוח ישיר / AIG / ליברה",
            planName = "ביטוח רכב (חובה + מקיף)",
            highlights = "הנחה לנהגים זהירים, ביטול השתתפות עצמית",
            priceRange = "חיסכון של ₪400 - ₪1,200 בשנה",
            discountDetails = "סריקת כפילויות ביטוח אוטומטית"
        ),
        MarketProviderOption(
            category = "ביטוח",
            providerName = "מגדל / הראל / הפניקס / כלל",
            planName = "ביטוח בריאות ודירה",
            highlights = "איחוד פוליסות, ניתוחים וטיפולים בחו\"ל, מבנה ותכולה",
            priceRange = "חיסכון של ₪80 - ₪350 בחודש",
            discountDetails = "ניתוח כפילויות במערכת 'הר הביטוח'"
        ),
        MarketProviderOption(
            category = "ביטוח",
            providerName = "ווישור (weSure) / מנורה מבטחים",
            planName = "פוליסה דיגיטלית מוזלת",
            highlights = "ניהול דיגיטלי מלא, תעריף מותאם קילומטראז'",
            priceRange = "הוזלה של עד 30% בפוליסה",
            discountDetails = "הפקה דיגיטלית מיידית"
        )
    )

    val tvSubscriptions = listOf(
        MarketProviderOption(
            category = "טלוויזיה ומנויים",
            providerName = "FreeTV / stingTV",
            planName = "טלוויזיה על גבי האינטרנט",
            highlights = "כל הערוצים הישראלים, ספורט, סרטים וסדרות",
            priceRange = "₪39 - ₪59 בחודש",
            discountDetails = "חיסכון של מאות שקלים בהשוואה לכבלים/לוויין"
        ),
        MarketProviderOption(
            category = "טלוויזיה ומנויים",
            providerName = "סלקום TV / פרטנר TV / yes / HOT",
            planName = "חבילות תוכן וספורט מורחבות",
            highlights = "ממירים חכמים, אפליקציית Smart TV, תכני איכות",
            priceRange = "₪69 - ₪129 בחודש",
            discountDetails = "מבצעים מיוחדים למעבירים חבילה"
        ),
        MarketProviderOption(
            category = "טלוויזיה ומנויים",
            providerName = "Netflix / Spotify / Apple",
            planName = "מנויי פרימיום משפחתיים",
            highlights = "איחוד חשבונות משפחתיים ומציאת דילים",
            priceRange = "₪19 - ₪55 בחודש",
            discountDetails = "זיהוי חיובים כפולים או לא פעילים"
        )
    )

    val allCategories = listOf("הכל", "חשמל", "סלולר", "אינטרנט", "ביטוח", "טלוויזיה ומנויים")

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

    fun getPaymentUrl(providerName: String): String {
        val name = providerName.lowercase()
        return when {
            name.contains("חשמל") || name.contains("iec") -> "https://www.iec.co.il/pay"
            name.contains("בזק") || name.contains("bezeq") -> "https://www.bezeq.co.il/payments"
            name.contains("סלקום") || name.contains("cellcom") -> "https://www.cellcom.co.il/pay"
            name.contains("פרטנר") || name.contains("partner") -> "https://www.partner.co.il/payment"
            name.contains("הוט") || name.contains("hot") -> "https://www.hot.net.il/heb/customerservice/paybill"
            name.contains("פלאפון") || name.contains("pelephone") -> "https://www.pelephone.co.il/digital/pay"
            name.contains("019") -> "https://019mobile.co.il/pay"
            name.contains("גולן") || name.contains("golan") -> "https://www.golantelecom.co.il"
            name.contains("אלקטרה") || name.contains("electra") -> "https://www.electrapower.co.il"
            name.contains("פזגז") || name.contains("pazgaz") -> "https://www.pazgaz.co.il"
            name.contains("ישיר") || name.contains("555") -> "https://www.555.co.il/pay"
            name.contains("aig") -> "https://www.aig.co.il/pay"
            name.contains("ליברה") || name.contains("libra") -> "https://www.my-libra.co.il"
            name.contains("הראל") || name.contains("harel") -> "https://www.harel-group.co.il"
            name.contains("פניקס") || name.contains("phoenix") || name.contains("fnx") -> "https://www.fnx.co.il"
            name.contains("כלל") || name.contains("clal") -> "https://www.clalbit.co.il"
            name.contains("מגדל") || name.contains("migdal") -> "https://www.migdal.co.il"
            name.contains("netflix") || name.contains("נטפליקס") -> "https://www.netflix.com/youraccount"
            name.contains("yes") || name.contains("יס") || name.contains("sting") -> "https://www.yes.co.il/payments"
            name.contains("freetv") || name.contains("פרי") -> "https://freetv.tv"
            else -> "https://www.iec.co.il/pay"
        }
    }
}
