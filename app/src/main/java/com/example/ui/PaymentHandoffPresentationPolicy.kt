package com.example.ui

data class PaymentHandoffMessage(
    val title: String,
    val body: String,
    val actionLabel: String? = null
)

enum class PaymentHandoffStage {
    UNAVAILABLE,
    VERIFIED_PROVIDER_DESTINATION,
    PROVIDER_PAYMENT_PAGE_OPENED,
    PAYMENT_VERIFIED
}

object PaymentHandoffPresentationPolicy {
    fun message(stage: PaymentHandoffStage): PaymentHandoffMessage = when (stage) {
        PaymentHandoffStage.UNAVAILABLE -> PaymentHandoffMessage(
            title = "תשלום ישירות מול הספק",
            body = "אין כרגע יעד תשלום מאומת שאפשר להציג בבטחה. החשבון נשאר זמין לצפייה."
        )
        PaymentHandoffStage.VERIFIED_PROVIDER_DESTINATION -> PaymentHandoffMessage(
            title = "תשלום אצל הספק",
            body = "המשך לעמוד התשלום המאומת של הספק. פרטי הכרטיס אינם נמסרים ל־Click&SaveAI ואינם נשמרים אצלנו.",
            actionLabel = "המשך לתשלום אצל הספק"
        )
        PaymentHandoffStage.PROVIDER_PAYMENT_PAGE_OPENED -> PaymentHandoffMessage(
            title = "עמוד התשלום של הספק נפתח",
            body = "פתיחת העמוד אינה מוכיחה שהתשלום בוצע. סטטוס החשבון ישתנה רק לאחר מידע מהימן."
        )
        PaymentHandoffStage.PAYMENT_VERIFIED -> PaymentHandoffMessage(
            title = "התשלום אומת",
            body = "קיבלנו מידע מהימן שהתשלום אצל הספק אומת."
        )
    }
}
