package com.mobilerun.portal.api

import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bounds accessibility-tree reads so a wedged a11y binder cannot hang the HTTP
 * server. Each read is single-flight per cache key and runs on a small bounded
 * pool of daemon workers with a hard [timeoutMs] budget; on timeout — or when
 * every worker is busy — it returns the last good serialized snapshot marked
 * `degraded`/`degradedReason`/`snapshotAgeMs` instead of blocking.
 *
 * A wedged read is not cancellable, so its worker is sacrificed, but it holds
 * only one worker — a stuck key cannot stall reads of other keys, and the HTTP
 * request workers stay free. Workers are reclaimed when idle, so an ApiHandler
 * dropped without [close] does not leak them.
 */
class A11yReadGate(
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    idleTimeoutMs: Long = IDLE_TIMEOUT_MS,
    maxWorkers: Int = MAX_READ_WORKERS,
) {
    private data class Snapshot(val payload: String, val raw: Boolean, val tsMs: Long)

    private val threadSeq = AtomicInteger()

    // Bounded cached pool: reuse an idle worker, else spawn one (up to
    // maxWorkers), else reject (all workers wedged). core=0 + SynchronousQueue
    // means idle workers time out and are reclaimed — no leak.
    private val executor = ThreadPoolExecutor(
        0, maxWorkers,
        idleTimeoutMs, TimeUnit.MILLISECONDS,
        SynchronousQueue(),
    ) { r -> Thread(r, "a11y-read-gate-${threadSeq.incrementAndGet()}").apply { isDaemon = true } }
    private val cache = ConcurrentHashMap<String, Snapshot>()
    private val inflight = ConcurrentHashMap<String, Future<ApiResponse>>()

    /** Run [produce] (a blocking a11y read) with a hard [timeoutMs] budget. */
    fun wrap(key: String, timeoutMs: Long, produce: () -> ApiResponse): ApiResponse {
        val future = try {
            inflight.compute(key) { _, existing ->
                // Single-flight per key: reuse an in-flight read for this key
                // instead of piling up duplicate work behind it.
                if (existing != null && !existing.isDone) existing
                // Cache inside the task so a read that completes AFTER the caller
                // timed out still refreshes the snapshot. Per-key single-flight
                // means at most one task per key, so no out-of-order overwrite.
                else executor.submit(Callable { produce().also { cacheIfReadable(key, it) } })
            }!!
        } catch (e: RejectedExecutionException) {
            // Every worker is busy/wedged (or the gate is shutting down): serve
            // the last good snapshot rather than block a request worker.
            return if (executor.isShutdown) ApiResponse.Error("accessibility read gate is shutting down")
            else degradedFromCache(key, "a11y_saturated")
        }
        return try {
            val result = future.get(timeoutMs, TimeUnit.MILLISECONDS)
            inflight.remove(key, future)
            result // already cached by the task on completion
        } catch (e: TimeoutException) {
            // Do NOT cancel — the binder call cannot be interrupted. Leave it on
            // its worker and serve the last good snapshot, degraded.
            degradedFromCache(key, "a11y_timeout")
        } catch (e: ExecutionException) {
            inflight.remove(key, future)
            val cause = e.cause ?: e
            Log.w(TAG, "accessibility read '$key' failed: ${cause.message}")
            ApiResponse.Error(cause.message ?: "accessibility read failed")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            ApiResponse.Error("accessibility read interrupted")
        }
    }

    private fun cacheIfReadable(key: String, result: ApiResponse) {
        when (result) {
            is ApiResponse.Success -> (result.data as? String)?.let {
                cache[key] = Snapshot(it, raw = false, tsMs = nowMs())
            }
            is ApiResponse.RawObject ->
                cache[key] = Snapshot(result.json.toString(), raw = true, tsMs = nowMs())
            // Only Success(String)/RawObject snapshots are cached; errors and
            // other variants are not (a wrapped read never produces them).
            else -> {}
        }
    }

    private fun degradedFromCache(key: String, reason: String): ApiResponse {
        val snap = cache[key]
            ?: return ApiResponse.Error(
                "accessibility read unavailable and no cached snapshot exists " +
                    "(degraded; reason=$reason)"
            )
        val ageMs = nowMs() - snap.tsMs
        return if (snap.raw) {
            ApiResponse.RawObject(augmentObject(JSONObject(snap.payload), reason, ageMs))
        } else {
            ApiResponse.Success(augmentSerialized(snap.payload, reason, ageMs))
        }
    }

    private fun augmentObject(obj: JSONObject, reason: String, ageMs: Long): JSONObject =
        obj.put("degraded", true).put("degradedReason", reason).put("snapshotAgeMs", ageMs)

    private fun augmentSerialized(serialized: String, reason: String, ageMs: Long): String =
        try {
            augmentObject(JSONObject(serialized), reason, ageMs).toString()
        } catch (_: JSONException) {
            // JSONArray payload (e.g. /a11y_tree): keep the client-visible shape
            // (a bare array) rather than wrapping it.
            serialized
        }

    fun close() {
        executor.shutdownNow()
    }

    companion object {
        private const val TAG = "A11yReadGate"

        // Idle worker reclaim window: a dropped gate returns its threads instead
        // of holding them for the whole process.
        private const val IDLE_TIMEOUT_MS = 30_000L

        // One worker per concurrently-read key. There are ~7 distinct read keys
        // (a11y_tree, a11y_tree_full:filter=*, phone_state, state,
        // state_full:filter=*), so 8 covers them with headroom; beyond that a
        // read is served from the degraded cache rather than spawning unbounded.
        private const val MAX_READ_WORKERS = 8
    }
}
