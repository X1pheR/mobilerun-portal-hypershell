package com.mobilerun.portal.taskprompt

import androidx.annotation.ColorRes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PortalDayActivity(
    val label: String,
    val count: Int,
)

data class PortalStatusCount(
    val status: String,
    val count: Int,
    @ColorRes val colorRes: Int,
)

data class PortalDashboardStats(
    val sampleSize: Int,
    val totalRuns: Int,
    val completedCount: Int,
    val failedCount: Int,
    val successRate: Double?,
    val statusCounts: List<PortalStatusCount>,
    val activityByDay: List<PortalDayActivity>,
    val avgDurationMs: Long?,
    val avgSteps: Int?,
    val topModel: String?,
    val lastTaskAgoMs: Long?,
) {
    companion object {
        private const val SPARKLINE_DAYS = 14
        private val DATE_LABEL_FORMAT = DateTimeFormatter.ofPattern("MMM d", Locale.US)

        fun compute(
            items: List<PortalTaskHistoryItem>,
            total: Int,
            nowMs: Long = System.currentTimeMillis(),
        ): PortalDashboardStats {
            val completed = items.count { it.status == PortalTaskTracking.STATUS_COMPLETED }
            val failed = items.count { it.status == PortalTaskTracking.STATUS_FAILED }
            val finished = completed + failed
            val successRate = if (finished > 0) completed.toDouble() / finished * 100.0 else null

            val countsByStatus = items.groupingBy { it.status }.eachCount()
            val statusCounts = countsByStatus
                .map { (status, count) ->
                    PortalStatusCount(
                        status = status,
                        count = count,
                        colorRes = PortalTaskUiSupport.statusColorRes(status),
                    )
                }
                .sortedByDescending { it.count }

            val zone = ZoneId.systemDefault()
            val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            val buckets = LinkedHashMap<LocalDate, Int>()
            for (i in SPARKLINE_DAYS - 1 downTo 0) {
                buckets[today.minusDays(i.toLong())] = 0
            }
            for (item in items) {
                val epochMs = PortalTaskTimestampSupport.parseEpochMillis(item.createdAt) ?: continue
                val date = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
                if (buckets.containsKey(date)) {
                    buckets[date] = buckets.getValue(date) + 1
                }
            }
            val activityByDay = buckets.map { (date, count) ->
                PortalDayActivity(date.format(DATE_LABEL_FORMAT), count)
            }

            val durations = items.mapNotNull { item ->
                val start = PortalTaskTimestampSupport.parseEpochMillis(item.claimedAt)
                    ?: PortalTaskTimestampSupport.parseEpochMillis(item.createdAt)
                    ?: return@mapNotNull null
                val end = PortalTaskTimestampSupport.parseEpochMillis(item.finishedAt) ?: return@mapNotNull null
                val ms = end - start
                if (ms > 0) ms else null
            }
            val avgDurationMs = if (durations.isNotEmpty()) durations.sum() / durations.size else null

            val stepValues = items.mapNotNull { it.steps }
            val avgSteps = if (stepValues.isNotEmpty()) {
                (stepValues.sum().toDouble() / stepValues.size).toInt()
            } else null

            val topModel = items
                .mapNotNull { it.llmModel?.trim()?.takeIf(String::isNotBlank) }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key

            val lastTaskEpochMs = items.mapNotNull {
                PortalTaskTimestampSupport.parseEpochMillis(it.createdAt)
            }.maxOrNull()
            val lastTaskAgoMs = if (lastTaskEpochMs != null) {
                (nowMs - lastTaskEpochMs).coerceAtLeast(0L)
            } else null

            return PortalDashboardStats(
                sampleSize = items.size,
                totalRuns = total,
                completedCount = completed,
                failedCount = failed,
                successRate = successRate,
                statusCounts = statusCounts,
                activityByDay = activityByDay,
                avgDurationMs = avgDurationMs,
                avgSteps = avgSteps,
                topModel = topModel,
                lastTaskAgoMs = lastTaskAgoMs,
            )
        }
    }
}
