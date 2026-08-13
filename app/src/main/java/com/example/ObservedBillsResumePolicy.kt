package com.example

internal const val OBSERVED_BILLS_RESUME_REFRESH_INTERVAL_MS = 5 * 60 * 1000L

internal fun shouldRefreshObservedBillsOnResume(
    lastRefreshElapsedRealtimeMs: Long,
    nowElapsedRealtimeMs: Long,
    intervalMs: Long = OBSERVED_BILLS_RESUME_REFRESH_INTERVAL_MS
): Boolean {
    require(intervalMs > 0L) { "intervalMs must be positive" }
    if (lastRefreshElapsedRealtimeMs <= 0L) return true
    if (nowElapsedRealtimeMs < lastRefreshElapsedRealtimeMs) return true
    return nowElapsedRealtimeMs - lastRefreshElapsedRealtimeMs >= intervalMs
}
