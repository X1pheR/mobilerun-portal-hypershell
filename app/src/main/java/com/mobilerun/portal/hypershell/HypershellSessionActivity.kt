package com.mobilerun.portal.hypershell

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.mobilerun.portal.config.ConfigManager
import com.mobilerun.portal.service.ReverseConnectionService

class HypershellSessionActivity : Activity() {
    companion object {
        private const val TAG = "HypershellSession"
        private const val PREFS_NAME = "hypershell_bootstrap"
        private const val KEY_LAST_START_AT_MS = "last_start_at_ms"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = ConfigManager.getInstance(applicationContext)
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val nowMs = SystemClock.elapsedRealtime()
        val lastStartAtMs = prefs.getLong(KEY_LAST_START_AT_MS, 0L)
        val allowed = HypershellBootstrapPolicy.shouldStart(
            action = intent?.action,
            reverseUrl = config.reverseConnectionUrlOrDefault,
            defaultReverseUrl = config.defaultReverseConnectionUrl,
            token = config.reverseConnectionToken,
            lastStartAtMs = lastStartAtMs,
            nowMs = nowMs,
        )

        if (!allowed) {
            Log.w(TAG, "Ignoring rejected or rate-limited on-demand start request")
            finish()
            return
        }

        prefs.edit { putLong(KEY_LAST_START_AT_MS, nowMs) }
        val serviceIntent = Intent(this, ReverseConnectionService::class.java).apply {
            action = ReverseConnectionService.ACTION_HYPERSHELL_START_SESSION
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        finish()
    }
}
