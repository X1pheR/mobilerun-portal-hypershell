package com.mobilerun.portal.hypershell

internal object HypershellBootstrapPolicy {
    const val ACTION_START_SESSION = "eu.hypershell.mobile.START_SESSION"
    const val MIN_START_INTERVAL_MS = 30_000L

    fun shouldStart(
        action: String?,
        reverseUrl: String,
        defaultReverseUrl: String,
        token: String,
        lastStartAtMs: Long,
        nowMs: Long,
    ): Boolean {
        if (action != ACTION_START_SESSION) return false
        if (token.isBlank()) return false
        if (!reverseUrl.startsWith("wss://", ignoreCase = true)) return false
        if (reverseUrl == defaultReverseUrl) return false
        if (lastStartAtMs <= 0L || nowMs < lastStartAtMs) return true
        return nowMs - lastStartAtMs >= MIN_START_INTERVAL_MS
    }
}
