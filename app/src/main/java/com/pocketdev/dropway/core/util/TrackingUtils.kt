package com.pocketdev.dropway.core.util

object TrackingUtils {
    /**
     * Normalizes tracking number by removing spaces, hyphens, and converting to uppercase.
     */
    fun normalize(raw: String): String {
        return raw.trim().replace("\\s+".toRegex(), "").replace("-", "").uppercase()
    }

    fun isPossibleTrackingNumber(input: String): Boolean {
        val norm = normalize(input)
        return norm.length in 8..35 && norm.all { it.isLetterOrDigit() }
    }
}
