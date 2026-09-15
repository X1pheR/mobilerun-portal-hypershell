package com.mobilerun.portal.ui.taskprompt

internal object TaskHistoryQueryState {
    fun normalizeQuery(raw: CharSequence?): String {
        return raw?.toString()?.trim().orEmpty()
    }

    fun shouldLoadHistory(
        hasLoadedHistory: Boolean,
        loadedHistoryQuery: String?,
        currentQuery: String,
    ): Boolean {
        return !hasLoadedHistory || loadedHistoryQuery != currentQuery
    }

    fun hasQueryChangedSinceRequest(
        requestQuery: String,
        currentQuery: String,
    ): Boolean {
        return requestQuery != currentQuery
    }
}
