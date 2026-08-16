package com.example.ai

import android.graphics.Bitmap
import com.example.data.repository.BackendRepository

data class DealAnalysisResult(
    val dealScore: Int,
    val recommendation: String,
    val summary: String,
    val predictedLowestPrice: Double,
    val couponSuggestion: String,
    val storeComparison: List<StorePriceInfo> = emptyList()
)

data class StorePriceInfo(
    val storeName: String,
    val price: Double,
    val badge: String = ""
)

data class ReceiptScanResult(
    val storeName: String,
    val totalAmount: Double,
    val estimatedSavings: Double,
    val itemSummary: String,
    val cashbackTips: String
)

/**
 * AI requests are sent only to an authenticated Firebase callable function.
 * No model credential is present in the Android application.
 */
class GeminiShoppingService(
    private val backendRepository: BackendRepository = BackendRepository()
) {
    suspend fun analyzeDeal(productOrUrl: String): DealAnalysisResult {
        val analysis = backendRepository.analyzeDeal(productOrUrl)
        val verificationNote = if (analysis.requiresVerification) {
            "כל טענה מסחרית דורשת אימות מול מקור רשמי ומתוארך."
        } else {
            ""
        }
        return DealAnalysisResult(
            dealScore = 0,
            recommendation = "בדיקה זהירה בלבד",
            summary = analysis.summary,
            predictedLowestPrice = 0.0,
            couponSuggestion = buildString {
                if (analysis.risks.isNotEmpty()) {
                    append("סיכונים: ")
                    append(analysis.risks.joinToString(" • "))
                }
                if (analysis.questions.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append("שאלות לבדיקה: ")
                    append(analysis.questions.joinToString(" • "))
                }
                if (verificationNote.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append(verificationNote)
                }
            },
            storeComparison = emptyList()
        )
    }

    suspend fun chatWithAi(userMessage: String, conversationHistory: String = ""): String {
        val query = buildString {
            if (conversationHistory.isNotBlank()) {
                append(conversationHistory.takeLast(2500))
                append("\n")
            }
            append(userMessage)
        }
        val analysis = backendRepository.analyzeDeal(query)
        return buildString {
            append(analysis.summary)
            if (analysis.risks.isNotEmpty()) {
                append("\n\nסיכונים: ")
                append(analysis.risks.joinToString(" • "))
            }
            if (analysis.questions.isNotEmpty()) {
                append("\n\nמה כדאי לבדוק: ")
                append(analysis.questions.joinToString(" • "))
            }
            append("\n\nיש לאמת מחירים ותנאים מול מקור רשמי ומתוארך.")
        }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun scanReceiptOrCoupon(bitmap: Bitmap): ReceiptScanResult {
        return ReceiptScanResult(
            storeName = "לא נותח",
            totalAmount = 0.0,
            estimatedSavings = 0.0,
            itemSummary = "סריקת תמונות אינה מחוברת עדיין ל-Backend, ולכן התמונה לא נשלחה.",
            cashbackTips = "לא הופקה המלצה."
        )
    }
}
