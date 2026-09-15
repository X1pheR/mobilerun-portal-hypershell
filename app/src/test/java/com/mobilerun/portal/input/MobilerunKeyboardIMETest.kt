package com.mobilerun.portal.input

import org.junit.Assert.assertEquals
import org.junit.Test

class MobilerunKeyboardIMETest {
    @Test
    fun restartingInput_advancesGeneration() {
        val ime = MobilerunKeyboardIME()
        val before = generationOf(ime)

        invokeStartInput(ime, restarting = true)

        assertEquals(before + 1, generationOf(ime))
    }

    private fun invokeStartInput(ime: MobilerunKeyboardIME, restarting: Boolean) {
        try {
            ime.onStartInput(null, restarting)
        } catch (error: RuntimeException) {
            if (!error.message.orEmpty().contains("not mocked")) {
                throw error
            }
        }
    }

    private fun generationOf(ime: MobilerunKeyboardIME): Long {
        val field = MobilerunKeyboardIME::class.java.getDeclaredField("inputGeneration")
        field.isAccessible = true
        return field.getLong(ime)
    }
}
