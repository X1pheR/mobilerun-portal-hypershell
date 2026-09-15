package com.mobilerun.portal.taskprompt

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.mobilerun.portal.R
import com.mobilerun.portal.state.ConnectionState

enum class PortalTaskStatusAppearance {
    INFO,
    SUCCESS,
    ERROR,
}

object PortalTaskUiSupport {
    fun shouldShowTaskSurface(
        connectionState: ConnectionState,
        authToken: String,
    ): Boolean {
        return connectionState == ConnectionState.CONNECTED &&
            authToken.trim().isNotBlank()
    }

    fun statusLabel(context: Context, status: String): String {
        return context.getString(statusLabelRes(status))
    }

    @StringRes
    fun statusLabelRes(status: String): Int {
        return when (status) {
            PortalTaskTracking.STATUS_CREATED -> R.string.task_prompt_status_created
            PortalTaskTracking.STATUS_RUNNING -> R.string.task_prompt_status_running
            PortalTaskTracking.STATUS_PAUSED -> R.string.task_prompt_status_paused
            PortalTaskTracking.STATUS_CANCELLING -> R.string.task_prompt_status_cancelling
            PortalTaskTracking.STATUS_COMPLETED -> R.string.task_prompt_status_completed
            PortalTaskTracking.STATUS_FAILED -> R.string.task_prompt_status_failed
            PortalTaskTracking.STATUS_CANCELLED -> R.string.task_prompt_status_cancelled
            PortalTaskTracking.STATUS_TRACKING_TIMEOUT -> R.string.task_prompt_status_tracking_timeout
            else -> R.string.task_prompt_status_running
        }
    }

    fun statusAppearance(status: String): PortalTaskStatusAppearance {
        return when (status) {
            PortalTaskTracking.STATUS_COMPLETED -> PortalTaskStatusAppearance.SUCCESS
            PortalTaskTracking.STATUS_FAILED,
            PortalTaskTracking.STATUS_CANCELLED,
            -> PortalTaskStatusAppearance.ERROR

            else -> PortalTaskStatusAppearance.INFO
        }
    }

    fun buildSummary(
        context: Context,
        status: String,
        summary: String?,
        steps: Int?,
        // Pass only on surfaces with room to render long text (details screen, trajectory).
        // Compact callers (history rows, active task card) leave it null to keep the
        // step-count fallback so long agent answers don't distort the layout.
        message: String? = null,
    ): String? {
        val normalizedMessage = message?.trim()?.takeIf { it.isNotBlank() }
        val normalizedSummary = summary?.trim()?.takeIf { it.isNotBlank() }
        val result = normalizedMessage ?: normalizedSummary
        return when (status) {
            PortalTaskTracking.STATUS_CREATED -> result
                ?: context.getString(R.string.task_prompt_created_generic)

            PortalTaskTracking.STATUS_RUNNING -> result
                ?: context.getString(R.string.task_prompt_running_generic)

            PortalTaskTracking.STATUS_PAUSED -> result
                ?: context.getString(R.string.task_prompt_paused_generic)

            PortalTaskTracking.STATUS_CANCELLING -> result
                ?: context.getString(R.string.task_prompt_cancelling_generic)

            PortalTaskTracking.STATUS_COMPLETED -> result
                ?: steps?.let { context.getString(R.string.task_prompt_completed_steps, it) }
                ?: context.getString(R.string.task_prompt_completed_generic)

            PortalTaskTracking.STATUS_FAILED -> result
                ?: steps?.let { context.getString(R.string.task_prompt_failed_steps, it) }
                ?: context.getString(R.string.task_prompt_failed_generic)

            PortalTaskTracking.STATUS_CANCELLED -> result
                ?: context.getString(R.string.task_prompt_cancelled_generic)

            PortalTaskTracking.STATUS_TRACKING_TIMEOUT ->
                context.getString(R.string.task_prompt_timeout_stopped)

            else -> result
        }
    }

    fun formatTimestamp(raw: String?): String? {
        return PortalTaskTimestampSupport.formatForDisplay(raw)
    }

    fun booleanLabel(context: Context, value: Boolean?): String {
        return when (value) {
            true -> context.getString(R.string.task_details_boolean_enabled)
            false -> context.getString(R.string.task_details_boolean_disabled)
            null -> context.getString(R.string.task_details_value_unavailable)
        }
    }

    @ColorRes
    fun statusColorRes(status: String): Int {
        return when (status) {
            PortalTaskTracking.STATUS_COMPLETED -> R.color.task_status_completed
            PortalTaskTracking.STATUS_FAILED -> R.color.task_status_failed
            PortalTaskTracking.STATUS_CANCELLED -> R.color.task_status_cancelled
            PortalTaskTracking.STATUS_CANCELLING -> R.color.task_status_cancelling
            PortalTaskTracking.STATUS_RUNNING -> R.color.task_status_running
            PortalTaskTracking.STATUS_CREATED -> R.color.task_status_created
            PortalTaskTracking.STATUS_PAUSED -> R.color.task_status_paused
            PortalTaskTracking.STATUS_TRACKING_TIMEOUT -> R.color.task_status_tracking_timeout
            else -> R.color.task_status_unknown
        }
    }

    fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    fun formatTimeAgo(ms: Long): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        return when {
            days > 0 -> "${days}d ago"
            hours > 0 -> "${hours}h ago"
            minutes > 0 -> "${minutes}m ago"
            else -> "just now"
        }
    }
}
