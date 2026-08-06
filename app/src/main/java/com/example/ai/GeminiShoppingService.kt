package com.example.ai

import android.graphics.Bitmap

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
 * Client-side Gemini calls are intentionally disabled.
 *
 * API provider secrets must never be embedded in an Android APK. A future implementation
 * should call an authenticated backend that performs authorization, redaction, quotas and
 * audit logging before invoking an AI provider.
 */
class GeminiShoppingService {

    suspend fun analyzeDeal(productOrUrl: String): DealAnalysisResult {
        val subject = productOrUrl.trim().take(120)
        return DealAnalysisResult(
            dealScore = 0,
            recommendation = "ניתוח AI אינו מחובר",
            summary = buildString {
                append("לא הופק ניתוח עבור ")
                append(if (subject.isBlank()) "הבקשה" else "\"$subject\"")
                append(". קריאות AI מהטלפון הושבתו כדי למנוע חשיפת מפתח API ומידע פיננסי.")
            },
            predictedLowestPrice = 0.0,
            couponSuggestion = "לא הופקה המלצה או השוואת מחיר.",
            storeComparison = emptyList()
        )
    }

    suspend fun chatWithAi(userMessage: String, conversationHistory: String = ""): String {
        val hasContext = userMessage.isNotBlank() || conversationHistory.isNotBlank()
        return if (hasContext) {
            "שירות ה-AI אינו מחובר כרגע. לא נשלח מידע לספק AI ולא הופקה תשובה פיננסית."
        } else {
            "שירות ה-AI אינו מחובר כרגע."
        }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun scanReceiptOrCoupon(bitmap: Bitmap): ReceiptScanResult {
        return ReceiptScanResult(
            storeName = "לא נותח",
            totalAmount = 0.0,
            estimatedSavings = 0.0,
            itemSummary = "סריקת מסמכים באמצעות AI אינה מחוברת. התמונה לא נשלחה לשירות חיצוני.",
            cashbackTips = "לא הופקה המלצה."
        )
    }
}
