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

    fun generateTracking(prefix: String = "DW"): String {
        val timestamp = (System.currentTimeMillis() / 1000) % 1000000
        val random = (1000..9999).random()
        return "$prefix$timestamp$random"
    }
}
