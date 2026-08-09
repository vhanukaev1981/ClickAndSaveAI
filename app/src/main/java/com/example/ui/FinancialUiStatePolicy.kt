package com.example.ui

enum class FinancialUiState {
    LOADING,
    EMPTY,
    ERROR,
    UNDER_REVIEW,
    READY
}

data class FinancialUiMessage(
    val title: String,
    val body: String
)

object FinancialUiStatePolicy {
    fun message(state: FinancialUiState): FinancialUiMessage = when (state) {
        FinancialUiState.LOADING -> FinancialUiMessage(
            title = "המידע מתעדכן",
            body = "אנחנו מסנכרנים את התמונה הפיננסית שלך. אין צורך לבצע פעולה."
        )
        FinancialUiState.EMPTY -> FinancialUiMessage(
            title = "עדיין אין מספיק מידע",
            body = "כשנזהה מידע חדש הוא יופיע כאן אוטומטית."
        )
        FinancialUiState.ERROR -> FinancialUiMessage(
            title = "לא הצלחנו לעדכן כרגע",
            body = "הנתונים הקיימים נשארים שמורים. ננסה לעדכן שוב אוטומטית."
        )
        FinancialUiState.UNDER_REVIEW -> FinancialUiMessage(
            title = "נבדק עבורך",
            body = "נציג סכום חיסכון רק לאחר שנוכל לאמת הצעה מתאימה ותנאים מלאים."
        )
        FinancialUiState.READY -> FinancialUiMessage(
            title = "המידע מעודכן",
            body = "התמונה הפיננסית מבוססת על המידע הזמין והמאומת כרגע."
        )
    }
}
