package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservedBillsResumePolicyTest {
    @Test
    fun firstRefreshIsAllowed() {
        assertTrue(
            shouldRefreshObservedBillsOnResume(
                lastRefreshElapsedRealtimeMs = 0L,
                nowElapsedRealtimeMs = 1_000L
            )
        )
    }

    @Test
    fun refreshIsThrottledBeforeFiveMinutes() {
        assertFalse(
            shouldRefreshObservedBillsOnResume(
                lastRefreshElapsedRealtimeMs = 10_000L,
                nowElapsedRealtimeMs = 10_000L + OBSERVED_BILLS_RESUME_REFRESH_INTERVAL_MS - 1L
            )
        )
    }

    @Test
    fun refreshIsAllowedAtFiveMinutes() {
        assertTrue(
            shouldRefreshObservedBillsOnResume(
                lastRefreshElapsedRealtimeMs = 10_000L,
                nowElapsedRealtimeMs = 10_000L + OBSERVED_BILLS_RESUME_REFRESH_INTERVAL_MS
            )
        )
    }

    @Test
    fun monotonicClockResetAllowsRecoveryRefresh() {
        assertTrue(
            shouldRefreshObservedBillsOnResume(
                lastRefreshElapsedRealtimeMs = 50_000L,
                nowElapsedRealtimeMs = 1_000L
            )
        )
    }
}
