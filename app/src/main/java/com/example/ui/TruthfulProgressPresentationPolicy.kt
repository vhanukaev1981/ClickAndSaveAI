package com.example.ui

enum class CustomerProgressStage {
    DETECTED,
    CHECKED,
    VERIFIED,
    STILL_CHECKING
}

enum class CustomerProgressTone {
    ACTIVE_BLUE,
    VERIFIED_GREEN,
    REVIEW_AMBER
}

/**
 * Customer-facing progress language for backend-driven financial work.
 *
 * Stream B renders only a stage supplied by Core. It deliberately does not
 * calculate percentages, infer completion, or advance to another stage.
 */
object TruthfulProgressPresentationPolicy {
    fun message(stage: CustomerProgressStage): String = when (stage) {
        CustomerProgressStage.DETECTED -> "זיהינו מידע חדש ובודקים מה הוא אומר עבורך."
        CustomerProgressStage.CHECKED -> "בדקנו את המידע מול התנאים הזמינים כרגע."
        CustomerProgressStage.VERIFIED -> "אימתנו את המידע שאפשר להציג בביטחון."
        CustomerProgressStage.STILL_CHECKING -> "אנחנו עדיין בודקים. נציג תוצאה רק כשיהיה מספיק מידע מאומת."
    }

    fun tone(stage: CustomerProgressStage): CustomerProgressTone = when (stage) {
        CustomerProgressStage.DETECTED,
        CustomerProgressStage.CHECKED -> CustomerProgressTone.ACTIVE_BLUE
        CustomerProgressStage.VERIFIED -> CustomerProgressTone.VERIFIED_GREEN
        CustomerProgressStage.STILL_CHECKING -> CustomerProgressTone.REVIEW_AMBER
    }

    fun nextStage(stage: CustomerProgressStage): CustomerProgressStage? = null
}
