package com.mobilerun.portal.ui

import com.mobilerun.portal.state.ConnectionState

object ConnectionCardUiPolicy {
    fun shouldShowSignOut(
        state: ConnectionState,
        hasCloudCredentials: Boolean,
    ): Boolean {
        if (!hasCloudCredentials) return false
        return when (state) {
            ConnectionState.CONNECTED,
            ConnectionState.CONNECTING,
            ConnectionState.RECONNECTING,
            ConnectionState.UNAUTHORIZED,
            ConnectionState.LIMIT_EXCEEDED,
            ConnectionState.BAD_REQUEST,
            ConnectionState.ERROR -> true
            else -> false
        }
    }
}
