package com.example

import com.example.ui.theme.FinancialDesignTokens
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialDesignTokensTest {
    @Test
    fun touchTargetsMeetMobileAccessibilityFloor() {
        assertTrue(FinancialDesignTokens.minimumTouchTarget.value >= 48f)
    }

    @Test
    fun heroKeepsStrongerHierarchyThanRegularCards() {
        assertTrue(FinancialDesignTokens.heroRadius.value >= FinancialDesignTokens.cardRadius.value)
        assertTrue(FinancialDesignTokens.heroPadding.value >= FinancialDesignTokens.cardPadding.value)
    }

    @Test
    fun financialScreenRhythmIsConsistent() {
        assertTrue(FinancialDesignTokens.sectionSpacing.value > FinancialDesignTokens.cardSpacing.value)
        assertTrue(FinancialDesignTokens.cardSpacing.value > FinancialDesignTokens.compactSpacing.value)
        assertTrue(FinancialDesignTokens.screenBottomNavigationClearance.value >= 80f)
    }
}
