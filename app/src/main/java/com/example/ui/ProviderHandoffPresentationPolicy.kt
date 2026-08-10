package com.example.ui

data class ProviderHandoffMessage(
    val title: String,
    val body: String
)

enum class ProviderHandoffStage {
    READY_FOR_CONSENT,
    PROVIDER_DESTINATION_OPENED,
    AWAITING_PROVIDER_EVIDENCE,
    ACTIVATION_VERIFIED
}

object ProviderHandoffPresentationPolicy {
    fun message(stage: ProviderHandoffStage): ProviderHandoffMessage = when (stage) {
        ProviderHandoffStage.READY_FOR_CONSENT -> ProviderHandoffMessage(
            title = "ההצעה מוכנה לבחירה",
            body = "לפני שנעביר פרטי קשר לספק, נציג לך בדיוק מה יישלח ונבקש אישור מפורש."
        )
        ProviderHandoffStage.PROVIDER_DESTINATION_OPENED -> ProviderHandoffMessage(
            title = "המשך הטיפול אצל הספק",
            body = "פתחנו את יעד הספק עבור ההצעה שבחרת. השלמת השירות נעשית מול הספק עצמו."
        )
        ProviderHandoffStage.AWAITING_PROVIDER_EVIDENCE -> ProviderHandoffMessage(
            title = "עדיין ממתינים לאישור מהספק",
            body = "לא נסמן מעבר או הפעלה כהושלמו לפני שיהיה לנו אישור מהימן מהספק."
        )
        ProviderHandoffStage.ACTIVATION_VERIFIED -> ProviderHandoffMessage(
            title = "הפעלת השירות אומתה",
            body = "קיבלנו אישור מהימן שהפעלת השירות אצל הספק אומתה."
        )
    }
}
