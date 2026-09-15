package com.mobilerun.portal.input

import com.mobilerun.portal.R

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.Toast
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MobilerunKeyboardIME : InputMethodService() {
    private val TAG = "MobilerunKeyboardIME"
    @Volatile
    private var inputGeneration = 0L
    private val textEditor by lazy {
        InputConnectionTextEditor(
            connectionProvider = { currentInputConnection },
            generationProvider = { inputGeneration },
            sleep = SystemClock::sleep,
        )
    }

    companion object {
        private var instance: MobilerunKeyboardIME? = null
        
        fun getInstance(): MobilerunKeyboardIME? = instance
        
        /**
         * Check if the MobilerunKeyboardIME is currently active and available
         */
        fun isAvailable(): Boolean = instance != null

        /**
         * Check if this IME is currently selected as the system default
         */
        fun isSelected(context: android.content.Context): Boolean {
            val currentId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.DEFAULT_INPUT_METHOD
            )
            val myId = android.content.ComponentName(context, MobilerunKeyboardIME::class.java).flattenToShortString()
            return currentId == myId
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "MobilerunKeyboardIME: onCreate() called")
    }


    /**
     * Direct method to input text from Base64 without using broadcasts
     */
    fun inputB64Text(base64Text: String, clear: Boolean = true): Boolean {
        return inputB64TextResult(base64Text, clear) == TextInputResult.Verified
    }

    internal fun inputB64TextResult(base64Text: String, clear: Boolean = true): TextInputResult {
        return try {
            val decoded = Base64.decode(base64Text, Base64.DEFAULT)
            val text = String(decoded, Charsets.UTF_8)
            inputTextResult(text, clear)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding base64 for direct input", e)
            TextInputResult.Rejected
        }
    }
    
    fun inputText(text: String, clear: Boolean = true): Boolean {
        return inputTextResult(text, clear) == TextInputResult.Verified
    }

    internal fun inputTextResult(text: String, clear: Boolean = true): TextInputResult {
        return try {
            val result = textEditor.inputText(text, clear)
            when (result) {
                TextInputResult.Verified -> Log.d(TAG, "Text input verified (clear=$clear)")
                TextInputResult.Rejected -> Log.w(TAG, "Text input rejected (clear=$clear)")
                TextInputResult.AcceptedUnverified -> {
                    Log.w(TAG, "Text input accepted but could not be verified (clear=$clear)")
                }
                TextInputResult.CommitOutcomeUnknown -> {
                    Log.w(TAG, "Text input commit outcome is unknown (clear=$clear)")
                }
                TextInputResult.InputSessionChanged -> {
                    Log.w(TAG, "Input session changed during text input (clear=$clear)")
                }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error in text input", e)
            TextInputResult.Rejected
        }
    }
    

    /**
     * Direct method to clear text without using broadcasts
     */
    fun clearText(): Boolean {
        return clearTextResult() == TextInputResult.Verified
    }

    internal fun clearTextResult(): TextInputResult {
        return inputTextResult("", clear = true)
    }

    /**
     * Direct method to send key events without using broadcasts
     */
    fun sendKeyEventDirect(keyCode: Int): Boolean {
        return try {
            val ic = currentInputConnection
            if (ic != null) {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
                Log.d(TAG, "Direct key event sent: $keyCode")
                true
            } else {
                Log.w(TAG, "No input connection available for direct key event")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending direct key event", e)
            false
        }
    }

    /**
     * Get current input connection status
     */
    fun hasInputConnection(): Boolean {
        return currentInputConnection != null
    }

    fun getClipboardText(): String? {
        return try {
            runOnMainThreadBlocking {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    ?: return@runOnMainThreadBlocking null
                clipboard.primaryClip
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.coerceToText(this)
                    ?.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read clipboard", e)
            null
        }
    }

    fun setClipboardText(text: String): Boolean {
        return try {
            runOnMainThreadBlocking {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    ?: return@runOnMainThreadBlocking false
                clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set clipboard", e)
            false
        }
    }

    private fun <T> runOnMainThreadBlocking(block: () -> T): T? {
        if (!shouldUseMainThreadClipboardAccess()) {
            return block()
        }

        var result: T? = null
        var failure: Throwable? = null
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            try {
                result = block()
            } catch (t: Throwable) {
                failure = t
            } finally {
                latch.countDown()
            }
        }
        if (!latch.await(2, TimeUnit.SECONDS)) {
            throw IllegalStateException("Timed out waiting for main thread clipboard access")
        }
        failure?.let { throw it }
        return result
    }

    private fun shouldUseMainThreadClipboardAccess(): Boolean {
        return Build.VERSION.SDK_INT > 0 &&
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1 &&
            Looper.myLooper() == null
    }

    override fun onCreateInputView(): View {
        Log.d(TAG, "onCreateInputView called")

        val view = layoutInflater.inflate(R.layout.keyboard_view, null)
        view.findViewById<Button>(R.id.switch_keyboard_button)?.setOnClickListener {
            handleSwitchKeyboard()
        }
        return view
    }

    override fun onStartInput(attribute: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        inputGeneration++
        super.onStartInput(attribute, restarting)
        Log.d(TAG, "onStartInput called - restarting: $restarting")
    }

    override fun onFinishInput() {
        inputGeneration++
        super.onFinishInput()
    }

    override fun onStartInputView(attribute: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(attribute, restarting)
        Log.d(TAG, "onStartInputView called - keyboard should be visible now")
    }

    override fun onDestroy() {
        Log.d(TAG, "MobilerunKeyboardIME: onDestroy() called")
        instance = null
        super.onDestroy()
    }

    private fun handleSwitchKeyboard() {
        if (showInputMethodPickerIfAlternativeExists()) return
        openInputMethodSettings()
    }

    private fun showInputMethodPickerIfAlternativeExists(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return false
        if (imm.enabledInputMethodList.size <= 1) return false

        return try {
            imm.showInputMethodPicker()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to show input method picker", e)
            false
        }
    }

    private fun openInputMethodSettings() {
        try {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Toast.makeText(this, R.string.keyboard_switch_settings_help, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error opening keyboard settings", e)
            Toast.makeText(this, R.string.keyboard_switch_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
}
