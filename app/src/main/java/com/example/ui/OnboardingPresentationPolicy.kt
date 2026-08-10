package com.example.ui

data class OnboardingMessage(
    val title: String,
    val body: String
)

enum class OnboardingStage {
    VALUE_PROMISE,
    PERMISSION_EXPLANATION,
    STILL_PROCESSING,
    FIRST_VERIFIED_VALUE
}

object OnboardingPresentationPolicy {
    fun message(stage: OnboardingStage): OnboardingMessage = when (stage) {
        OnboardingStage.VALUE_PROMISE -> OnboardingMessage(
            title = "מחברים פעם אחת, ומתחילים לחפש חיסכון",
            body = "Click&SaveAI מזהה חשבונות ושירותים, ממשיך לבדוק אותם ומציג חיסכון רק כשהוא מאומת."
        )
        OnboardingStage.PERMISSION_EXPLANATION -> OnboardingMessage(
            title = "למה אנחנו מבקשים גישה",
            body = "הגישה היא לקריאה בלבד ונועדה לזהות חשבוניות ומסמכי חיוב. תוכן תיבת הדואר המלא לא נשלח לספק, והחיבור לא מאשר פעולה אצל ספק."
        )
        OnboardingStage.STILL_PROCESSING -> OnboardingMessage(
            title = "אנחנו עדיין בודקים",
            body = "נמשיך לעדכן את התמונה הפיננסית ונציג תוצאה רק כשיהיה מספיק מידע שאפשר להציג בביטחון."
        )
        OnboardingStage.FIRST_VERIFIED_VALUE -> OnboardingMessage(
            title = "מצאנו ערך שאפשר להציג בביטחון",
            body = "הנתונים שמוצגים כאן מבוססים על מידע שנבדק ואומת."
        )
    }

    fun nextStage(stage: OnboardingStage): OnboardingStage? = null
}
