package com.mobilerun.portal.config

internal object CloudTokenNormalizer {
    fun normalize(value: String?): String? {
        val trimmed = value?.trim() ?: return null
        return trimmed.takeUnless { it.isEmpty() || it.equals("null", ignoreCase = true) }
    }
}
