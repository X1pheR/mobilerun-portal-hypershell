package com.mobilerun.portal.hypershell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HypershellBootstrapPolicyTest {
    private val defaultUrl = "wss://api.mobilerun.ai/v1/providers/personal/join"
    private val customUrl = "wss://mobile.example.test/v1/reverse"

    @Test
    fun onlyExactActionWithCustomWssAndTokenCanStart() {
        assertTrue(allowed())
        assertFalse(allowed(action = "other.action"))
        assertFalse(allowed(url = defaultUrl))
        assertFalse(allowed(url = "ws://mobile.example.test/v1/reverse"))
        assertFalse(allowed(token = ""))
    }

    @Test
    fun repeatedStartsAreRateLimitedButRebootClockResetIsAllowed() {
        assertFalse(allowed(lastStart = 10_000L, now = 39_999L))
        assertTrue(allowed(lastStart = 10_000L, now = 40_000L))
        assertTrue(allowed(lastStart = 50_000L, now = 1_000L))
    }

    private fun allowed(
        action: String? = HypershellBootstrapPolicy.ACTION_START_SESSION,
        url: String = customUrl,
        token: String = "configured-bearer",
        lastStart: Long = 0L,
        now: Long = 100_000L,
    ): Boolean = HypershellBootstrapPolicy.shouldStart(
        action = action,
        reverseUrl = url,
        defaultReverseUrl = defaultUrl,
        token = token,
        lastStartAtMs = lastStart,
        nowMs = now,
    )
}
