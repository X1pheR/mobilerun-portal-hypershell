package com.mobilerun.portal.api

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class A11yReadGateTest {
    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun success_returns_and_caches() {
        val gate = A11yReadGate()
        val r = gate.wrap("k", 1000) { ApiResponse.Success(JSONObject().put("a", 1).toString()) }
        assertTrue(r is ApiResponse.Success)
        gate.close()
    }

    @Test
    fun timeout_returns_cached_object_with_degraded_markers() {
        val now = AtomicLong(1_000L)
        val gate = A11yReadGate(nowMs = { now.get() })
        // warm the cache with a good object payload
        val warm = gate.wrap("state", 1000) {
            ApiResponse.Success(JSONObject().put("a11y_tree", "[]").put("phone_state", JSONObject()).toString())
        }
        assertTrue(warm is ApiResponse.Success)

        // now the read blocks; advance the clock so snapshotAgeMs is observable
        now.set(1_500L)
        val release = CountDownLatch(1)
        val r = gate.wrap("state", 50) { release.await(); ApiResponse.Success("late") }
        assertTrue("expected Success", r is ApiResponse.Success)
        val obj = JSONObject((r as ApiResponse.Success).data as String)
        assertTrue("a11y_tree preserved", obj.has("a11y_tree"))
        assertTrue("phone_state preserved", obj.has("phone_state"))
        assertEquals(true, obj.getBoolean("degraded"))
        assertEquals("a11y_timeout", obj.getString("degradedReason"))
        assertEquals(500L, obj.getLong("snapshotAgeMs"))
        release.countDown()
        gate.close()
    }

    @Test
    fun cold_timeout_with_no_cache_returns_error() {
        val gate = A11yReadGate()
        val release = CountDownLatch(1)
        val r = gate.wrap("cold", 50) { release.await(); ApiResponse.Success("x") }
        assertTrue("expected Error", r is ApiResponse.Error)
        assertTrue((r as ApiResponse.Error).message.lowercase().contains("degraded"))
        release.countDown()
        gate.close()
    }

    @Test
    fun single_flight_does_not_pile_up_invocations() {
        val gate = A11yReadGate()
        val invocations = AtomicInteger(0)
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)
        val produce = {
            invocations.incrementAndGet(); started.countDown(); release.await()
            ApiResponse.Success("done")
        }
        val t1 = Thread { gate.wrap("state", 100, produce) }
        t1.start()
        started.await() // ensure the first read is in flight
        // second concurrent call must reuse the in-flight read, not submit a new one
        val r2 = gate.wrap("state", 100, produce)
        assertTrue(r2 is ApiResponse.Error || r2 is ApiResponse.Success) // degraded/cold either way
        assertEquals("produce must run once under single-flight", 1, invocations.get())
        release.countDown(); t1.join(2000)
        gate.close()
    }

    @Test
    fun late_completion_after_timeout_is_cached_for_next_caller() {
        // A read that exceeds the budget but completes shortly after must still
        // populate the cache. The second read also times out, so it is forced
        // onto the cache path and only succeeds if the late completion cached.
        val gate = A11yReadGate()
        val firstPayload = JSONObject().put("v", "FRESH").toString()

        // First read blocks past the budget: caller times out with a COLD cache.
        val release = CountDownLatch(1)
        val first = gate.wrap("state", 50) { release.await(); ApiResponse.Success(firstPayload) }
        assertTrue("cold cache -> error", first is ApiResponse.Error)

        // The read then completes; the task must cache firstPayload.
        release.countDown()
        Thread.sleep(500) // single-thread executor finishes + caches

        // A subsequent read also times out, so it is FORCED onto the cache path.
        // It must serve the late-cached snapshot (FRESH), marked degraded —
        // without the fix the cache would still be cold and this returns an error.
        val release2 = CountDownLatch(1)
        val r = gate.wrap("state", 50) { release2.await(); ApiResponse.Success("NEWER") }
        assertTrue("expected cached Success, got $r", r is ApiResponse.Success)
        val data = (r as ApiResponse.Success).data as String
        assertTrue("must serve late-cached payload", data.contains("FRESH"))
        assertTrue("served snapshot must be marked degraded", data.contains("degraded"))
        release2.countDown()
        gate.close()
    }

    @Test
    fun raw_object_timeout_returns_degraded_raw_object() {
        val now = AtomicLong(0L)
        val gate = A11yReadGate(nowMs = { now.get() })
        gate.wrap("state_full", 1000) { ApiResponse.RawObject(JSONObject().put("a11y_tree", JSONObject())) }
        now.set(123L)
        val release = CountDownLatch(1)
        val r = gate.wrap("state_full", 50) { release.await(); ApiResponse.RawObject(JSONObject()) }
        assertTrue("expected RawObject", r is ApiResponse.RawObject)
        val obj = (r as ApiResponse.RawObject).json
        assertEquals(true, obj.getBoolean("degraded"))
        assertEquals(123L, obj.getLong("snapshotAgeMs"))
        release.countDown()
        gate.close()
    }

    @Test
    fun idle_worker_thread_is_reclaimed_so_unclosed_owners_do_not_leak() {
        // A gate dropped without close() must not leak its worker: the idle
        // thread is reclaimed by the core-thread timeout. Capture this gate's
        // exact worker thread (not a global name scan) and assert it dies idle.
        val worker = AtomicReference<Thread>()
        val gate = A11yReadGate(idleTimeoutMs = 100)
        gate.wrap("k", 1000) {
            worker.set(Thread.currentThread())
            ApiResponse.Success("x")
        }
        val t = worker.get()
        assertTrue("the read must have run on the gate worker", t != null && t.name.startsWith("a11y-read-gate"))

        val deadline = System.currentTimeMillis() + 3_000
        while (t!!.isAlive && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        assertFalse("idle worker thread must be reclaimed (no leak)", t!!.isAlive)
        gate.close()
    }

    @Test
    fun array_payload_timeout_returned_unchanged() {
        val gate = A11yReadGate()
        gate.wrap("a11y_tree", 1000) { ApiResponse.Success("[{\"x\":1}]") }
        val release = CountDownLatch(1)
        val r = gate.wrap("a11y_tree", 50) { release.await(); ApiResponse.Success("[]") }
        assertTrue(r is ApiResponse.Success)
        // bare JSON array payload is preserved as-is (no degraded wrapper that
        // would change the client-visible shape)
        assertEquals("[{\"x\":1}]", (r as ApiResponse.Success).data)
        assertFalse((r.data as String).contains("degraded"))
        release.countDown()
        gate.close()
    }

    @Test
    fun a_wedged_read_does_not_block_other_keys() {
        // One key stuck in a binder call must not stall reads of other keys.
        val gate = A11yReadGate()
        val started = CountDownLatch(1)
        val wedge = CountDownLatch(1)
        val stuck = Thread { gate.wrap("state_full", 5_000) { started.countDown(); wedge.await(); ApiResponse.Success("x") } }
        stuck.start()
        assertTrue("wedged read must be running on a worker", started.await(2, TimeUnit.SECONDS))

        val r = gate.wrap("phone_state", 1_000) { ApiResponse.Success(JSONObject().put("ok", 1).toString()) }
        assertTrue("independent key must complete on its own worker, got $r", r is ApiResponse.Success)
        assertFalse("a fresh read is not degraded", ((r as ApiResponse.Success).data as String).contains("degraded"))

        wedge.countDown(); stuck.join(2_000)
        gate.close()
    }

    @Test
    fun saturation_serves_cached_degraded() {
        // With every worker busy, a fresh read is served from the degraded cache
        // (reason a11y_saturated) instead of blocking.
        val gate = A11yReadGate(maxWorkers = 1)
        gate.wrap("a", 1_000) { ApiResponse.Success(JSONObject().put("v", "A").toString()) } // warm cache

        val started = CountDownLatch(1)
        val wedge = CountDownLatch(1)
        val stuck = Thread { gate.wrap("b", 5_000) { started.countDown(); wedge.await(); ApiResponse.Success("x") } }
        stuck.start()
        assertTrue("the single worker must be occupied", started.await(2, TimeUnit.SECONDS))

        val r = gate.wrap("a", 1_000) { ApiResponse.Success(JSONObject().put("v", "A2").toString()) }
        assertTrue("saturated read serves cache, got $r", r is ApiResponse.Success)
        val data = (r as ApiResponse.Success).data as String
        assertTrue("serves the cached snapshot", data.contains("\"v\":\"A\""))
        assertTrue("marked degraded", data.contains("degraded"))
        assertTrue("saturation reason", data.contains("a11y_saturated"))

        wedge.countDown(); stuck.join(2_000)
        gate.close()
    }
}
