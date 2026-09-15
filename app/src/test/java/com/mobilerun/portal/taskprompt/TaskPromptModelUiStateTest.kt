package com.mobilerun.portal.taskprompt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskPromptModelUiStateTest {

    @Test
    fun cachedEmptyModels_showRetryAndDisableSubmit() {
        val state = TaskPromptModelUiState.forCachedModels(
            isModelsLoading = false,
            hasModels = false,
            isSubmitting = false,
            hasBlockingTask = false,
        )

        assertTrue(state.showRetry)
        assertFalse(state.submissionEnabled)
    }

    @Test
    fun cachedLoadedModels_hideRetryAndAllowSubmit() {
        val state = TaskPromptModelUiState.forCachedModels(
            isModelsLoading = false,
            hasModels = true,
            isSubmitting = false,
            hasBlockingTask = false,
        )

        assertFalse(state.showRetry)
        assertTrue(state.submissionEnabled)
    }

    @Test
    fun loadingModels_hideRetryAndDisableSubmit() {
        val state = TaskPromptModelUiState.forCachedModels(
            isModelsLoading = true,
            hasModels = false,
            isSubmitting = false,
            hasBlockingTask = false,
        )

        assertFalse(state.showRetry)
        assertFalse(state.submissionEnabled)
    }
}
