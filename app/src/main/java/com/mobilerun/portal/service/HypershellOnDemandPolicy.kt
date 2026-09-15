package com.mobilerun.portal.service

internal object HypershellOnDemandPolicy {
    const val RECONNECT_BUDGET_MS = 120_000L

    private val reconnectDelaysMs = longArrayOf(
        1_000L,
        2_000L,
        4_000L,
        8_000L,
        16_000L,
        30_000L,
    )

    fun reconnectDelayMs(attempt: Int): Long =
        reconnectDelaysMs[attempt.coerceAtLeast(0).coerceAtMost(reconnectDelaysMs.lastIndex)]

    fun shouldGiveUp(reconnectStartedAtMs: Long, nowMs: Long): Boolean {
        if (reconnectStartedAtMs <= 0L) return false
        return nowMs - reconnectStartedAtMs >= RECONNECT_BUDGET_MS
    }

    fun shouldStopAfterClose(onDemand: Boolean, closeCode: Int): Boolean =
        onDemand && closeCode == 1000
}
