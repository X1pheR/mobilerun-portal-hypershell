package com.mobilerun.portal.taskprompt

data class TaskPromptModelUiState(
    val showRetry: Boolean,
    val submissionEnabled: Boolean,
) {
    companion object {
        fun forCachedModels(
            isModelsLoading: Boolean,
            hasModels: Boolean,
            isSubmitting: Boolean,
            hasBlockingTask: Boolean,
        ): TaskPromptModelUiState {
            return TaskPromptModelUiState(
                showRetry = !isModelsLoading && !hasModels,
                submissionEnabled = hasModels && !isModelsLoading && !isSubmitting && !hasBlockingTask,
            )
        }
    }
}
