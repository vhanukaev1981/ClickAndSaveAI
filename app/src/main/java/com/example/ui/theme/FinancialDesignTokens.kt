package com.example.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared financial-product layout tokens.
 *
 * Stream B owns these values so Dashboard, Bills, Savings and Profile keep the
 * same visual rhythm. They intentionally contain no business or backend logic.
 */
object FinancialDesignTokens {
    val screenHorizontalPadding: Dp = 16.dp
    val screenTopPadding: Dp = 18.dp
    val screenBottomNavigationClearance: Dp = 100.dp

    val sectionSpacing: Dp = 14.dp
    val cardSpacing: Dp = 10.dp
    val compactSpacing: Dp = 6.dp

    val heroRadius: Dp = 24.dp
    val cardRadius: Dp = 20.dp
    val compactCardRadius: Dp = 18.dp
    val iconSurfaceRadius: Dp = 14.dp

    val heroPadding: Dp = 20.dp
    val cardPadding: Dp = 18.dp
    val compactCardPadding: Dp = 15.dp

    val minimumTouchTarget: Dp = 48.dp
}
