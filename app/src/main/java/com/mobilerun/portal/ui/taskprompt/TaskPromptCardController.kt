package com.mobilerun.portal.ui.taskprompt

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.mobilerun.portal.R
import com.mobilerun.portal.databinding.ViewTaskPromptCardBinding
import com.mobilerun.portal.taskprompt.PortalModelOption
import com.mobilerun.portal.taskprompt.PortalTaskDraft
import com.mobilerun.portal.taskprompt.PortalTaskSettings

class TaskPromptCardController(
    private val context: Context,
    layoutInflater: LayoutInflater,
    private val onSubmit: (PortalTaskDraft) -> Unit,
    private val onReturnToPortalChanged: (Boolean) -> Unit,
    private val onCancelTask: () -> Unit,
    private val onOpenTaskDetails: (String) -> Unit,
    private val onOpenTaskHistory: () -> Unit,
    private val onRetryModels: () -> Unit,
) {
    data class TaskStateViewModel(
        val taskId: String,
        val statusLabel: String,
        val statusKind: StatusKind,
        val promptPreview: String? = null,
        val summary: String? = null,
        val isClickable: Boolean = false,
        val isBlocking: Boolean = false,
        val canCancel: Boolean = false,
        val cancelInFlight: Boolean = false,
    )

    enum class StatusKind {
        INFO,
        SUCCESS,
        ERROR,
    }

    private val binding = ViewTaskPromptCardBinding.inflate(layoutInflater)

    private var currentSettings = PortalTaskSettings()
    private var isModelsLoading = false
    private var isSubmitting = false
    private var canSubmit = false
    private var isFormEnabled = true
    private var isHistoryEnabled = false
    private var taskState: TaskStateViewModel? = null
    private var suppressReturnToPortalChange = false
    private val settingsController = TaskPromptSettingsPanelController(
        context,
        binding.taskPromptSettingsPanel,
    )

    init {
        binding.taskPromptHistoryButton.setOnClickListener { onOpenTaskHistory() }

        binding.taskPromptCancelButton.setOnClickListener {
            onCancelTask()
        }
        binding.taskPromptRetryModelsButton.setOnClickListener {
            onRetryModels()
        }
        binding.taskPromptReturnToPortalSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!suppressReturnToPortalChange) {
                onReturnToPortalChanged(isChecked)
            }
        }

        binding.taskPromptSubmitButton.setOnClickListener {
            val draft = buildDraft() ?: return@setOnClickListener
            onSubmit(draft)
        }

        updateSubmitButtonState()
        applyTaskState(null)
    }

    fun attachTo(container: ViewGroup) {
        if (binding.root.parent === container) return
        (binding.root.parent as? ViewGroup)?.removeView(binding.root)
        container.removeAllViews()
        container.addView(binding.root)
    }

    fun setVisible(visible: Boolean) {
        binding.root.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun applySettings(settings: PortalTaskSettings, preservePrompt: Boolean = true) {
        currentSettings = settings
        settingsController.applySettings(settings)
        if (!preservePrompt) {
            binding.taskPromptInput.setText("")
        }
    }

    fun setReturnToPortalChecked(checked: Boolean) {
        if (binding.taskPromptReturnToPortalSwitch.isChecked == checked) return
        suppressReturnToPortalChange = true
        binding.taskPromptReturnToPortalSwitch.isChecked = checked
        suppressReturnToPortalChange = false
    }

    fun setModelOptions(options: List<PortalModelOption>) {
        settingsController.setModelOptions(options)
        updateSubmitButtonState()
    }

    fun setTaskState(state: TaskStateViewModel?) {
        taskState = state
        applyTaskState(state)
        updateSubmitButtonState()
    }

    fun setModelsLoading(loading: Boolean) {
        isModelsLoading = loading
        settingsController.setModelsLoading(loading)
        if (loading) {
            setModelRetryVisible(false)
        }
        updateSubmitButtonState()
    }

    fun setModelRetryVisible(visible: Boolean) {
        val retryButton = binding.taskPromptRetryModelsButton
        retryButton.visibility = if (visible) View.VISIBLE else View.GONE
        retryButton.isEnabled = visible && !isModelsLoading && isFormEnabled
    }

    fun setSubmissionEnabled(enabled: Boolean) {
        canSubmit = enabled
        updateSubmitButtonState()
    }

    fun setSubmitting(submitting: Boolean) {
        isSubmitting = submitting
        updateSubmitButtonState()
    }

    fun setHistoryEnabled(enabled: Boolean) {
        isHistoryEnabled = enabled
        binding.taskPromptHistoryButton.isEnabled = enabled
        binding.taskPromptHistoryButton.alpha = if (enabled) 1f else 0.5f
    }

    fun setFormEnabled(enabled: Boolean) {
        isFormEnabled = enabled
        binding.taskPromptInput.isEnabled = enabled
        binding.taskPromptReturnToPortalSwitch.isEnabled = enabled
        settingsController.setEnabled(enabled)
        updateSubmitButtonState()
    }

    fun clearPrompt() {
        binding.taskPromptInput.setText("")
        binding.taskPromptInputLayout.error = null
    }

    fun clearTaskId() {
        // No-op. Task IDs are surfaced through task details metadata only.
    }

    fun showTaskCreated(taskId: String) {
        if (taskId.isBlank()) return
        showStatus(context.getString(R.string.task_prompt_started), StatusKind.SUCCESS)
    }

    fun showStatus(message: String?, kind: StatusKind) {
        if (message.isNullOrBlank()) {
            binding.taskPromptStatusText.visibility = View.GONE
            binding.taskPromptStatusText.text = ""
            return
        }

        binding.taskPromptStatusText.visibility = View.VISIBLE
        binding.taskPromptStatusText.text = message
        val colorRes = when (kind) {
            StatusKind.INFO -> R.color.task_prompt_text_secondary
            StatusKind.SUCCESS -> R.color.task_prompt_accent_light
            StatusKind.ERROR -> R.color.task_prompt_error
        }
        binding.taskPromptStatusText.setTextColor(ContextCompat.getColor(context, colorRes))
    }

    fun clearStatus() {
        showStatus(null, StatusKind.INFO)
    }

    private fun updateSubmitButtonState() {
        val showRunningState = taskState?.isBlocking == true && taskState?.cancelInFlight != true
        binding.taskPromptSubmitButton.text = when {
            isSubmitting -> context.getString(R.string.task_prompt_starting_button)
            showRunningState -> context.getString(R.string.task_prompt_running_button)
            else -> context.getString(R.string.task_prompt_submit)
        }
        binding.taskPromptSubmitProgress.visibility = if (showRunningState) View.VISIBLE else View.GONE

        binding.taskPromptCancelButton.text = if (taskState?.cancelInFlight == true) {
            context.getString(R.string.task_prompt_cancelling_button)
        } else {
            context.getString(R.string.task_prompt_cancel_button)
        }

        val hasModels = settingsController.hasModels()
        binding.taskPromptSubmitButton.isEnabled =
            isFormEnabled && canSubmit && !isModelsLoading && !isSubmitting && !showRunningState && hasModels
        binding.taskPromptCancelButton.isEnabled =
            taskState?.canCancel == true && taskState?.cancelInFlight != true
        binding.taskPromptHistoryButton.isEnabled = isHistoryEnabled
        binding.taskPromptHistoryButton.alpha = if (isHistoryEnabled) 1f else 0.5f
        val retryButton = binding.taskPromptRetryModelsButton
        retryButton.isEnabled = retryButton.visibility == View.VISIBLE && !isModelsLoading && isFormEnabled
    }

    private fun buildDraft(): PortalTaskDraft? {
        binding.taskPromptInputLayout.error = null

        val prompt = binding.taskPromptInput.text?.toString()?.trim().orEmpty()
        if (prompt.isBlank()) {
            binding.taskPromptInputLayout.error = context.getString(R.string.task_prompt_required)
            return null
        }

        currentSettings = settingsController.buildSettingsOrShowErrors() ?: return null

        return PortalTaskDraft(
            prompt = prompt,
            settings = currentSettings,
            returnToPortalOnTerminal = binding.taskPromptReturnToPortalSwitch.isChecked,
        )
    }

    private fun applyTaskState(state: TaskStateViewModel?) {
        binding.taskPromptTaskStateContainer.visibility = if (state == null) View.GONE else View.VISIBLE
        if (state == null) {
            binding.taskPromptLatestLabel.visibility = View.GONE
            binding.taskPromptTaskStateSummary.visibility = View.GONE
            binding.taskPromptTaskStateDetail.visibility = View.GONE
            binding.taskPromptCancelButton.visibility = View.GONE
            binding.taskPromptTaskStateContainer.isClickable = false
            binding.taskPromptTaskStateContainer.isFocusable = false
            binding.taskPromptTaskStateContainer.setOnClickListener(null)
            return
        }

        binding.taskPromptLatestLabel.visibility = View.VISIBLE
        binding.taskPromptTaskStateChip.text = state.statusLabel
        binding.taskPromptTaskStateChip.background = createChipBackground(state.statusKind)
        binding.taskPromptTaskStateChip.setTextColor(ContextCompat.getColor(context, R.color.task_prompt_text_primary))

        binding.taskPromptTaskStateDetail.text = state.promptPreview.orEmpty()
        binding.taskPromptTaskStateDetail.visibility =
            if (state.promptPreview.isNullOrBlank()) View.GONE else View.VISIBLE

        binding.taskPromptTaskStateSummary.text = state.summary.orEmpty()
        binding.taskPromptTaskStateSummary.visibility =
            if (state.summary.isNullOrBlank()) View.GONE else View.VISIBLE

        binding.taskPromptCancelButton.visibility =
            if (state.canCancel || state.cancelInFlight) View.VISIBLE else View.GONE

        binding.taskPromptTaskStateContainer.isClickable = state.isClickable
        binding.taskPromptTaskStateContainer.isFocusable = state.isClickable
        binding.taskPromptTaskStateContainer.setOnClickListener(
            if (state.isClickable) {
                View.OnClickListener { onOpenTaskDetails(state.taskId) }
            } else {
                null
            },
        )
    }

    private fun createChipBackground(kind: StatusKind): GradientDrawable {
        val backgroundColor = when (kind) {
            StatusKind.INFO -> ContextCompat.getColor(context, R.color.task_prompt_chip_info_bg)
            StatusKind.SUCCESS -> ContextCompat.getColor(context, R.color.task_prompt_chip_success_bg)
            StatusKind.ERROR -> ContextCompat.getColor(context, R.color.task_prompt_chip_error_bg)
        }

        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 999f
            setColor(backgroundColor)
        }
    }

}
