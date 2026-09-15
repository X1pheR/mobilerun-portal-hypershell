package com.mobilerun.portal.ui.taskprompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskHistoryQueryStateTest {
    @Test
    fun normalizeQuery_trimsWhitespaceAndHandlesNull() {
        assertEquals("", TaskHistoryQueryState.normalizeQuery(null))
        assertEquals("open settings", TaskHistoryQueryState.normalizeQuery("  open settings  "))
    }

    @Test
    fun shouldLoadHistory_returnsTrueWhenHistoryHasNotLoaded() {
        assertTrue(
            TaskHistoryQueryState.shouldLoadHistory(
                hasLoadedHistory = false,
                loadedHistoryQuery = "open settings",
                currentQuery = "open settings",
            ),
        )
    }

    @Test
    fun shouldLoadHistory_returnsTrueWhenCurrentQueryDiffersFromLoadedQuery() {
        assertTrue(
            TaskHistoryQueryState.shouldLoadHistory(
                hasLoadedHistory = true,
                loadedHistoryQuery = "",
                currentQuery = "open settings",
            ),
        )
    }

    @Test
    fun shouldLoadHistory_returnsFalseWhenLoadedQueryMatchesCurrentQuery() {
        assertFalse(
            TaskHistoryQueryState.shouldLoadHistory(
                hasLoadedHistory = true,
                loadedHistoryQuery = "open settings",
                currentQuery = "open settings",
            ),
        )
    }

    @Test
    fun hasQueryChangedSinceRequest_returnsFalseWhenQueriesMatch() {
        assertFalse(
            TaskHistoryQueryState.hasQueryChangedSinceRequest(
                requestQuery = "open settings",
                currentQuery = "open settings",
            ),
        )
    }

    @Test
    fun hasQueryChangedSinceRequest_returnsTrueWhenCurrentQueryDiffers() {
        assertTrue(
            TaskHistoryQueryState.hasQueryChangedSinceRequest(
                requestQuery = "open settings",
                currentQuery = "close settings",
            ),
        )
    }

    @Test
    fun hasQueryChangedSinceRequest_detectsBlankTransitions() {
        assertTrue(
            TaskHistoryQueryState.hasQueryChangedSinceRequest(
                requestQuery = "",
                currentQuery = "open settings",
            ),
        )
        assertTrue(
            TaskHistoryQueryState.hasQueryChangedSinceRequest(
                requestQuery = "open settings",
                currentQuery = "",
            ),
        )
    }
}
