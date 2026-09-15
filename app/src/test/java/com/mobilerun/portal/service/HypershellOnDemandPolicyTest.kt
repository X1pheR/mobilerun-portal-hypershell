package com.mobilerun.portal.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HypershellOnDemandPolicyTest {
    @Test
    fun reconnectBackoff_isBoundedExponential() {
        assertEquals(1_000L, HypershellOnDemandPolicy.reconnectDelayMs(0))
        assertEquals(2_000L, HypershellOnDemandPolicy.reconnectDelayMs(1))
        assertEquals(4_000L, HypershellOnDemandPolicy.reconnectDelayMs(2))
        assertEquals(8_000L, HypershellOnDemandPolicy.reconnectDelayMs(3))
        assertEquals(16_000L, HypershellOnDemandPolicy.reconnectDelayMs(4))
        assertEquals(30_000L, HypershellOnDemandPolicy.reconnectDelayMs(5))
        assertEquals(30_000L, HypershellOnDemandPolicy.reconnectDelayMs(20))
    }

    @Test
    fun reconnectBudget_expiresAtTwoMinutes() {
        val startedAt = 10_000L
        assertFalse(HypershellOnDemandPolicy.shouldGiveUp(startedAt, startedAt + 119_999L))
        assertTrue(HypershellOnDemandPolicy.shouldGiveUp(startedAt, startedAt + 120_000L))
    }

    @Test
    fun normalServerClose_isTerminalOnlyForOnDemandMode() {
        assertTrue(HypershellOnDemandPolicy.shouldStopAfterClose(onDemand = true, closeCode = 1000))
        assertFalse(HypershellOnDemandPolicy.shouldStopAfterClose(onDemand = true, closeCode = 1006))
        assertFalse(HypershellOnDemandPolicy.shouldStopAfterClose(onDemand = false, closeCode = 1000))
    }
}
